"""Pruebas aisladas con fixtures SINTÉTICOS: jamás son estaciones ANH reales.

Estos datos solo existen durante cada prueba y no se distribuyen como V2.
"""
import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validacion_anh_t089 import (clave, dentro_cobertura, validar_cobertura_aprobada,
                                 verificar_snapshot_anh)
from preparar_revision_anh import preparar
from generar_migracion_surtidores import generar
from validacion_anh_t089 import PROTOTIPO_LEGADO


def forma_aprobada():
    return {
        'type': 'FeatureCollection', 'estado_validacion': 'APROBADO',
        'features': [{'type': 'Feature', 'properties': {'revision_equipo': 'APROBADO'},
                      'geometry': {'type': 'Polygon', 'coordinates': [
                          [[-65., -22.], [-64., -22.], [-64., -21.], [-65., -21.], [-65., -22.]]
                      ]}}],
    }


def fila(sid, prod, nombre, lon, lat, dep=12345, tls=True):
    return {
        'estacion_id': sid, 'producto_id': prod, 'nombre_estacion': nombre,
        'longitud': lon, 'latitud': lat, 'departamento': 'Tarija', 'departamento_id': dep,
        'alcance': 'departamento_de_tarija_no_solo_ciudad',
        'tls_verificado': tls, 'uso_datos': 'fuente_reportada',
        'verificacion_departamento': 'campo_y_filtro',
        'fuente_url': f'https://abastecimiento.prod.anh.gob.bo/api/estaciones/?departamento={dep}&producto={prod}',
    }


def ejemplo_sintetico():
    reg = [fila(99, p, 'PRUEBA NO REAL', -64.5, -21.5) for p in (0, 1)]
    return {
        'estado': 'completa', 'tls_verificado': True, 'uso_datos': 'fuente_reportada',
        'departamento': 'Tarija', 'departamento_id': 12345,
        'alcance': 'departamento_de_tarija_no_solo_ciudad',
        'observado_en_utc': '2026-10-24T12:00:00+00:00',
        'consultas_productos': [
            {'producto_id': p, 'estado': 'correcta', 'tls_verificado': True,
             'registros': 1, 'fuente_url': reg[p]['fuente_url']} for p in (0, 1)
        ],
        'estaciones_distintas': 1, 'registros_totales': 2, 'registros': reg,
    }


class FlujoSeguridadANH(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.snapshot = self.base / 'datos_tarija.json'
        self.geojson = self.base / 'mancha.geojson'
        self.csv = self.base / 'revision.csv'
        self.auditoria = self.base / 'auditoria.json'
        self.sql = self.base / 'V2__surtidores_revisados.sql'
        self.acta = self.base / 'acta_21.csv'
        self.datos = ejemplo_sintetico()
        self.forma = forma_aprobada()
        self.guardar()
        self.acta_sintetica()

    def acta_sintetica(self):
        # Solo para pruebas: simula explícitamente 21 decisiones humanas.
        cols = ['nombre', 'revision_franz', 'aprobado_equipo', 'decision', 'motivo_exclusion']
        with self.acta.open('w', encoding='utf-8', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=cols)
            writer.writeheader()
            for nombre in PROTOTIPO_LEGADO:
                incluir = nombre == 'El Molle'
                writer.writerow(dict(nombre=nombre, revision_franz='SI', aprobado_equipo='SI',
                                     decision='INCLUIR' if incluir else 'EXCLUIR',
                                     motivo_exclusion='' if incluir else 'Prueba de descarte simulado'))

    def generar(self):
        return generar(self.csv, self.geojson, self.snapshot, self.acta, self.sql)

    def guardar(self):
        self.snapshot.write_text(json.dumps(self.datos, ensure_ascii=False), encoding='utf-8')
        self.geojson.write_text(json.dumps(self.forma), encoding='utf-8')

    def preparar(self):
        self.guardar()
        return preparar(self.snapshot, self.geojson, self.csv, self.auditoria)

    def aprobar(self):
        self.preparar()
        with self.csv.open(encoding='utf-8', newline='') as f:
            dr = csv.DictReader(f)
            cols = dr.fieldnames
            filas = list(dr)
        for registro in filas:
            registro['nombre_prototipo'] = 'El Molle'  # ASOCIACIÓN MANUAL SINTÉTICA SOLO EN TEST
            registro['revision_franz'] = 'SI'
            registro['aprobado_equipo'] = 'SI'
            registro['decision'] = 'INCLUIR'
        with self.csv.open('w', encoding='utf-8', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=cols)
            writer.writeheader()
            writer.writerows(filas)
        return filas, cols

    def test_api_validada_estructuralmente(self):
        _, estaciones, digest, incidencias = verificar_snapshot_anh(self.snapshot)
        self.assertEqual(set(estaciones), {99})
        self.assertEqual(len(digest), 64)
        self.assertFalse(incidencias)

    def test_sin_json_no_se_inventa(self):
        self.snapshot.unlink()
        with self.assertRaises(FileNotFoundError):
            preparar(self.snapshot, self.geojson, self.csv, self.auditoria)
        self.assertFalse(self.csv.exists())

    def test_revision_no_aprueba_sola(self):
        informe = self.preparar()
        self.assertEqual(informe['candidatas_urbanas_sin_aprobar'], 1)
        with self.csv.open() as archivo:
            fila_rev = list(csv.DictReader(archivo))[0]
        self.assertEqual(fila_rev['nombre'], 'PRUEBA NO REAL')
        self.assertEqual(fila_rev['aliases'], '')
        self.assertEqual(fila_rev['nombre_prototipo'], '')
        self.assertEqual(fila_rev['revision_franz'], 'NO')
        self.assertEqual(fila_rev['aprobado_equipo'], 'NO')
        self.assertEqual(fila_rev['decision'], 'PENDIENTE')
        self.assertFalse(self.sql.exists())

    def test_rechaza_poligono_provisional(self):
        self.forma['estado_validacion'] = 'PROVISIONAL'
        with self.assertRaisesRegex(ValueError, 'aprobado'):
            self.preparar()
        self.assertFalse(self.csv.exists())

    def test_rechaza_feature_pendiente_con_raiz_aprobada(self):
        self.forma['features'][0]['properties']['revision_equipo'] = 'PENDIENTE'
        with self.assertRaisesRegex(ValueError, 'aprobación'):
            self.preparar()

    def test_rechaza_cobertura_sin_features(self):
        self.forma['features'] = []
        with self.assertRaises(ValueError):
            self.preparar()

    def test_rechaza_anillo_autointersectado(self):
        self.forma['features'][0]['geometry']['coordinates'] = [
            [[-65,-22],[-64,-21],[-65,-21],[-64,-22],[-65,-22]]]
        with self.assertRaises(ValueError):
            self.preparar()

    def test_invalida_json_parcial(self):
        self.datos['estado'] = 'parcial'
        with self.assertRaisesRegex(ValueError, 'completa'):
            self.preparar()

    def test_invalida_tls(self):
        self.datos['tls_verificado'] = False
        with self.assertRaises(ValueError):
            self.preparar()

    def test_invalida_ubicacion_fuera_departamento(self):
        self.datos['registros'][1]['departamento_id'] = 99999
        with self.assertRaisesRegex(ValueError, 'departamento'):
            self.preparar()

    def test_rechaza_url_ajena(self):
        self.datos['registros'][0]['fuente_url'] = 'https://estaciones-de-ejemplo.invalid/api/estaciones/'
        with self.assertRaisesRegex(ValueError, 'origen'):
            self.preparar()

    def test_rechaza_consulta_duplicada(self):
        self.datos['consultas_productos'][1]['producto_id'] = 0
        with self.assertRaises(ValueError):
            self.preparar()

    def test_rechaza_registro_duplicado(self):
        self.datos['registros'][1]['producto_id'] = 0
        self.datos['registros'][1]['fuente_url'] = self.datos['registros'][0]['fuente_url']
        with self.assertRaisesRegex(ValueError, 'duplicado'):
            self.preparar()

    def test_coordenada_ausente_no_pasa_como_cero(self):
        for r in self.datos['registros']:
            r['latitud'] = None
        res = self.preparar()
        self.assertEqual(res['candidatas_urbanas_sin_aprobar'], 0)
        self.assertEqual(res['ids_sin_coordenadas'], [99])

    def test_fuera_de_zona_urbana_no_pasa(self):
        for r in self.datos['registros']:
            r['latitud'] = -20.0
        res = self.preparar()
        self.assertEqual(res['candidatas_urbanas_sin_aprobar'], 0)
        self.assertEqual(res['ids_fuera_zona_urbana'], [99])

    def test_dos_productos_coordenadas_inconsistentes(self):
        self.datos['registros'][1]['latitud'] = -21.6
        res = self.preparar()
        self.assertEqual(res['candidatas_urbanas_sin_aprobar'], 0)
        self.assertEqual(res['estaciones_con_identidad_consistente'], 0)

    def test_falta_aprobacion_no_genera_sql(self):
        self.preparar()
        with self.assertRaisesRegex(ValueError, 'pendiente'):
            self.generar()
        self.assertFalse(self.sql.exists())

    def test_con_aprobaciones_si_genera_sql_solo_con_fuente(self):
        self.aprobar()
        estaciones, aliases = self.generar()
        self.assertEqual((estaciones, aliases), (1, 1))
        contenido = self.sql.read_text()
        self.assertIn("VALUES ('PRUEBA NO REAL', ST_SetSRID(ST_MakePoint(-64.5, -21.5)", contenido)
        self.assertIn("'prueba no real'", contenido)
        self.assertNotIn('APODO INVENTADO', contenido)

    def test_sql_destino_intacto_si_falla_validacion(self):
        self.aprobar()
        self.sql.write_text('SENTINELA NO SOBRESCRIBIR', encoding='utf-8')
        self.datos['tls_verificado'] = False
        self.guardar()
        with self.assertRaises(ValueError):
            self.generar()
        self.assertEqual(self.sql.read_text(), 'SENTINELA NO SOBRESCRIBIR')

    def test_rechaza_coordenada_modificada_a_mano(self):
        filas, cols = self.aprobar()
        filas[0]['latitud'] = '-21.6'
        with self.csv.open('w') as f:
            w = csv.DictWriter(f, fieldnames=cols); w.writeheader(); w.writerows(filas)
        with self.assertRaisesRegex(ValueError, 'coordenadas'):
            self.generar()

    def test_rechaza_renombrar_estacion(self):
        filas, cols = self.aprobar()
        filas[0]['nombre'] = 'NOMBRE NO EXISTENTE EN API'
        with self.csv.open('w') as f:
            w = csv.DictWriter(f, fieldnames=cols); w.writeheader(); w.writerows(filas)
        with self.assertRaisesRegex(ValueError, 'nombre/fuente'):
            self.generar()

    def test_rechaza_aliases_manual_sin_api(self):
        filas, cols = self.aprobar()
        filas[0]['aliases'] = 'apodo no acreditado'
        with self.csv.open('w') as f:
            w = csv.DictWriter(f, fieldnames=cols); w.writeheader(); w.writerows(filas)
        with self.assertRaisesRegex(ValueError, 'alias'):
            self.generar()

    def test_rechaza_json_diferente_en_revision(self):
        self.aprobar()
        self.datos['observado_en_utc'] = '2026-10-24T20:00:00+00:00'
        self.guardar()
        with self.assertRaisesRegex(ValueError, 'Snapshot'):
            self.generar()

    def test_exclusion_tiene_que_tener_motivo(self):
        filas, cols = self.aprobar()
        filas[0]['decision'] = 'EXCLUIR'
        with self.csv.open('w') as f:
            w = csv.DictWriter(f, fieldnames=cols); w.writeheader(); w.writerows(filas)
        with self.assertRaisesRegex(ValueError, 'exclusión sin motivo'):
            self.generar()

    def test_excluir_todas_rechaza_v2_vacia(self):
        filas, cols = self.aprobar()
        filas[0]['decision'] = 'EXCLUIR'
        filas[0]['motivo_exclusion'] = 'Verificación humana de prueba'
        with self.csv.open('w') as f:
            w = csv.DictWriter(f, fieldnames=cols); w.writeheader(); w.writerows(filas)
        # El acta del prototipo también debe reflejar la exclusión.
        with self.acta.open(newline='', encoding='utf-8') as f:
            reader = csv.DictReader(f); acta_cols = reader.fieldnames; acta = list(reader)
        for fila_acta in acta:
            if fila_acta['nombre'] == 'El Molle':
                fila_acta['decision'] = 'EXCLUIR'
                fila_acta['motivo_exclusion'] = 'Prueba de exclusión sin dato verificable'
        with self.acta.open('w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=acta_cols)
            writer.writeheader(); writer.writerows(acta)
        with self.assertRaisesRegex(ValueError, 'Sin surtidores'):
            self.generar()

    def test_poligono_hueco(self):
        doc = forma_aprobada()
        doc['features'][0]['geometry']['coordinates'].append([
            [-64.8,-21.8],[-64.4,-21.8],[-64.4,-21.4],[-64.8,-21.4],[-64.8,-21.8]])
        validar_cobertura_aprobada(doc)
        self.assertFalse(dentro_cobertura(doc, -64.5, -21.5))
        self.assertTrue(dentro_cobertura(doc, -64.8, -21.5))

    def test_poligono_multipolygon(self):
        doc = forma_aprobada()
        doc['features'][0]['geometry'] = {'type':'MultiPolygon','coordinates':[
            doc['features'][0]['geometry']['coordinates'],
            [[[-63,-22],[-62,-22],[-62,-21],[-63,-21],[-63,-22]]]]}
        validar_cobertura_aprobada(doc)
        self.assertTrue(dentro_cobertura(doc,-62.5,-21.5))
        self.assertTrue(dentro_cobertura(doc,-64.5,-21.5))

    def test_conciliacion_prototipo_obligatoria_antes_de_generar_sql(self):
        filas, cols = self.aprobar()
        filas[0]['nombre_prototipo'] = ''
        with self.csv.open('w') as f:
            w = csv.DictWriter(f, fieldnames=cols); w.writeheader(); w.writerows(filas)
        with self.assertRaisesRegex(ValueError, 'conciliación'):
            self.generar()

    def test_estacion_adicional_no_autorizada_es_excluida(self):
        for r in self.datos['registros']:
            r['nombre_estacion'] = 'Copacabana'
        res = self.preparar()
        self.assertEqual(res['ids_adicionales_fuera_de_alcance_t089'], [99])
        self.assertEqual(res['candidatas_urbanas_sin_aprobar'], 0)

    def test_rechaza_acta_del_prototipo_con_pendientes(self):
        self.aprobar()
        self.acta.write_text('nombre,revision_franz,aprobado_equipo,decision,motivo_exclusion\n', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, '21 filas'):
            self.generar()

    def test_impide_sobrescribir_v2_ya_existente(self):
        self.aprobar()
        self.sql.write_text('V2 REGISTRADA EN FLYWAY', encoding='utf-8')
        with self.assertRaises(FileExistsError):
            self.generar()
        self.assertEqual(self.sql.read_text(), 'V2 REGISTRADA EN FLYWAY')

    def test_normalizar_con_diacriticos(self):
        self.assertEqual(clave('   MÓTO    MÉNDEZ  '), 'moto mendez')
        with self.assertRaises(ValueError):
            clave('---')


if __name__ == '__main__':
    unittest.main(verbosity=2)
