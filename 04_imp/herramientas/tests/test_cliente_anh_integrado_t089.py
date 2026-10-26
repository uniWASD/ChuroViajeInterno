"""Integración de T-115 (CLIENTE REAL) con T-089 bajo transporte ficticio efímero.

TODAS las estaciones en este archivo son SINTÉTICAS, nunca se guardan en
migraciones ni en la entrega como registros provenientes de ANH.
"""
import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

RAIZ = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RAIZ / 'herramientas'))
sys.path.insert(0, str(RAIZ / 'integraciones' / 'ANH_T115'))
import anh_tarija
from ejecutar_extraccion_anh import evaluar_snapshot, extraer_desde_api, verificar_evidencias_cliente
from preparar_revision_anh import preparar
from validacion_anh_t089 import verificar_snapshot_anh, fuente_ubicacion_registro
from test_validacion_anh_t089 import forma_aprobada


class RespuestasANHSimuladas:
    tls_inseguro = False
    politica_tls = 'certifi_exclusivo'
    almacen_ca = {'origen': 'certifi_exclusivo'}
    peticiones = []

    def __init__(self, *, segundo_sin_coordenadas=False, tls=True, error_diesel=False):
        self.segundo_sin_coordenadas = segundo_sin_coordenadas
        self.tls = tls
        self.error_diesel = error_diesel
        self.consultas = []

    def get(self, ruta, params=None):
        params = params or {}
        self.consultas.append((ruta, dict(params)))
        url = 'https://abastecimiento.prod.anh.gob.bo/api/' + ruta
        if ruta == 'departamentos/':
            cuerpo = {'decCodigo': 1, 'oResultado': [
                {'id': 12345, 'nombre': 'Tarija'}, {'id': 100, 'nombre': 'OTRO DEPARTAMENTO DE PRUEBA'}]}
            url += '?producto=' + str(params['producto'])
        elif ruta == 'estaciones/':
            codigo = params['producto']
            url += '?departamento=12345&producto=' + str(codigo)
            if codigo == 1 and self.error_diesel:
                raise anh_tarija.ErrorANH('Solo prueba: diésel sin respuesta', 'red')
            cuerpo = {'decCodigo': 1, 'oResultado': [{
                'id': 900, 'nombre': 'NOMBRE FICTICIO SOLO PARA TEST',
                'departamento_id': 12345,
                'lat': None if (codigo == 1 and self.segundo_sin_coordenadas) else -21.5,
                'lng': None if (codigo == 1 and self.segundo_sin_coordenadas) else -64.5,
                'producto_id': codigo, 'producto_nombre': 'Gasolina' if codigo == 0 else 'Diésel',
                'direccion': 'SOLO PRUEBA', 'saldo_litros': 0,
            }]}
        else:
            raise AssertionError(f'Ruta ANH no esperada en test: {ruta}')
        return cuerpo, {'url': url, 'fin_utc': '2026-10-24T17:00:00+00:00',
                        'tls_verificado': self.tls, 'http_status': 200}


class ClienteANHIntegradoT089(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.base = Path(temp.name)
        self.snapshot = self.base / 'datos_tarija.json'
        self.geojson = self.base / 'cobertura.geojson'
        self.csv = self.base / 'revision.csv'
        self.audit = self.base / 'auditoria.json'

    def traer_offline(self, **opciones):
        cliente = RespuestasANHSimuladas(**opciones)
        resultado = anh_tarija.obtener_datos_tarija(cliente=cliente, max_detalles=0)
        anh_tarija.exportar(resultado, self.base)
        return resultado, cliente

    def test_cliente_original_t115_consulta_dos_productos_y_exporta_sin_inventar(self):
        data, cliente = self.traer_offline()
        self.assertEqual(data['registros_totales'], 2)
        self.assertEqual(data['estaciones_distintas'], 1)
        self.assertEqual(data['estado'], 'completa')
        self.assertTrue(data['tls_verificado'])
        self.assertEqual(cliente.consultas, [
            ('departamentos/', {'producto': 0}),
            ('estaciones/', {'departamento': 12345, 'producto': 0}),
            ('estaciones/', {'departamento': 12345, 'producto': 1}),
        ])
        self.assertTrue((self.base / 'estaciones_tarija.csv').exists())
        self.assertTrue((self.base / 'estaciones_tarija.geojson').exists())
        doc, estaciones, _, incidencias = verificar_snapshot_anh(self.snapshot)
        self.assertEqual(len(estaciones), 1)
        self.assertEqual(doc['registros_totales'], 2)
        self.assertFalse(incidencias)

    def test_client_t115_mas_preparador_t089_no_pone_aprobaciones(self):
        self.traer_offline()
        self.geojson.write_text(json.dumps(forma_aprobada()))
        res = preparar(self.snapshot, self.geojson, self.csv, self.audit)
        self.assertEqual(res['candidatas_urbanas_sin_aprobar'], 1)
        csv = self.csv.read_text(encoding='utf-8')
        self.assertIn('NOMBRE FICTICIO SOLO PARA TEST', csv)
        self.assertIn('NO,NO,PENDIENTE', csv)
        self.assertNotIn('APROBADO', csv)
        self.assertFalse((self.base / 'V2__surtidores_revisados.sql').exists())

    def test_falla_api_diesel_no_es_extraccion_completa(self):
        data, _ = self.traer_offline(error_diesel=True)
        self.assertEqual(data['estado'], 'parcial')
        with self.assertRaisesRegex(ValueError, 'completa'):
            verificar_snapshot_anh(self.snapshot)

    def test_tls_falso_no_es_inventario_admitido(self):
        data, _ = self.traer_offline(tls=False)
        self.assertFalse(data['tls_verificado'])
        with self.assertRaisesRegex(ValueError, 'verificada'):
            verificar_snapshot_anh(self.snapshot)

    def test_producto_con_coordenadas_nulas_aprovecha_otro_producto_real(self):
        self.traer_offline(segundo_sin_coordenadas=True)
        _, estaciones, _, incidencias = verificar_snapshot_anh(self.snapshot)
        self.assertEqual(estaciones[900]['latitud'], -21.5)
        self.assertEqual(estaciones[900]['longitud'], -64.5)
        self.assertTrue(incidencias)

    def test_si_ambos_productos_difieren_se_excluye_identidad(self):
        data, _ = self.traer_offline()
        data['registros'][1]['longitud'] = -64.4
        self.snapshot.write_text(json.dumps(data), encoding='utf-8')
        _, estaciones, _, incidencias = verificar_snapshot_anh(self.snapshot)
        self.assertNotIn(900, estaciones)
        self.assertTrue(any('contradictorios' in aviso for aviso in incidencias))

    def test_detalle_anh_es_fuente_de_coordenadas_si_las_completo(self):
        data, _ = self.traer_offline()
        detalle = {'url': 'https://abastecimiento.prod.anh.gob.bo/api/estaciones/900',
                   'campos': ['latitud', 'longitud', 'url_mapa'],
                   'observado_en_utc': '2026-10-24T17:00:00+00:00', 'tls_verificado': True}
        for reg in data['registros']:
            reg['fuente_complemento_identidad'] = copy.deepcopy(detalle)
        self.snapshot.write_text(json.dumps(data), encoding='utf-8')
        _, estaciones, _, _ = verificar_snapshot_anh(self.snapshot)
        self.assertEqual(fuente_ubicacion_registro(estaciones[900]), detalle['url'])
        self.geojson.write_text(json.dumps(forma_aprobada()))
        preparar(self.snapshot, self.geojson, self.csv, self.audit)
        self.assertIn(detalle['url'], self.csv.read_text())

    def test_detalle_url_fraudulenta_es_rechazada(self):
        data, _ = self.traer_offline()
        data['registros'][0]['fuente_complemento_identidad'] = {
            'url': 'https://ejemplo.invalid/api/estaciones/900', 'campos': ['latitud', 'longitud'],
            'observado_en_utc': '2026-10-24T17:00:00+00:00', 'tls_verificado': True}
        self.snapshot.write_text(json.dumps(data), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'origen'):
            verificar_snapshot_anh(self.snapshot)

    def test_poligono_provisional_no_emite_candidatas_urbanas(self):
        self.traer_offline()
        forma = forma_aprobada()
        forma['estado_validacion'] = 'PROVISIONAL'
        self.geojson.write_text(json.dumps(forma))
        reporte = evaluar_snapshot(self.snapshot, self.geojson)
        self.assertFalse(reporte['cobertura_urbana_aprobada'])
        self.assertIsNone(reporte['candidatas_urbanas'])
        self.assertFalse(self.csv.exists())
        self.assertFalse((self.base / 'surtidores_anh_revision_PENDIENTE.csv').exists())
        self.assertTrue((self.base / 'evaluacion_T089.json').is_file())

    def test_reevaluacion_desde_provisional_a_aprobada_conserva_auditoria_previa(self):
        self.traer_offline()
        geometria = forma_aprobada()
        geometria['estado_validacion'] = 'PROVISIONAL'
        self.geojson.write_text(json.dumps(geometria))
        primer = evaluar_snapshot(self.snapshot, self.geojson)
        self.assertFalse(primer['cobertura_urbana_aprobada'])
        geometria['estado_validacion'] = 'APROBADO'   # EXCLUSIVAMENTE PRUEBA TEMPORAL
        self.geojson.write_text(json.dumps(geometria))
        segundo = evaluar_snapshot(self.snapshot, self.geojson)
        self.assertTrue(segundo['cobertura_urbana_aprobada'])
        self.assertTrue((self.base / 'evaluacion_T089.json').exists())
        self.assertTrue((self.base / 'evaluacion_T089_002.json').exists())
        self.assertTrue((self.base / 'surtidores_anh_revision_PENDIENTE.csv').exists())

    def test_cobertura_aprobada_emite_solo_revision_no_migracion(self):
        self.traer_offline()
        self.geojson.write_text(json.dumps(forma_aprobada()))
        reporte = evaluar_snapshot(self.snapshot, self.geojson)
        self.assertTrue(reporte['cobertura_urbana_aprobada'])
        self.assertEqual(reporte['candidatas_urbanas'], 1)
        revision = self.base / 'surtidores_anh_revision_PENDIENTE.csv'
        self.assertTrue(revision.is_file())
        self.assertFalse((self.base / 'V2__surtidores_revisados.sql').exists())
        with self.assertRaises(FileExistsError):
            evaluar_snapshot(self.snapshot, self.geojson)

    def test_wrapper_extrae_solo_con_certifi_y_productos_cero_uno(self):
        data, _ = self.traer_offline()
        carpeta_destino = self.base / 'nueva_consulta'
        def simular_ejecucion(comando, **kwargs):
            self.assertIn('--usar-certifi', comando)
            self.assertNotIn('--tls-inseguro-solo-pruebas', comando)
            n = comando.index('--productos')
            self.assertEqual(comando[n+1:n+3], ['0','1'])
            carpeta = carpeta_destino / 'sesion_efimera'
            carpeta.mkdir(parents=True, exist_ok=True)
            (carpeta / 'datos_tarija.json').write_text(json.dumps(data), encoding='utf-8')
            traces_dir = carpeta / 'respuestas'
            traces_dir.mkdir()
            urls = [data['fuente_catalogo']] + [q['fuente_url'] for q in data['consultas_productos']]
            peticiones = []
            for i, url in enumerate(urls):
                filename = f'respuesta_{i+1:03d}_sin_fecha_venta.json'
                if i == 0:
                    cuerpo = {'decCodigo':1,'oResultado':[{'id':12345,'nombre':'Tarija'}]}
                else:
                    code = i - 1
                    cuerpo = {'decCodigo':1,'oResultado':[{
                        'id':900, 'departamento_id':12345,
                        'nombre':'NOMBRE FICTICIO SOLO PARA TEST',
                        'direccion':'SOLO PRUEBA', 'lat':-21.5, 'lng':-64.5,
                        'producto_id':code, 'producto_nombre':'Gasolina' if code == 0 else 'Diésel',
                        'saldo_litros':0,
                    }]}
                (traces_dir / filename).write_text(json.dumps(cuerpo), encoding='utf-8')
                peticiones.append({'url':url,'metodo':'GET','http_status':200,
                                    'json_valido':True,'tls_verificado':True,
                                    'politica_tls':'verificacion_estricta',
                                    'respuesta_filtrada':filename})
            (traces_dir / 'peticiones.json').write_text(json.dumps(peticiones), encoding='utf-8')
            (carpeta / 'ejecucion.json').write_text(json.dumps({
                'estado':'completa', 'exit_code':0,'modo':'consulta',
                'version_cliente':data['version_cliente'],
                'registros':data['registros_totales'],
                'estaciones':data['estaciones_distintas'],
                'peticiones_http_intentadas':len(peticiones),
                'politica_tls':'verificacion_estricta',
            }), encoding='utf-8')
            return type('Proceso', (), {'returncode': 0, 'stderr': ''})()
        with patch('ejecutar_extraccion_anh.subprocess.run', side_effect=simular_ejecucion):
            salida = extraer_desde_api(carpeta_destino)
        self.assertEqual(salida.name, 'datos_tarija.json')
        self.assertTrue(salida.is_file())

    def test_evidencia_sin_peticion_diesel_no_se_acepta_aun_con_json(self):
        data, _ = self.traer_offline()
        (self.base / 'respuestas').mkdir(exist_ok=True)
        (self.base / 'ejecucion.json').write_text(json.dumps({
            'estado':'completa','exit_code':0,'modo':'consulta',
            'version_cliente':data['version_cliente'],
            'registros':data['registros_totales'],
            'estaciones':data['estaciones_distintas'],
            'peticiones_http_intentadas':0,
        }))
        (self.base / 'respuestas/peticiones.json').write_text('[]')
        with self.assertRaisesRegex(ValueError, 'peticiones ANH'):
            verificar_evidencias_cliente(self.snapshot, data)


    def test_wrapper_no_confunde_error_de_red_con_cero_estaciones(self):
        with patch('ejecutar_extraccion_anh.subprocess.run', return_value=type(
                'Proceso', (), {'returncode': 2, 'stderr': 'fallo DNS'})()):
            with self.assertRaisesRegex(RuntimeError, 'NO válida'):
                extraer_desde_api(self.base / 'error')


if __name__ == '__main__':
    unittest.main(verbosity=2)
