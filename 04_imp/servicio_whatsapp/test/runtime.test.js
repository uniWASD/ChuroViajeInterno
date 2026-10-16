import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {MessageStore} from '../src/store.js';
import {workspace} from '../test-support/helpers.js';

const cwd=fileURLToPath(new URL('..',import.meta.url));
function spawnService(env) {
  const child=spawn(process.execPath,['src/main.js'],{cwd,
    env:{...process.env,...env},stdio:['ignore','pipe','pipe'],windowsHide:true});
  let stdout='',stderr='';
  child.stdout.on('data',chunk=>{stdout+=chunk;});
  child.stderr.on('data',chunk=>{stderr+=chunk;});
  const done=new Promise(resolve=>child.once('close',code=>resolve({code,get stdout(){return stdout;},get stderr(){return stderr;}})));
  const waitForStart=()=>new Promise((resolve,reject)=>{
    if (stdout.includes('SERVICIO_INICIADO')) return resolve();
    const deadline=setTimeout(()=>reject(new Error('TIMEOUT_DE_ARRANQUE')),15000);
    child.stdout.on('data',function listener(){
      if (stdout.includes('SERVICIO_INICIADO')) {clearTimeout(deadline);child.stdout.off('data',listener);resolve();}
    });
    child.once('error',e=>{clearTimeout(deadline);reject(e);});
    child.once('close',code=>{
      if (!stdout.includes('SERVICIO_INICIADO')) {clearTimeout(deadline);reject(new Error('PROCESO_SALIO_ANTES_DE_INICIAR_'+code));}
    });
  });
  return {child,done,waitForStart};
}
test('proceso Node arranca en subprocess real, restaura SQLite y elimina texto vencido',async t=>{
  const ws=workspace(t),now=Date.now();
  const store=new MessageStore(ws.db,{clock:()=>now-4*60000});
  const text='TEXTO_SINTETICO_SECRETO_NO_DEBE_ESTAR_EN_LOG';
  const token='TOKEN_FICTICIO_NO_DEBE_ESTAR_EN_LOG';
  store.savePending({id:'m-vencido',group:'grupo-autorizado@g.us',date:new Date(now-32*60000).toISOString(),text});
  store.close();
  const task=spawnService({BACKEND_URL:'http://127.0.0.1:18080',SERVICE_TOKEN:token,
    ALLOWED_GROUP_IDS:'grupo-autorizado@g.us',SQLITE_PATH:ws.db,POLL_INTERVAL_MS:'1000'});
  ws.addCleanup(async()=>{
    if (task.child.exitCode===null) task.child.kill();
    await task.done;
  });
  await task.waitForStart();
  task.child.kill();
  const result=await task.done;
  assert(result.stdout.includes('SERVICIO_INICIADO'));
  assert(result.stdout.includes('"caducados":1'));
  assert(!result.stdout.includes(text));
  assert(!result.stdout.includes(token));
  const reopened=new MessageStore(ws.db);
  assert.equal(reopened.getById('m-vencido').estado,'DESCARTADO');
  assert.equal(reopened.getById('m-vencido').texto,null);
  reopened.close();
});
test('proceso Node sin configuración válida termina con fallo y sin revelar secretos',async()=>{
  const env={...process.env};
  for (const key of ['BACKEND_URL','SERVICE_TOKEN','ALLOWED_GROUP_IDS','WHATSAPP_SERVICE_CONFIG_FILE']) delete env[key];
  const child=spawn(process.execPath,['src/main.js'],{cwd,env,stdio:['ignore','pipe','pipe'],windowsHide:true});
  const closed=new Promise(resolve=>child.once('close',resolve));
  let out='';for await (const chunk of child.stdout) out+=chunk;
  const result=await closed;
  assert.notEqual(result,0);
  assert(out.includes('ERROR_CONFIGURACION'));
});
