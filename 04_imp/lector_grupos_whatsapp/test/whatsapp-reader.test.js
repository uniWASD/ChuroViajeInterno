import test from 'node:test';
import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {mkdtempSync, writeFileSync, chmodSync, rmSync, readFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {WhatsappReader} from '../src/whatsapp-reader.js';
import {extractGroupText} from '../src/whatsapp-messages.js';
import {MessageStore} from '../src/store.js';
import {createAuthState} from '../src/auth-state.js';
import {loadAuthorizations} from '../src/authorizations.js';
import {loadConfig} from '../src/config.js';

const G1 = '1234567890-1234567890@g.us';
const G2 = '9999999999-1234567890@g.us';
const PHONE = '59170000000';
const NOW = Date.parse('2026-10-29T20:00:00Z');

function workspace() {
  const dir = mkdtempSync(join(tmpdir(), 'hu08-'));
  const authorizations = join(dir, 'autorizaciones.json');
  const writeAuth = records => {
    writeFileSync(authorizations, JSON.stringify({autorizaciones: records}), {mode: 0o600});
    if (process.platform !== 'win32') chmodSync(authorizations, 0o600);
  };
  const record = group => ({grupo: group, fecha: '2026-10-29T17:00:00-04:00',
    solicitud: 'Solicitada y confirmada por el administrador del grupo de prueba', autorizadoPorAdmin: true});
  writeAuth([record(G1), record(G2)]);
  return {dir, authorizations, writeAuth, record, clean: () => rmSync(dir, {recursive: true, force: true})};
}

function sample(kind, overrides = {}) {
  const message = {
    key: {remoteJid: G1, id: 'ABC001', fromMe: false, participant: '99999999@s.whatsapp.net'},
    pushName: 'Dato personal de prueba',
    messageTimestamp: NOW / 1000,
    message: kind === 'text' ? {conversation: 'Hay gasolina en el surtidor'} :
      kind === 'extended' ? {extendedTextMessage: {text: 'Diésel disponible', contextInfo: {participant: '99999999'}}} :
      kind === 'image' ? {imageMessage: {caption: 'Aquí hay gasolina'}} :
      kind === 'audio' ? {audioMessage: {mimetype: 'audio/ogg'}} :
      kind === 'sticker' ? {stickerMessage: {mimetype: 'image/webp'}} :
      kind === 'reaction' ? {reactionMessage: {text: '👍'}} :
      {protocolMessage: {type: 'REVOKE'}}
  };
  return {...message, ...overrides};
}

function fakeHarness(options = {}) {
  const env = workspace();
  const events = [];
  const store = new MessageStore(join(env.dir, 'mensajes.sqlite'), {clock: () => NOW});
  const config = {
    allowedGroupIds: [G1], whatsappPhoneNumber: PHONE,
    whatsappSessionPath: join(env.dir, 'session.sqlite'),
    whatsappAuthorizationsFile: env.authorizations,
    sqlitePath: join(env.dir, 'mensajes.sqlite'), whatsappReconnectMs: 2000
  };
  const sockets = [];
  const timers = new Map();
  let counter = 0;
  const makeSocket = async () => {
    const ev = new EventEmitter();
    const socket = {ev, user: {id: `${PHONE}:7@s.whatsapp.net`}, ws: {close() { socket.closed = true; }}};
    sockets.push(socket);
    if (options.failSocket && sockets.length === 1) throw new Error('offline');
    return socket;
  };
  const auth = {state: {creds: {me: {id: `${PHONE}@s.whatsapp.net`}}}, saveCreds: async () => {}, close() {this.closed = true;}};
  const reader = new WhatsappReader(config, {store, ingestText: x => store.savePending(x)}, {
    makeAuth: async () => auth, makeSocket,
    logger: {event: e => events.push(e)}, clock: () => NOW,
    schedule: (fn, delay) => { const id = ++counter; timers.set(id, {fn, delay}); return id; },
    unschedule: id => timers.delete(id), showQR: () => events.push('QR')
  });
  return {reader, config, store, sockets, timers, events, env, auth,
    stop() {reader.stop();store.close();env.clean();}};
}

for (const kind of ['image','audio','sticker','reaction','system']) {
  test(`CP-48 ignora ${kind}`, () => {
    assert.equal(extractGroupText(sample(kind), new Set([G1]), () => NOW), null);
  });
}

test('CP-48 guarda exclusivamente texto normal y extendido sin remitente', async () => {
  const h = fakeHarness();
  try {
    await h.reader.start();
    const sock = h.sockets[0];
    sock.ev.emit('connection.update', {connection: 'open'});
    sock.ev.emit('messages.upsert', {type: 'notify', messages: [sample('text'),
      sample('image', {key: {...sample('image').key, id: 'IMG'}}),
      sample('audio', {key: {...sample('audio').key, id: 'AUD'}}),
      sample('extended', {key: {...sample('text').key, id: 'EXT'}})]});
    const rows = h.store.listPending();
    assert.equal(rows.length, 2);
    assert.deepEqual(rows.map(r => r.texto).sort(), ['Diésel disponible', 'Hay gasolina en el surtidor'].sort());
    assert.equal(rows.every(row => !JSON.stringify(row).includes('99999999') && !JSON.stringify(row).includes('Dato personal')), true);
    assert.equal(h.store.db.prepare('PRAGMA table_info(mensaje_pendiente)').all().some(r => /participant|sender|remitente/i.test(r.name)), false);
  } finally {h.stop();}
});

test('CP-49 no almacena nada de un grupo no autorizado, aunque lleve texto', async () => {
  const h = fakeHarness();
  try {
    await h.reader.start();
    h.sockets[0].ev.emit('connection.update', {connection: 'open'});
    h.sockets[0].ev.emit('messages.upsert', {type: 'notify', messages: [sample('text', {
      key: {remoteJid: G2, id: 'EXTERNO', participant: 'secret@s.whatsapp.net'}
    })]});
    assert.equal(h.store.countPending(), 0);
  } finally {h.stop();}
});

test('CP-50 reintenta después de caída, acepta al reconectar y deduplica', async () => {
  const h = fakeHarness();
  try {
    await h.reader.start();
    h.sockets[0].ev.emit('connection.update', {connection: 'open'});
    h.sockets[0].ev.emit('messages.upsert', {type: 'notify', messages: [sample('text')]});
    h.sockets[0].ev.emit('connection.update', {connection: 'close', lastDisconnect: {error: {output: {statusCode: 408}}}});
    assert.equal(h.reader.connected, false);
    assert.equal(h.timers.size, 1);
    const retry = [...h.timers.values()][0];
    assert.equal(retry.delay, 2000);
    retry.fn();
    await new Promise(resolve => setImmediate(resolve));
    h.sockets[1].ev.emit('connection.update', {connection: 'open'});
    h.sockets[1].ev.emit('messages.upsert', {type: 'notify', messages: [sample('text')]});
    assert.equal(h.store.countPending(), 1);
    assert.equal(h.events.includes('WHATSAPP_DESCONECTADO'), true);
    assert.equal(h.events.includes('WHATSAPP_REINTENTO'), true);
  } finally {h.stop();}
});

test('cierre de sesión 401 no provoca reconexiones infinitas', async () => {
  const h = fakeHarness();
  try {
    await h.reader.start();
    h.sockets[0].ev.emit('connection.update', {connection: 'close', lastDisconnect: {error: {output: {statusCode: 401}}}});
    assert.equal(h.timers.size, 0);
    assert.equal(h.events.includes('WHATSAPP_SESION_CERRADA'), true);
    assert.equal(h.auth.closed, true);
  } finally {h.stop();}
});

test('cuenta distinta del número dedicado bloquea lectura y cancela el cliente', async () => {
  const h = fakeHarness();
  try {
    await h.reader.start();
    h.sockets[0].user.id = '59179999999@s.whatsapp.net';
    h.sockets[0].ev.emit('connection.update', {connection: 'open'});
    h.sockets[0].ev.emit('messages.upsert', {type: 'notify', messages: [sample('text')]});
    assert.equal(h.store.countPending(), 0);
    assert.equal(h.events.includes('WHATSAPP_CUENTA_INVALIDA'), true);
  } finally {h.stop();}
});

test('antes de autenticar, eventos históricos y mensajes propios no ingresan', async () => {
  const h = fakeHarness();
  try {
    await h.reader.start();
    const s = h.sockets[0];
    s.ev.emit('messages.upsert', {type: 'notify', messages: [sample('text')]});
    s.ev.emit('connection.update', {connection: 'open'});
    s.ev.emit('messages.upsert', {type: 'append', messages: [sample('text')]});
    s.ev.emit('messages.upsert', {type: 'notify', messages: [sample('text', {key: {...sample('text').key, fromMe: true}})]});
    assert.equal(h.store.countPending(), 0);
  } finally {h.stop();}
});

test('dos grupos con el mismo ID externo no colisionan al deduplicar', () => {
  const a = extractGroupText(sample('text'), new Set([G1,G2]), () => NOW);
  const b = extractGroupText(sample('text', {key: {...sample('text').key, remoteJid: G2}}), new Set([G1,G2]), () => NOW);
  assert.notEqual(a.id, b.id);
  assert.equal(a.id.length, 64);
});

test('texto efímero se admite, multimedia aun con caption se rechaza', () => {
  const event = sample('text', {message: {ephemeralMessage: {message: {extendedTextMessage: {text: 'Gasolina sí'}}}}});
  assert.equal(extractGroupText(event, new Set([G1])).text, 'Gasolina sí');
  const mixed = sample('text', {message: {conversation: 'texto', imageMessage: {caption: 'foto'}}});
  assert.equal(extractGroupText(mixed, new Set([G1])), null);
  const grouped = sample('text', {message: {conversation: 'Aviso de texto', senderKeyDistributionMessage: {groupId: 'x'}}});
  assert.equal(extractGroupText(grouped, new Set([G1])).text, 'Aviso de texto');
});

test('el archivo externo exige prueba expresa por grupo y permisos privados', () => {
  const env = workspace();
  try {
    assert.deepEqual([...loadAuthorizations(env.authorizations, [G1])], [G1]);
    assert.throws(() => loadAuthorizations(env.authorizations, ['4567890@g.us']), /autorizaciones/);
    env.writeAuth([{...env.record(G1), autorizadoPorAdmin: false}]);
    assert.throws(() => loadAuthorizations(env.authorizations, [G1]), /autorizaciones/);
    env.writeAuth([{...env.record(G1), solicitud: ''}]);
    assert.throws(() => loadAuthorizations(env.authorizations, [G1]), /autorizaciones/);
    if (process.platform !== 'win32') {
      env.writeAuth([env.record(G1)]);
      chmodSync(env.authorizations, 0o644);
      assert.throws(() => loadAuthorizations(env.authorizations, [G1]), /autorizaciones/);
    }
  } finally {env.clean();}
});

test('sesión privada SQLite persiste credenciales y claves Signal y permite eliminarlas', async () => {
  const env = workspace();
  try {
    const filename = join(env.dir, 'sesion.sqlite');
    let initiated = 0;
    const lib = {
      initAuthCreds: () => {initiated++; return {me: {id: `${PHONE}@s.whatsapp.net`}};},
      BufferJSON: {replacer: (_key, value) => value, reviver: (_key, value) => value},
      proto: {Message: {AppStateSyncKeyData: {fromObject: x => ({...x, converted: true})}}}
    };
    const state1 = createAuthState(filename, lib);
    state1.state.creds.registered = true;
    await state1.saveCreds();
    await state1.state.keys.set({session: {'id-1': {secret: 'protected'}}, 'app-state-sync-key': {'id2': {v: 3}}});
    state1.close();
    const state2 = createAuthState(filename, lib);
    assert.equal(initiated, 1);
    assert.equal(state2.state.creds.registered, true);
    assert.deepEqual(await state2.state.keys.get('session', ['id-1']), {'id-1': {secret: 'protected'}});
    assert.equal((await state2.state.keys.get('app-state-sync-key', ['id2'])).id2.converted, true);
    await state2.state.keys.set({session: {'id-1': null}});
    assert.equal((await state2.state.keys.get('session', ['id-1']))['id-1'], undefined);
    if (process.platform !== 'win32') {
      const {statSync} = await import('node:fs');
      assert.equal(statSync(filename).mode & 0o077, 0);
    }
    state2.close();
  } finally {env.clean();}
});

test('configuración activa WhatsApp de forma explícita y valida sus límites', () => {
  const env = {BACKEND_URL:'http://localhost:5678', SERVICE_TOKEN:'token', ALLOWED_GROUP_IDS:G1};
  const base = loadConfig(env);
  assert.equal(base.whatsappEnabled, false);
  const live = loadConfig({...env, WHATSAPP_ENABLED:'true', WHATSAPP_PHONE_NUMBER:PHONE,
    WHATSAPP_AUTHORIZATIONS_FILE:'/tmp/autorizaciones.json', WHATSAPP_RECONNECT_MS:'2000'});
  assert.equal(live.whatsappEnabled, true);
  assert.equal(live.whatsappPhoneNumber, PHONE);
  assert.throws(() => loadConfig({...env, WHATSAPP_PHONE_NUMBER:'591 70000000'}), /WHATSAPP_PHONE_NUMBER/);
  assert.throws(() => loadConfig({...env, WHATSAPP_RECONNECT_MS:'0'}), /WHATSAPP_RECONNECT_MS/);
});

test('fallo inicial del socket programa reconexión periódica', async () => {
  const h = fakeHarness({failSocket: true});
  try {
    await h.reader.start();
    assert.equal(h.timers.size, 1);
    [...h.timers.values()][0].fn();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(h.sockets.length, 2);
  } finally {h.stop();}
});
