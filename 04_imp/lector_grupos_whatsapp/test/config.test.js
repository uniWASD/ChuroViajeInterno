import test from 'node:test';
import assert from 'node:assert/strict';
import {writeFileSync} from 'node:fs';
import {join,resolve} from 'node:path';
import {loadConfig} from '../src/config.js';
import {fileURLToPath} from 'node:url';
import {config,workspace} from '../test-support/helpers.js';

test('variables de entorno: credenciales, grupos, URL y LLM futuro',()=>{
  const data=config({ALLOWED_GROUP_IDS:'uno@g.us,dos@g.us,uno@g.us',LLM_API_KEY:'CREDENCIAL_NO_REAL'});
  assert.deepEqual(data.allowedGroupIds,['uno@g.us','dos@g.us']);
  assert.equal(data.llmApiKey,'CREDENCIAL_NO_REAL');
    assert.equal(data.serviceTokenPrefix,'Bearer ');
  assert(Object.isFrozen(data));
});
test('JSON externo al repositorio y sin usar credenciales reales',t=>{
  const ws=workspace(t), path=join(ws.dir,'config.json');
  writeFileSync(path,JSON.stringify({BACKEND_URL:'https://ejemplo.invalid',SERVICE_TOKEN:'FICTICIO',
    ALLOWED_GROUP_IDS:['uno@g.us','dos@g.us'],LLM_API_KEY:'clave-solo-ejemplo',SQLITE_PATH:ws.db}));
  const loaded=loadConfig({}, {filePath:path});
  assert.equal(loaded.serviceToken,'FICTICIO');
  assert.equal(loaded.backendUrl,'https://ejemplo.invalid');
  assert.equal(loaded.sqlitePath,ws.db);
  assert.deepEqual(loaded.allowedGroupIds,['uno@g.us','dos@g.us']);
});
test('env prevalece sobre JSON externo',t=>{
  const ws=workspace(t),path=join(ws.dir,'config.json');
  writeFileSync(path,JSON.stringify({BACKEND_URL:'https://ejemplo.invalid',SERVICE_TOKEN:'ANTERIOR',ALLOWED_GROUP_IDS:['grupo@g.us']}));
  const result=loadConfig({SERVICE_TOKEN:'NUEVO',ALLOWED_GROUP_IDS:'otro@g.us'},{filePath:path});
  assert.equal(result.serviceToken,'NUEVO');assert.deepEqual(result.allowedGroupIds,['otro@g.us']);
});
test('archivo configurado mediante variable WHATSAPP_SERVICE_CONFIG_FILE',t=>{
  const ws=workspace(t),path=join(ws.dir,'config.json');
  writeFileSync(path,JSON.stringify({BACKEND_URL:'https://servidor.invalid',SERVICE_TOKEN:'PRUEBA',ALLOWED_GROUP_IDS:['grupo@g.us']}));
  assert.equal(loadConfig({WHATSAPP_SERVICE_CONFIG_FILE:path}).serviceToken,'PRUEBA');
});
test('rechaza configuración dentro del servicio, independientemente del cwd',()=>{
  const root = fileURLToPath(new URL('..', import.meta.url));
  for (const filename of ['package.json', 'src/config.js']) {
    assert.throws(()=>loadConfig({}, {filePath:resolve(root, filename)}),
      e=>e.code==='CONFIG_INVALIDA' && /fuera del servicio/.test(e.message));
  }
});
test('rechaza JSON corrupto sin mostrar contenido',t=>{
  const ws=workspace(t),path=join(ws.dir,'mal.json');writeFileSync(path,'TOKEN_SUPER_SECRETO {{{');
  assert.throws(()=>loadConfig({},{filePath:path}),e=>e.code==='CONFIG_INVALIDA' && !e.message.includes('TOKEN_SUPER_SECRETO'));
});
test('sin backend obligatorio falla',()=>{
  assert.throws(()=>loadConfig({SERVICE_TOKEN:'FICTICIO',ALLOWED_GROUP_IDS:'a@g.us'}),e=>e.code==='CONFIG_INVALIDA');
});
test('sin credencial de servicio falla',()=>{
  assert.throws(()=>loadConfig({BACKEND_URL:'https://ejemplo.invalid',ALLOWED_GROUP_IDS:'a@g.us'}),e=>e.code==='CONFIG_INVALIDA');
});
test('sin grupos falla',()=>{
  assert.throws(()=>loadConfig({BACKEND_URL:'https://ejemplo.invalid',SERVICE_TOKEN:'FICTICIO'}),e=>e.code==='CONFIG_INVALIDA');
});
test('no transmite credenciales mediante URL remota HTTP',()=>{
  assert.throws(()=>config({BACKEND_URL:'http://example.com'}),e=>e.code==='CONFIG_INVALIDA');
  assert.equal(config({BACKEND_URL:'http://localhost:18080'}).backendUrl,'http://localhost:18080');
});
test('rechaza URLs con usuario, parámetros, rutas o fragmentos',()=>{
  for (const url of ['https://usuario:clave@example.com','https://host.invalid/a','https://host.invalid?token=x','https://host.invalid#secreto','ftp://host.invalid']) {
    assert.throws(()=>config({BACKEND_URL:url}),e=>e.code==='CONFIG_INVALIDA');
  }
});
test('rechaza inyección CRLF en cabecera/prefijo',()=>{
  assert.throws(()=>config({SERVICE_AUTH_HEADER:'X-Credential\nAutor'}),e=>e.code==='CONFIG_INVALIDA');
  assert.throws(()=>config({SERVICE_TOKEN_PREFIX:'Bearer\r\nOtro: '}),e=>e.code==='CONFIG_INVALIDA');
});
test('rechaza grupos inválidos, intervalos imposibles y LLM no cadena',()=>{
  for (const wrong of [{ALLOWED_GROUP_IDS:'   '},{ALLOWED_GROUP_IDS:'grupo incorrecto@g.us'},
    {MAINTENANCE_INTERVAL_MS:'999'},{MAINTENANCE_INTERVAL_MS:'Infinity'},{MAINTENANCE_INTERVAL_MS:'1000.5'},{LLM_API_KEY:123}]) {
    assert.throws(()=>config(wrong),e=>e.code==='CONFIG_INVALIDA');
  }
});

test('la variable antigua POLL_INTERVAL_MS se rechaza con diagnóstico claro',()=>{
  assert.throws(()=>config({POLL_INTERVAL_MS:'2000'}),e=>e.code==='CONFIG_INVALIDA' && /MAINTENANCE_INTERVAL_MS/.test(e.message));
});

test('ruta SQLite relativa queda dentro del servicio, aunque cambie cwd',()=>{
  const c = config({SQLITE_PATH:'./data/pendientes.sqlite'});
  const root = fileURLToPath(new URL('..', import.meta.url));
  assert.equal(c.sqlitePath,resolve(root,'data/pendientes.sqlite'));
});
