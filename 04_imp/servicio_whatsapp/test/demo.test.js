import test from 'node:test';
import assert from 'node:assert/strict';
import {runDemo} from '../demo/run-demo.js';

test('E2E: CP-26, caducidad, GET y POST con doble HTTP efimero',async()=>{
  const result=await runDemo();
  assert.equal(result.resultado,'APROBADO');
  assert.equal(result.cp26.persistenciaTrasReapertura,true);
  assert.equal(result.cp26.sinRemitente,true);
  assert.equal(result.cp26.textoEliminado,true);
  assert.equal(result.mantenimiento.pendienteVencidoDescartado,true);
  assert.equal(result.http.getStatus,200);
  assert.equal(result.http.postStatus,200);
  assert.equal(result.http.sinCredencial,401);
  assert.equal(result.seguridad.sinDatosPrivadosEnLogs,true);
});
