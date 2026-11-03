"""Controles de analítica y modificación limitada de documentación. 100% offline."""
import csv
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / 'scripts'

def import_script(name):
    spec = importlib.util.spec_from_file_location(name,SCRIPTS/(name+'.py'))
    mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod); return mod

analizar = import_script('analizar_medicion')
actualizar = import_script('actualizar_arquitectura')


class AnaliticaTests(unittest.TestCase):
    def test_percentil_medido(self):
        self.assertEqual(analizar.percentile([10,20,30,40,50],95),48)
        self.assertIsNone(analizar.percentile([],95))

    def test_zona_horaria_exige_utc(self):
        self.assertRaises(ValueError,analizar.utc,'2026-11-02T10:00:00')
        self.assertRaises(ValueError,analizar.utc,'2026-11-02T10:00:00-04:00')
        self.assertEqual(analizar.utc('2026-11-02T14:00:00Z').utcoffset(),timedelta())

    def test_simulacion_rechazada_pese_a_csv_correcto(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            start=datetime.now(timezone.utc)-timedelta(days=1)
            hdr=['consulta_id','hora_utc','fin_utc','tipo','producto','intento','estado_http','tiempo_ms',
                 'n_estaciones','retry_after','error_codigo']
            with (root/'medicion-oracle.csv').open('w',newline='') as f:
                w=csv.DictWriter(f,fieldnames=hdr);w.writeheader();
                w.writerow({'consulta_id':'a','hora_utc':start.isoformat(),'fin_utc':(start+timedelta(seconds=1)).isoformat(),
                            'tipo':'catalogo','producto':'0','intento':'1','estado_http':'200','tiempo_ms':'10'})
            hdrs=['consulta_id','hora_utc','id_estacion','producto_id','saldo','updated_at','zona_en_updated_at']
            with (root/'estaciones-oracle.csv').open('w',newline='') as f:
                w=csv.DictWriter(f,fieldnames=hdrs);w.writeheader()
            (root/'estado.properties').write_text(f'origen=oracle\nduracion_horas=24\ninicio_utc={start.isoformat()}\nbase_url=http://127.0.0.1:9999/api/\npruebas=true\n')
            result=analizar.evaluate(root,'oracle',24)
            self.assertFalse(result['etapa_valida'])
            self.assertFalse(result['es_entorno_real'])


class DocumentacionTests(unittest.TestCase):
    @staticmethod
    def fixture():
        return {
            'parametros': '''# Parámetros\n| Código | Qué se define | Dónde se usa | Valor | Estado |\n| --- | --- | --- | --- | --- |\n| P-29 | Cada cuánto el backend consulta a ANH | [RN-09.1](a) | Se fija con la tarea T-117 | Pendiente |\n| P-30 | Vigencia del dato de ANH | [RN-05.5](b) | Hoy valen 6 horas | Pendiente |\n| P-32 | Tiempo de espera y reintentos | [RN-09.4](c) | 12 segundos, 1 reintento | Propuesta |\n| P-45 | Intocable | [RN](d) | Conservar | Propuesta |\n''',
            'modelo': '''# Modelo\n| Catálogo o valor | Contenido | Fuente |\n| --- | --- | --- |\n| Vigencia del estado de un surtidor | 6 horas sin dato nuevo. Para ANH se cuentan desde el backend | CU-05 |\n| Ciclo de consulta a ANH | Lo fija P-29 con T-117 | CU-09 |\n| Espera y reintentos de la consulta a ANH | 12 s, 1 reintento, 1 s | P-32 |\n| Otro valor | Intocable | D-02 |\n''',
            'contratos': '''# Contratos\n## Backend con la API de ANH\nLa base es segura.\n### Rutas que se usan\ntexto no editable\n''',
            'cu09': '''# CU-09\n## Reglas\nRN-09.1 el ciclo se fija con P-29\n## Recorrido por Vista, Controlador y Modelo\ntexto no editable\n'''}

    @staticmethod
    def decision():
        return {'P-29_minutos':20,'P-30_horas':6,'P-32_timeout_segundos':12,
                'P-32_reintentos':1,'P-32_pausa_segundos':1, 'disclaimer_http':200,
                'condiciones_uso':'No se halló cuota publicada en lo revisado; ver evidencia verificable',
                'responsable':'Equipo de ChuroViaje'}

    def test_cambio_exclusivamente_esperado(self):
        inp=self.fixture(); out=actualizar.update(inp,self.decision())
        self.assertEqual(set(inp),set(out))
        self.assertIn('Confirmada',out['parametros'])
        self.assertIn('P-29 |',out['parametros'])
        self.assertIn('20 minutos',out['parametros'])
        self.assertIn('| P-45 | Intocable',out['parametros'])
        self.assertIn('| Otro valor | Intocable',out['modelo'])
        self.assertIn('texto no editable',out['contratos'])
        self.assertIn('texto no editable',out['cu09'])

    def test_zip_viejo_no_debe_modificarse(self):
        old=self.fixture(); old['contratos']=old['contratos'].replace('## Backend con la API de ANH','## Backend con el motor core')
        self.assertRaises(ValueError,actualizar.update,old,self.decision())

    def test_gate_real_de_aplicacion_y_cero_edicion_sin_evidencia(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); repo=root/'repo';repo.mkdir();(repo/'.git').mkdir()
            fixtures=self.fixture()
            for k,rel in actualizar.PATHS.items():
                # write_bytes evita que Windows convierta LF a CRLF en el fixture artificial.
                # No se modifican ni normalizan los documentos reales del repositorio.
                f=repo/rel;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(fixtures[k].encode('utf-8'))
            before={k:(repo/rel).read_bytes() for k,rel in actualizar.PATHS.items()}
            summary=root/'summary.json';summary.write_text(json.dumps({'CP-157_apto':False,'etapas':{}}))
            dec=root/'decision.json';dec.write_text(json.dumps(self.decision()))
            evidence=root/'disclaimer.txt';evidence.write_text('http_status: 200\nrespuesta...')
            cmd=[sys.executable,str(SCRIPTS/'actualizar_arquitectura.py'),'--repo',str(repo),
                 '--resumen',str(summary),'--decision',str(dec),'--disclaimer',str(evidence)]
            attempt=subprocess.run(cmd,capture_output=True,text=True)
            self.assertNotEqual(attempt.returncode,0)
            self.assertTrue(all((repo/rel).read_bytes()==before[k] for k,rel in actualizar.PATHS.items()))
            summary.write_text(json.dumps({'CP-157_apto':True,'etapas':{
                'local':{'es_entorno_real':True},'oracle':{'es_entorno_real':True}}}))
            dry=subprocess.run(cmd,capture_output=True,text=True)
            self.assertEqual(dry.returncode,0,dry.stderr)
            self.assertTrue(all((repo/rel).read_bytes()==before[k] for k,rel in actualizar.PATHS.items()))
            applied=subprocess.run(cmd+['--aplicar','--respaldos',str(root/'backups')],capture_output=True,text=True)
            self.assertEqual(applied.returncode,0,applied.stderr)
            self.assertTrue(all((root/'backups'/rel).read_bytes()==before[k] for k,rel in actualizar.PATHS.items()))
            self.assertTrue(all((repo/rel).read_bytes()!=before[k] for k,rel in actualizar.PATHS.items()))


    def test_guardia_crlf_no_modifica_documentos(self):
        """Un documento real con CRLF debe rechazarse, sin alterar los bytes originales."""
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); repo=root/'repo'; repo.mkdir(); (repo/'.git').mkdir()
            fixtures=self.fixture()
            for k,rel in actualizar.PATHS.items():
                f=repo/rel; f.parent.mkdir(parents=True,exist_ok=True)
                contenido=fixtures[k].replace('\n','\r\n') if k=='contratos' else fixtures[k]
                f.write_bytes(contenido.encode('utf-8'))
            before={k:(repo/rel).read_bytes() for k,rel in actualizar.PATHS.items()}
            summary=root/'summary.json'
            summary.write_text(json.dumps({'CP-157_apto':True,'etapas':{
                'local':{'es_entorno_real':True},'oracle':{'es_entorno_real':True}}}))
            dec=root/'decision.json'; dec.write_text(json.dumps(self.decision()))
            evidence=root/'disclaimer.txt'; evidence.write_bytes(b'http_status: 200\n')
            cmd=[sys.executable,str(SCRIPTS/'actualizar_arquitectura.py'),'--repo',str(repo),
                 '--resumen',str(summary),'--decision',str(dec),'--disclaimer',str(evidence)]
            result=subprocess.run(cmd,capture_output=True,text=True)
            self.assertNotEqual(result.returncode,0)
            self.assertIn('CRLF',result.stderr)
            self.assertTrue(all((repo/rel).read_bytes()==before[k] for k,rel in actualizar.PATHS.items()))


if __name__=='__main__': unittest.main(verbosity=2)
