import test from 'node:test';
import assert from 'node:assert/strict';
import {statSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {MessageStore,STATUS,TTL_MS} from '../src/store.js';
import {msg,NOW,workspace} from '../test-support/helpers.js';
function create(t) {
  const ws=workspace(t), store=new MessageStore(ws.db,{clock:()=>NOW});
  ws.addCleanup(()=>store.close());return {store,ws};
}
test('CP-26: persiste identificador, grupo, fecha y texto con SQLite real',t=>{
  const {store}=create(t), message=msg();
  assert.deepEqual(store.savePending(message),{inserted:true,status:STATUS.PENDING});
  assert.deepEqual(store.getById('m-1'),{id:message.id,grupo:message.group,fecha:message.date,texto:message.text,estado:'PENDIENTE'});
});
test('CP-26: no existen columnas autor, número, remitente ni ninguna otra',t=>{
  const {store}=create(t);
  const columns=store.db.prepare('PRAGMA table_info(mensaje_pendiente)').all().map(x=>x.name);
  assert.deepEqual(columns,['id','grupo','fecha','fecha_ms','texto','estado','finalizado_ms']);
  assert.deepEqual(store.db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all().map(x=>x.name),['mensaje_pendiente']);
});
test('la BD se cierra y abre de nuevo sin perder pendientes (disco)',t=>{
  const ws=workspace(t);let db=new MessageStore(ws.db,{clock:()=>NOW});
  db.savePending(msg());db.close();
  db=new MessageStore(ws.db,{clock:()=>NOW});
  assert.equal(db.getById('m-1').texto,msg().text);
  assert.equal(db.getById('m-1').grupo,msg().group);db.close();
});
test('eliminar texto al procesar, sin posibilidad de reactivar',t=>{
  const {store}=create(t);store.savePending(msg());
  assert.equal(store.markProcessed('m-1'),1);
  assert.equal(store.getById('m-1').texto,null);
  assert.equal(store.getById('m-1').estado,'PROCESADO');
  assert.equal(store.markProcessed('m-1'),0);
  assert.equal(store.savePending(msg()).inserted,false);
  assert.equal(store.getById('m-1').texto,null);
});
test('descartar elimina texto y conserva grupo/fecha/ID',t=>{
  const {store}=create(t);store.savePending(msg());store.markDiscarded('m-1');
  assert.equal(store.getById('m-1').texto,null);
  assert.equal(store.getById('m-1').grupo,msg().group);
  assert.equal(store.getById('m-1').estado,'DESCARTADO');
});
test('RN-06.11: más de 30 minutos se descarta en mantenimiento',t=>{
  const {store}=create(t);
  store.savePending(msg({date:new Date(NOW-TTL_MS).toISOString()}));
  store.clock=()=>NOW+1;
  assert.equal(store.expireOldPending(),1);
  assert.equal(store.getById('m-1').texto,null);
  assert.equal(store.getById('m-1').estado,'DESCARTADO');
});
test('exactamente 30 minutos no se descarta (límite estricto)',t=>{
  const {store}=create(t);store.savePending(msg({date:new Date(NOW-TTL_MS).toISOString()}));
  assert.equal(store.expireOldPending(),0);
  assert.equal(store.countPending(),1);
  assert.equal(store.nextExpirationMs(),NOW+1);
});
test('mensaje vencido al ingresar jamás persiste su texto',t=>{
  const {store}=create(t);
  assert.deepEqual(store.savePending(msg({date:new Date(NOW-TTL_MS-1).toISOString()})),{inserted:true,status:'DESCARTADO'});
  assert.equal(store.getById('m-1').texto,null);
});
test('listPending excluye expirados y ordena por fecha',t=>{
  const {store}=create(t);
  store.savePending(msg({id:'b',date:new Date(NOW-500).toISOString()}));
  store.savePending(msg({id:'a',date:new Date(NOW-1000).toISOString()}));
  assert.deepEqual(store.listPending().map(x=>x.id),['a','b']);
  store.clock=()=>NOW+TTL_MS;
  assert.deepEqual(store.listPending().map(x=>x.id),[]);
});
test('insertar un ID duplicado no cambia el texto anterior',t=>{
  const {store}=create(t);store.savePending(msg());
  assert.equal(store.savePending(msg({text:'TEXTO_NO_PUEDE_REEMPLAZAR'})).inserted,false);
  assert.equal(store.getById('m-1').texto,msg().text);
});
test('rechaza nombres de remitentes y cualquier columna extra en API',t=>{
  const {store}=create(t);
  for (const additional of [{remitente:'77889900'},{autor:'ALGUIEN'},{numero:'777888999'},{sender:'987654'},{foo:'bar'}]) {
    assert.throws(()=>store.savePending(msg(additional)),e=>e.code==='MENSAJE_INVALIDO');
  }
  assert.equal(store.countPending(),0);
});
test('no acepta mensajes incompletos, vacíos o no textuales',t=>{
  const {store}=create(t);
  for (const additional of [{id:''},{group:''},{text:' '},{text:234},{date:'2026-10-15'},
    {date:'2026-02-30T10:00:00Z'},{date:'2025-02-29T10:00:00Z'}]) {
    assert.throws(()=>store.savePending(msg(additional)),e=>e.code==='MENSAJE_INVALIDO');
  }
});
test('rechaza schema legacy con columna remitente',t=>{
  const ws=workspace(t),db=new DatabaseSync(ws.db);
  db.exec(`CREATE TABLE mensaje_pendiente(id TEXT,grupo TEXT,fecha TEXT,fecha_ms INTEGER,texto TEXT,estado TEXT,finalizado_ms INTEGER,remitente TEXT)`);
  db.close();assert.throws(()=>new MessageStore(ws.db,{clock:()=>NOW}),e=>e.code==='ESQUEMA_NO_PERMITIDO');
});
test('un texto que parece SQL no ejecuta sentencias',t=>{
  const {store}=create(t);
  store.savePending(msg({text:"'; DROP TABLE mensaje_pendiente; --"}));
  assert.equal(store.getById('m-1').texto,"'; DROP TABLE mensaje_pendiente; --");
  assert.equal(store.countPending(),1);
});
test('SQLite privado tiene permisos de archivo 0600 en POSIX',{skip:process.platform==='win32'},t=>{
  const {ws}=create(t);
  assert.equal(statSync(ws.db).mode & 0o077,0);
});
test('estado solo permite pendiente/procesado/descartado por restricción SQL',t=>{
  const {store}=create(t);store.savePending(msg());
  assert.throws(()=>store.db.prepare('UPDATE mensaje_pendiente SET estado=? WHERE id=?').run('CUALQUIERA','m-1'));
});
