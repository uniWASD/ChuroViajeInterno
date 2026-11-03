"""Pruebas funcionales T-117 contra un servidor HTTP exclusivo de loopback.
No realiza conexiones reales a ANH ni consume su cuota.
"""
import csv
import json
import subprocess
import tempfile
import threading
import time
import unittest
from collections import defaultdict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'medicion-anh.jar'


def ok(items, **extra):
    return 200, {"decCodigo": 1, "oResultado": items, **extra}, {}

CATALOGO = ok([{"id": 7, "nombre": "Tarija"}, {"id": 1, "nombre": "La Paz"}])


def station(product, *, ident=101, saldo="14.5", updated="2026-11-02T10:15:00-04:00", nested=False):
    x = {"id": ident, "departamento_id": 7, "nombre": "Surtidor A", "direccion": "PRIVADO NO GUARDAR",
         "fecha_ultima_venta": "PRIVADO NO GUARDAR", "lat": -21.53, "lng": -64.72}
    fuel = {"producto_id": product, "saldo_litros": saldo, "updated_at": updated, "producto_nombre": "Gasolina"}
    if nested:
        x["productos"] = [fuel]
    else:
        x.update(fuel)
    return x


class Server:
    def __init__(self, response_overrides=None):
        self.overrides = response_overrides or {}
        self.counts = defaultdict(int)
        self.calls = []
        host = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                current = time.time()
                parsed = urlsplit(self.path)
                key = (parsed.path, tuple(sorted(parse_qs(parsed.query).items())))
                host.calls.append((self.path, current))
                host.counts[parsed.path] += 1
                product = parse_qs(parsed.query).get('producto', ['0'])[0]
                if parsed.path.endswith('/departamentos/'):
                    response = CATALOGO
                elif parsed.path.endswith('/estaciones/'):
                    response = ok([station(int(product), nested=(product == '1'),
                                           saldo='0' if product == '1' else '14.5',
                                           updated='2026-11-02T10:15:00' if product == '1'
                                           else '2026-11-02T10:15:00-04:00')])
                elif parsed.path.endswith('/Disclaimer'):
                    response = 200, {'terminos': 'prueba de uso'}, {}
                else:
                    response = 404, '', {}
                if parsed.path in host.overrides:
                    item = host.overrides[parsed.path]
                    if callable(item):
                        response = item(host.counts[parsed.path], product)
                    else:
                        response = item
                status, content, headers = response
                body = content if isinstance(content, bytes) else (
                    content.encode() if isinstance(content, str) else json.dumps(content, ensure_ascii=False).encode())
                self.send_response(status)
                self.send_header('Content-Type', headers.get('Content-Type', 'application/json'))
                self.send_header('Content-Length', str(len(body)))
                for k, v in headers.items():
                    if k != 'Content-Type': self.send_header(k, v)
                self.end_headers()
                try: self.wfile.write(body)
                except BrokenPipeError: pass

            def log_message(self, *args): pass

        self.httpd = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()
        self.base = f'http://127.0.0.1:{self.httpd.server_port}/api/'

    def close(self):
        self.httpd.shutdown(); self.httpd.server_close(); self.thread.join()


def rows(file):
    with file.open(newline='', encoding='utf-8') as f:
        return list(csv.DictReader(f))


class Pruebas(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='anh-t117-tests-')
        self.folder = Path(self.tmp.name) / 'datos'
        self.server = Server()

    def tearDown(self):
        self.server.close()
        self.tmp.cleanup()

    def run_jar(self, *custom, base=None, dir=None):
        args = ["java", "-jar", str(JAR), "--pruebas", "--base-url", base or self.server.base,
                "--salida", str(dir or self.folder), "--intervalo-segundos", "1", "--una-vez"]
        args.extend(custom)
        return subprocess.run(args, capture_output=True, text=True, timeout=40)

    def med(self): return rows(self.folder / 'medicion-local.csv')
    def stations(self): return rows(self.folder / 'estaciones-local.csv')

    def test_01_exito_tres_get_y_sin_cuerpos(self):
        p = self.run_jar()
        self.assertEqual(p.returncode, 0, p.stderr)
        m, st = self.med(), self.stations()
        self.assertEqual([x['tipo'] for x in m], ['catalogo', 'estaciones', 'estaciones'])
        self.assertEqual(len(st), 2)
        self.assertEqual([s['saldo'] for s in st], ['14.5', '0'])
        self.assertEqual([s['zona_en_updated_at'] for s in st], ['SI', 'NO'])
        self.assertEqual([s['producto_id'] for s in st], ['0', '1'])
        self.assertTrue(all(m['hora_utc'].endswith('Z') for m in m))
        self.assertTrue(all(m['estado_http'] == '200' for m in m))
        self.assertEqual([x['n_estaciones'] for x in m], ['', '1', '1'])
        for item in self.server.calls:
            self.assertNotIn('/estaciones/101', item[0])
        for first, second in zip(self.server.calls, self.server.calls[1:]):
            self.assertGreaterEqual(second[1] - first[1], 0.98)
        combined = (self.folder / 'medicion-local.csv').read_text() + (self.folder / 'estaciones-local.csv').read_text()
        self.assertNotIn('PRIVADO NO GUARDAR', combined)
        self.assertNotIn('fecha_ultima_venta', combined)
        self.assertNotIn('oResultado', combined)

    def test_02_fallo_un_producto_igual_sigue_el_otro(self):
        self.server.overrides['/api/estaciones/'] = lambda count, prod: (500, 'fallo', {}) if prod == '0' else ok([station(1)])
        self.assertEqual(self.run_jar().returncode, 0)
        self.assertEqual([m['estado_http'] for m in self.med()], ['200', '500', '200'])
        self.assertEqual([s['producto_id'] for s in self.stations()], ['1'])

    def test_03_429_retry_after_largo_detiene_y_persiste(self):
        self.server.overrides['/api/estaciones/'] = lambda count, prod: (429, 'cuota', {'Retry-After': '65'})
        self.assertEqual(self.run_jar().returncode, 0)
        self.assertEqual(len(self.server.calls), 2)
        self.assertEqual([x['estado_http'] for x in self.med()], ['200', '429'])
        properties = (self.folder / 'estado.properties').read_text()
        self.assertIn('bloqueo_hasta_utc=', properties)
        self.assertIn('65', self.med()[-1]['retry_after'])

    def test_04_429_retry_after_corto_reintenta_una_vez(self):
        self.server.overrides['/api/estaciones/'] = lambda count, prod: (429, 'cuota', {'Retry-After': '1'}) if count == 1 else ok([station(int(prod))])
        self.assertEqual(self.run_jar().returncode, 0)
        self.assertEqual([x['estado_http'] for x in self.med()], ['200', '429', '200', '200'])
        self.assertEqual([x['intento'] for x in self.med()], ['1', '1', '2', '1'])

    def test_05_json_duplicado_datos_de_estacion_invalida_se_descartan(self):
        self.server.overrides['/api/estaciones/'] = lambda count, prod: ok([station(0), station(0)]) if prod == '0' else ok([station(1)])
        self.assertEqual(self.run_jar().returncode, 0)
        self.assertEqual(self.med()[1]['error_codigo'], 'estacion_duplicada')
        self.assertEqual(len(self.stations()), 1)
        self.assertEqual(self.stations()[0]['producto_id'], '1')

    def test_06_saldo_invalido_no_se_convierte_a_cero(self):
        self.server.overrides['/api/estaciones/'] = lambda count, prod: ok([station(int(prod), saldo='-23')])
        self.assertEqual(self.run_jar().returncode, 0)
        self.assertTrue(all(x['saldo'] == '' for x in self.stations()))

    def test_07_reanudacion_no_trunca_y_respeta_intervalo(self):
        self.assertEqual(self.run_jar('--intervalo-segundos', '6').returncode, 2)  # repetido: debe rechazar
        # Un solo ciclo de la prueba, con calendario de 600 segundos y datos persistentes.
        args = ["java", "-jar", str(JAR), "--pruebas", "--base-url", self.server.base,
                "--salida", str(self.folder), "--intervalo-segundos", "600", "--una-vez"]
        first = subprocess.run(args, capture_output=True, text=True, timeout=20)
        self.assertEqual(first.returncode, 0, first.stderr)
        self.assertEqual(len(self.med()), 3)
        before = len(self.server.calls)
        second = subprocess.run(args, capture_output=True, text=True, timeout=8)
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(len(self.server.calls), before)
        self.assertEqual(len(self.med()), 3)
        self.assertIn('No se repite ciclo', second.stdout)

    def test_08_disclaimer_una_vez_separado_del_registro(self):
        p1 = self.run_jar('--disclaimer')
        p2 = self.run_jar('--disclaimer')
        self.assertEqual((p1.returncode, p2.returncode), (0, 0))
        self.assertEqual(self.server.counts['/api/Disclaimer'], 1)
        self.assertTrue((self.folder / 'disclaimer-evidencia-NO-COMMIT.txt').exists())
        self.assertFalse((self.folder / 'medicion-local.csv').exists())

    def test_09_restricciones_origen_y_no_cadencia_insegura(self):
        p = subprocess.run(['java', '-jar', str(JAR), '--intervalo-segundos', '30', '--una-vez',
                            '--salida', str(self.folder)], capture_output=True, text=True, timeout=15)
        self.assertNotEqual(p.returncode, 0)
        self.assertFalse((self.folder / 'medicion-local.csv').exists())
        p = self.run_jar(base='http://ejemplo.invalid/api/')
        self.assertNotEqual(p.returncode, 0)

    def test_10_no_redirige_fuera_de_origen(self):
        self.server.overrides['/api/departamentos/'] = (302, '', {'Location': 'https://ejemplo.invalid/otro'})
        self.assertEqual(self.run_jar().returncode, 0)
        self.assertEqual(len(self.server.calls), 2)  # fallback a producto=1 (misma ruta); NO URL externa
        self.assertTrue(all(x['estado_http'] == '302' for x in self.med()))

    def test_11_paginacion_rechazada_y_catalogo_fallback(self):
        self.server.overrides['/api/departamentos/'] = lambda count, prod: ok([], next='otra') if prod == '0' else CATALOGO
        self.assertEqual(self.run_jar().returncode, 0)
        self.assertEqual([m['estado_http'] for m in self.med()], ['200', '200', '200', '200'])
        self.assertEqual(self.med()[0]['error_codigo'], 'paginacion_detectada')

    def test_12_error_200_no_es_cero_estaciones(self):
        self.server.overrides['/api/estaciones/'] = lambda count, prod: (200, {'decCodigo': 2, 'oResultado': []}, {}) if prod == '0' else ok([])
        self.assertEqual(self.run_jar().returncode, 0)
        self.assertEqual([m['error_codigo'] for m in self.med()], ['', 'decCodigo_no_exitoso', ''])
        self.assertEqual(self.med()[-1]['n_estaciones'], '0')

    def test_13_updated_at_ausente_registrado(self):
        self.server.overrides['/api/estaciones/'] = lambda count, prod: ok([station(int(prod), updated=None)])
        self.assertEqual(self.run_jar().returncode, 0)
        self.assertEqual([x['zona_en_updated_at'] for x in self.stations()], ['AUSENTE', 'AUSENTE'])
        self.assertEqual(self.med()[1]['updated_at_ausente'], '1')


if __name__ == '__main__':
    unittest.main(verbosity=2)
