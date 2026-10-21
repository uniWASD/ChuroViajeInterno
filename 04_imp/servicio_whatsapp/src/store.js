import {DatabaseSync} from 'node:sqlite';
import {chmodSync, mkdirSync} from 'node:fs';
import {dirname} from 'node:path';
import {ErrorServicio} from './errors.js';
import {requireDate} from './datetime.js';

// RN-06.11 y RN-06.12: ningún texto puede sobrevivir más de 30 min desde la
// recepción, incluso cuando la fecha declarada por el teléfono está en el futuro.
export const TTL_MS = 30 * 60 * 1000;
// Las filas finalizadas evitan reingresar duplicados durante 24 h; ya no tienen texto.
export const FINISHED_RETENTION_MS = 24 * 60 * 60 * 1000;
export const STATUS = Object.freeze({PENDING: 'PENDIENTE', PROCESSED: 'PROCESADO', DISCARDED: 'DESCARTADO'});
const LEGACY_COLUMNS = ['id', 'grupo', 'fecha', 'fecha_ms', 'texto', 'estado', 'finalizado_ms'];
const REQUIRED_COLUMNS = [...LEGACY_COLUMNS, 'recibido_ms'];
const AGE_BASE = '(CASE WHEN fecha_ms < recibido_ms THEN fecha_ms ELSE recibido_ms END)';
// CP-26: nuevos campos técnicos/tablas son posibles, pero nunca datos de remitentes.
const SENDER_FIELDS = /remitente|sender|autor|author|tel[eé]fono|telef[oó]nico|n[uú]mero|phone|nombre|contacto|usuario|wa_?id|participant|jid/i;
const CREATE_TABLE = `CREATE TABLE IF NOT EXISTS mensaje_pendiente (
  id TEXT PRIMARY KEY NOT NULL,
  grupo TEXT NOT NULL,
  fecha TEXT NOT NULL,
  fecha_ms INTEGER NOT NULL,
  texto TEXT,
  estado TEXT NOT NULL CHECK(estado IN ('PENDIENTE','PROCESADO','DESCARTADO')),
  finalizado_ms INTEGER,
  recibido_ms INTEGER NOT NULL,
  CHECK((estado='PENDIENTE' AND texto IS NOT NULL AND finalizado_ms IS NULL)
    OR (estado IN ('PROCESADO','DESCARTADO') AND texto IS NULL AND finalizado_ms IS NOT NULL))
)`;

function validate(message) {
  if (!message || typeof message !== 'object' || Array.isArray(message)) throw new ErrorServicio('MENSAJE_INVALIDO', 'Mensaje inválido.');
  const keys = Object.keys(message);
  if (keys.length !== 4 || keys.some(x => !['id','group','date','text'].includes(x))) throw new ErrorServicio('MENSAJE_INVALIDO', 'Campos de mensaje no permitidos.');
  for (const key of keys) {
    if (typeof message[key] !== 'string' || !message[key].trim()) throw new ErrorServicio('MENSAJE_INVALIDO', 'Un campo obligatorio está vacío.');
  }
  if (message.id.length > 256 || message.group.length > 256 || message.text.length > 100000) throw new ErrorServicio('MENSAJE_INVALIDO', 'Mensaje supera los límites.');
  try { return requireDate(message.date); }
  catch { throw new ErrorServicio('MENSAJE_INVALIDO', 'La fecha debe incluir zona horaria.'); }
}
function schemaError() { throw new ErrorServicio('ESQUEMA_NO_PERMITIDO', 'El esquema SQLite carece de columnas requeridas o incluye datos privados.'); }

export class MessageStore {
  constructor(filename, {clock = Date.now} = {}) {
    if (typeof clock !== 'function') throw new TypeError('Se necesita un reloj.');
    if (typeof filename !== 'string' || !filename) throw new TypeError('Ruta SQLite inválida.');
    this.clock = clock;
    if (filename !== ':memory:') mkdirSync(dirname(filename), {recursive: true, mode: 0o700});
    this.db = new DatabaseSync(filename);
    try {
      if (filename !== ':memory:' && process.platform !== 'win32') chmodSync(filename, 0o600);
      this.db.exec('PRAGMA busy_timeout=5000; PRAGMA secure_delete=ON; PRAGMA synchronous=FULL; PRAGMA journal_mode=DELETE;');
      this.db.exec(CREATE_TABLE);
      this.#checkAndUpgradeSchema();
      this.db.exec(`CREATE INDEX IF NOT EXISTS ix_pendiente_caducidad
        ON mensaje_pendiente(${AGE_BASE}) WHERE estado='PENDIENTE'`);
      this.db.exec('CREATE INDEX IF NOT EXISTS ix_pendiente_finalizado ON mensaje_pendiente(finalizado_ms) WHERE estado!=\'PENDIENTE\'');
      this.insert = this.db.prepare(`INSERT OR IGNORE INTO mensaje_pendiente
        (id, grupo, fecha, fecha_ms, texto, estado, finalizado_ms, recibido_ms)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)`);
      this.expire = this.db.prepare(`UPDATE mensaje_pendiente SET estado='DESCARTADO', texto=NULL, finalizado_ms=?
        WHERE estado='PENDIENTE' AND ${AGE_BASE} < ?`);
      this.finish = this.db.prepare(`UPDATE mensaje_pendiente SET estado=?, texto=NULL, finalizado_ms=?
        WHERE id=? AND estado='PENDIENTE'`);
      this.purge = this.db.prepare("DELETE FROM mensaje_pendiente WHERE estado!='PENDIENTE' AND finalizado_ms < ?");
    } catch (error) { this.db.close(); throw error; }
  }
  #checkAndUpgradeSchema() {
    const version = this.db.prepare('PRAGMA user_version').get().user_version;
    if (version > 1) schemaError();
    let columns = this.db.prepare('PRAGMA table_info(mensaje_pendiente)').all().map(row => row.name);
    if (columns.some(name => SENDER_FIELDS.test(name))) schemaError();
    if (columns.length === LEGACY_COLUMNS.length && columns.every((name, i) => name === LEGACY_COLUMNS[i])) {
      // Migración 0 -> 1: los pendientes con fecha futura se descartan por
      // precaución, ya que el formato antiguo no almacenaba su recepción real.
      const now = this.clock();
      this.db.exec('BEGIN IMMEDIATE');
      try {
        this.db.exec('ALTER TABLE mensaje_pendiente RENAME TO mensaje_pendiente_legacy');
        this.db.exec(CREATE_TABLE);
        this.db.prepare(`INSERT INTO mensaje_pendiente
          (id,grupo,fecha,fecha_ms,texto,estado,finalizado_ms,recibido_ms)
          SELECT id,grupo,fecha,fecha_ms,
            CASE WHEN estado='PENDIENTE' AND fecha_ms>? THEN NULL ELSE texto END,
            CASE WHEN estado='PENDIENTE' AND fecha_ms>? THEN 'DESCARTADO' ELSE estado END,
            CASE WHEN estado='PENDIENTE' AND fecha_ms>? THEN ? ELSE finalizado_ms END,
            CASE WHEN fecha_ms>? THEN ? ELSE fecha_ms END
          FROM mensaje_pendiente_legacy`).run(now, now, now, now, now, now - TTL_MS - 1);
        this.db.exec('DROP TABLE mensaje_pendiente_legacy; PRAGMA user_version=1; COMMIT');
      } catch (error) { this.db.exec('ROLLBACK'); throw error; }
      columns = this.db.prepare('PRAGMA table_info(mensaje_pendiente)').all().map(row => row.name);
    }
    if (!REQUIRED_COLUMNS.every(name => columns.includes(name))) schemaError();
    if (version === 0) this.db.exec('PRAGMA user_version=1');
  }
  savePending(message) {
    // RN-06.3/06.4: solo texto y cuatro campos; sin datos de remitente.
    const ms = validate(message);
    const now = this.clock();
    const expired = Math.min(ms, now) < now - TTL_MS;
    const outcome = this.insert.run(message.id, message.group, message.date, ms,
      expired ? null : message.text, expired ? STATUS.DISCARDED : STATUS.PENDING,
      expired ? now : null, now);
    if (outcome.changes === 0) return {inserted: false, status: this.getById(message.id)?.estado ?? null};
    return {inserted: true, status: expired ? STATUS.DISCARDED : STATUS.PENDING};
  }
  getById(id) {
    const found = this.db.prepare('SELECT id,grupo,fecha,texto,estado FROM mensaje_pendiente WHERE id=?').get(id);
    return found ? {...found} : null;
  }
  listPending(limit = 1000) {
    if (!Number.isSafeInteger(limit) || limit < 1 || limit > 10000) throw new RangeError('Límite inválido.');
    return this.db.prepare(`SELECT id,grupo,fecha,texto,estado FROM mensaje_pendiente
      WHERE estado='PENDIENTE' AND ${AGE_BASE} >= ? ORDER BY fecha_ms,id LIMIT ?`)
      .all(this.clock() - TTL_MS, limit).map(row => ({...row}));
  }
  countPending() {
    return Number(this.db.prepare(`SELECT COUNT(*) AS n FROM mensaje_pendiente
      WHERE estado='PENDIENTE' AND ${AGE_BASE} >= ?`).get(this.clock() - TTL_MS).n);
  }
  nextExpirationMs() {
    const row = this.db.prepare(`SELECT MIN(${AGE_BASE}) AS t FROM mensaje_pendiente WHERE estado='PENDIENTE'`).get();
    return row.t === null ? null : Number(row.t) + TTL_MS + 1;
  }
  expireOldPending() {
    const now = this.clock();
    return Number(this.expire.run(now, now - TTL_MS).changes);
  }
  purgeFinished() { return Number(this.purge.run(this.clock() - FINISHED_RETENTION_MS).changes); }
  markProcessed(id) { return this.#finish(id, STATUS.PROCESSED); }
  markDiscarded(id) { return this.#finish(id, STATUS.DISCARDED); }
  #finish(id, status) { return Number(this.finish.run(status, this.clock(), id).changes); }
  close() { this.db.close(); }
}
