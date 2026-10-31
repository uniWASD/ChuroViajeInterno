"""Pruebas de interfaz de línea de comandos, contrato documental y regresión."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from validar_red import validar_red

RUTA_SCRIPTS = Path(__file__).resolve().parents[1]
RUTA_EJEMPLO = RUTA_SCRIPTS / "ejemplos" / "red_vial_ejemplo.json"


class PruebaCLI(unittest.TestCase):
    def setUp(self):
        temporal = tempfile.TemporaryDirectory()
        self.addCleanup(temporal.cleanup)
        self.base = Path(temporal.name)
        self.poligono = self.base / "poligono.geojson"
        self.osm = self.base / "ciudad.osm"
        self.red = self.base / "red_vial_tarija.json"
        self.csv = self.base / "sentidos.csv"
        self.poligono.write_text(json.dumps({"type": "Polygon", "coordinates": [[
            [-64.74, -21.54], [-64.73, -21.54], [-64.73, -21.53], [-64.74, -21.53], [-64.74, -21.54]
        ]]}), encoding="utf-8")
        self.osm.write_text('''<osm version="0.6" timestamp="2026-10-28T00:00:00Z">
<node id="1" lon="-64.735" lat="-21.534"/><node id="2" lon="-64.7349" lat="-21.5341"/>
<node id="3" lon="-64.7346" lat="-21.5343"/><node id="4" lon="-64.7343" lat="-21.5344"/>
<way id="10"><nd ref="1"/><nd ref="2"/><tag k="highway" v="footway"/><tag k="name" v="Pasaje Baldiviezo"/></way>
<way id="11"><nd ref="2"/><nd ref="3"/><tag k="highway" v="residential"/><tag k="name" v="Calle General Trigo"/><tag k="oneway" v="yes"/></way>
<way id="12"><nd ref="3"/><nd ref="4"/><tag k="highway" v="residential"/><tag k="name" v="Calle Conectora"/></way>
</osm>''', encoding="utf-8")

    def ejecutar(self, nombre, *args):
        return subprocess.run([sys.executable, str(RUTA_SCRIPTS / nombre), *map(str, args)],
                              capture_output=True, text=True, cwd=self.base, timeout=35)

    def generar(self):
        return self.ejecutar("preparar_red.py", "--osm", self.osm, "--cobertura", self.poligono,
                             "--salida", self.red)

    def test_ciclo_completo_cli_positivo_y_repetido(self):
        primera = self.generar()
        self.assertEqual(primera.returncode, 0, primera.stderr)
        self.assertIn("Tamaño JSON:", primera.stdout)
        self.assertIn("tipo_highway_no_admitido: 1", primera.stdout)
        contenido = self.red.read_bytes()
        resultado = self.ejecutar("validar_red.py", "--red", self.red, "--cobertura", self.poligono)
        self.assertEqual(resultado.returncode, 0, resultado.stderr)
        self.assertIn("Conectividad dirigida", resultado.stdout)
        casos = self.ejecutar("verificar_casos_reales.py", "--osm", self.osm, "--red", self.red,
                              "--cobertura", self.poligono)
        self.assertEqual(casos.returncode, 0, casos.stderr)
        self.assertIn("CP-45 OK", casos.stdout)
        self.assertIn("CP-46 OK", casos.stdout)
        revision = self.ejecutar("listar_sentidos_unicos.py", "--red", self.red,
                                "--cobertura", self.poligono, "--salida-csv", self.csv)
        self.assertEqual(revision.returncode, 0, revision.stderr)
        self.assertIn("Calle General Trigo", self.csv.read_text(encoding="utf-8-sig"))
        self.assertEqual(self.generar().returncode, 0)
        self.assertEqual(self.red.read_bytes(), contenido)

    def test_error_en_cli_no_destruye_red_anterior(self):
        self.assertEqual(self.generar().returncode, 0)
        original = self.red.read_bytes()
        self.osm.write_text('<osm version="0.6"></osm>', encoding="utf-8")
        error = self.generar()
        self.assertNotEqual(error.returncode, 0)
        self.assertIn("Error:", error.stderr)
        self.assertEqual(self.red.read_bytes(), original)

    def test_validador_cli_rechaza_archivo_corrupto(self):
        self.assertEqual(self.generar().returncode, 0)
        dato = json.loads(self.red.read_text(encoding="utf-8"))
        dato["tramos"][0]["opuesto"] = "fantasma"
        self.red.write_text(json.dumps(dato), encoding="utf-8")
        resultado = self.ejecutar("validar_red.py", "--red", self.red, "--cobertura", self.poligono)
        self.assertNotEqual(resultado.returncode, 0)
        self.assertIn("Opuesto inexistente", resultado.stderr)

    def test_carga_pbf_inexistencia_dependencia_informa(self):
        from unittest.mock import patch
        from preparar_red import generar
        pbf = self.base / "ciudad.osm.pbf"
        pbf.write_bytes(b"PBF imposible de procesar sin dependencia")
        with patch.dict(sys.modules, {"osmium": None}):
            with self.assertRaisesRegex(RuntimeError, "requisitos"):
                generar(pbf, self.poligono, self.red)

    def test_ejemplo_json_del_paquete_cumple_contrato(self):
        # Fixture versionado en esta misma carpeta: no depende de archivos
        # externos al alcance de T-111 ni de la rama de documentación.
        ejemplo = json.loads(RUTA_EJEMPLO.read_text(encoding="utf-8"))
        self.red.write_text(json.dumps(ejemplo), encoding="utf-8")
        self.assertEqual(validar_red(self.red, self.poligono), (3, 3))


if __name__ == "__main__":
    unittest.main()
