import {DatabaseSync} from 'node:sqlite';
import {chmodSync, mkdirSync} from 'node:fs';
import {dirname} from 'node:path';
import {T091Error} from './errors.js';
import {requireDate} from './datetime.js';

export const TTL_MS = 30 * 60 * 1000;
export const STATUS = Object.freeze({PENDING: 'PENDIENTE', PROCESSED: 'PROCESADO', DISCARDED: 'DESCARTADO'});
const SCHEMA_COLUMNS = ['id','grupo','fecha','fecha_ms','texto','estado','finalizado_ms'];
function validate(message) {
  if (!message || typeof message !== 'object' || Array.isArray(message)) throw new T091Error('MENSAJE_INVALIDO','Mensaje inválido.');
  const keys = Object.keys(message);
  if (keys.length !== 4 || keys.some(x => !['id','group','date','text'].includes(x))) throw new T091Error('MENSAJE_INVALIDO','Campos de mensaje no permitidos.');
  for (const k of keys) {
    if (typeof message[k] !== 'string' || !message[k].trim()) throw new T091Error('MENSAJE_INVALIDO', 'Un campo obligatorio está vacío.');
  }
  if (message.id.length > 256 || message.group.length > 256 || message.text.length > 100000) throw new T091Error('MENSAJE_INVALIDO','Mensaje supera los límites.');
  let ms;
  try {ms=requireDate(message.date);} catch {throw new T091Error('MENSAJE_INVALIDO','La fecha debe incluir zona horaria.');}
  return ms;
}
export class MessageStore {
  constructor(filename, {clock=Date.now}={}) {
    if (typeof clock !== 'function') throw new TypeError('Se necesita un reloj.');
    if (typeof filename !== 'string' || !filename) throw new TypeError('Ruta SQLite inválida.');
    this.clock=clock;
    if (filename!==':memory:') mkdirSync(dirname(filename), {recursive:true, mode:0o700});
    this.db=new DatabaseSync(filename);
    try {
      if (filename!==':memory:' && process.platform!=='win32') chmodSync(filename, 0o600);
      this.db.exec('PRAGMA busy_timeout=5000; PRAGMA secure_delete=ON; PRAGMA synchronous=FULL; PRAGMA journal_mode=DELETE;');
      this.db.exec(`CREATE TABLE IF NOT EXISTS mensaje_pendiente (
        id TEXT PRIMARY KEY NOT NULL,
        grupo TEXT NOT NULL,
        fecha TEXT NOT NULL,
        fecha_ms INTEGER NOT NULL,
        texto TEXT,
        estado TEXT NOT NULL CHECK(estado IN ('PENDIENTE','PROCESADO','DESCARTADO')),
        finalizado_ms INTEGER,
        CHECK((estado='PENDIENTE' AND texto IS NOT NULL AND finalizado_ms IS NULL)
           OR (estado IN ('PROCESADO','DESCARTADO') AND texto IS NULL AND finalizado_ms IS NOT NULL))
      )`);
      const found=this.db.prepare('PRAGMA table_info(mensaje_pendiente)').all().map(row=>row.name);
      if (JSON.stringify(found)!==JSON.stringify(SCHEMA_COLUMNS)) {
        throw new T091Error('ESQUEMA_NO_PERMITIDO','La tabla debe tener exactamente las siete columnas autorizadas.');
      }
      this.db.exec("CREATE INDEX IF NOT EXISTS ix_pendiente_caducidad ON mensaje_pendiente(fecha_ms) WHERE estado='PENDIENTE'");
      this.insert=this.db.prepare(`INSERT OR IGNORE INTO mensaje_pendiente
        (id, grupo, fecha, fecha_ms, texto, estado, finalizado_ms) VALUES (?, ?, ?, ?, ?, ?, ?)`);
      this.expire=this.db.prepare(`UPDATE mensaje_pendiente SET estado='DESCARTADO', texto=NULL, finalizado_ms=?
        WHERE estado='PENDIENTE' AND fecha_ms < ?`);
      this.finish=this.db.prepare(`UPDATE mensaje_pendiente SET estado=?, texto=NULL, finalizado_ms=?
        WHERE id=? AND estado='PENDIENTE'`);
    } catch(error) {this.db.close();throw error;}
  }
  savePending(message) {
    const ms=validate(message), now=this.clock();
    const expired=ms<now-TTL_MS;
    const outcome=this.insert.run(message.id,message.group,message.date,ms,
      expired?null:message.text, expired?STATUS.DISCARDED:STATUS.PENDING, expired?now:null);
    if (outcome.changes===0) return {inserted:false,status:this.getById(message.id)?.estado??null};
    return {inserted:true,status:expired?STATUS.DISCARDED:STATUS.PENDING};
  }
  getById(id) {
    const found=this.db.prepare('SELECT id,grupo,fecha,texto,estado FROM mensaje_pendiente WHERE id=?').get(id);
    return found?{...found}:null;
  }
  listPending(limit=1000) {
    if (!Number.isSafeInteger(limit) || limit<1 || limit>10000) throw new RangeError('Límite inválido.');
    return this.db.prepare(`SELECT id,grupo,fecha,texto,estado FROM mensaje_pendiente
      WHERE estado='PENDIENTE' AND fecha_ms>=? ORDER BY fecha_ms,id LIMIT ?`).all(this.clock()-TTL_MS,limit).map(row=>({...row}));
  }
  countPending() {
    return Number(this.db.prepare("SELECT COUNT(*) AS n FROM mensaje_pendiente WHERE estado='PENDIENTE' AND fecha_ms>=?").get(this.clock()-TTL_MS).n);
  }
  nextExpirationMs() {
    const row=this.db.prepare("SELECT MIN(fecha_ms) AS t FROM mensaje_pendiente WHERE estado='PENDIENTE'").get();
    return row.t===null?null:Number(row.t)+TTL_MS+1;
  }
  expireOldPending() {return Number(this.expire.run(this.clock(),this.clock()-TTL_MS).changes);}
  markProcessed(id) {return this.#finish(id,STATUS.PROCESSED);}
  markDiscarded(id) {return this.#finish(id,STATUS.DISCARDED);}
  #finish(id,status) {return Number(this.finish.run(status,this.clock(),id).changes);}
  close() {this.db.close();}
}
