import test from 'node:test';
import assert from 'node:assert/strict';
import {BackendClient,validateBatch} from '../src/backend-client.js';
import {config} from '../test-support/helpers.js';
const batch={lote:'2026-10-15T12:00:00Z',avisos:[{
  surtidor:'Las Vegas',combustible:'Gasolina',estado:'DISPONIBLE',fila:'CORTA',fecha:'2026-10-15T11:59:00Z'
}]};
test('GET llama a la ruta correcta con credencial propia y sin body',async()=>{
  const calls=[];
  const stub=async(url,options)=>{calls.push({url,options});return new Response(JSON.stringify({surtidores:[],combustibles:[]}),{status:200});};
  const client=new BackendClient(config(),{fetchImpl:stub});
  const result=await client.getSurtidores();
  assert.deepEqual(result,{surtidores:[],combustibles:[]});
  assert.equal(calls[0].url,'http://127.0.0.1:18080/monitoreo/surtidores');
  assert.equal(calls[0].options.method,'GET');
  assert.equal(calls[0].options.headers.Authorization,'Bearer TOKEN_DE_PRUEBA_NO_REAL');
  assert.equal(calls[0].options.body,undefined);
  assert.equal(calls[0].options.redirect,'error');
});
test('POST transmite JSON autorizado y sin datos de remitentes',async()=>{
  const requests=[];
  const client=new BackendClient(config(),{fetchImpl:async(url,options)=>{
    requests.push({url,options});return new Response(JSON.stringify({aceptados:1,descartados:[]}));
  }});
  assert.equal((await client.postResultados(batch)).aceptados,1);
  assert.equal(requests[0].url,'http://127.0.0.1:18080/monitoreo/resultados');
  assert.equal(requests[0].options.method,'POST');
  assert.equal(requests[0].options.headers['Content-Type'],'application/json');
  assert.deepEqual(JSON.parse(requests[0].options.body),batch);
  assert(!requests[0].options.body.includes('remitente'));
});
test('cabecera y prefijo configurables para acuerdo futuro con Java',async()=>{
  let saved;
  const client=new BackendClient(config({SERVICE_AUTH_HEADER:'X-Credencial-Servicio',SERVICE_TOKEN_PREFIX:'Clave '}),
    {fetchImpl:async(_url,opts)=>{saved=opts.headers;return new Response('{}');}});
  await client.getSurtidores();
  assert.equal(saved['X-Credencial-Servicio'],'Clave TOKEN_DE_PRUEBA_NO_REAL');
  assert.equal(saved.Authorization,undefined);
});
test('no envía lote con sender/autor/número u otros campos',async()=>{
  let requests=0;
  const client=new BackendClient(config(),{fetchImpl:async()=>{requests++;return new Response('{}');}});
  for (const extra of [{sender:'111'},{autor:'Usuario X'},{remitente:'77777'},{id:'mensaje'}]) {
    assert.throws(()=>client.postResultados({...batch,avisos:[{...batch.avisos[0],...extra}]}),e=>e.code==='RESULTADO_INVALIDO');
  }
  assert.equal(requests,0);
});
test('rechaza fechas inválidas en lote o avisos antes del HTTP',()=>{
  for (const candidate of [{...batch,lote:'2026-02-30T12:00:00Z'},
    {...batch,avisos:[{...batch.avisos[0],fecha:'2026-10-15T12:00:00'}]},
    {...batch,avisos:[{...batch.avisos[0],fecha:'2025-02-29T12:00:00Z'}]}]) {
    assert.throws(()=>validateBatch(candidate),e=>e.code==='RESULTADO_INVALIDO');
  }
});
test('rechaza estado y fila fuera de contrato',()=>{
  for (const extra of [{estado:'QUIZÁS'},{fila:'INFINITA'},{surtidor:''},{combustible:null}]) {
    assert.throws(()=>validateBatch({...batch,avisos:[{...batch.avisos[0],...extra}]}));
  }
});
test('rechaza lote con estructura no prevista',()=>{
  for (const invalid of [null,[],{lote:batch.lote},{...batch,autor:'fulano'},{...batch,avisos:'texto'},
    {...batch,avisos:[null]},{...batch,avisos:[{...batch.avisos[0],otro:'falso'}]}]) {
    assert.throws(()=>validateBatch(invalid));
  }
});
test('reporte HTTP 401 sanitiza información',async()=>{
  const client=new BackendClient(config(),{fetchImpl:async()=>new Response('TOKEN_INTERNO_SECRETO',{status:401})});
  await assert.rejects(()=>client.getSurtidores(),e=>e.code==='BACKEND_HTTP' && e.status===401 && !e.message.includes('TOKEN_INTERNO'));
});
test('problema de conexión o timeout produce error sin URL ni token',async()=>{
  const client=new BackendClient(config(),{fetchImpl:async()=>{throw Error('TOKEN_REAL_EN_EXCEPTION');}});
  await assert.rejects(()=>client.getSurtidores(),e=>e.code==='BACKEND_CONEXION' && !e.message.includes('TOKEN_REAL_EN_EXCEPTION'));
});
test('respuesta no JSON o array se rechaza',async()=>{
  for (const response of [new Response('mal'),new Response('[]'),new Response('null')]) {
    const client=new BackendClient(config(),{fetchImpl:async()=>response});
    await assert.rejects(()=>client.getSurtidores(),e=>e.code==='BACKEND_FORMATO');
  }
});
