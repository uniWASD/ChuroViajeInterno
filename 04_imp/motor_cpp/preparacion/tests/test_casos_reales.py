import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from xml.sax.saxutils import escape

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from preparar_red import generar
from verificar_casos_reales import verificar_casos


class PruebasRealesTest(unittest.TestCase):
    def setUp(self):
        self.temporal = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporal.cleanup)
        self.directorio = Path(self.temporal.name)
        self.osm = self.directorio / "tarija.osm"
        self.red = self.directorio / "red_vial.json"
        self.cobertura = self.directorio / "cobertura.geojson"
        self.cobertura.write_text(json.dumps({
            "type": "Polygon",
            "coordinates": [[[-64.74, -21.54], [-64.73, -21.54], [-64.73, -21.53],
                             [-64.74, -21.53], [-64.74, -21.54]]]
        }), encoding="utf-8")
        self.nodos = {
            1: (-64.735, -21.534),
            2: (-64.7349, -21.5341),
            3: (-64.7347, -21.5343),
            4: (-64.734, -21.5344),
            5: (-64.734, -21.5349),
            6: (-64.7338, -21.535),
            7: (-64.7337, -21.5352),
            8: (-64.7335, -21.5353),
        }
        self.vias = [
            (100, (1, 2), {"highway": "footway", "name": "Pasaje Baldiviezo"}),
            (200, (3, 4), {"highway": "residential", "name": "Calle General Trigo", "oneway": "yes"}),
            (300, (5, 6), {"highway": "residential", "name": "Calle Otra", "oneway": "no"}),
        ]

    def escribir_osm(self):
        contenido = ['<osm version="0.6" osmosis_replication_timestamp="2026-10-28T00:00:00Z">']
        for identificador, (lon, lat) in sorted(self.nodos.items()):
            contenido.append(f'<node id="{identificador}" lon="{lon}" lat="{lat}"/>')
        for identificador, nodos, etiquetas in self.vias:
            contenido.append(f'<way id="{identificador}">')
            for nodo in nodos:
                contenido.append(f'<nd ref="{nodo}"/>')
            for clave, valor in etiquetas.items():
                contenido.append(f'<tag k="{escape(clave)}" v="{escape(valor)}"/>')
            contenido.append('</way>')
        contenido.append('</osm>')
        self.osm.write_text("\n".join(contenido), encoding="utf-8")

    def preparar(self):
        self.escribir_osm()
        generar(self.osm, self.cobertura, self.red)

    def verificar(self):
        return verificar_casos(self.osm, self.red, self.cobertura)

    def actualizar_red(self, cambio):
        dato = json.loads(self.red.read_text(encoding="utf-8"))
        cambio(dato)
        self.red.write_text(json.dumps(dato), encoding="utf-8")

    def test_cp45_y_cp46_coinciden_con_el_archivo_de_origen(self):
        self.preparar()
        resultado = self.verificar()
        self.assertEqual(resultado["cp45"]["vias_osm"], [100])
        self.assertEqual(resultado["cp45"]["tramos_en_red"], 0)
        self.assertEqual(resultado["cp46"]["vias_osm"], [200])
        self.assertEqual(resultado["cp46"]["tramos_unidireccionales"], 1)

    def test_casos_con_poligono_original_de_tarija(self):
        self.cobertura = Path(__file__).resolve().parents[3] / "datos" / "mancha_urbana_tarija.geojson"
        self.preparar()
        resultado = self.verificar()
        self.assertEqual(resultado["cp45"]["tramos_en_red"], 0)
        self.assertEqual(resultado["cp46"]["tramos_en_sentido_contrario"], 0)

    def test_cp45_detecta_paso_peatonal_incluido(self):
        self.preparar()
        def alterar(dato):
            tramo = dict(dato["tramos"][0])
            tramo["id"] = "osm:100:1:ida"
            tramo["nombre"] = "Pasaje Baldiviezo"
            dato["tramos"].append(tramo)
        self.actualizar_red(alterar)
        with self.assertRaisesRegex(ValueError, "CP-45.*aparece"):
            self.verificar()

    def test_cp45_detecta_via_falsa_clasificada_para_coches(self):
        self.vias[0] = (100, (1, 2), {"highway": "service", "name": "Pasaje Baldiviezo"})
        self.preparar()
        with self.assertRaisesRegex(ValueError, "CP-45.*clasificada"):
            self.verificar()

    def test_cp45_rechaza_evidencia_ausente(self):
        self.vias.pop(0)
        self.preparar()
        with self.assertRaisesRegex(ValueError, "CP-45.*no aparece"):
            self.verificar()

    def test_cp46_detecta_sentido_contrario(self):
        self.preparar()
        def alterar(dato):
            tramo = next(tramo for tramo in dato["tramos"] if tramo["id"].startswith("osm:200:"))
            contrario = dict(tramo)
            contrario["id"] = tramo["id"].replace(":ida", ":vuelta")
            contrario["desde"], contrario["hasta"] = tramo["hasta"], tramo["desde"]
            contrario["geometria"] = tramo["geometria"][::-1]
            dato["tramos"].append(contrario)
        self.actualizar_red(alterar)
        with self.assertRaisesRegex(ValueError, "CP-46.*incorrecto"):
            self.verificar()

    def test_cp46_detecta_ausencia_del_tramo(self):
        self.preparar()
        self.actualizar_red(lambda dato: dato.__setitem__(
            "tramos", [tramo for tramo in dato["tramos"] if not tramo["id"].startswith("osm:200:")]))
        with self.assertRaisesRegex(ValueError, "CP-46.*falta"):
            self.verificar()

    def test_cp46_rechaza_evidencia_ausente(self):
        self.vias = [via for via in self.vias if via[0] != 200]
        self.preparar()
        with self.assertRaisesRegex(ValueError, "CP-46.*no hay"):
            self.verificar()

    def test_cp46_rechaza_tramo_bidireccional(self):
        self.vias[1] = (200, (3, 4), {"highway": "residential", "name": "Calle General Trigo", "oneway": "no"})
        self.preparar()
        with self.assertRaisesRegex(ValueError, "CP-46.*no hay"):
            self.verificar()

    def test_cp46_respeta_direccion_inversa(self):
        self.vias[1] = (200, (3, 4), {"highway": "residential", "name": "Calle General Trigo", "oneway": "-1"})
        self.preparar()
        self.assertEqual(self.verificar()["cp46"]["tramos_unidireccionales"], 1)

    def test_cp46_valida_multiples_fragmentos(self):
        self.vias[1] = (200, (3, 4, 5), {"highway": "residential", "name": "Calle General Trigo", "oneway": "yes"})
        self.vias.append((400, (4, 6), {"highway": "residential", "name": "Calle Adyacente"}))
        self.preparar()
        self.assertEqual(self.verificar()["cp46"]["tramos_unidireccionales"], 2)

    def test_nombres_abreviados_se_identifican(self):
        self.vias[0] = (100, (1, 2), {"highway": "footway", "name": "Pje. Baldiviezo"})
        self.vias[1] = (200, (3, 4), {"highway": "residential", "name": "Calle Gral. Trigo", "oneway": "yes"})
        self.preparar()
        self.assertEqual(self.verificar()["cp46"]["vias_osm"], [200])

    def test_rechaza_fecha_de_extracto_diferente(self):
        self.preparar()
        with patch("verificar_casos_reales.obtener_fecha_pbf", return_value="2026-10-27"):
            with self.assertRaisesRegex(ValueError, "El extracto OSM es"):
                self.verificar()

    def test_el_verificador_no_modifica_el_json(self):
        self.preparar()
        original = self.red.read_bytes()
        self.verificar()
        self.assertEqual(self.red.read_bytes(), original)

    def test_cp46_detecta_prioridad_de_oneway_motorcar(self):
        self.vias[1] = (200, (3, 4), {"highway": "residential", "name": "Calle General Trigo",
                                      "oneway": "yes", "oneway:motorcar": "no"})
        self.preparar()
        with self.assertRaisesRegex(ValueError, "CP-46.*no hay"):
            self.verificar()


if __name__ == "__main__":
    unittest.main()
