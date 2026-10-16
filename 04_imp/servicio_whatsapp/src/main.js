import {loadConfig} from './config.js';
import {WhatsappService} from './service.js';
import {createLogger} from './logger.js';
const logger=createLogger();
let service;
try {service=new WhatsappService(loadConfig(),{logger});service.start();}
catch(error) {
  logger.event(error?.code==='CONFIG_INVALIDA'?'ERROR_CONFIGURACION':'ERROR_SQLITE');
  process.exitCode=1;
  if (service) service.stop();
}
if (service && process.exitCode!==1) {
  const close=()=>{try {service.stop();} catch {process.exitCode=1;}};
  process.once('SIGINT',close);
  process.once('SIGTERM',close);
}
