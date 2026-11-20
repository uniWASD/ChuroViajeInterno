// Diagnóstico manual: solo informa los JID de grupos que enviaron mensajes nuevos.
// No imprime texto, remitentes, nombres ni números de teléfono.
import {createBaileysAdapters} from '../04_imp/servicio_whatsapp/src/baileys-adapter.js';
import {resolve} from 'node:path';

if (!process.env.WHATSAPP_SESSION_PATH) {
  console.error('Debes definir WHATSAPP_SESSION_PATH fuera de la carpeta del repositorio.');
  process.exit(2);
}
const {makeAuth, makeSocket, showQR} = await createBaileysAdapters();
const auth = await makeAuth(resolve(process.env.WHATSAPP_SESSION_PATH));
let socket;
let reinicios=0;
try {
  async function iniciar() {
    socket = await makeSocket(auth.state);
  socket.ev.on('creds.update', () => { void auth.saveCreds(); });
  socket.ev.on('connection.update', update => {
    if (update?.qr) showQR(update.qr);
    if (update?.connection === 'open') console.log('CONECTADO: envía un mensaje sintético al grupo autorizado.');
    if (update?.connection === 'close') {
      const code = update?.lastDisconnect?.error?.output?.statusCode;
      console.log('DESCONECTADO: codigo=' + String(code ?? 'sin_codigo'));
      if (code === 515 && reinicios++ < 3) {
        socket.ev.removeAllListeners('connection.update');
        socket.ev.removeAllListeners('creds.update');
        socket.ev.removeAllListeners('messages.upsert');
        try { socket?.ws?.close?.(); } catch {}
        void Promise.resolve(auth.saveCreds()).then(() => setTimeout(() => {
          void iniciar().catch(() => console.error('ERROR_RECONEXION'));
        }, 600)).catch(() => console.error('ERROR_GUARDADO'));
      }
    }
  });
  const jidPattern = /^\d{5,20}(?:-\d+)?@g\.us$/;
  const printed = new Set();
  socket.ev.on('messages.upsert', event => {
    if (event?.type !== 'notify') return;
    for (const msg of event.messages ?? []) {
      const jid = msg?.key?.remoteJid;
      if (typeof jid === 'string' && jidPattern.test(jid) && !printed.has(jid)) {
        printed.add(jid);
        console.log(`JID_GRUPO: ${jid}  (no compartas este identificador)`);
      }
    }
  });
  }
  void iniciar().catch(() => console.error('ERROR_INICIO'));
  function stop() {
    try { socket?.ws?.close?.(); auth.close(); } catch { process.exitCode = 1; }
  }
  process.once('SIGINT', stop);
  process.once('SIGTERM', stop);
} catch (err) {
  try { socket?.ws?.close?.(); auth.close(); } catch {}
  console.error('No pudo iniciarse el diagnostico de JID; revisa Baileys y la sesion.');
  process.exitCode = 1;
}
