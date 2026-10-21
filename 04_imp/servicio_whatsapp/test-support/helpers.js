import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {loadConfig} from '../src/config.js';
export const NOW=Date.parse('2026-10-15T12:00:00.000Z');
/**
 * Entorno temporal por caso de prueba.
 * Orden garantizado, tambien en Windows: primero cierran TODAS las bases/servidores,
 * despues se elimina el directorio. Los hooks t.after se ejecutan en orden de
 * registro, por eso NO deben registrarse cierres en hooks posteriores separados.
 */
export function workspace(t) {
  const dir=mkdtempSync(join(tmpdir(),'churo-whatsapp-test-'));
  const disposers=[];
  if (t) t.after(async()=>{
    let firstError;
    for (const dispose of [...disposers].reverse()) {
      try {await dispose();} catch(error) {firstError??=error;}
    }
    try {rmSync(dir,{recursive:true,force:true,maxRetries:5,retryDelay:150});}
    catch(error) {firstError??=error;}
    if (firstError) throw firstError;
  });
  return {
    dir,db:join(dir,'pending.sqlite'),
    addCleanup(dispose) {
      if (!t || typeof dispose!=='function') throw new TypeError('Se requiere una funcion de limpieza y un contexto de prueba.');
      disposers.push(dispose);
    }
  };
}
export function config(extra={}) {
  return loadConfig({BACKEND_URL:'http://127.0.0.1:18080',SERVICE_TOKEN:'TOKEN_DE_PRUEBA_NO_REAL',
    ALLOWED_GROUP_IDS:'grupo-autorizado@g.us',SQLITE_PATH:':memory:',...extra});
}
export function msg(extra={}) {
  return {id:'m-1',group:'grupo-autorizado@g.us',date:new Date(NOW-60000).toISOString(),
    text:'TEXTO_DE_MENSAJE_PRIVADO_SINTETICO',...extra};
}
