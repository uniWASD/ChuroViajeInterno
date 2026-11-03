"""Pruebas offline del diagnóstico rápido y simulación virtual explícita."""
import csv
import importlib.util
import json
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT/'scripts'
sys.path.insert(0,str(SCRIPTS))
import diagnostico_corto as diag
import simular_72h as sim
import analizar_medicion as analitica


class DiagnosticoBreve(unittest.TestCase):
    def test_01_simulacion_72h_se_rechaza_para_cp157(self):
        with tempfile.TemporaryDirectory() as t:
            folder = Path(t)
            sim.generar(folder)
            local = folder/'SIMULADO-datos-local'
            oracle = folder/'SIMULADO-datos-oracle'
            result_l=diag.verificar_mediciones(local,'local')
            result_o=diag.verificar_mediciones(oracle,'oracle')
            self.assertEqual((result_l['ciclos_observados'],result_o['ciclos_observados']),(288,144))
            self.assertTrue(result_l['controles']['pausas_entre_peticiones_de_1s'])
            self.assertTrue(result_l['controles']['ciclos_separados_10min'])
            self.assertFalse(result_l['prueba_corta_completa'])
            self.assertFalse(result_o['CP-157_apto'])
            result, content = diag.construir_informe(result_l,result_o)
            self.assertFalse(result['CP-157_apto'])
            self.assertFalse(result['CP-158_apto'])
            self.assertIn('NO acreditado',content)
            # También el validador original de 72 horas se debe negar a confirmar.
            self.assertFalse(analitica.evaluate(local,'local',48)['etapa_valida'])
            self.assertFalse(analitica.evaluate(oracle,'oracle',24)['etapa_valida'])

    def test_02_referencia_huerfana_impedida(self):
        with tempfile.TemporaryDirectory() as t:
            folder=Path(t)
            sim.generar(folder)
            origin=folder/'SIMULADO-datos-local'
            p=origin/'estaciones-local.csv'
            with p.open('a',encoding='utf-8',newline='') as f:
                csv.writer(f).writerow(['NO_EXISTE','2026-10-01T00:00:00Z','1','0','10','2026-10-01T00:00:00','NO'])
            with self.assertRaisesRegex(ValueError,'desconocida o fallida'):
                diag.verificar_mediciones(origin,'local')

    def test_03_cantidad_incoherente_impedida(self):
        with tempfile.TemporaryDirectory() as t:
            folder=Path(t)
            sim.generar(folder)
            origin=folder/'SIMULADO-datos-oracle'
            p=origin/'estaciones-oracle.csv'
            with p.open(encoding='utf-8', newline='') as f: rows=list(csv.DictReader(f))
            rows.pop()
            with p.open('w',encoding='utf-8',newline='') as f:
                w=csv.DictWriter(f,fieldnames=list(rows[0].keys()));w.writeheader();w.writerows(rows)
            with self.assertRaisesRegex(ValueError,'n_estaciones'):
                diag.verificar_mediciones(origin,'oracle')

    def test_04_registros_reales_breves_no_acreditan_cp157(self):
        with tempfile.TemporaryDirectory() as t:
            folder=Path(t)
            sim.generar(folder)
            origin=folder/'SIMULADO-datos-local'
            state=origin/'estado.properties'
            state.write_text(state.read_text().replace('pruebas=true','pruebas=false').replace('http://127.0.0.1:9999/api/',diag.OFFICIAL))
            actual=diag.verificar_mediciones(origin,'local')
            self.assertEqual(actual['tipo_registro'],'real_declarado')
            self.assertFalse(actual['CP-157_apto'])
            result,_=diag.construir_informe(actual,None)
            self.assertFalse(result['CP-157_apto'])

    def test_06_propiedades_reales_java_escapan_dos_puntos(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/'estado.properties'
            p.write_text(r'base_url=https\://abastecimiento.prod.anh.gob.bo/api/' + '\n'
                         + r'inicio_utc=2026-11-02T22\:51\:12.123Z' + '\n'
                         + 'origen=local\npruebas=false\n', encoding='utf-8')
            # La prueba crea textualmente \:; no usa URL ni datos de una conexión real.
            vals=analitica.read_properties(p)
            self.assertEqual(vals['base_url'],diag.OFFICIAL)
            self.assertEqual(vals['inicio_utc'],'2026-11-02T22:51:12.123Z')
            self.assertIsNotNone(analitica.utc(vals['inicio_utc']))

    def test_05_no_hay_oracle_no_comparacion(self):
        with tempfile.TemporaryDirectory() as t:
            folder=Path(t)
            sim.generar(folder)
            actual=diag.verificar_mediciones(folder/'SIMULADO-datos-local','local')
            result, text=diag.construir_informe(actual,None)
            self.assertIn('Pendiente cargar',text)
            self.assertFalse(result['prueba_funcional_y_conectividad_satisfactoria'])


if __name__=='__main__': unittest.main()
