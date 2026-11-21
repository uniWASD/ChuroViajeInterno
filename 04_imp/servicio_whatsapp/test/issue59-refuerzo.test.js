/** Pruebas adversas añadidas para issue #59; todos los datos son sintéticos. */
import test from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {WhatsappService, horaTarija} from '../src/service.js';
import {MessageStore, STATUS, TTL_MS} from '../src/store.js';
import {interpretOutput} from '../src/analysis.js';
import {maskPhones} from '../src/phone-mask.js';
import {GeminiClient} from '../src/gemini-client.js';
import {BackendClient} from '../src/backend-client.js';
import {createLogger} from '../src/logger.js';
import {config, NOW, msg} from '../test-support/helpers.js';
import {ErrorServicio, BackendHttpError} from '../src/errors.js';

const C = {
  surtidores: [{nombre: 'Las Vegas', alias: ['vegas', 'mas vegas']},
    {nombre: 'El Portillo', alias: ['portillo']}, {nombre: 'Don Daniel', alias: ['daniel']}],
  combustibles: ['Gasolina', 'Gasolina Premium', 'Diesel', 'Diesel ULS'],
  filas: ['SIN_FILA', 'CORTA', 'MEDIA', 'LARGA']
};
const FECHA = '2026-11-17T12:00:00-04:00';
const propuesta = (changes = {}) => ({mensaje: 'm1', surtidor: 'Las Vegas', combustible: 'Gasolina',
  estado: 'DISPONIBLE', fila: 'NO_MENCIONADA', ...changes});
function pruebaTexto(texto, aviso) {
  return interpretOutput({avisos: [propuesta(aviso)], descartados: []},
    [{id: 'id-sintetico', texto, fecha: FECHA}], C).avisos;
}

const adversos = [
  ['NEG-59-01: combustible del catálogo pero distinto del texto', 'ya hay gasolina en Las Vegas, fila corta',
    {combustible: 'Diesel', fila: 'CORTA'}, false],
  ['NEG-59-02: estado contrario a la evidencia', 'ya hay gasolina en Las Vegas',
    {estado: 'AGOTADO'}, false],
  ['NEG-59-04: creencia no confirmada', 'creo que hay gasolina en Las Vegas', {}, false],
  ['NEG-59-05: versiones contradictorias', 'hay gasolina en Las Vegas, pero otro dice que no hay', {}, false],
  ['NEG-59-06: surtidor distinto aunque exista', 'ya hay gasolina en Las Vegas',
    {surtidor: 'El Portillo'}, false],
  ['NEG-59-07: pregunta sin signos', 'alguien sabe si hay gasolina en Las Vegas', {}, false],
  ['NEG-59-09: no se infiere combustible genérico de premium', 'hay gasolina premium en Las Vegas', {}, false],
  ['NEG-59-10: no se infiere diesel genérico de ULS', 'hay diesel ULS en Las Vegas',
    {combustible: 'Diesel'}, false],
  ['NEG-59-11: no se admite Desconocido si hay tipo explícito', 'hay gasolina en Las Vegas',
    {combustible: 'Desconocido'}, false],
  ['NEG-59-12: cola no demuestra combustible', 'hay fila corta en Las Vegas',
    {fila: 'CORTA', combustible: 'Desconocido'}, false],
  ['NEG-59-13: menciona surtidor pero no disponibilidad', 'la estación Las Vegas está normal',
    {combustible: 'Desconocido'}, false],
  ['POSITIVO-59-01: CP74 literal', 'ya hay gasolina en Las Vegas, fila corta', {fila: 'CORTA'}, true],
  ['POSITIVO-59-02: secuencia final inequívoca', 'llegó gasolina a Las Vegas, pero se acabó al toque',
    {estado: 'AGOTADO'}, true],
  ['POSITIVO-59-03: no indica tipo', 'en Las Vegas se acabó el combustible',
    {combustible: 'Desconocido', estado: 'AGOTADO'}, true],
  ['POSITIVO-59-04: variante premium', 'hay plus en Las Vegas',
    {combustible: 'Gasolina Premium'}, true],
  ['POSITIVO-59-05: variante diesel ULS', 'en Las Vegas hay diesel ULS',
    {combustible: 'Diesel ULS'}, true],
  ['POSITIVO-59-06: dos surtidores sin cruzar estados, afirmativo',
    'en Las Vegas hay gasolina, pero en El Portillo no hay diesel', {}, true],
  ['POSITIVO-59-07: dos surtidores sin cruzar estados, negativo',
    'en Las Vegas hay gasolina, pero en El Portillo no hay diesel',
    {surtidor: 'El Portillo', combustible: 'Diesel', estado: 'AGOTADO'}, true],
  ['NEG-59-14: no cruzar estado de otro surtidor',
    'en Las Vegas hay gasolina, pero en El Portillo no hay diesel',
    {surtidor: 'El Portillo', combustible: 'Diesel', estado: 'DISPONIBLE'}, false],
  ['NEG-59-15: no aceptar información sin surtidor', 'hay gasolina, fila corta',
    {fila: 'CORTA'}, false]
];
for (const [nombre, texto, aviso, acepta] of adversos) {
  test(nombre, () => {
    const obtenido = pruebaTexto(texto, aviso);
    assert.equal(obtenido.length, acepta ? 1 : 0);
    if (acepta) assert.equal(obtenido[0].fecha, FECHA);
  });
}

// La fila se revisa aparte: si el texto no la respalda, el aviso sale sin fila en vez de perderse.
for (const [nombre, texto, aviso] of [
  ['NEG-59-03: fila larga inventada sale sin fila', 'ya hay gasolina en Las Vegas, fila corta', {fila: 'LARGA'}],
  ['NEG-59-08: el LLM no dio la fila: sale sin fila', 'ya hay gasolina en Las Vegas, fila corta', {}],
  ['NEG-59-16: fila dicha de otra forma: sale sin fila', 'en Vegas hay gasolina, fila de unas 3 cuadras', {fila: 'LARGA'}],
  ['NEG-59-17: dos filas distintas: sale sin fila', 'hay gasolina en Las Vegas, fila corta, no, fila larga', {fila: 'CORTA'}]
]) {
  test(nombre, () => {
    const obtenido = pruebaTexto(texto, aviso);
    assert.equal(obtenido.length, 1);
    assert.equal(obtenido[0].estado, 'DISPONIBLE');
    assert.equal('fila' in obtenido[0], false);
  });
}

// Sin verbo también se dice que hay, si la frase nombra el surtidor y el combustible.
const sinVerbo = [
  ['POSITIVO-59-08: surtidor con combustible', 'Las Vegas con gasolina', {}, true],
  ['POSITIVO-59-09: combustible en surtidor', 'gasolina en vegas', {}, true],
  ['POSITIVO-59-10: con emoji', 'Vegas: gasolina ✅', {}, true],
  ['POSITIVO-59-11: fila larga dicha como larguísima', 'en Vegas hay gasolina, fila larguísima', {fila: 'LARGA'}, true],
  ['NEG-59-18: lo que va a llegar no es que haya', 'mañana gasolina en Vegas', {}, false],
  ['NEG-59-19: esperando no es que haya', 'esperando gasolina en Vegas', {}, false],
  ['NEG-59-20: fila sin verbo no confirma combustible', 'fila larga en Vegas para gasolina', {}, false],
  ['NEG-59-21: sin verbo no sirve para un combustible no escrito', 'Las Vegas con gasolina', {combustible: 'Diesel'}, false],
  ['NEG-59-22: sin verbo no vale como pregunta', 'gasolina en vegas?', {}, false],
  ['NEG-59-23: lo explícito gana a lo que se nombra sin verbo', 'ya no hay en vegas, gasolina', {}, false],
  ['POSITIVO-59-12: lo explícito gana a lo que se nombra sin verbo', 'ya no hay en vegas, gasolina',
    {estado: 'AGOTADO'}, true]
];
for (const [nombre, texto, aviso, acepta] of sinVerbo) {
  test(nombre, () => {
    const obtenido = pruebaTexto(texto, aviso);
    assert.equal(obtenido.length, acepta ? 1 : 0);
    if (acepta && aviso.fila) assert.equal(obtenido[0].fila, aviso.fila);
  });
}

test('LLM inconsistente: un mismo ID propuesto como descartado y como aviso nunca se publica', () => {
  const salida = interpretOutput({avisos: [propuesta()],
    descartados: [{mensaje: 'm1', motivo: 'AMBIGUO'}]},
  [{id: 'id', texto: 'hay gasolina en Las Vegas', fecha: FECHA}], C);
  assert.deepEqual(salida.avisos, []);
});

test('RN-06.6 y CP-78: diferentes formatos de teléfonos y fechas', () => {
  const original = 'al +591 (712) 34567, fijo (04) 6641234, al +1 (213) 555-0123 el 14/11/2026';
  const filtrado = maskPhones(original);
  assert.equal(filtrado, 'al [número], fijo [número], al [número] el 14/11/2026');
  assert.equal(maskPhones('2026-11-17 y 14.11.2026 a las 14:35'), '2026-11-17 y 14.11.2026 a las 14:35');
});

function context(t, llmMock, backendMock) {
  const h = {now: NOW, invocaciones: [], post: [], eventos: [], fail: false};
  const store = new MessageStore(':memory:', {clock: () => h.now});
  const service = new WhatsappService(config({LLM_API_KEY: 'FICTICIA'}), {
    store, clock: () => h.now, logger: createLogger(s => h.eventos.push(s)),
    llm: {analyze: async x => {h.invocaciones.push(x); return llmMock(x, h);}},
    backend: {
      getSurtidores: async () => C,
      postResultados: async r => {h.post.push(structuredClone(r)); return backendMock(r, h);}
    },
    schedule: () => 1, unschedule: () => {}, scheduleExpiry: () => 1,
    unscheduleExpiry: () => {}, scheduleAnalysis: () => 1, unscheduleAnalysis: () => {}
  });
  service.start();
  t.after(() => {service.stop(); store.close();});
  return {h, store, service};
}
const ingresar = (service, id, text, date = horaTarija(NOW - 60_000)) =>
  service.ingestText(msg({id, text, date}));
const aviso = ({user}) => ({avisos: JSON.parse(user).mensajes.map(m =>
  ({mensaje: m.id, surtidor: 'Las Vegas', combustible: 'Gasolina', estado: 'DISPONIBLE', fila: 'NO_MENCIONADA'})), descartados: []});

test('Backend caído A no bloquea B; el POST de B se intenta de inmediato y ambos se reenvían luego', async t => {
  const {h, store, service} = context(t, aviso, () => {
    if (h.fail) throw new ErrorServicio('BACKEND_CONEXION', 'backend simulado caído');
    return {aceptados: 1, descartados: []};
  });
  h.fail = true;
  ingresar(service, 'a', 'hay gasolina en Las Vegas');
  await service.analyzeCycle();
  assert.equal(store.listResults().length, 1);
  ingresar(service, 'b', 'hay gasolina en Las Vegas');
  h.now += 5 * 60000;
  await service.analyzeCycle();
  assert.equal(h.invocaciones.length, 2, 'B sí se analizó pese al fallo de envío de A');
  assert.equal(store.getById('b').estado, STATUS.PROCESSED);
  assert.equal(store.getById('b').texto, null);
  assert.equal(store.listResults().length, 2, 'A y B quedan retenidos, sin texto');
  assert.equal(h.post.length, 3, 'A inicial + reintento A + envío inmediato B');
  assert(!JSON.stringify(store.listResults()).includes('hay gasolina'));
  h.fail = false;
  h.now += 5 * 60000;
  await service.analyzeCycle();
  assert.equal(store.listResults().length, 0);
  assert.equal(h.post.length, 5);
  assert.equal(h.invocaciones.length, 2, 'sin nuevos pendientes no llama de nuevo al LLM');
  assert(!h.eventos.join('').includes('hay gasolina'));
});

test('Mensajes caducados mientras el LLM trabaja NO generan POST', async t => {
  let terminar;
  const llm = () => new Promise(resolve => {terminar = () => resolve(aviso({user: JSON.stringify({mensajes: [{id: 'm1'}]})}));});
  const {h, store, service} = context(t, llm, () => ({aceptados: 1, descartados: []}));
  ingresar(service, 'antiguo', 'hay gasolina en Las Vegas', horaTarija(NOW - TTL_MS + 60_000));
  const enCurso = service.analyzeCycle();
  await new Promise(resolve => setImmediate(resolve));
  h.now += 2 * 60_000;
  terminar();
  await enCurso;
  assert.equal(store.getById('antiguo').estado, STATUS.DISCARDED);
  assert.equal(store.getById('antiguo').texto, null);
  assert.equal(store.listResults().length, 0);
  assert.equal(h.post.length, 0);
});

test('501 mensajes pendientes se analizan en el mismo ciclo, en dos llamadas de 500 y 1, en orden', async t => {
  const {h, store, service} = context(t, () => ({avisos: [], descartados: []}),
    () => {throw new Error('No debería llamar al backend.');});
  for (let i = 0; i < 501; i++) ingresar(service, `i-${i}`, `texto informativo ${i}`, horaTarija(NOW - i * 1000));
  await service.analyzeCycle();
  assert.equal(h.invocaciones.length, 2);
  const primera = JSON.parse(h.invocaciones[0].user).mensajes;
  const segunda = JSON.parse(h.invocaciones[1].user).mensajes;
  assert.equal(primera.length, 500);
  assert.equal(segunda.length, 1);
  assert.equal(primera[0].texto, 'texto informativo 500');
  assert.equal(segunda[0].texto, 'texto informativo 0');
  // Cada llamada numera sus mensajes desde m1, y el esquema solo admite esos ids.
  assert.equal(segunda[0].id, 'm1');
  assert.equal(store.countPending(), 0);
  assert.equal(h.post.length, 0);
});

test('Si falla la segunda llamada, lo de la primera se entrega y el resto queda pendiente', async t => {
  let llamadas = 0;
  const {h, store, service} = context(t, ({user}) => {
    if (++llamadas === 2) throw new ErrorServicio('LLM_NO_DISPONIBLE', 'caído');
    return {avisos: [], descartados: JSON.parse(user).mensajes.map(m => ({mensaje: m.id, motivo: 'SIN_SURTIDOR'}))};
  }, () => {throw new Error('No debería llamar al backend.');});
  for (let i = 0; i < 501; i++) ingresar(service, `f-${i}`, `texto informativo ${i}`, horaTarija(NOW - i * 1000));
  await service.analyzeCycle();
  assert.equal(h.invocaciones.length, 2);
  assert.equal(store.countPending(), 1);
  assert.equal(store.getById('f-0').estado, STATUS.PENDING);
  assert(h.eventos.some(l => l.includes('"evento":"LLM_FALLO"') && l.includes('"mensajes":1')));
  assert(h.eventos.some(l => l.includes('"evento":"LOTE_ANALIZADO"') && l.includes('"mensajes":500') &&
    l.includes('"llamadas":1')));
});

test('Un 422 descarta el resultado inválido sin bloquear el siguiente', async t => {
  let n = 0;
  const {h, store, service} = context(t, aviso, () => {
    if (++n === 1) throw new BackendHttpError(422);
    return {aceptados: 1, descartados: []};
  });
  ingresar(service, 'uno', 'hay gasolina en Las Vegas');
  await service.analyzeCycle();
  assert.equal(store.listResults().length, 0);
  ingresar(service, 'dos', 'hay gasolina en Las Vegas');
  h.now += 1000;
  await service.analyzeCycle();
  assert.equal(store.listResults().length, 0);
  assert.equal(h.post.length, 2);
});

/** Verifica HTTP real local sin credenciales, servicios externos ni datos de personas. NO es CP-77 live. */
test('Integración local HTTP: Node + Gemini simulado + backend simulado + SQLite', async t => {
  let prompt, solicitud, llegadas = 0;
  const server = createServer(async (req, res) => {
    let body = '';
    for await (const chunk of req) body += chunk.toString();
    if (req.url === '/v1beta/models/gemini-3.1-flash-lite:generateContent') {
      prompt = JSON.parse(body);
      const out = {avisos: [{mensaje: 'm1', surtidor: 'Las Vegas', combustible: 'Gasolina',
        estado: 'DISPONIBLE', fila: 'CORTA'}], descartados: []};
      res.setHeader('content-type', 'application/json');
      res.end(JSON.stringify({candidates: [{finishReason: 'STOP', content: {parts: [{text: JSON.stringify(out)}]}}]}));
      return;
    }
    if (req.url === '/monitoreo/surtidores' && req.method === 'GET') {
      res.setHeader('content-type', 'application/json');
      res.end(JSON.stringify(C)); return;
    }
    if (req.url === '/monitoreo/resultados' && req.method === 'POST') {
      solicitud = JSON.parse(body);
      llegadas++;
      res.setHeader('content-type', 'application/json');
      res.end(JSON.stringify({aceptados: 1, descartados: []})); return;
    }
    res.statusCode = 404; res.end();
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => server.close());
  const origin = `http://127.0.0.1:${server.address().port}`;
  const c = config({BACKEND_URL: origin, LLM_API_KEY: 'CLAVE_SIMULADA', LLM_BASE_URL: origin});
  const store = new MessageStore(':memory:', {clock: () => NOW});
  const eventos = [];
  const service = new WhatsappService(c, {store, clock: () => NOW,
    backend: new BackendClient(c), llm: new GeminiClient({apiKey: 'CLAVE_SIMULADA',
      model: 'gemini-3.1-flash-lite', baseUrl: origin}),
    logger: createLogger(x => eventos.push(x)),
    schedule: () => 1, unschedule: () => {}, scheduleExpiry: () => 1,
    unscheduleExpiry: () => {}, scheduleAnalysis: () => 1, unscheduleAnalysis: () => {}});
  t.after(() => {service.stop(); store.close();});
  service.start();
  ingresar(service, 'http-1', 'ya hay gasolina en Las Vegas, fila corta; llamen al +591 (712) 34567');
  await service.analyzeCycle();
  assert.equal(llegadas, 1);
  assert.equal(solicitud.avisos[0].estado, 'DISPONIBLE');
  assert.equal(solicitud.avisos[0].fila, 'CORTA');
  assert.equal(store.getById('http-1').texto, null);
  assert.equal(store.listResults().length, 0);
  assert.equal(prompt.generationConfig.responseMimeType, 'application/json');
  assert(prompt.generationConfig.responseJsonSchema);
  assert(!JSON.stringify(prompt).includes('71234567'));
  assert(!JSON.stringify(prompt).includes('http-1'));
  assert(!eventos.join(' ').includes('71234567'));
});


test('La configuración impide frecuencias más lentas que el minuto/5 minutos exigidos', () => {
  assert.throws(() => config({MAINTENANCE_INTERVAL_MS: '61000'}), e => e.code === 'CONFIG_INVALIDA');
  assert.throws(() => config({ANALYSIS_INTERVAL_MS: '300001'}), e => e.code === 'CONFIG_INVALIDA');
  assert.equal(config().maintenanceIntervalMs, 60000);
  assert.equal(config().analysisIntervalMs, 300000);
});
