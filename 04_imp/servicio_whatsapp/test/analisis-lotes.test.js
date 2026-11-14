import test from 'node:test';
import assert from 'node:assert/strict';
import {WhatsappService, horaTarija, MAX_AVISOS_POR_LOTE} from '../src/service.js';
import {MessageStore, TTL_MS} from '../src/store.js';
import {createLogger} from '../src/logger.js';
import {BackendHttpError, ErrorServicio} from '../src/errors.js';
import {msg, NOW, config} from '../test-support/helpers.js';

const CATALOGO = {
  surtidores: [{nombre: 'Las Vegas', alias: ['la vegas', 'las vegas', 'mas vegas', 'vegas']},
    {nombre: 'El Portillo', alias: ['el portillo', 'portillo']}, {nombre: 'Don Daniel', alias: ['daniel', 'don daniel']}],
  combustibles: ['Diesel', 'Diesel ULS', 'Gasolina', 'Gasolina Premium'],
  filas: ['SIN_FILA', 'CORTA', 'MEDIA', 'LARGA']
};
const fecha = minutos => horaTarija(NOW - minutos * 60000);

/** El servicio con el LLM y el backend simulados; cada llamada queda anotada. */
function contexto(t, {llm, backend} = {}) {
  const h = {now: NOW, llamadas: [], enviados: [], eventos: [], catalogos: 0};
  const store = new MessageStore(':memory:', {clock: () => h.now});
  const logger = createLogger(line => h.eventos.push(line));
  h.llm = llm ?? (() => ({avisos: [], descartados: []}));
  h.backend = backend ?? (() => ({aceptados: 0, descartados: []}));
  const service = new WhatsappService(config({LLM_API_KEY: 'CLAVE_FICTICIA'}), {store, logger, clock: () => h.now,
    llm: {analyze: async peticion => { h.llamadas.push(peticion); return h.llm(peticion); }},
    backend: {
      getSurtidores: async () => { h.catalogos++; return h.catalogoFalla ? Promise.reject(h.catalogoFalla) : CATALOGO; },
      postResultados: async lote => { h.enviados.push(structuredClone(lote)); return h.backend(lote); }
    },
    schedule: () => 1, unschedule: () => {}, scheduleExpiry: () => 2, unscheduleExpiry: () => {},
    scheduleAnalysis: (f, ms) => { h.periodoAnalisis = ms; return 3; }, unscheduleAnalysis: () => {}});
  t.after(() => { service.stop(); store.close(); });
  service.start();
  return {h, store, service};
}
function recibir(service, id, texto, minutos = 2) {
  return service.ingestText(msg({id, text: texto, date: fecha(minutos)}));
}
const mensajesDe = h => JSON.parse(h.llamadas.at(-1).user).mensajes;
function sinPrivados(h, ...prohibidos) {
  const todo = h.eventos.join('\n');
  for (const p of prohibidos) assert(!todo.includes(p), `el registro contiene «${p}»`);
}

test('RN-06.5: el análisis corre cada 5 minutos y sin pendientes no llama al LLM', async t => {
  const {h, service} = contexto(t);
  assert.equal(h.periodoAnalisis, 300000);
  await service.analyzeCycle();
  assert.equal(h.llamadas.length, 0);
  assert.equal(h.catalogos, 0);
});

test('CP-74: «ya hay gasolina en Las Vegas, fila corta» da un aviso de Las Vegas, Gasolina, disponible, fila corta', async t => {
  const {h, store, service} = contexto(t, {
    llm: () => ({avisos: [{mensaje: 'm1', surtidor: 'Las Vegas', combustible: 'Gasolina', estado: 'DISPONIBLE', fila: 'CORTA'}],
      descartados: []}),
    backend: () => ({aceptados: 1, descartados: []})
  });
  recibir(service, 'w-74', 'ya hay gasolina en Las Vegas, fila corta', 3);
  await service.analyzeCycle();

  assert.equal(h.enviados.length, 1);
  assert.deepEqual(h.enviados[0], {lote: horaTarija(NOW), avisos: [
    {surtidor: 'Las Vegas', combustible: 'Gasolina', estado: 'DISPONIBLE', fila: 'CORTA', fecha: fecha(3)}]});
  // RN-06.12: analizado, queda procesado y sin texto; no queda nada por reenviar.
  assert.deepEqual(store.getById('w-74'), {id: 'w-74', grupo: 'grupo-autorizado@g.us', fecha: fecha(3), texto: null, estado: 'PROCESADO'});
  assert.equal(store.listResults().length, 0);
  assert(h.eventos.some(l => l.includes('"evento":"LOTE_ANALIZADO"') && l.includes('"avisos":1')));
  assert(h.eventos.some(l => l.includes('"evento":"RESULTADO_ENVIADO"') && l.includes('"aceptados":1')));
  sinPrivados(h, 'gasolina en Las Vegas', 'grupo-autorizado');
  console.log('CP-74 registro:\n' + h.eventos.map(l => '  ' + l).join('\n'));
});

test('CP-75: «¿alguien sabe si hay diésel en El Portillo?» se descarta, aunque el LLM lo tome como aviso', async t => {
  const {h, store, service} = contexto(t, {
    llm: () => ({avisos: [{mensaje: 'm2', surtidor: 'El Portillo', combustible: 'Diesel', estado: 'DISPONIBLE', fila: 'NO_MENCIONADA'}],
      descartados: [{mensaje: 'm1', motivo: 'PREGUNTA'}]})
  });
  recibir(service, 'w-75a', '¿alguien sabe si hay diésel en El Portillo?');
  recibir(service, 'w-75b', 'hay diesel en portillo?', 1);
  await service.analyzeCycle();

  assert.equal(h.enviados.length, 0, 'sin avisos no se llama al backend');
  for (const id of ['w-75a', 'w-75b']) {
    assert.equal(store.getById(id).estado, 'PROCESADO');
    assert.equal(store.getById(id).texto, null);
  }
  assert(h.eventos.some(l => l.includes('"evento":"LOTE_ANALIZADO"') && l.includes('"descartados":2')
    && l.includes('"descartadosValidador":1')));
  sinPrivados(h, 'Portillo', 'diésel');
  console.log('CP-75 registro:\n' + h.eventos.map(l => '  ' + l).join('\n'));
});

test('CP-76: con el LLM caído los mensajes siguen pendientes y se analizan en el ciclo siguiente', async t => {
  let caido = true;
  const {h, store, service} = contexto(t, {
    llm: () => {
      if (caido) throw new ErrorServicio('LLM_NO_DISPONIBLE', 'No se pudo conectar con el LLM.');
      return {avisos: [{mensaje: 'm1', surtidor: 'Don Daniel', combustible: 'Desconocido', estado: 'AGOTADO', fila: 'NO_MENCIONADA'}],
        descartados: []};
    }
  });
  recibir(service, 'w-76', 'en don daniel ya se acabó');
  await service.analyzeCycle();
  assert.equal(store.getById('w-76').estado, 'PENDIENTE');
  assert.equal(store.getById('w-76').texto, 'en don daniel ya se acabó');
  assert.equal(h.enviados.length, 0);
  assert(h.eventos.some(l => l.includes('"evento":"LLM_FALLO"') && l.includes('"codigo":"LLM_NO_DISPONIBLE"')));

  caido = false;
  h.now += 5 * 60000;
  await service.analyzeCycle();
  assert.equal(h.llamadas.length, 2);
  assert.equal(store.getById('w-76').estado, 'PROCESADO');
  assert.deepEqual(h.enviados[0].avisos, [{surtidor: 'Don Daniel', combustible: 'Desconocido', estado: 'AGOTADO', fecha: fecha(2)}]);
  sinPrivados(h, 'se acabó');
  console.log('CP-76 registro:\n' + h.eventos.map(l => '  ' + l).join('\n'));
});

test('CP-78: un mensaje con «llamen al 71234567» llega al LLM con el número enmascarado', async t => {
  const {h, service} = contexto(t);
  recibir(service, 'w-78', 'hay gasolina en vegas, llamen al 71234567 o al +591 712-34568');
  await service.analyzeCycle();
  const enviado = mensajesDe(h)[0];
  assert.equal(enviado.texto, 'hay gasolina en vegas, llamen al [número] o al [número]');
  // RN-06.6: solo el texto y la fecha; el id es la posición en el lote, no el de WhatsApp, y no viaja el grupo.
  assert.deepEqual(Object.keys(enviado).sort(), ['fecha', 'id', 'texto']);
  assert.equal(enviado.id, 'm1');
  const todo = JSON.stringify(h.llamadas);
  assert(!todo.includes('71234567') && !todo.includes('w-78') && !todo.includes('grupo-autorizado'));
  sinPrivados(h, '71234567');
  console.log('CP-78 texto que llegó al LLM: ' + enviado.texto.replace(/vegas/i, '<surtidor>'));
});

test('RN-06.5: el lote va ordenado por fecha y lleva el catálogo del backend en el prompt y en el esquema', async t => {
  const {h, service} = contexto(t);
  recibir(service, 'b', 'segundo', 1);
  recibir(service, 'a', 'primero', 4);
  await service.analyzeCycle();
  assert.deepEqual(mensajesDe(h).map(m => m.texto), ['primero', 'segundo']);
  const {system, schema} = h.llamadas[0];
  assert(system.includes('- Las Vegas: la vegas, las vegas, mas vegas, vegas'));
  assert.deepEqual(schema.properties.avisos.items.properties.surtidor.enum, ['Las Vegas', 'El Portillo', 'Don Daniel']);
  assert.deepEqual(schema.properties.avisos.items.properties.combustible.enum,
    ['Diesel', 'Diesel ULS', 'Gasolina', 'Gasolina Premium', 'Desconocido']);
  assert.deepEqual(schema.properties.avisos.items.properties.estado.enum, ['DISPONIBLE', 'AGOTADO']);
});

test('RN-06.11: un mensaje con más de 30 minutos se descarta antes de llegar al LLM', async t => {
  const {h, store, service} = contexto(t);
  recibir(service, 'viejo', 'hay en vegas', 29);
  h.now += 2 * 60000;
  await service.analyzeCycle();
  assert.equal(h.llamadas.length, 0);
  assert.equal(store.getById('viejo').estado, 'DESCARTADO');
  assert.equal(store.getById('viejo').texto, null);
  assert.ok(TTL_MS === 30 * 60000);
});

test('RN-06.19: con el backend caído el resultado se guarda sin texto y se reenvía primero en el ciclo siguiente', async t => {
  let caido = true;
  const {h, store, service} = contexto(t, {
    llm: () => ({avisos: [{mensaje: 'm1', surtidor: 'Las Vegas', combustible: 'Gasolina', estado: 'AGOTADO', fila: 'LARGA'}],
      descartados: []}),
    backend: () => { if (caido) throw new ErrorServicio('BACKEND_CONEXION', 'No se pudo conectar con el backend.'); return {aceptados: 1, descartados: []}; }
  });
  recibir(service, 'w-19', 'se terminó la gasolina en las vegas, había fila larga');
  await service.analyzeCycle();
  assert.equal(store.getById('w-19').texto, null, 'el texto se borra aunque el backend no responda');
  const guardado = store.listResults();
  assert.equal(guardado.length, 1);
  assert(!JSON.stringify(guardado).includes('terminó'));
  assert(h.eventos.some(l => l.includes('"evento":"RESULTADO_GUARDADO"')));

  caido = false;
  h.now += 5 * 60000;
  await service.analyzeCycle();
  assert.equal(h.enviados.length, 2);
  assert.deepEqual(h.enviados[1], h.enviados[0], 'se reenvía el mismo lote');
  assert.equal(store.listResults().length, 0);
  assert.equal(h.llamadas.length, 1, 'no había pendientes nuevos: no se volvió a llamar al LLM');
});

test('Un 400 o 422 del backend suelta el resultado; un 401 o 403 lo conserva', async t => {
  let respuesta = new BackendHttpError(403);
  const {h, store, service} = contexto(t, {
    llm: () => ({avisos: [{mensaje: 'm1', surtidor: 'Las Vegas', combustible: 'Gasolina', estado: 'AGOTADO', fila: 'NO_MENCIONADA'}],
      descartados: []}),
    backend: () => { throw respuesta; }
  });
  recibir(service, 'w-1', 'no hay nada en vegas');
  await service.analyzeCycle();
  assert.equal(store.listResults().length, 1);
  assert(h.eventos.some(l => l.includes('"evento":"RESULTADO_ESPERA_CREDENCIAL"') && l.includes('"estadoHttp":403')));
  respuesta = new BackendHttpError(400);
  await service.analyzeCycle();
  assert.equal(store.listResults().length, 0);
  assert(h.eventos.some(l => l.includes('"evento":"RESULTADO_RECHAZADO"') && l.includes('"estadoHttp":400')));
});

test('Un resultado sin entregar en 24 horas se suelta', async t => {
  const {h, store, service} = contexto(t, {
    llm: () => ({avisos: [{mensaje: 'm1', surtidor: 'Las Vegas', combustible: 'Gasolina', estado: 'AGOTADO', fila: 'NO_MENCIONADA'}],
      descartados: []}),
    backend: () => { throw new BackendHttpError(503); }
  });
  recibir(service, 'w-1', 'no hay nada en vegas');
  await service.analyzeCycle();
  h.now += 24 * 60 * 60000 + 1;
  await service.analyzeCycle();
  assert.equal(store.listResults().length, 0);
  assert(h.eventos.some(l => l.includes('"evento":"RESULTADO_VENCIDO"')));
});

test('Sin catálogo del backend no se analiza; con un catálogo leído antes, sí', async t => {
  const {h, store, service} = contexto(t);
  h.catalogoFalla = new ErrorServicio('BACKEND_CONEXION', 'No se pudo conectar con el backend.');
  recibir(service, 'w-1', 'hay en vegas');
  await service.analyzeCycle();
  assert.equal(h.llamadas.length, 0);
  assert.equal(store.getById('w-1').estado, 'PENDIENTE');
  h.catalogoFalla = null;
  await service.analyzeCycle();
  assert.equal(h.llamadas.length, 1);
  recibir(service, 'w-2', 'hay en portillo', 1);
  h.catalogoFalla = new BackendHttpError(503);
  await service.analyzeCycle();
  assert.equal(h.llamadas.length, 2, 'usa el último catálogo leído');
});

test('Validador: se descarta lo que el mensaje no sostiene y lo que se contradice', async t => {
  const {h, service} = contexto(t, {
    llm: () => ({avisos: [
      {mensaje: 'm1', surtidor: 'Don Daniel', combustible: 'Gasolina', estado: 'DISPONIBLE', fila: 'NO_MENCIONADA'}, // no lo nombra
      {mensaje: 'm1', surtidor: 'Las Vegas', combustible: 'Gasolina', estado: 'DISPONIBLE', fila: 'ENORME'},       // fila inventada
      {mensaje: 'm1', surtidor: 'Las Vegas', combustible: 'Kerosene', estado: 'DISPONIBLE', fila: 'NO_MENCIONADA'}, // combustible inventado
      {mensaje: 'm9', surtidor: 'Las Vegas', combustible: 'Gasolina', estado: 'DISPONIBLE', fila: 'NO_MENCIONADA'}, // mensaje que no existe
      {mensaje: 'm2', surtidor: 'El Portillo', combustible: 'Diesel', estado: 'DISPONIBLE', fila: 'NO_MENCIONADA'},
      {mensaje: 'm2', surtidor: 'El Portillo', combustible: 'Diesel', estado: 'AGOTADO', fila: 'NO_MENCIONADA'},    // contradictorio
      {mensaje: 'm3', surtidor: 'Las Vegas', combustible: 'Gasolina', estado: 'DISPONIBLE', fila: 'SIN_FILA'},
      {mensaje: 'm3', surtidor: 'Las Vegas', combustible: 'Diesel', estado: 'AGOTADO', fila: 'NO_MENCIONADA'}
    ], descartados: []}),
    backend: () => ({aceptados: 2, descartados: []})
  });
  recibir(service, 'w-1', 'en vegas hay', 5);
  recibir(service, 'w-2', 'en portillo hay diesel, otro dice que no', 4);
  recibir(service, 'w-3', 'Más Vegas: gasolina sin fila, diésel no hay', 3);
  await service.analyzeCycle();
  assert.deepEqual(h.enviados[0].avisos, [
    {surtidor: 'Las Vegas', combustible: 'Gasolina', estado: 'DISPONIBLE', fila: 'SIN_FILA', fecha: fecha(3)},
    {surtidor: 'Las Vegas', combustible: 'Diesel', estado: 'AGOTADO', fecha: fecha(3)}]);
  assert(h.eventos.some(l => l.includes('"descartados":2') && l.includes('"descartadosValidador":5')));
});

test('Una salida del LLM sin la estructura pedida es una falla: los mensajes siguen pendientes', async t => {
  const {store, service} = contexto(t, {llm: () => ({eventos: []})});
  recibir(service, 'w-1', 'hay en vegas');
  await service.analyzeCycle();
  assert.equal(store.getById('w-1').estado, 'PENDIENTE');
});

test('Más de 1000 avisos se entregan en varios lotes', async t => {
  const {h, service} = contexto(t, {
    llm: ({user}) => ({avisos: JSON.parse(user).mensajes.flatMap(m => ['Gasolina', 'Diesel', 'Desconocido'].map(c =>
      ({mensaje: m.id, surtidor: 'Las Vegas', combustible: c, estado: 'DISPONIBLE', fila: 'NO_MENCIONADA'}))), descartados: []})
  });
  for (let i = 0; i < 400; i++) recibir(service, `w-${i}`, `hay de todo en vegas ${i}`);
  await service.analyzeCycle();
  assert.deepEqual(h.enviados.map(l => l.avisos.length), [MAX_AVISOS_POR_LOTE, 200]);
});

test('Dos ciclos a la vez no se superponen', async t => {
  let soltar;
  const {h, service} = contexto(t, {llm: () => new Promise(r => { soltar = () => r({avisos: [], descartados: []}); })});
  recibir(service, 'w-1', 'hay en vegas');
  const primero = service.analyzeCycle();
  await new Promise(r => setImmediate(r));
  const segundo = service.analyzeCycle();
  assert.equal(primero, segundo);
  soltar();
  await primero;
  assert.equal(h.llamadas.length, 1);
});

test('Sin LLM_API_KEY no se programa el análisis', t => {
  const store = new MessageStore(':memory:');
  const eventos = [];
  let programado = false;
  const service = new WhatsappService(config(), {store, logger: createLogger(l => eventos.push(l)),
    schedule: () => 1, unschedule: () => {}, scheduleExpiry: () => 2, unscheduleExpiry: () => {},
    scheduleAnalysis: () => { programado = true; return 3; }, unscheduleAnalysis: () => {}});
  t.after(() => { service.stop(); store.close(); });
  service.start();
  assert.equal(programado, false);
  assert(eventos.some(l => l.includes('ANALISIS_DESACTIVADO')));
});
