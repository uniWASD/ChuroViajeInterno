import {loadConfig} from './config.js';
import {WhatsappService} from './service.js';
import {WhatsappReader} from './whatsapp-reader.js';
import {createBaileysAdapters} from './baileys-adapter.js';
import {createLogger} from './logger.js';
import {ErrorServicio} from './errors.js';

const logger = createLogger();
let service;
let reader;
let phase = 'config';
try {
  const config = loadConfig();
  phase = 'sqlite';
  service = new WhatsappService(config, {logger});
  phase = 'start';
  service.start();
  if (config.whatsappEnabled) {
    phase = 'whatsapp';
    const adapters = await createBaileysAdapters();
    reader = new WhatsappReader(config, service, {...adapters, logger});
    await reader.start();
  }
} catch (error) {
  const own = error instanceof ErrorServicio;
  const isConfig = own && (error.code === 'CONFIG_INVALIDA' || error.code === 'AUTORIZACION_INVALIDA');
  const isSqlite = own && (error.code === 'ESQUEMA_NO_PERMITIDO' || error.code === 'SESION_INVALIDA') ||
    (phase === 'sqlite' || phase === 'whatsapp') && (typeof error?.code === 'string') &&
      (/^(ERR_SQLITE|SQLITE_)/.test(error.code) || /^E[A-Z0-9]+$/.test(error.code));
  logger.event(isConfig ? 'ERROR_CONFIGURACION' : isSqlite ? 'ERROR_SQLITE' : 'ERROR_INESPERADO',
    own ? {codigo: error.code, detalle: error.message} :
      isSqlite ? {codigo: error.code} : {});
  process.exitCode = 1;
  reader?.stop();
  service?.stop();
}
if (service && process.exitCode !== 1) {
  const close = () => { try { reader?.stop(); service.stop(); } catch { process.exitCode = 1; } };
  process.once('SIGINT', close);
  process.once('SIGTERM', close);
}
