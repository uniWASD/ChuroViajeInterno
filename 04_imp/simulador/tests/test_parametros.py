import io
import json
import tempfile
import unittest
from pathlib import Path

from simulador import limites
from simulador.cli import OK, PARAMETROS_INVALIDOS, main
from simulador.cobertura import Cobertura
from simulador.parametros import (BACKEND_POR_DEFECTO, CARGA_NORMAL, COBERTURA, RUTAS_POR_DEFECTO,
                                  ParametrosInvalidos, leer_archivo, validar)

PLAZA = [-21.5355, -64.7296]
POTOSI = [-19.5836, -65.7531]


def correr(*argv):
    salida, errores = io.StringIO(), io.StringIO()
    codigo = main(list(argv), salida=salida, errores=errores)
    return codigo, salida.getvalue(), errores.getvalue()


class TemporalMixin:
    def setUp(self):
        self._dir = tempfile.TemporaryDirectory()
        self.dir = Path(self._dir.name)

    def tearDown(self):
        self._dir.cleanup()

    def archivo(self, nombre, contenido):
        p = self.dir / nombre
        p.write_text(contenido if isinstance(contenido, str) else json.dumps(contenido), encoding="utf-8")
        return str(p)


class Cp128(unittest.TestCase):
    def test_cp128_usuarios_negativos_no_arranca_y_muestra_los_limites(self):
        """CP-128: con −5 usuarios fantasma, el simulador no arranca y muestra los límites permitidos."""
        codigo, salida, errores = correr("--usuarios", "-5")
        print("\n--- CP-128: python -m simulador --usuarios -5 ---\n" + errores + f"(código de salida {codigo})")
        self.assertEqual(codigo, PARAMETROS_INVALIDOS)
        self.assertEqual(salida, "", "no muestra un plan ni ejecuta nada")
        self.assertIn("La simulación no se ejecuta", errores)
        self.assertIn("usuarios: -5 está fuera del límite permitido (de 1 a 200 usuarios fantasma)", errores)
        self.assertIn("Límites permitidos (propuesta de P-18)", errores)
        for nombre in limites.TODOS:
            self.assertIn(nombre, errores)


class Validacion(TemporalMixin, unittest.TestCase):
    def test_por_defecto_es_la_carga_normal_contra_el_ambiente_de_pruebas(self):
        par = validar({})
        self.assertEqual((par.usuarios, par.navegando, par.eventos_por_minuto, par.duracion_minutos),
                         (50, 10, 6, 15))
        self.assertEqual(CARGA_NORMAL["usuarios"], 50)
        self.assertEqual(par.backend, BACKEND_POR_DEFECTO)
        self.assertFalse(par.ambiente_remoto)
        self.assertEqual(len(par.rutas), 6, "los viajes de 04_imp/datos/rutas_referencia.json")

    def test_junta_todos_los_problemas(self):
        with self.assertRaises(ParametrosInvalidos) as e:
            validar({"usuarios": 500, "navegando": -1, "eventos_por_minuto": "muchos", "duracion_minutos": 0})
        texto = "\n".join(e.exception.errores)
        self.assertEqual(len(e.exception.errores), 4)
        self.assertIn("usuarios: 500", texto)
        self.assertIn("navegando: -1", texto)
        self.assertIn("eventos_por_minuto: debe ser un número entero", texto)
        self.assertIn("duracion_minutos: 0", texto)

    def test_limites_exactos(self):
        validar({"usuarios": 200, "navegando": 100, "eventos_por_minuto": 60, "duracion_minutos": 120})
        validar({"usuarios": 1, "navegando": 0, "eventos_por_minuto": 0, "duracion_minutos": 1})
        for malo in ({"usuarios": 201}, {"usuarios": 0}, {"duracion_minutos": 121}, {"navegando": 101, "usuarios": 200}):
            with self.assertRaises(ParametrosInvalidos):
                validar(malo)

    def test_no_puede_navegar_mas_gente_de_la_que_hay(self):
        with self.assertRaises(ParametrosInvalidos) as e:
            validar({"usuarios": 5, "navegando": 6})
        self.assertIn("es más que los 5 usuarios", e.exception.errores[0])

    def test_rn_01_9_no_mas_eventos_de_los_que_pueden_reportar(self):
        validar({"usuarios": 12, "navegando": 2, "eventos_por_minuto": 6})
        with self.assertRaises(ParametrosInvalidos) as e:
            validar({"usuarios": 12, "navegando": 2, "eventos_por_minuto": 7})
        self.assertIn("RN-01.9", e.exception.errores[0])

    def test_booleanos_y_decimales_no_son_enteros(self):
        for malo in (True, 1.5):
            with self.assertRaises(ParametrosInvalidos):
                validar({"usuarios": malo})

    def test_rn_07_12_otro_servidor_pide_permiso_y_https(self):
        for local in ("http://127.0.0.1:8080", "http://localhost:8080/", "https://localhost"):
            validar({"backend": local})
        with self.assertRaises(ParametrosInvalidos) as e:
            validar({"backend": "https://churoviaje.example.org"})
        self.assertIn("--ambiente-remoto", e.exception.errores[0])
        with self.assertRaises(ParametrosInvalidos) as e:
            validar({"backend": "http://pruebas.example.org", "ambiente_remoto": True})
        self.assertIn("HTTPS", e.exception.errores[0])
        par = validar({"backend": "https://pruebas.example.org", "ambiente_remoto": True})
        self.assertTrue(par.ambiente_remoto)
        for malo in ("127.0.0.1:8080", "http://127.0.0.1:8080/api", "ftp://127.0.0.1", "http://u:c@127.0.0.1"):
            with self.assertRaises(ParametrosInvalidos):
                validar({"backend": malo})

    def test_rutas_fuera_de_tarija_o_mal_formadas(self):
        rutas = self.archivo("rutas.json", {"viajes": [
            {"nombre": "Buena", "origen": PLAZA, "destino": [-21.541756, -64.725011]},
            {"nombre": "A Potosí", "origen": PLAZA, "destino": POTOSI},
            {"nombre": "Rota", "origen": [1], "destino": PLAZA},
            {"nombre": "Con paradas de más", "origen": PLAZA, "destino": PLAZA, "paradas": [PLAZA] * 11}]})
        with self.assertRaises(ParametrosInvalidos) as e:
            validar({"rutas": rutas})
        texto = "\n".join(e.exception.errores)
        self.assertIn("A Potosí, destino", texto)
        self.assertIn("fuera de la zona de cobertura", texto)
        self.assertIn("Rota, origen: debe ser [latitud, longitud]", texto)
        self.assertIn("hasta 10 puntos", texto)

    def test_archivo_de_rutas_ausente_o_invalido(self):
        for contenido, esperado in (("no es json", "no es JSON"), ({"rutas": []}, "«viajes»")):
            with self.assertRaises(ParametrosInvalidos) as e:
                validar({"rutas": self.archivo("r.json", contenido)})
            self.assertIn(esperado, e.exception.errores[0])
        with self.assertRaises(ParametrosInvalidos) as e:
            validar({"rutas": str(self.dir / "no-existe.json")})
        self.assertIn("No existe", e.exception.errores[0])

    def test_sin_rutas_solo_si_nadie_navega(self):
        vacio = self.archivo("v.json", {"viajes": []})
        with self.assertRaises(ParametrosInvalidos):
            validar({"rutas": vacio})
        with self.assertRaises(ParametrosInvalidos):
            validar({"rutas": vacio, "navegando": 0})  # RUTAS va de 1 a 50
        demasiadas = self.archivo("d.json", {"viajes": [{"origen": PLAZA, "destino": PLAZA}] * 51})
        with self.assertRaises(ParametrosInvalidos) as e:
            validar({"rutas": demasiadas})
        self.assertIn("51 está fuera del límite", e.exception.errores[0])

    def test_cobertura_real(self):
        c = Cobertura.desde_archivo(COBERTURA)
        self.assertTrue(c.contiene(*PLAZA))
        self.assertFalse(c.contiene(*POTOSI))
        self.assertTrue(RUTAS_POR_DEFECTO.exists())


class Configuracion(TemporalMixin, unittest.TestCase):
    def test_rn_07_2_archivo_y_linea_de_comandos(self):
        cfg = self.archivo("s.toml", 'usuarios = 20\nnavegando = 4\neventos_por_minuto = 3\nduracion_minutos = 5\n')
        codigo, salida, _ = correr("--config", cfg, "--solo-validar")
        self.assertEqual(codigo, OK)
        self.assertIn("usuarios fantasma   20 (4 navegando)", salida)
        # La línea de comandos manda sobre el archivo.
        codigo, salida, _ = correr("--config", cfg, "--usuarios", "30", "--solo-validar")
        self.assertIn("usuarios fantasma   30 (4 navegando)", salida)

    def test_clave_desconocida_en_el_archivo(self):
        cfg = self.archivo("s.toml", 'usuarioz = 20\n')
        codigo, _, errores = correr("--config", cfg)
        self.assertEqual(codigo, PARAMETROS_INVALIDOS)
        self.assertIn("Parámetro desconocido en el archivo: usuarioz", errores)
        with self.assertRaises(ParametrosInvalidos):
            leer_archivo(self.archivo("x.toml", "usuarios = = 3"))

    def test_el_ejemplo_del_repositorio_es_valido(self):
        raiz = Path(__file__).resolve().parent.parent
        datos = leer_archivo(raiz / "simulador.ejemplo.toml")
        datos["rutas"] = str((raiz / datos["rutas"]).resolve())
        par = validar(datos)
        self.assertEqual((par.usuarios, par.navegando), (50, 10))

    def test_sin_solo_validar_no_envia_nada_todavia(self):
        codigo, salida, _ = correr()
        self.assertEqual(codigo, OK)
        self.assertIn("Simulación válida", salida)
        self.assertIn("no se envió nada al backend", salida)

    def test_ayuda_muestra_los_limites(self):
        with self.assertRaises(SystemExit):
            import contextlib
            ayuda = io.StringIO()
            with contextlib.redirect_stdout(ayuda):
                main(["--help"])
        self.assertIn("Límites permitidos", ayuda.getvalue())


if __name__ == "__main__":
    unittest.main()
