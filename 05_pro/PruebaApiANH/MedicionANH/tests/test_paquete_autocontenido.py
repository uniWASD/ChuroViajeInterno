"""Pruebas de distribución: no requieren Git, datos-local ni acceso a ANH."""
import hashlib
import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def import_script(stem):
    spec = importlib.util.spec_from_file_location(stem,ROOT/'scripts'/f'{stem}.py')
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


sim = import_script('simular_72h')
suite = import_script('ejecutar_suite')


class PaqueteTests(unittest.TestCase):
    def test_01_jar_autocontenido_con_manifiesto(self):
        with zipfile.ZipFile(ROOT/'medicion-anh.jar') as archive:
            self.assertIn('MedicionANH.class',archive.namelist())
            self.assertIn('Main-Class: MedicionANH',archive.read('META-INF/MANIFEST.MF').decode('utf-8'))
        self.assertTrue((ROOT/'java'/'MedicionANH.java').exists())

    def test_02_scripts_doble_clic_en_windows_y_linux(self):
        self.assertTrue((ROOT/'EJECUTAR_PRUEBAS_WINDOWS.cmd').is_file())
        self.assertTrue((ROOT/'EJECUTAR_PRUEBAS_LINUX.sh').is_file())
        command=(ROOT/'EJECUTAR_PRUEBAS_WINDOWS.cmd').read_text(encoding='utf-8')
        self.assertIn('ejecutar_suite.py',command)
        self.assertIn('%~dp0',command)
        self.assertTrue((ROOT/'scripts'/'PRUEBA_CORTA_WINDOWS.ps1').is_file())

    def test_03_entrada_no_exige_csv_local_preexistente(self):
        with tempfile.TemporaryDirectory() as temp:
            t=Path(temp)
            self.assertFalse((t/'datos-local'/'medicion-local.csv').exists())
            # La generación y el análisis virtual no necesitan ningún CSV real.
            sim.generar(t/'virtual')
            r=suite.check_virtual(t/'virtual')
            self.assertEqual(r['local']['ciclos'],288)
            self.assertEqual(r['oracle']['ciclos'],144)

    def test_04_simulacion_con_marcadores_de_no_evidencia(self):
        with tempfile.TemporaryDirectory() as temp:
            t=Path(temp)
            sim.generar(t)
            marker=json.loads((t/'ESTO_ES_SIMULADO.json').read_text(encoding='utf-8'))
            self.assertIn('VIRTUALES',marker['advertencia'])
            self.assertTrue(all(v['sin_uso_de_ANH'] for v in marker['etapas'].values()))
            for origen in ('local','oracle'):
                state=(t/f'SIMULADO-datos-{origen}'/'estado.properties').read_text()
                self.assertIn('pruebas=true',state)
                self.assertIn('http://127.0.0.1:',state)

    def test_05_salida_no_confirma_parametros_ni_altera_md(self):
        contents=(ROOT/'scripts'/'ejecutar_suite.py').read_text(encoding='utf-8')
        self.assertIn("'CP-157_apto':False",contents)
        self.assertIn("'CP-158_apto':False",contents)
        self.assertNotIn('actualizar_arquitectura.py',contents)
        self.assertNotIn('https://abastecimiento.prod.anh.gob.bo/api/',contents)

    def test_06_ssh_no_incluye_ips_ni_llaves_privadas(self):
        scripts='\n'.join(p.read_text(encoding='utf-8-sig') for p in (ROOT/'scripts').glob('*.ps1'))
        self.assertIn('-IPServidor',scripts)
        self.assertNotIn('147.224.',scripts)
        self.assertNotIn('BEGIN OPENSSH PRIVATE KEY',scripts)
        self.assertTrue((ROOT/'scripts'/'oracle_prueba_corta.sh').exists())

    def test_07_lanzadores_windows_resuelven_ruta_absoluta_y_estable(self):
        principal=(ROOT.parents[2]/'EJECUTAR_EN_WINDOWS.cmd').read_text(encoding='utf-8')
        interno=(ROOT/'EJECUTAR_PRUEBAS_WINDOWS.cmd').read_text(encoding='utf-8')
        oracle=(ROOT.parents[2]/'COMPROBAR_ORACLE_WINDOWS.cmd').read_text(encoding='utf-8')
        self.assertIn('set "T117_RAIZ=%~dp0"',principal)
        self.assertIn('call "%T117_LANZADOR%"',principal)
        self.assertIn('set "T117_BASE=%~dp0"',interno)
        self.assertIn('"%T117_SUITE%"',interno)
        self.assertLess(interno.index('set "T117_BASE='),interno.index('pushd'))
        self.assertIn('set "T117_SCRIPT=%T117_RAIZ%',oracle)

    def test_08_subprocesos_python_usan_interprete_activo(self):
        # Ubuntu puede ofrecer /usr/bin/python3 sin comando `python`.
        source=(ROOT/'tests'/'test_analisis_documentacion.py').read_text(encoding='utf-8')
        self.assertGreaterEqual(source.count("cmd=[sys.executable,"),2)
        self.assertNotIn("cmd=['python',",source)

    def test_09_oracle_conserva_jerarquia_y_copia_lanzadores_raiz(self):
        # Evita regresión: Oracle no recibe MedicionANH plano. La prueba 07
        # usa ROOT.parents[2] para localizar los dos lanzadores de la raíz.
        ps=(ROOT/'scripts'/'PRUEBA_CORTA_WINDOWS.ps1').read_text(encoding='utf-8-sig')
        self.assertIn('$remotoMedidor = "$remoto/05_pro/PruebaApiANH/MedicionANH"', ps)
        self.assertIn('$raizPaquete = (Resolve-Path', ps)
        self.assertIn('$lanzadorRaiz = Join-Path', ps)
        self.assertIn('$lanzadorOracle = Join-Path', ps)
        self.assertIn('$lanzadorRaiz $lanzadorOracle "${destino}:~/$remoto/"', ps)
        self.assertIn('"${destino}:~/$remotoMedidor/"', ps)
        self.assertIn('cd ~/$remotoMedidor && bash scripts/oracle_prueba_corta.sh', ps)
        self.assertIn('~/$remotoMedidor/pruebas-funcionales-oracle.txt', ps)
        # El test 07 debe seguir estricto, no ser omitido en Linux.
        self.assertIn("ROOT.parents[2]/'EJECUTAR_EN_WINDOWS.cmd'",
                      (ROOT/'tests'/'test_paquete_autocontenido.py').read_text(encoding='utf-8'))


if __name__ == '__main__': unittest.main()
