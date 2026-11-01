import {readFileSync, realpathSync, statSync, existsSync} from 'node:fs';
import {dirname, isAbsolute, join, relative, resolve} from 'node:path';
import {SERVICE_ROOT} from './config.js';
import {zonedMilliseconds} from './datetime.js';
import {ErrorServicio} from './errors.js';

function invalid() {
  throw new ErrorServicio('AUTORIZACION_INVALIDA', 'El registro externo de autorizaciones es inválido o incompleto.');
}

function inside(parent, target) {
  const rel = relative(parent, target);
  return rel === '' || (rel !== '..' && !rel.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`) && !isAbsolute(rel));
}

export function loadAuthorizations(filename, allowedGroups) {
  if (!filename || !isAbsolute(filename)) invalid();
  let path;
  try {
    path = realpathSync(resolve(filename));
    if (inside(SERVICE_ROOT, path)) invalid();
    for (let dir = dirname(path); ; dir = dirname(dir)) {
      if (existsSync(join(dir, '.git'))) invalid();
      if (dirname(dir) === dir) break;
    }
    const metadata = statSync(path);
    if (!metadata.isFile() || (process.platform !== 'win32' && (metadata.mode & 0o077) !== 0)) invalid();
    const content = JSON.parse(readFileSync(path, 'utf8'));
    if (!content || Object.keys(content).join(',') !== 'autorizaciones' || !Array.isArray(content.autorizaciones)) invalid();
    const authorized = new Set();
    for (const entry of content.autorizaciones) {
      if (!entry || Object.keys(entry).sort().join(',') !== 'autorizadoPorAdmin,fecha,grupo,solicitud' ||
          typeof entry.grupo !== 'string' || !/^\d{5,20}(?:-\d+)?@g\.us$/.test(entry.grupo) ||
          zonedMilliseconds(entry.fecha) === null || entry.autorizadoPorAdmin !== true ||
          typeof entry.solicitud !== 'string' || !entry.solicitud.trim() || entry.solicitud.length > 500 ||
          authorized.has(entry.grupo)) invalid();
      authorized.add(entry.grupo);
    }
    if (!allowedGroups.length || allowedGroups.some(id => !authorized.has(id))) invalid();
    return new Set(allowedGroups);
  } catch (error) {
    if (error instanceof ErrorServicio) throw error;
    invalid();
  }
}
