import test from 'node:test';
import assert from 'node:assert/strict';
import {maskPhones} from '../src/phone-mask.js';
import {GeminiClient} from '../src/gemini-client.js';
import {clave, mencionaFrase} from '../src/normalize.js';
import {validateCatalog} from '../src/catalog.js';
import {config} from '../test-support/helpers.js';

test('RN-06.6: se enmascaran celulares y fijos, con o sin prefijo y separadores', () => {
  assert.equal(maskPhones('llamen al 71234567'), 'llamen al [número]');
  assert.equal(maskPhones('cel 712-34567, fijo 6641234 o +591 7 123 4567'), 'cel [número], fijo [número] o [número]');
  assert.equal(maskPhones('wsp +59171234567.'), 'wsp [número].');
});

test('No se enmascaran horas, fechas, litros ni precios', () => {
  const texto = 'a las 15:30 del 14/11/2026 quedan 1000 litros a 3.74 Bs, fila de 200 m';
  assert.equal(maskPhones(texto), texto);
});

test('La clave del alias coincide con la del backend (ClaveAlias)', () => {
  assert.equal(clave(' San  Martín '), 'san martin');
  assert.equal(clave('Estación S.R.L.'), 'estacion s r l');
  assert(mencionaFrase('¡Ya hay en MÁS VEGAS!', 'mas vegas'));
  assert(!mencionaFrase('las vegasitas', 'vegas'));
});

test('El catálogo del backend se valida antes de usarlo', () => {
  assert.throws(() => validateCatalog({surtidores: [], combustibles: ['Gasolina'], filas: []}), e => e.code === 'CATALOGO_INVALIDO');
  assert.throws(() => validateCatalog({surtidores: [{nombre: 'X'}], combustibles: ['Gasolina'], filas: []}), e => e.code === 'CATALOGO_INVALIDO');
  assert.equal(validateCatalog({surtidores: [{nombre: 'X', alias: []}], combustibles: ['Gasolina'], filas: ['CORTA']}).surtidores[0].nombre, 'X');
});

function falsoFetch(respuesta) {
  const llamadas = [];
  const fetchImpl = async (url, opciones) => {
    llamadas.push({url, opciones});
    if (respuesta instanceof Error) throw respuesta;
    return typeof respuesta === 'function' ? respuesta() : respuesta;
  };
  return {llamadas, fetchImpl};
}
const ok = cuerpo => ({ok: true, status: 200, json: async () => cuerpo});
const salida = texto => ({candidates: [{finishReason: 'STOP', content: {parts: [{text: texto}]}}]});

test('Gemini: pide salida estructurada con la clave en la cabecera, no en la URL', async () => {
  const {llamadas, fetchImpl} = falsoFetch(ok(salida('{"avisos":[],"descartados":[]}')));
  const cliente = new GeminiClient({apiKey: 'CLAVE_FICTICIA', model: 'gemini-3.1-flash-lite'}, {fetchImpl});
  const r = await cliente.analyze({system: 'S', user: 'U', schema: {type: 'object'}});
  assert.deepEqual(r, {avisos: [], descartados: []});
  const {url, opciones} = llamadas[0];
  assert.equal(url, 'https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent');
  assert.equal(opciones.headers['x-goog-api-key'], 'CLAVE_FICTICIA');
  const cuerpo = JSON.parse(opciones.body);
  assert.equal(cuerpo.generationConfig.responseMimeType, 'application/json');
  assert.deepEqual(cuerpo.generationConfig.responseJsonSchema, {type: 'object'});
  assert.equal(cuerpo.generationConfig.temperature, 0);
  assert.equal(cuerpo.systemInstruction.parts[0].text, 'S');
  assert.equal(cuerpo.contents[0].parts[0].text, 'U');
});

test('Gemini: cuota, error, red caída y respuesta incompleta son fallas con código propio', async () => {
  const casos = [
    [{ok: false, status: 429, json: async () => ({})}, 'LLM_CUOTA'],
    [{ok: false, status: 503, json: async () => ({})}, 'LLM_NO_DISPONIBLE'],
    [new TypeError('fetch failed'), 'LLM_NO_DISPONIBLE'],
    [ok(salida('no es json')), 'LLM_FORMATO'],
    [ok({candidates: [{finishReason: 'MAX_TOKENS', content: {parts: [{text: '{"avisos":['}]}}]}), 'LLM_FORMATO'],
    [{ok: true, status: 200, json: async () => { throw new SyntaxError('x'); }}, 'LLM_FORMATO']
  ];
  for (const [respuesta, codigo] of casos) {
    const cliente = new GeminiClient({apiKey: 'K', model: 'm'}, falsoFetch(respuesta));
    await assert.rejects(cliente.analyze({system: '', user: '', schema: {}}), e => e.code === codigo && !e.message.includes('{'));
  }
});

test('Configuración del LLM: valores por defecto y rechazos', () => {
  const c = config();
  assert.equal(c.llmModel, 'gemini-3.1-flash-lite');
  assert.equal(c.llmBaseUrl, 'https://generativelanguage.googleapis.com');
  assert.equal(c.analysisIntervalMs, 300000);
  assert.equal(c.maintenanceIntervalMs, 60000);
  assert.equal(config({LLM_BASE_URL: 'http://127.0.0.1:9099'}).llmBaseUrl, 'http://127.0.0.1:9099');
  for (const malo of [{LLM_BASE_URL: 'http://ejemplo.com'}, {LLM_BASE_URL: 'https://x.com/ruta'}, {LLM_MODEL: 'Modelo Malo'},
    {ANALYSIS_INTERVAL_MS: '10'}, {LLM_API_KEY: 'a\nb'}]) {
    assert.throws(() => config(malo), e => e.code === 'CONFIG_INVALIDA');
  }
});
