import {DatabaseSync} from 'node:sqlite';
import {mkdirSync, chmodSync, statSync} from 'node:fs';
import {dirname, resolve} from 'node:path';
import {ErrorServicio} from './errors.js';

export function createAuthState(filename, {initAuthCreds, BufferJSON, proto}) {
  if (!filename || filename === ':memory:') throw new ErrorServicio('SESION_INVALIDA', 'La sesión debe persistirse en un archivo privado.');
  mkdirSync(dirname(resolve(filename)), {recursive: true, mode: 0o700});
  if (process.platform !== 'win32' && (statSync(dirname(resolve(filename))).mode & 0o077) !== 0) {
    throw new ErrorServicio('SESION_INVALIDA', 'El directorio de sesión debe ser privado.');
  }
  const db = new DatabaseSync(filename);
  try {
    if (process.platform !== 'win32') chmodSync(filename, 0o600);
    db.exec('PRAGMA busy_timeout=5000; PRAGMA secure_delete=ON; PRAGMA synchronous=FULL; PRAGMA journal_mode=DELETE;');
    db.exec('CREATE TABLE IF NOT EXISTS auth (tipo TEXT NOT NULL, id TEXT NOT NULL, valor TEXT NOT NULL, PRIMARY KEY(tipo,id))');
    const get = db.prepare('SELECT valor FROM auth WHERE tipo=? AND id=?');
    const put = db.prepare('INSERT INTO auth(tipo,id,valor) VALUES (?,?,?) ON CONFLICT(tipo,id) DO UPDATE SET valor=excluded.valor');
    const del = db.prepare('DELETE FROM auth WHERE tipo=? AND id=?');
    const serialize = value => JSON.stringify(value, BufferJSON.replacer);
    const deserialize = value => JSON.parse(value, BufferJSON.reviver);
    const saved = get.get('creds', 'principal');
    const creds = saved ? deserialize(saved.valor) : initAuthCreds();
    const state = {
      creds,
      keys: {
        async get(type, ids) {
          const output = {};
          for (const id of ids) {
            const row = get.get(type, id);
            let value = row ? deserialize(row.valor) : undefined;
            if (type === 'app-state-sync-key' && value) value = proto.Message.AppStateSyncKeyData.fromObject(value);
            output[id] = value;
          }
          return output;
        },
        async set(data) {
          db.exec('BEGIN IMMEDIATE');
          try {
            for (const [type, items] of Object.entries(data)) {
              if (!items) continue;
              for (const [id, value] of Object.entries(items)) {
                if (value === null || value === undefined) del.run(type, id);
                else put.run(type, id, serialize(value));
              }
            }
            db.exec('COMMIT');
          } catch (error) {
            db.exec('ROLLBACK');
            throw error;
          }
        }
      }
    };
    return {
      state,
      async saveCreds() { put.run('creds', 'principal', serialize(creds)); },
      close() { db.close(); }
    };
  } catch (error) {
    db.close();
    throw error;
  }
}
