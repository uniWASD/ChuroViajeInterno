import {loadConfig} from './config.js';
import {WhatsappService} from './service.js';
import {createLogger} from './logger.js';
import {ErrorServicio} from './errors.js';

const logger = createLogger();
let service;
let phase = 'config';
try {
  const config = loadConfig();
  phase = 'sqlite';
  service = new WhatsappService(config, {logger});
  phase = 'start';
  service.start();
} catch (error) {
  const own = error instanceof ErrorServicio;
  const isConfig = own && error.code === 'CONFIG_INVALIDA';
  const isSqlite = own && error.code === 'ESQUEMA_NO_PERMITIDO' ||
    phase === 'sqlite' && (typeof error?.code === 'string') &&
      (/^(ERR_SQLITE|SQLITE_)/.test(error.code) || ['EACCES','EPERM','ENOSPC','EISDIR','ENOENT'].includes(error.code));
  logger.event(isConfig ? 'ERROR_CONFIGURACION' : isSqlite ? 'ERROR_SQLITE' : 'ERROR_INESPERADO',
    own ? {codigo: error.code, detalle: error.message} :
      isSqlite ? {codigo: error.code} : {});
  process.exitCode = 1;
  if (service) service.stop();
}
if (service && process.exitCode !== 1) {
  const close = () => { try { service.stop(); } catch { process.exitCode = 1; } };
  process.once('SIGINT', close);
  process.once('SIGTERM', close);
}
