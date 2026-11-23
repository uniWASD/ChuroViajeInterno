"""Pruebas del verificador de cierre sin conexiones a ANH ni a BD."""
import csv
import tempfile
import unittest
from pathlib import Path

from auditar_vinculos import PARES_ESPERADOS
from proponer_vinculos import leer_catalogo, ErrorRevision, P31_MAXIMA_M
from verificar_cierre import validar_inventario_completo, estado_parametros, evaluar, DECISIONES, MIGRACIONES, leer_log_maven, maven_acredita_v14


class VerificarCierre(unittest.TestCase):
    def generar(self, carpeta: Path, mod_id=None):
        catalogo = {s.nombre: s for s in leer_catalogo()}
        campos = ["anh_id", "nombre_anh", "latitud_anh", "longitud_anh", "surtidor", "distancia_m",
                  "revisado", "comentario"]
        pares = carpeta / "pares.csv"
        fuente = carpeta / "estaciones_tarija.csv"
        with pares.open('w', newline='', encoding='utf-8') as p, fuente.open('w', newline='', encoding='utf-8') as a:
            w = csv.DictWriter(p, campos, delimiter=';')
            w.writeheader()
            x = csv.writer(a)
            x.writerow(['estado_consulta', 'estacion_id', 'nombre_estacion', 'latitud', 'longitud',
                        'producto_id', 'cantidad_litros'])
            for id, (nombre, *_ ) in PARES_ESPERADOS.items():
                s = catalogo[nombre]
                w.writerow({'anh_id': str(mod_id) if mod_id and id == 2339 else str(id),
                            'nombre_anh': f'ESTACION {id}', 'latitud_anh': str(s.latitud),
                            'longitud_anh': str(s.longitud), 'surtidor': nombre, 'distancia_m': '0.0',
                            'revisado': '', 'comentario': ''})
                x.writerow(['completa', str(id), f'ESTACION {id}', str(s.latitud), str(s.longitud), '0', '10'])
        return pares, fuente

    def test_parametros_aceptados_por_solicitante_no_son_firma_del_equipo(self):
        parametros = estado_parametros(DECISIONES)
        for clave in ('P-29', 'P-30', 'P-31', 'P-32'):
            self.assertTrue(parametros[clave].startswith('Aceptado por solicitante'), clave)
        with tempfile.TemporaryDirectory() as d:
            pares, fuente = self.generar(Path(d))
            info = evaluar(pares, fuente)
        self.assertTrue(info['controles']['parametros_aceptados_por_solicitante'])
        self.assertTrue(info['controles']['parametros_aceptados_por_solicitante'])

    def test_umbral_p31_consistente_con_documentacion(self):
        documento = DECISIONES.read_text(encoding='utf-8')
        self.assertEqual(P31_MAXIMA_M, 50.0)
        linea = next(l for l in documento.splitlines() if l.startswith('| P-31 |'))
        self.assertIn('50 metros', linea)

    def test_sin_aprobaciones_el_cierre_permanece_pendiente(self):
        with tempfile.TemporaryDirectory() as d:
            pares, fuente = self.generar(Path(d))
            info = evaluar(pares, fuente)
        self.assertEqual(info['total_filas'], 14)
        self.assertEqual(info['aprobadas_id'], [])
        self.assertEqual(len(info['sin_decision_id']), 14)
        self.assertFalse(info['cierre_documental_listo'])

    def test_matriz_delegada_v14_aprueba_los_14_pares_y_deja_e2e_pendiente_hasta_log(self):
        raiz = Path(__file__).resolve().parents[3]
        evidencia = raiz / '05_pro/PruebaApiANH/vinculacion/evidencias_t118'
        pares = evidencia / 'pares_para_revision_final_delegada.csv'
        fuente = evidencia / 'estaciones_tarija_fuente_anh_snapshot_14.csv'
        info = evaluar(pares, fuente)
        self.assertEqual(len(info['aprobadas_id']), 14)
        self.assertEqual(info['sin_decision_id'], [])
        self.assertTrue(info['controles']['migracion_v14_presente'])
        self.assertTrue(info['controles']['prueba_e2e_get_surtidores_implementada'])
        self.assertFalse(info['controles']['prueba_e2e_get_surtidores_ejecutada'])
        self.assertFalse(info['cierre_documental_listo'])

        with tempfile.TemporaryDirectory() as d:
            log = Path(d) / 'maven-e2e.log'
            suite = Path(d) / 'maven-suite.log'
            log.write_text(self.log_maven(2, 0), encoding='utf-16')
            suite.write_text(self.log_maven(307, 5), encoding='utf-8')
            info_con_log = evaluar(pares, fuente, evidencia_maven=log, evidencia_suite=suite)
        self.assertTrue(info_con_log['controles']['prueba_e2e_get_surtidores_ejecutada'])
        self.assertTrue(info_con_log['controles']['suite_java_completa_ejecutada'])
        self.assertTrue(info_con_log['cierre_documental_listo'])

    @staticmethod
    def log_maven(total, omitidas, omitida_e2e=0):
        return (
            '[INFO] Successfully applied 14 migrations to schema "public", now at version v14 (execution time 00:00.300s)\n'
            '[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1 s -- in com.churoviaje.backend.persistencia.MigracionesPostgisTest\n'
            f'[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: {omitida_e2e}, Time elapsed: 1 s -- in com.churoviaje.backend.anh.ConsultaAnhVinculosRealesGetIntegracionTest\n'
            '[INFO] Results:\n[INFO]\n'
            f'[INFO] Tests run: {total}, Failures: 0, Errors: 0, Skipped: {omitidas}\n'
            '[INFO] BUILD SUCCESS\n'
        )

    def test_lee_logs_powershell_utf16_y_utf8(self):
        with tempfile.TemporaryDirectory() as d:
            ruta = Path(d) / 'log.txt'
            for encoding in ('utf-16', 'utf-16-le', 'utf-8-sig', 'utf-8'):
                ruta.write_text(self.log_maven(2, 0), encoding=encoding)
                self.assertTrue(maven_acredita_v14(leer_log_maven(ruta)), encoding)

    def test_no_acepta_e2e_omitida_o_solo_mencionada(self):
        self.assertFalse(maven_acredita_v14(self.log_maven(2, 1, omitida_e2e=1)))
        falso = self.log_maven(2, 0).replace('Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1 s -- in com.churoviaje.backend.anh.', 'Running com.churoviaje.backend.anh.')
        self.assertFalse(maven_acredita_v14(falso))

    def test_no_acepta_suite_parcial_o_version_anterior(self):
        self.assertFalse(maven_acredita_v14(self.log_maven(2, 0), suite=True))
        self.assertFalse(maven_acredita_v14(self.log_maven(307, 5).replace('version v14', 'version v13'), suite=True))
        self.assertFalse(maven_acredita_v14(self.log_maven(307, 5) + '[INFO] BUILD FAILURE\n', suite=True))

    def test_id_alterado_rechazado_antes_de_escribir_migracion(self):
        with tempfile.TemporaryDirectory() as d:
            pares, fuente = self.generar(Path(d), mod_id=9999)
            with pares.open(encoding='utf-8') as archivo:
                with self.assertRaises(ErrorRevision):
                    validar_inventario_completo(list(csv.DictReader(archivo, delimiter=';')), fuente)


if __name__ == '__main__':
    unittest.main()
