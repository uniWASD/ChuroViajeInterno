"""Inspección estructural del paquete; NO pretende sustituir PostGIS/Testcontainers."""
import csv
import hashlib
import json
import sys
import unittest
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RAIZ / 'herramientas'))
from validacion_anh_t089 import PROTOTIPO_LEGADO, ADICIONALES_PENDIENTES, dentro_cobertura, validar_cobertura_aprobada


class CalidadDelPaquete(unittest.TestCase):
    def test_client_anh_t115_original_sin_modificaciones(self):
        cliente = RAIZ / 'integraciones' / 'ANH_T115' / 'anh_tarija.py'
        self.assertTrue(cliente.exists())
        self.assertEqual(hashlib.sha256(cliente.read_bytes()).hexdigest(),
                         '7bba307fdb4eafced99ac871f07d1aaa79500b3b46e2f92880315f900557d0a4')
        self.assertTrue((cliente.parent / 'requirements.txt').exists())
        self.assertTrue((cliente.parent / 'ejecutar_windows.bat').exists())

    def test_V1_original_conserva_checksum_y_V1_1_corrige_sin_inventar(self):
        mig = RAIZ / 'backend_java' / 'src/main/resources/db/migration'
        v1 = (mig / 'V1__postgis_y_catalogos.sql').read_bytes()
        self.assertEqual(hashlib.sha256(v1).hexdigest(),
                         '06fd693039a5ca0bbc2c69e2a1d1ba2392398bdbc5f707b8a159363cbc30ce3b')
        v11 = (mig / 'V1_1__colores_pendientes_y_alias_normalizados.sql').read_text()
        self.assertIn('ALTER COLUMN color DROP NOT NULL', v11)
        self.assertIn('SET color = NULL', v11)
        self.assertIn('ck_surtidor_alias_normalizado', v11)
        self.assertIn('ON DELETE CASCADE', v1.decode())
        self.assertIn('GEOGRAPHY(Point, 4326)', v1.decode())
        self.assertIn('USING GIST', v1.decode())
        self.assertEqual(v1.decode().count("('GASOLINA',"), 1)
        self.assertFalse((mig / 'V2__surtidores_revisados.sql').exists(),
                         'Solo existe V2 cuando hay evidencia ANH y doble aprobación documentada')

    def test_diccionario_Surtidores_no_se_toma_por_datos_api(self):
        prueba = json.loads((RAIZ/'datos_pendientes/patrones_prototipo_surtidores_SIN_VALIDAR.json').read_text())
        self.assertEqual(list(prueba['surtidores']), list(PROTOTIPO_LEGADO))
        with (RAIZ/'datos_pendientes/surtidores_revision.csv').open(encoding='utf-8-sig') as archivo:
            filas = list(csv.DictReader(archivo))
        self.assertEqual([f['nombre'] for f in filas], list(PROTOTIPO_LEGADO))
        for fila in filas:
            self.assertEqual(fila['aliases'], '')
            self.assertEqual(fila['longitud'], '')
            self.assertEqual(fila['latitud'], '')
            self.assertEqual(fila['revision_franz'], 'NO')
            self.assertEqual(fila['aprobado_equipo'], 'NO')

    def test_adicionales_quedan_separadas_y_vacias(self):
        with (RAIZ/'datos_pendientes/estaciones_adicionales_propuestas.csv').open(encoding='utf-8-sig') as f:
            filas = list(csv.DictReader(f))
        self.assertEqual([fila['nombre'] for fila in filas], list(ADICIONALES_PENDIENTES))
        self.assertTrue(all(not fila['fuente_ubicacion'] and not fila['latitud'] and not fila['longitud'] for fila in filas))

    def test_geojson_original_permanece_provisional_y_no_pasa_como_aprobado(self):
        mapa = json.loads((RAIZ/'datos/mancha_urbana_tarija.geojson').read_text())
        self.assertEqual(mapa['estado_validacion'], 'PROVISIONAL')
        with self.assertRaises(ValueError):
            validar_cobertura_aprobada(mapa)

    def test_referencias_y_trazabilidad_sin_alterar(self):
        for referencia in ('06_base_de_datos.md', '03_modelo_dominio.md',
                           '08_decisiones_parametros.md', 'CONTRIBUTING.md',
                           'TRAZABILIDAD.md', 'CU-01_ReportarEventoVial.md',
                           'CU-02_VisualizarEventosViales.md',
                           'CU-03_CalcularRutaOptima.md',
                           'CU-06_MonitorearSurtidoresWhatsApp.md'):
            self.assertTrue((RAIZ/'referencias_t089'/referencia).is_file(), referencia)
        self.assertNotIn('T-089', (RAIZ/'referencias_t089/TRAZABILIDAD.md').read_text())

    def test_geometria_vs_referencia_shapely_con_bordes_y_huecos(self):
        try:
            from shapely.geometry import Polygon, Point
        except ImportError:
            self.skipTest('Comparación opcional: pip install shapely')
        exterior = [[-65,-22],[-64,-22],[-64,-21],[-65,-21],[-65,-22]]
        interior = [[-64.8,-21.8],[-64.4,-21.8],[-64.4,-21.4],[-64.8,-21.4],[-64.8,-21.8]]
        pol = Polygon(exterior, [interior])
        geo = {'type': 'FeatureCollection', 'estado_validacion': 'APROBADO',
               'features': [{'type':'Feature','properties':{'revision_equipo':'APROBADO'},
                             'geometry':{'type':'Polygon','coordinates':[exterior,interior]}}]}
        validar_cobertura_aprobada(geo)
        casos = 0
        for i in range(31):
            for j in range(31):
                x = -65.1 + i * .04
                y = -22.1 + j * .04
                self.assertEqual(dentro_cobertura(geo,x,y), pol.covers(Point(x,y)), (x,y))
                casos += 1
        self.assertEqual(casos, 961)


if __name__ == '__main__':
    unittest.main(verbosity=2)
