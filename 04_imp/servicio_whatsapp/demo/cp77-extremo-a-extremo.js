/**
 * CP-77 (issue #59): un mensaje válido del grupo de prueba queda en `estado_combustible` del backend en menos de
 * 6 minutos (RNF-04).
 *
 * Corre el servicio real (almacén SQLite, ciclo de 5 minutos, cliente del backend y cliente de Gemini) contra un
 * backend Java real. Solo el LLM está simulado: un servidor en 127.0.0.1 que responde como Gemini, para no depender de
 * una clave ni de la cuota. El mensaje entra por `ingestText`, el mismo punto por el que entra lo que lee Baileys.
 *
 * Uso, con el backend ya levantado y su SERVICE_CREDENTIAL:
 *   BACKEND_URL=http://127.0.0.1:8080 SERVICE_TOKEN=<credencial> node demo/cp77-extremo-a-extremo.js
 * Termina con código 0 cuando el backend aceptó el aviso, e imprime cuánto tardó desde que llegó el mensaje. La
 * consulta a `estado_combustible` la hace quien corre la prueba (ver el LEEME del sprint).
 */
import {createServer} from 'node:http';
import {mkdtempSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {loadConfig} from '../src/config.js';
import {WhatsappService, horaTarija} from '../src/service.js';
import {createLogger} from '../src/logger.js';

const TEXTO = 'ya hay gasolina en Las Vegas, fila corta';
let llegoAlLlm = null;
const llm = createServer((req, res) => {
  let cuerpo = '';
  req.on('data', c => { cuerpo += c; });
  req.on('end', () => {
    const peticion = JSON.parse(cuerpo);
    const mensajes = JSON.parse(peticion.contents[0].parts[0].text).mensajes;
    llegoAlLlm = {mensajes: mensajes.length, esquema: Boolean(peticion.generationConfig?.responseJsonSchema)};
    // Responde lo que respondería Gemini a este mensaje.
    const salida = {avisos: mensajes.map(m => ({mensaje: m.id, surtidor: 'Las Vegas', combustible: 'Gasolina',
      estado: 'DISPONIBLE', fila: 'CORTA'})), descartados: []};
    res.writeHead(200, {'Content-Type': 'application/json'});
    res.end(JSON.stringify({candidates: [{finishReason: 'STOP', content: {parts: [{text: JSON.stringify(salida)}]}}]}));
  });
});
await new Promise(r => llm.listen(0, '127.0.0.1', r));
const dir = mkdtempSync(join(tmpdir(), 'churo-cp77-'));
const lineas = [];
let terminar;
const listo = new Promise(r => { terminar = r; });
const logger = createLogger(linea => {
  process.stdout.write(`${linea}\n`);
  lineas.push(linea);
  if (linea.includes('"evento":"RESULTADO_ENVIADO"')) terminar(true);
});
const config = loadConfig({...process.env, ALLOWED_GROUP_IDS: 'grupo-prueba@g.us', SQLITE_PATH: join(dir, 'p.sqlite'),
  LLM_API_KEY: 'CLAVE_DEL_LLM_SIMULADO', LLM_BASE_URL: `http://127.0.0.1:${llm.address().port}`});
const service = new WhatsappService(config, {logger});
service.start();
const llegada = Date.now();
service.ingestText({id: 'cp77-1', group: 'grupo-prueba@g.us', date: horaTarija(llegada), text: TEXTO});
console.log(JSON.stringify({prueba: 'CP-77', mensajeRecibido: horaTarija(llegada), cicloCadaMs: config.analysisIntervalMs}));
const tiempoMaximo = setTimeout(() => terminar(false), 6 * 60 * 1000);
const ok = await listo;
clearTimeout(tiempoMaximo);
const segundos = Math.round((Date.now() - llegada) / 1000);
console.log(JSON.stringify({prueba: 'CP-77', resultado: ok ? 'ACEPTADO_POR_EL_BACKEND' : 'NO_LLEGO_EN_6_MINUTOS',
  segundosDesdeElMensaje: segundos, llegoAlLlm}));
service.stop();
llm.close();
rmSync(dir, {recursive: true, force: true});
process.exitCode = ok && lineas.some(l => l.includes('"aceptados":1')) ? 0 : 1;
