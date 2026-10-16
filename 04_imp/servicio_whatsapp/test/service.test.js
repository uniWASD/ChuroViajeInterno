import test from 'node:test';
import assert from 'node:assert/strict';
import {WhatsappService} from '../src/service.js';
import {MessageStore,TTL_MS} from '../src/store.js';
import {createLogger} from '../src/logger.js';
import {msg,NOW,config} from '../test-support/helpers.js';
function context(t,{now=NOW}={}) {
  const holder={now,period:null,interval:null,alarm:null,delay:null,events:[],get:0,post:0};
  const store=new MessageStore(':memory:',{clock:()=>holder.now});
  const logger=createLogger(line=>holder.events.push(line));
  const service=new WhatsappService(config(),{store,logger,clock:()=>holder.now,
    backend:{getSurtidores(){holder.get++;},postResultados(){holder.post++;}},
    schedule:(f,ms)=>{holder.interval=f;holder.period=ms;return 1;},unschedule:()=>{holder.interval=null;},
    scheduleExpiry:(f,ms)=>{holder.alarm=f;holder.delay=ms;return 2;},unscheduleExpiry:()=>{holder.alarm=null;}});
  t.after(()=>{service.stop();store.close();});
  return {holder,store,service};
}
test('arranque descarta vencidos y borra texto',t=>{
  const {holder,store,service}=context(t);
  store.savePending(msg({date:new Date(NOW-29*60000).toISOString()}));
  holder.now=NOW+2*60000;
  service.start();
  assert.equal(store.getById('m-1').estado,'DESCARTADO');
  assert.equal(store.getById('m-1').texto,null);
  assert(holder.events.some(line=>line.includes('"caducados":1')));
});
test('arranque no llama al backend HTTP ni al LLM',t=>{
  const {holder,service}=context(t);
  service.start();assert.deepEqual({get:holder.get,post:holder.post},{get:0,post:0});
});
test('intervalo de mantenimiento por defecto 5 minutos',t=>{
  const {holder,service}=context(t);
  service.start();
  assert.equal(holder.period,300000);
  holder.interval();assert(holder.events.filter(line=>line.includes('MANTENIMIENTO')).length>=2);
});
test('grupo autorizado guarda, no envía mensajes',t=>{
  const {store,service,holder}=context(t);
  assert.equal(service.ingestText(msg()).status,'PENDIENTE');
  assert.equal(store.getById('m-1').texto,msg().text);
  assert.equal(holder.get,0);assert.equal(holder.post,0);
});
test('grupo no autorizado no escribe en SQLite',t=>{
  const {service,store}=context(t);
  assert.throws(()=>service.ingestText(msg({group:'otro@g.us'})),e=>e.code==='GRUPO_NO_AUTORIZADO');
  assert.equal(store.countPending(),0);
});
test('vencimiento individual, sin esperar cinco minutos',t=>{
  const {holder,store,service}=context(t);
  service.start();service.ingestText(msg({date:new Date(NOW-29*60000).toISOString()}));
  assert.equal(holder.delay,60001);
  holder.now=NOW+60001;holder.alarm();
  assert.equal(store.getById('m-1').estado,'DESCARTADO');
  assert.equal(store.getById('m-1').texto,null);
});
test('diferencia exacta de TTL se conserva hasta siguiente milisegundo',t=>{
  const {holder,store,service}=context(t);
  service.start();service.ingestText(msg({date:new Date(NOW-TTL_MS).toISOString()}));
  assert.equal(holder.delay,1);
  assert.equal(store.getById('m-1').estado,'PENDIENTE');
  holder.now++;holder.alarm();
  assert.equal(store.getById('m-1').estado,'DESCARTADO');
});
test('stop cancela relojes y bloquea reiniciar',t=>{
  const {holder,service}=context(t);
  service.start();service.ingestText(msg());
  service.stop();
  assert.equal(holder.alarm,null);assert.equal(holder.interval,null);
  assert.throws(()=>service.start(),e=>e.code==='SERVICIO_CERRADO');
});
test('registros nunca incluyen mensaje, token, remitente o grupo',()=>{
  const buffer=[],logger=createLogger(line=>buffer.push(line));
  logger.event('MANTENIMIENTO',{caducados:2,pendientes:3,texto:'MENSAJE PRIVADO',remitente:'NOMBRE',token:'CLAVE',grupo:'@g.us'});
  assert.deepEqual(JSON.parse(buffer[0]),{evento:'MANTENIMIENTO',caducados:2,pendientes:3});
  assert(!buffer[0].includes('MENSAJE PRIVADO') && !buffer[0].includes('NOMBRE') && !buffer[0].includes('CLAVE'));
});
test('rechaza nombres arbitrarios de eventos',()=>{
  assert.throws(()=>createLogger().event('REGISTRAR_TEXTO_PRIVADO',{}));
});
