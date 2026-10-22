import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn, spawnSync} from 'node:child_process';
import {DatabaseSync} from 'node:sqlite';
import {copyFileSync, mkdirSync, readdirSync, readFileSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {MessageStore, STATUS, TTL_MS, FINISHED_RETENTION_MS} from '../src/store.js';
import {WhatsappService} from '../src/service.js';
import {BackendClient, validateBatch} from '../src/backend-client.js';
import {loadConfig} from '../src/config.js';
import {createLogger} from '../src/logger.js';
import {msg, NOW, config, workspace} from '../test-support/helpers.js';

const projectRoot = fileURLToPath(new URL('..', import.meta.url));

/**
 * Copia los módulos de src/ sin fs.cpSync({recursive:true}). En ciertas
 * versiones de Node 22 sobre Windows, la ruta nativa de cpSync puede terminar
 * el proceso con 0xC0000409 antes de que el ejecutor reporte las pruebas.
 * Esta implementación evita esa ruta y mantiene el aislamiento del despliegue.
 */
function copyTreeForTest(source, destination) {
  mkdirSync(destination, {recursive: true});
  for (const entry of readdirSync(source, {withFileTypes: true})) {
    const origin = join(source, entry.name);
    const target = join(destination, entry.name);
    if (entry.isDirectory()) copyTreeForTest(origin, target);
    else if (entry.isFile()) copyFileSync(origin, target);
    else throw new Error('Solo se admiten archivos regulares en src/ para estas pruebas.');
  }
}

test('RN-06.11: fecha +10 minutos nunca prolonga 30 minutos desde recepción', t => {
  const ws = workspace(t); let now = NOW;
  const store = new MessageStore(ws.db, {clock: () => now});
  ws.addCleanup(() => store.close());
  store.savePending(msg({date: new Date(NOW + 10 * 60000).toISOString()}));
  assert.equal(store.db.prepare('SELECT recibido_ms FROM mensaje_pendiente').get().recibido_ms, NOW);
  assert.equal(store.nextExpirationMs(), NOW + TTL_MS + 1);
  now = NOW + TTL_MS;
  assert.equal(store.countPending(), 1);
  assert.equal(store.expireOldPending(), 0);
  now++;
  assert.equal(store.expireOldPending(), 1);
  assert.deepEqual({estado: store.getById('m-1').estado, texto: store.getById('m-1').texto},
    {estado: 'DESCARTADO', texto: null});
});

test('RN-06.12: fecha futura extrema 2099 se descarta tras 30 minutos, sin fuga de texto', t => {
  const ws = workspace(t); let now = NOW;
  const store = new MessageStore(ws.db, {clock: () => now});
  ws.addCleanup(() => store.close());
  store.savePending(msg({date: '2099-12-31T23:59:59Z'}));
  now += TTL_MS + 1;
  assert.equal(store.listPending().length, 0);
  assert.equal(store.expireOldPending(), 1);
  assert.equal(store.getById('m-1').texto, null);
});

test('RN-06.11: fecha antigua sigue imponiendo vencimiento temprano', t => {
  const ws = workspace(t); const store = new MessageStore(ws.db, {clock: () => NOW});
  ws.addCleanup(() => store.close());
  store.savePending(msg({date: new Date(NOW - TTL_MS + 1234).toISOString()}));
  assert.equal(store.nextExpirationMs(), NOW + 1235);
  assert.equal(store.countPending(), 1);
});

test('retención de 24 h: se eliminan filas finalizadas, pero no antes', t => {
  const ws = workspace(t); let now = NOW;
  const store = new MessageStore(ws.db, {clock: () => now});
  ws.addCleanup(() => store.close());
  store.savePending(msg());
  assert.equal(store.markProcessed('m-1'), 1);
  now = NOW + FINISHED_RETENTION_MS;
  assert.equal(store.purgeFinished(), 0);
  assert.equal(store.savePending(msg()).inserted, false);
  now++;
  assert.equal(store.purgeFinished(), 1);
  assert.equal(store.getById('m-1'), null);
  assert.deepEqual(store.savePending(msg({date: new Date(now).toISOString()})),
    {inserted: true, status: STATUS.PENDING});
});

test('migración SQLite v0 -> v1 conserva mensajes recientes y elimina textos de fechas futuras', t => {
  const ws = workspace(t); const legacy = new DatabaseSync(ws.db);
  legacy.exec(`CREATE TABLE mensaje_pendiente (
    id TEXT PRIMARY KEY NOT NULL,grupo TEXT NOT NULL,fecha TEXT NOT NULL,
    fecha_ms INTEGER NOT NULL,texto TEXT,estado TEXT NOT NULL,
    finalizado_ms INTEGER)`);
  const old = legacy.prepare('INSERT INTO mensaje_pendiente VALUES (?,?,?,?,?,?,?)');
  const past = NOW - 1000, future = NOW + 3600000;
  old.run('reciente','grupo',new Date(past).toISOString(),past,'TEXTO_RECIENTE','PENDIENTE',null);
  old.run('futuro','grupo',new Date(future).toISOString(),future,'TEXTO_FUTURO','PENDIENTE',null);
  old.run('finalizado','grupo',new Date(past).toISOString(),past,null,'PROCESADO',NOW-100);
  legacy.close();
  const migrated = new MessageStore(ws.db, {clock: () => NOW});
  ws.addCleanup(() => migrated.close());
  assert.equal(migrated.db.prepare('PRAGMA user_version').get().user_version, 1);
  assert.equal(migrated.getById('reciente').texto, 'TEXTO_RECIENTE');
  assert.deepEqual({estado: migrated.getById('futuro').estado, texto: migrated.getById('futuro').texto},
    {estado: 'DESCARTADO', texto: null});
  assert.equal(migrated.getById('finalizado').estado, 'PROCESADO');
  assert.equal(migrated.db.prepare('PRAGMA table_info(mensaje_pendiente)').all()
    .find(x => x.name === 'recibido_ms').notnull, 1);
});

test('se admiten tablas y columnas técnicas nuevas, pero no remitente', t => {
  const ws = workspace(t); const store = new MessageStore(ws.db, {clock: () => NOW});
  store.db.exec('ALTER TABLE mensaje_pendiente ADD COLUMN intentos INTEGER DEFAULT 0');
  store.db.exec('CREATE TABLE cola_de_resultados (id TEXT PRIMARY KEY)');
  store.close();
  const updated = new MessageStore(ws.db, {clock: () => NOW});
  updated.savePending(msg());
  assert.equal(updated.getById('m-1').estado, 'PENDIENTE');
  updated.close();
  const db = new DatabaseSync(ws.db);
  db.exec('ALTER TABLE mensaje_pendiente ADD COLUMN remitente TEXT');
  db.close();
  assert.throws(() => new MessageStore(ws.db), e => e.code === 'ESQUEMA_NO_PERMITIDO');
});

test('Git real y carpeta de servicio se rechazan; JSON hermano funciona al desplegar', async t => {
  const ws = workspace(t);
  const deployed = join(ws.dir, 'deploy');
  mkdirSync(deployed);
  copyTreeForTest(join(projectRoot, 'src'), join(deployed, 'src'));
  writeFileSync(join(deployed, 'package.json'), JSON.stringify({type:'module'}));
  const external = join(ws.dir, 'secrets.json');
  const internal = join(deployed, 'internal.json');
  const values = {BACKEND_URL:'https://backend.example.invalid',SERVICE_TOKEN:'EJEMPLO',ALLOWED_GROUP_IDS:'grupo@g.us'};
  writeFileSync(external, JSON.stringify(values));
  writeFileSync(internal, JSON.stringify(values));
  // Importar una copia del módulo, no el original, evita falsos positivos por rutas del checkout.
  const imported = await import(pathToFileURL(join(deployed, 'src/config.js')).href);
  const loaded = imported.loadConfig({}, {filePath: external});
  assert.equal(loaded.serviceToken, 'EJEMPLO');
  assert.throws(() => imported.loadConfig({}, {filePath: internal}), e => e.code === 'CONFIG_INVALIDA');
  mkdirSync(join(ws.dir, '.git')); // representa un repositorio que contiene el JSON
  assert.throws(() => imported.loadConfig({}, {filePath: external}), e => e.code === 'CONFIG_INVALIDA');
});

test('SERVICE_TOKEN_PREFIX sin espacio se rechaza, sin enviar BearerTOKEN', () => {
  assert.throws(() => config({SERVICE_TOKEN_PREFIX:'Bearer'}), e => e.code === 'CONFIG_INVALIDA');
  assert.equal(config({SERVICE_TOKEN_PREFIX:'Bearer '}).serviceTokenPrefix, 'Bearer ');
  assert.equal(config({SERVICE_TOKEN_PREFIX:''}).serviceTokenPrefix, '');
});

test('cabecera HTTP acepta 204 en POST, no en GET; conserva JSON 200', async () => {
  const client = new BackendClient(config(), {fetchImpl: async () => new Response(null, {status: 204})});
  const batch = {lote:'2026-10-15T12:00:00Z',avisos:[]};
  assert.equal(await client.postResultados(batch), null);
  await assert.rejects(() => client.getSurtidores(), e => e.code === 'BACKEND_FORMATO');
});

test('fila sin enumeración fija: se delega el catálogo de filas a Java', () => {
  const batch = {lote:'2026-10-15T12:00:00Z',avisos:[{surtidor:'S',combustible:'D',estado:'DISPONIBLE',fila:'VALOR_DEL_CATALOGO',fecha:'2026-10-15T12:00:00Z'}]};
  assert.equal(validateBatch(batch), batch);
  assert.throws(() => validateBatch({...batch,avisos:[{...batch.avisos[0],fila:' '}]}), e => e.code === 'RESULTADO_INVALIDO');
});

test('diagnóstico de fallos: solo se registran código y detalle permitidos', () => {
  const buffer = []; const logger = createLogger(line => buffer.push(JSON.parse(line)));
  logger.event('ERROR_CONFIGURACION', {
    codigo:'CONFIG_INVALIDA',detalle:'Falta BACKEND_URL.', token:'VALOR_PRIVADO',mensaje:'TEXTO_PRIVADO'
  });
  assert.deepEqual(buffer[0], {evento:'ERROR_CONFIGURACION',codigo:'CONFIG_INVALIDA',detalle:'Falta BACKEND_URL.'});
  logger.event('ERROR_INESPERADO', {detalle:'CLAVE_SECRETA',codigo:'CODIGO_NO_PERMITIDO'});
  assert.deepEqual(buffer[1], {evento:'ERROR_INESPERADO'});
  logger.event('ERROR_SQLITE', {codigo:'EROFS',detalle:'VALOR_PRIVADO'});
  assert.deepEqual(buffer[2], {evento:'ERROR_SQLITE',codigo:'EROFS'});
});

test('fallo de arranque: ruta SQLite atravesando archivo informa código nativo sin revelar la ruta', t => {
  const ws = workspace(t);
  const fileInPath = join(ws.dir, 'es_archivo');
  writeFileSync(fileInPath, 'contenido ficticio');
  const sqlitePath = join(fileInPath, 'subcarpeta', 'pendientes.sqlite');
  const env = {...process.env, BACKEND_URL:'http://127.0.0.1:18080',
    SERVICE_TOKEN:'TOKEN_FICTICIO', ALLOWED_GROUP_IDS:'grupo@g.us', SQLITE_PATH:sqlitePath};
  delete env.WHATSAPP_SERVICE_CONFIG_FILE;
  delete env.POLL_INTERVAL_MS;
  const result = spawnSync(process.execPath, ['src/main.js'],
    {cwd: projectRoot, env, encoding:'utf8', timeout:15000, windowsHide:true});
  assert.equal(result.status, 1, result.stderr || result.error?.message);
  const output = result.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line));
  assert.equal(output.length, 1);
  assert.equal(output[0].evento, 'ERROR_SQLITE');
  if (process.platform === 'win32') assert.match(output[0].codigo, /^E[A-Z0-9]+$/);
  else assert.equal(output[0].codigo, 'ENOTDIR');
  assert(!result.stdout.includes(sqlitePath));
  assert(!result.stdout.includes('TOKEN_FICTICIO'));
});

test('integración: .env opcional carga el arranque en otro directorio sin tocar el repositorio', async t => {
  const ws = workspace(t); const target = join(ws.dir, 'servicio');
  mkdirSync(target);
  copyTreeForTest(join(projectRoot, 'src'), join(target, 'src'));
  writeFileSync(join(target, 'package.json'), JSON.stringify({type:'module'}));
  writeFileSync(join(target, '.env'), `BACKEND_URL=http://127.0.0.1:18080\nSERVICE_TOKEN=TOKEN_FICTICIO_DE_TEST\nALLOWED_GROUP_IDS=grupo@g.us\nSQLITE_PATH=./data/pendientes.sqlite\nMAINTENANCE_INTERVAL_MS=1000\n`);
  const env = {...process.env};
  for (const key of ['BACKEND_URL','SERVICE_TOKEN','ALLOWED_GROUP_IDS','SQLITE_PATH','WHATSAPP_SERVICE_CONFIG_FILE']) delete env[key];
  const child = spawn(process.execPath, ['--env-file-if-exists=.env','src/main.js'],
    {cwd: target, env, stdio: ['ignore','pipe','pipe'], windowsHide: true});
  let stdout = ''; let stderr = '';
  child.stdout.on('data', data => stdout += data);
  child.stderr.on('data', data => stderr += data);
  const completed = new Promise(resolve => child.once('close', (code, signal) => resolve({code, signal})));
  ws.addCleanup(async () => { if (child.exitCode === null) child.kill(); await completed; });
  let timeout;
  const started = await Promise.race([
    new Promise((resolve, reject) => {
      child.stdout.on('data', () => { if (stdout.includes('SERVICIO_INICIADO')) resolve(); });
      child.once('close', () => reject(new Error('SALIDA_ANTES_DE_INICIAR: ' + stdout + stderr)));
    }),
    new Promise((_resolve, reject) => { timeout = setTimeout(() => reject(new Error('ARRANQUE_DEMO_TARDO_DEMASIADO')), 10000); })
  ]).finally(() => clearTimeout(timeout));
  assert.equal(started, undefined);
  assert(stdout.includes('SERVICIO_INICIADO'));
  assert(!stdout.includes('TOKEN_FICTICIO'));
  child.kill();
  await completed;
  assert.equal(readFileSync(join(target,'package.json'), 'utf8').includes('module'), true);
});

test('secure_delete: tras descartar el texto no permanece visible en el archivo SQLite', t => {
  const ws = workspace(t);
  let store = new MessageStore(ws.db, {clock: () => NOW});
  ws.addCleanup(() => store.close());
  const secret = 'PRIVADO_DE_BORRADO_FISICO_1234567890_ABCDE';
  store.savePending(msg({text:secret}));
  store.close();
  assert(readFileSync(ws.db).includes(Buffer.from(secret)), 'La sonda debe encontrar el texto antes de borrarlo');
  store = new MessageStore(ws.db, {clock: () => NOW});
  store.markDiscarded('m-1');
  assert.equal(store.getById('m-1').texto, null);
  assert.equal(store.db.prepare('PRAGMA secure_delete').get().secure_delete, 1);
  store.close();
  assert(!readFileSync(ws.db).includes(Buffer.from(secret)), 'secure_delete debe sobrescribir el texto anterior');
  store = new MessageStore(ws.db, {clock: () => NOW});
});

test('sin remitente incluso con 1500 mensajes sintéticos, se borran todos al caducar', t => {
  const ws = workspace(t);
  let now = NOW;
  const store = new MessageStore(':memory:', {clock: () => now});
  ws.addCleanup(() => store.close());
  for (let n = 0; n < 1500; n++) {
    store.savePending(msg({id:`masivo-${n}`, date: new Date(NOW + 60000).toISOString()}));
  }
  assert.equal(store.countPending(), 1500);
  now += TTL_MS + 1;
  assert.equal(store.expireOldPending(), 1500);
  assert.equal(store.countPending(), 0);
  assert.equal(store.db.prepare("SELECT count(*) AS n FROM mensaje_pendiente WHERE texto IS NOT NULL").get().n, 0);
  assert.equal(store.db.prepare("SELECT count(*) AS n FROM mensaje_pendiente WHERE estado='DESCARTADO'").get().n, 1500);
});
