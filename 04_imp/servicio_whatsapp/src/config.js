import {readFileSync, realpathSync} from 'node:fs';
import {isAbsolute, relative, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {T091Error} from './errors.js';

const PROJECT_ROOT = resolve(fileURLToPath(new URL('..', import.meta.url)));
// Estructura obligatoria 04_imp/servicio_whatsapp dentro del repositorio.
const REPOSITORY_ROOT = resolve(PROJECT_ROOT, '../..');
function invalid(text) {throw new T091Error('CONFIG_INVALIDA', text);}
function required(value, key) {
  if (typeof value !== 'string' || !value.trim()) invalid(`Falta ${key}.`);
  return value.trim();
}
function externalJson(filename) {
  if (filename === undefined || filename === '') return {};
  if (typeof filename !== 'string') invalid('Ruta externa de configuración inválida.');
  let path;
  try {path = realpathSync(resolve(filename));}
  catch {invalid('No se puede leer el archivo de configuración externa.');}
  const rel = relative(REPOSITORY_ROOT, path);
  if (rel === '' || (!rel.startsWith('..') && !isAbsolute(rel))) {
    invalid('El JSON de configuración debe estar fuera del repositorio.');
  }
  try {
    const obj = JSON.parse(readFileSync(path, 'utf8'));
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) invalid('El JSON externo debe ser un objeto.');
    return obj;
  } catch {invalid('El JSON externo no se puede leer o no es válido.');}
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
  try {u = new URL(value);} catch {invalid('BACKEND_URL no es válida.');}
  if (!['http:','https:'].includes(u.protocol) || u.username || u.password || u.search || u.hash) invalid('BACKEND_URL contiene componentes no permitidos.');
  if (u.protocol === 'http:' && !['127.0.0.1','[::1]','localhost'].includes(u.hostname)) invalid('BACKEND_URL remoto requiere HTTPS.');
  if (u.pathname !== '/' && u.pathname !== '') invalid('BACKEND_URL debe ser la URL base sin rutas.');
  return u.origin;
}
export function loadConfig(env = process.env, {filePath} = {}) {
  const source = externalJson(filePath ?? env.WHATSAPP_SERVICE_CONFIG_FILE);
  const read = (key, fallback) => env[key] !== undefined ? env[key] : (source[key] ?? fallback);
  const header = required(read('SERVICE_AUTH_HEADER', 'Authorization'), 'SERVICE_AUTH_HEADER');
  if (!/^[!#$%&'*+.^_`|~A-Za-z0-9-]+$/.test(header) || /^content-(type|length)$/i.test(header)) invalid('SERVICE_AUTH_HEADER inválido.');
  const prefix = read('SERVICE_TOKEN_PREFIX', 'Bearer ');
  if (typeof prefix !== 'string' || /[\r\n]/.test(prefix)) invalid('SERVICE_TOKEN_PREFIX inválido.');
  const interval = Number(read('POLL_INTERVAL_MS', 300000));
  if (!Number.isSafeInteger(interval) || interval < 1000 || interval > 86400000) invalid('POLL_INTERVAL_MS inválido.');
  const sqlite = required(read('SQLITE_PATH', './data/pendientes.sqlite'), 'SQLITE_PATH');
  const llm = read('LLM_API_KEY', '');
  if (typeof llm !== 'string') invalid('LLM_API_KEY debe ser una cadena.');
  return Object.freeze({
    backendUrl: backendUrl(read('BACKEND_URL')),
    serviceToken: required(read('SERVICE_TOKEN'), 'SERVICE_TOKEN'),
    serviceAuthHeader: header, serviceTokenPrefix: prefix,
    allowedGroupIds: groups(read('ALLOWED_GROUP_IDS')),
    llmApiKey: llm,  // se lee pero nunca se utiliza: T-091 no realiza análisis LLM
    sqlitePath: sqlite === ':memory:' ? sqlite : resolve(sqlite),
    pollIntervalMs: interval,
    pendingTtlMs: 30 * 60 * 1000
  });
}
