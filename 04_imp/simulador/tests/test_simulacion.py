import _thread
import io
import json
import os
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest import mock

from simulador.cli import BORRADO_FALLIDO, INTERRUMPIDA, OK, PARAMETROS_INVALIDOS, SIN_SESION, main
from simulador.parametros import validar
from simulador.recorrido import Recorrido, distancia_m
from simulador.simulacion import Reloj, Simulacion
from tests.backend_falso import CREDENCIAL, BackendFalso

PLAZA = (-21.5355, -64.7296)
MERCADO = (-21.5300, -64.7260)


def correr(*argv, reloj=None, credencial=CREDENCIAL):
    salida, errores = io.StringIO(), io.StringIO()
    entorno = {"SIMULACION_CREDENCIAL": credencial} if credencial else {}
    with mock.patch.dict(os.environ, entorno, clear=False):
        if not credencial:
            os.environ.pop("SIMULACION_CREDENCIAL", None)
        codigo = main(list(argv), salida=salida, errores=errores, reloj=reloj)
    return codigo, salida.getvalue(), errores.getvalue()


class ConBackend(unittest.TestCase):
    def setUp(self):
        self.backend = BackendFalso()
        self._dir = tempfile.TemporaryDirectory()
        self.dir = Path(self._dir.name)
        self.rutas = self.dir / "rutas.json"
        self.rutas.write_text(json.dumps({"viajes": [
            {"nombre": "Plaza al mercado", "origen": list(PLAZA), "destino": list(MERCADO)},
            {"nombre": "Mercado a la plaza", "origen": list(MERCADO), "destino": list(PLAZA)},
        ]}), encoding="utf-8")

    def tearDown(self):
        self.backend.cerrar()
        self._dir.cleanup()

    def argumentos(self, *extra):
        return ("--backend", self.backend.direccion, "--rutas", str(self.rutas), "--semilla", "7", *extra)


class UsuariosFantasma(ConBackend):
    def test_cada_fantasma_usa_su_sesion_de_prueba_y_la_misma_api_que_la_app(self):
        """RN-07.3, RN-07.4 y RN-07.11: sesión con el identificador de la prueba; telemetría, eventos y revisión."""
        reporte = self.dir / "reporte.json"
        codigo, salida, errores = correr(*self.argumentos("--usuarios", "4", "--navegando", "2", "--eventos-por-minuto",
                                                          "2", "--duracion", "1", "--prueba", "carga-test",
                                                          "--reporte", str(reporte)), reloj=Reloj(factor=60))
        self.assertEqual(codigo, OK, errores)

        sesiones = self.backend.de("/sesion/prueba")
        self.assertEqual(sorted(p[2]["usuario"] for p in sesiones), [1, 2, 3, 4])
        self.assertTrue(all(p[2]["pruebaId"] == "carga-test" for p in sesiones))
        # Cada petición posterior va con el JWT de su usuario fantasma.
        jwts = {p[3] for p in self.backend.pedidos if p[1] not in ("/sesion/prueba",)}
        self.assertTrue(jwts <= {f"jwt-carga-test-{n}" for n in range(1, 5)})

        telemetria = self.backend.de("/telemetria")
        self.assertTrue(telemetria, "envían telemetría")
        lecturas = telemetria[0][2]["lecturas"]
        self.assertEqual(len(lecturas), 5, "P-13: una lectura cada 3 s, enviadas cada 15 s")
        self.assertEqual(set(lecturas[0]), {"ubicacion", "precisionM", "momento"})
        self.assertGreaterEqual(len(self.backend.de("/eventos")), 1, "reportan eventos")
        revisiones = self.backend.de("/rutas/revision")
        self.assertTrue(revisiones, "los que navegan piden la revisión")
        self.assertTrue({p[3] for p in revisiones} <= {"jwt-carga-test-1", "jwt-carga-test-2"},
                        "solo los que navegan")
        self.assertTrue(any(p[2]["eventosAvisados"] == [9] for p in revisiones),
                        "un evento ya avisado se manda en eventosAvisados")
        rutas_que_no_son_api = {p[1] for p in self.backend.pedidos} - {
            "/sesion/prueba", "/rutas", "/rutas/revision", "/telemetria", "/eventos"}
        self.assertEqual(rutas_que_no_son_api, set(), "nunca nada fuera de la API")

        self.assertIn("Resumen de la prueba carga-test:", salida)
        self.assertIn("peticiones enviadas", salida)
        self.assertIn("p95", salida)
        self.assertIn("python -m simulador --borrar", salida)
        datos = json.loads(reporte.read_text(encoding="utf-8"))
        self.assertEqual(datos["usuarios_con_sesion"], 4)
        self.assertEqual(datos["peticiones"], len(self.backend.pedidos))
        self.assertEqual(datos["errores"], 0)

    def test_la_credencial_nunca_aparece_en_la_salida(self):
        codigo, salida, errores = correr(*self.argumentos("--usuarios", "1", "--navegando", "0",
                                                          "--eventos-por-minuto", "0", "--duracion", "1"),
                                         reloj=Reloj(factor=120))
        self.assertEqual(codigo, OK, errores)
        self.assertNotIn(CREDENCIAL, salida + errores)

    def test_sin_credencial_no_se_ejecuta(self):
        codigo, _, errores = correr(*self.argumentos("--duracion", "1"), credencial="")
        self.assertEqual(codigo, PARAMETROS_INVALIDOS)
        self.assertIn("SIMULACION_CREDENCIAL", errores)
        self.assertEqual(self.backend.pedidos, [])

    def test_identificador_de_prueba_invalido(self):
        codigo, _, errores = correr(*self.argumentos("--prueba", "con espacios"))
        self.assertEqual(codigo, PARAMETROS_INVALIDOS)
        self.assertIn("prueba:", errores)

    def test_sin_sesiones_de_prueba_termina_avisando(self):
        self.backend.sesiones_habilitadas = False
        codigo, _, errores = correr(*self.argumentos("--usuarios", "2", "--navegando", "1", "--eventos-por-minuto", "1", "--duracion", "1"),
                                    reloj=Reloj(factor=120))
        self.assertEqual(codigo, SIN_SESION)
        self.assertIn("Ningún usuario fantasma obtuvo su sesión", errores)
        self.assertEqual({p[1] for p in self.backend.pedidos}, {"/sesion/prueba"})


class Interrupcion(ConBackend):
    def test_ctrl_c_detiene_la_simulacion_limpia_y_deja_el_resumen(self):
        """RN-07.9 y CP-147: al interrumpir, los hilos se detienen y no se envía nada más."""
        par = validar({"usuarios": 3, "navegando": 1, "eventos_por_minuto": 1, "duracion_minutos": 30,
                       "backend": self.backend.direccion, "rutas": str(self.rutas), "semilla": 3})
        simulacion = Simulacion(par, CREDENCIAL, "carga-interrumpida", reloj=Reloj(factor=10), escribir=lambda t: None)
        threading.Timer(1.0, _thread.interrupt_main).start()
        inicio = time.monotonic()
        resultado = simulacion.ejecutar()
        self.assertTrue(resultado.interrumpida)
        self.assertLess(time.monotonic() - inicio, 10, "no espera a que pasen los 30 minutos")
        enviados = len(self.backend.pedidos)
        time.sleep(0.5)
        self.assertEqual(len(self.backend.pedidos), enviados, "después de detenerse no sale nada")
        self.assertFalse([h for h in threading.enumerate() if h.name.startswith("fantasma-")])
        self.assertGreater(resultado.resumen["peticiones"], 0)


class Borrado(ConBackend):
    def test_borrar_usa_el_comando_del_backend(self):
        """RN-07.8: python -m simulador --borrar llama a POST /simulacion/borrar con la credencial."""
        codigo, salida, errores = correr("--borrar", "--backend", self.backend.direccion)
        self.assertEqual(codigo, OK, errores)
        self.assertEqual(len(self.backend.de("/simulacion/borrar")), 1)
        self.assertIn("2 votos, 5 eventos y 3 usuarios fantasma", salida)

    def test_borrar_con_otra_credencial_falla_y_lo_dice(self):
        codigo, _, errores = correr("--borrar", "--backend", self.backend.direccion,
                                    credencial="otra-credencial-que-no-sirve-0123456789")
        self.assertEqual(codigo, BORRADO_FALLIDO)
        self.assertIn("La credencial de prueba no es válida.", errores)

    def test_borrar_contra_otro_servidor_pide_ambiente_remoto(self):
        codigo, _, errores = correr("--borrar", "--backend", "https://churoviaje.orelqu.com")
        self.assertEqual(codigo, PARAMETROS_INVALIDOS)
        self.assertIn("--ambiente-remoto", errores)


class RecorridoTest(unittest.TestCase):
    def test_avanza_sobre_el_trazado_y_calcula_lo_que_falta(self):
        r = Recorrido("x", [PLAZA, (PLAZA[0], MERCADO[1]), MERCADO], ["a", "b", "c", "d"])
        self.assertAlmostEqual(r.largo_m, distancia_m(PLAZA, (PLAZA[0], MERCADO[1])) + distancia_m((PLAZA[0], MERCADO[1]), MERCADO), 3)
        self.assertEqual(r.punto_en(0), PLAZA)
        self.assertEqual(r.punto_en(r.largo_m + 50), MERCADO)
        self.assertEqual(r.tramos_que_faltan(0), ["a", "b", "c", "d"])
        self.assertEqual(r.tramos_que_faltan(r.largo_m * 0.6), ["c", "d"])
        self.assertEqual(r.tramos_que_faltan(r.largo_m), ["d"])


if __name__ == "__main__":
    unittest.main()
