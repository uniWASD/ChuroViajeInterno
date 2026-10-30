import {existsSync, readFileSync, realpathSync} from 'node:fs';
import {isAbsolute, dirname, join, relative, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {ErrorServicio} from './errors.js';

export const SERVICE_ROOT = fileURLToPath(new URL('..', import.meta.url));
function invalid(message) { throw new ErrorServicio('CONFIG_INVALIDA', message); }
function required(value, key) {
  if (typeof value !== 'string' || !value.trim()) invalid(`Falta ${key}.`);
  return value.trim();
}
function isWithin(root, path) {
  const rel = relative(root, path);
  return rel === '' || (!rel.startsWith('..' + (process.platform === 'win32' ? '\\' : '/')) && rel !== '..' && !isAbsolute(rel));
}
function insideGitTree(path) {
  let dir = dirname(path);
  for (;;) {
    if (existsSync(join(dir, '.git'))) return true;
    const parent = dirname(dir);
    if (parent === dir) return false;
    dir = parent;
  }
}
function externalJson(filename) {
  if (filename === undefined || filename === '') return {};
  if (typeof filename !== 'string') invalid('Ruta externa de configuración inválida.');
  let path;
  try { path = realpathSync(resolve(filename)); }
  catch { invalid('No se puede leer el archivo de configuración externa.'); }
  if (isWithin(SERVICE_ROOT, path) || insideGitTree(path)) {
    invalid('El JSON de configuración debe estar fuera del servicio y del repositorio Git.');
  }
  let data;
  try { data = JSON.parse(readFileSync(path, 'utf8')); }
  catch { invalid('El JSON externo no se puede leer o no es válido.'); }
  if (!data || typeof data !== 'object' || Array.isArray(data)) invalid('El JSON externo debe ser un objeto.');
  return data;
}
function groups(raw) {
  const a = Array.isArray(raw) ? raw : (typeof raw === 'string' ? raw.split(',') : []);
  if (!a.length || a.some(x => typeof x !== 'string' || !x.trim() || /\s/.test(x.trim()))) {
    invalid('ALLOWED_GROUP_IDS requiere identificadores sin espacios.');
  }
  return Object.freeze([...new Set(a.map(x => x.trim()))]);
}
function backendUrl(raw) {
  const value = required(raw, 'BACKEND_URL');
  let u;
  try { u = new URL(value); } catch { invalid('BACKEND_URL no es válida.'); }
  if (!['http:', 'https:'].includes(u.protocol) || u.username || u.password || u.search || u.hash) invalid('BACKEND_URL contiene componentes no permitidos.');
  if (u.protocol === 'http:' && !['127.0.0.1', '[::1]', 'localhost'].includes(u.hostname)) invalid('BACKEND_URL remoto requiere HTTPS.');
  if (u.pathname !== '/' && u.pathname !== '') invalid('BACKEND_URL debe ser la URL base sin rutas.');
  return u.origin;
}
export function loadConfig(env = process.env, {filePath} = {}) {
  const source = externalJson(filePath ?? env.WHATSAPP_SERVICE_CONFIG_FILE);
  const read = (key, fallback) => env[key] !== undefined ? env[key] : (source[key] ?? fallback);
  const backend = backendUrl(read('BACKEND_URL'));
  const header = required(read('SERVICE_AUTH_HEADER', 'Authorization'), 'SERVICE_AUTH_HEADER');
  if (!/^[!#$%&'*+.^_`|~A-Za-z0-9-]+$/.test(header) || /^content-(type|length)$/i.test(header)) invalid('SERVICE_AUTH_HEADER inválido.');
  const prefix = read('SERVICE_TOKEN_PREFIX', 'Bearer ');
  if (typeof prefix !== 'string' || /[\r\n]/.test(prefix) || (prefix !== '' && !prefix.endsWith(' '))) {
    invalid('SERVICE_TOKEN_PREFIX debe terminar en espacio o estar vacío.');
  }
  if (env.POLL_INTERVAL_MS !== undefined || source.POLL_INTERVAL_MS !== undefined) {
    invalid('POLL_INTERVAL_MS fue reemplazado por MAINTENANCE_INTERVAL_MS.');
  }
  const interval = Number(read('MAINTENANCE_INTERVAL_MS', 300000));
  if (!Number.isSafeInteger(interval) || interval < 1000 || interval > 86400000) invalid('MAINTENANCE_INTERVAL_MS inválido.');
  const sqlite = required(read('SQLITE_PATH', './data/pendientes.sqlite'), 'SQLITE_PATH');
  const llm = read('LLM_API_KEY', '');
  if (typeof llm !== 'string') invalid('LLM_API_KEY debe ser una cadena.');
  const enabledRaw = read('WHATSAPP_ENABLED', 'false');
  if (enabledRaw !== 'true' && enabledRaw !== 'false') invalid('WHATSAPP_ENABLED debe ser true o false.');
  const whatsappEnabled = enabledRaw === 'true';
  const phone = read('WHATSAPP_PHONE_NUMBER');
  if (phone !== undefined && (typeof phone !== 'string' || !/^[1-9][0-9]{7,14}$/.test(phone))) invalid('WHATSAPP_PHONE_NUMBER inválido.');
  const session = required(read('WHATSAPP_SESSION_PATH', './data/session.sqlite'), 'WHATSAPP_SESSION_PATH');
  if (session === ':memory:') invalid('WHATSAPP_SESSION_PATH no puede ser temporal.');
  const authorizations = read('WHATSAPP_AUTHORIZATIONS_FILE');
  if (authorizations !== undefined && (typeof authorizations !== 'string' || !isAbsolute(authorizations))) invalid('WHATSAPP_AUTHORIZATIONS_FILE debe ser una ruta absoluta.');
  const reconnect = Number(read('WHATSAPP_RECONNECT_MS', 15000));
  if (!Number.isSafeInteger(reconnect) || reconnect < 1000 || reconnect > 300000) invalid('WHATSAPP_RECONNECT_MS inválido.');
  const serviceToken = required(read('SERVICE_TOKEN'), 'SERVICE_TOKEN');
  if (/[\r\n]/.test(serviceToken)) invalid('SERVICE_TOKEN inválido.');
  return Object.freeze({
    backendUrl: backend,
    serviceToken, serviceAuthHeader: header, serviceTokenPrefix: prefix,
    allowedGroupIds: groups(read('ALLOWED_GROUP_IDS')),
    llmApiKey: llm,
    sqlitePath: sqlite === ':memory:' ? sqlite : resolve(SERVICE_ROOT, sqlite),
    maintenanceIntervalMs: interval,
    whatsappEnabled, whatsappPhoneNumber: phone,
    whatsappSessionPath: resolve(SERVICE_ROOT, session),
    whatsappAuthorizationsFile: authorizations,
    whatsappReconnectMs: reconnect
  });
}
