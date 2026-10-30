import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {mkdtempSync,rmSync} from 'node:fs';
import {createServer} from 'node:http';
import {once} from 'node:events';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {MessageStore} from '../src/store.js';
import {BackendClient} from '../src/backend-client.js';
import {WhatsappService} from '../src/service.js';
import {createLogger} from '../src/logger.js';
import {loadConfig} from '../src/config.js';

export async function runDemo() {
  const dir=mkdtempSync(join(tmpdir(),'churoviaje-demo-'));
  const path=join(dir,'pendientes.sqlite');
  const token=randomBytes(24).toString('hex');
  let getCount=0, postCount=0, lastBatch=null;
  const stub=createServer(async (req,res)=>{
    const answer=(status,body)=>{
      res.writeHead(status,{'Content-Type':'application/json'});
      res.end(JSON.stringify(body));
    };
    if(req.headers.authorization!==`Bearer ${token}`) return answer(401,{error:'No autorizado'});
    if(req.method==='GET' && req.url==='/monitoreo/surtidores') {
      getCount++;
      return answer(200,{surtidores:[],combustibles:[]});
    }
    if(req.method==='POST' && req.url==='/monitoreo/resultados') {
      postCount++;
      let raw='';
      for await (const chunk of req) raw+=chunk;
      try {lastBatch=JSON.parse(raw);} catch {return answer(400,{error:'JSON inválido'});}
      return answer(200,{aceptados:lastBatch.avisos.length,descartados:[]});
    }
    return answer(404,{error:'No encontrado'});
  });
  let store=null,service=null,reopened=null;
  try {
    stub.listen(0,'127.0.0.1');
    await once(stub,'listening');
    const backendUrl=`http://127.0.0.1:${stub.address().port}`;
    const config=loadConfig({BACKEND_URL:backendUrl,SERVICE_TOKEN:token,
      ALLOWED_GROUP_IDS:'grupo-demo@g.us',SQLITE_PATH:path,LLM_API_KEY:''});
    const logs=[];
    const logger=createLogger(line=>logs.push(line));
    let now=Date.now();
    store=new MessageStore(path,{clock:()=>now});
    service=new WhatsappService(config,{store,clock:()=>now,logger});
    const text='TEXTO_SINTETICO_PRIVADO_NO_LOG';
    const date=new Date(now-60000).toISOString();
    assert.deepEqual(service.ingestText({id:'CP-26',group:'grupo-demo@g.us',date,text}),
      {inserted:true,status:'PENDIENTE'});
    assert.deepEqual(store.getById('CP-26'),{id:'CP-26',grupo:'grupo-demo@g.us',fecha:date,
      texto:text,estado:'PENDIENTE'});
    const columns=store.db.prepare('PRAGMA table_info(mensaje_pendiente)').all().map(row=>row.name);
    assert.deepEqual(columns,['id','grupo','fecha','fecha_ms','texto','estado','finalizado_ms','recibido_ms']);
    assert(!columns.some(name=>/remitente|autor|telefono|numero|sender|nombre/i.test(name)));
    const viejo=new Date(now-29*60000).toISOString();
    assert.deepEqual(service.ingestText({id:'vencido',group:'grupo-demo@g.us',date:viejo,text}),
      {inserted:true,status:'PENDIENTE'});
    service.stop();service=null;
    store.close();store=null;
    now+=2*60000;
    store=new MessageStore(path,{clock:()=>now});
    service=new WhatsappService(config,{store,clock:()=>now,logger});
    const requestsBefore=getCount+postCount;
    service.start();
    assert.equal(requestsBefore,getCount+postCount);
    assert.deepEqual({estado:store.getById('vencido').estado,texto:store.getById('vencido').texto},
      {estado:'DESCARTADO',texto:null});
    assert.equal(store.markProcessed('CP-26'),1);
    assert.equal(store.getById('CP-26').texto,null);
    service.stop();service=null;
    store.close();store=null;
    reopened=new MessageStore(path,{clock:()=>now});
    assert.equal(reopened.getById('CP-26').estado,'PROCESADO');
    assert.equal(reopened.getById('CP-26').texto,null);
    assert.equal(reopened.getById('vencido').texto,null);
    reopened.close();reopened=null;
    const client=new BackendClient(config);
    const catalogo=await client.getSurtidores();
    assert.deepEqual(catalogo,{surtidores:[],combustibles:[]});
    const lote={lote:new Date(now).toISOString(),avisos:[{
      surtidor:'EJEMPLO_FICTICIO',combustible:'GASOLINA',estado:'DISPONIBLE',fecha:date
    }]};
    const posted=await client.postResultados(lote);
    assert.deepEqual(posted,{aceptados:1,descartados:[]});
    assert.deepEqual(lastBatch,lote);
    assert.equal(getCount,1);assert.equal(postCount,1);
    const unauth=await fetch(`${backendUrl}/monitoreo/surtidores`);
    assert.equal(unauth.status,401);
    assert(!logs.join(' ').includes(text));
    assert(!logs.join(' ').includes(token));
    return {
      tipo:'DEMO_HTTP_LOCAL',resultado:'APROBADO',
      cp26:{persistenciaTrasReapertura:true,sinRemitente:true,textoEliminado:true},
      mantenimiento:{pendienteVencidoDescartado:true,textoVencidoBorrado:true},
      http:{getStatus:200,postStatus:200,sinCredencial:401},
      seguridad:{sinDatosPrivadosEnLogs:true},
      alcance:'Doble HTTP sin backend de negocio ni SQLite de backend; sin WhatsApp, LLM ni Python reales'
    };
  } finally {
    if(service) service.stop();
    if(store) store.close();
    if(reopened) reopened.close();
    if(stub.listening) await new Promise((resolve,reject)=>stub.close(error=>error?reject(error):resolve()));
    rmSync(dir,{recursive:true,force:true,maxRetries:5,retryDelay:150});
  }
}

if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href) {
  try {process.stdout.write(`${JSON.stringify(await runDemo(),null,2)}\n`);}
  catch {process.stderr.write('DEMO_FALLIDA: consulte los tests.\n');process.exitCode=1;}
}
