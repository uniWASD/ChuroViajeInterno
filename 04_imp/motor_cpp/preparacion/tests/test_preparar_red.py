from collections import Counter
import gzip
import bz2
import json
from pathlib import Path
import os
import random
import sys
import tempfile
import unittest
from xml.sax.saxutils import escape

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from preparar_red import TIPOS_VIA, Vertice, Via, cargar_poligono, distancia_m, es_vehicular, fecha_iso, generar, preparar_red, sentido_circulacion
from validar_red import validar_red


class PreparacionTest(unittest.TestCase):
    def setUp(self):
        self.temporal = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporal.cleanup)
        self.base = Path(self.temporal.name)
        self.cobertura = self.base / "cobertura.geojson"
        self.osm = self.base / "ciudad.osm"
        self.salida = self.base / "red.json"
        self.crear_cobertura()

    def crear_cobertura(self, anillos=None, multipoligono=False):
        if anillos is None:
            anillos = [[[0, 0], [1, 0], [1, 1], [0, 1], [0, 0]]]
        geometria = {"type": "MultiPolygon", "coordinates": [anillos]} if multipoligono else {"type": "Polygon", "coordinates": anillos}
        self.cobertura.write_text(json.dumps({"type": "FeatureCollection", "features": [{"type": "Feature", "geometry": geometria, "properties": {}}]}), encoding="utf-8")

    def crear_osm(self, nodos, vias, fecha="2026-10-20T12:00:00Z", nombre="ciudad.osm"):
        inicio = f'<osm version="0.6" timestamp="{fecha}">' if fecha else '<osm version="0.6">'
        partes = [inicio]
        for identificador, (lon, lat) in nodos.items():
            partes.append(f'<node id="{identificador}" lon="{lon}" lat="{lat}"/>')
        for identificador, referencias, etiquetas in vias:
            partes.append(f'<way id="{identificador}">')
            for ref in referencias:
                partes.append(f'<nd ref="{ref}"/>')
            for clave, valor in etiquetas.items():
                partes.append(f'<tag k="{escape(clave)}" v="{escape(valor)}"/>')
            partes.append("</way>")
        partes.append("</osm>")
        self.osm = self.base / nombre
        contenido = "\n".join(partes).encode("utf-8")
        if nombre.endswith(".gz"):
            with gzip.open(self.osm, "wb") as archivo:
                archivo.write(contenido)
        elif nombre.endswith(".bz2"):
            with bz2.open(self.osm, "wb") as archivo:
                archivo.write(contenido)
        else:
            self.osm.write_bytes(contenido)

    def ejecutar(self, fecha=None):
        red = generar(self.osm, self.cobertura, self.salida, fecha)
        self.assertEqual(validar_red(self.salida, self.cobertura), (len(red["nodos"]), len(red["tramos"])))
        return red

    def test_cp45_no_existen_tramos_peatonales(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .1), 3: (.1, .2), 4: (.9, .2)}, [
            (101, [1, 2], {"highway": "pedestrian", "name": "Peatonal del centro"}),
            (102, [3, 4], {"highway": "residential", "name": "Calle vehicular"}),
        ])
        red = self.ejecutar()
        self.assertEqual(len(red["tramos"]), 2)
        self.assertNotIn("Peatonal del centro", [tramo["nombre"] for tramo in red["tramos"]])

    def test_cp46_un_solo_sentido_no_tiene_contrario(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .1)}, [(101, [1, 2], {"highway": "residential", "oneway": "yes"})])
        red = self.ejecutar()
        self.assertEqual(len(red["tramos"]), 1)
        self.assertEqual((red["tramos"][0]["desde"], red["tramos"][0]["hasta"]), ("osm:1", "osm:2"))

    def test_sentido_invertido_mueve_geometria(self):
        self.crear_osm({1: (.1, .1), 2: (.5, .3), 3: (.9, .1)}, [(101, [1, 2, 3], {"highway": "residential", "oneway": "-1"})])
        tramos = self.ejecutar()["tramos"]
        self.assertEqual(len(tramos), 1)
        self.assertEqual((tramos[0]["desde"], tramos[0]["hasta"]), ("osm:3", "osm:1"))
        self.assertEqual(tramos[0]["geometria"], [[.9, .1], [.5, .3], [.1, .1]])

    def test_doble_sentido_genera_exactamente_dos_tramos(self):
        self.crear_osm({1: (.1, .1), 2: (.5, .3), 3: (.9, .1)}, [(101, [1, 2, 3], {"highway": "residential"})])
        a, b = self.ejecutar()["tramos"]
        self.assertEqual((a["desde"], a["hasta"]), (b["hasta"], b["desde"]))
        self.assertEqual(a["geometria"], b["geometria"][::-1])
        self.assertEqual(a["longitud_m"], b["longitud_m"])

    def test_longitud_curva_es_mayor_que_linea_recta(self):
        self.crear_osm({1: (.1, .1), 2: (.5, .8), 3: (.9, .1)}, [(101, [1, 2, 3], {"highway": "secondary"})])
        tramo = self.ejecutar()["tramos"][0]
        self.assertGreater(tramo["longitud_m"], distancia_m((.1, .1), (.9, .1)))
        self.assertEqual(tramo["tipo_via"], "avenida_principal")

    def test_cruce_con_nodo_compartido_divide_tramos(self):
        self.crear_osm({1: (.1, .1), 2: (.5, .5), 3: (.9, .9), 4: (.1, .9)}, [
            (101, [1, 2, 3], {"highway": "residential"}),
            (102, [4, 2], {"highway": "residential"}),
        ])
        red = self.ejecutar()
        self.assertEqual(len(red["tramos"]), 6)
        self.assertEqual(sum(t["hasta"] == "osm:2" for t in red["tramos"]), 3)

    def test_cruce_visual_sin_nodo_compartido_no_crea_interseccion(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .9), 3: (.1, .9), 4: (.9, .1)}, [
            (101, [1, 2], {"highway": "residential"}),
            (102, [3, 4], {"highway": "residential"}),
        ])
        self.assertEqual(len(self.ejecutar()["tramos"]), 4)

    def test_recorte_en_limite_termina_en_borde(self):
        self.crear_osm({1: (-.5, .5), 2: (.5, .5), 3: (1.5, .5)}, [(101, [1, 2, 3], {"highway": "primary"})])
        red = self.ejecutar()
        self.assertEqual(len(red["tramos"]), 2)
        self.assertEqual(red["tramos"][0]["geometria"], [[0, .5], [.5, .5], [1, .5]])
        self.assertEqual(red["tramos"][0]["tipo_via"], "avenida_principal")
        self.assertEqual(len(red["nodos"]), 2)

    def test_recorte_poligono_con_hueco_no_cruza_hueco(self):
        anillo = [[0, 0], [1, 0], [1, 1], [0, 1], [0, 0]]
        hueco = [[.4, .4], [.6, .4], [.6, .6], [.4, .6], [.4, .4]]
        self.crear_cobertura([anillo, hueco])
        self.crear_osm({1: (.1, .5), 2: (.9, .5)}, [(101, [1, 2], {"highway": "residential"})])
        red = self.ejecutar()
        self.assertEqual(len(red["tramos"]), 4)
        coordenadas = [tramo["geometria"] for tramo in red["tramos"]]
        self.assertIn([[.1, .5], [.4, .5]], coordenadas)
        self.assertIn([[.6, .5], [.9, .5]], coordenadas)

    def test_recorte_poligono_con_multiples_entradas(self):
        puntos = [[0, 0], [1, 0], [1, .2], [.2, .2], [.2, .8], [1, .8], [1, 1], [0, 1], [0, 0]]
        self.crear_cobertura([puntos])
        self.crear_osm({1: (-.1, .5), 2: (1.1, .5)}, [(101, [1, 2], {"highway": "secondary"})])
        self.assertEqual(len(self.ejecutar()["tramos"]), 2)

    def test_toca_borde_en_un_punto_no_crea_tramo(self):
        self.crear_osm({1: (-.5, .5), 2: (.5, 1.5), 3: (.2, .2), 4: (.8, .2)}, [
            (101, [1, 2], {"highway": "residential"}),
            (102, [3, 4], {"highway": "residential"}),
        ])
        self.assertEqual(len(self.ejecutar()["tramos"]), 2)

    def test_vehicular_excluye_vias_peatonales_y_ciclistas(self):
        excluidas = ("footway", "pedestrian", "path", "cycleway", "steps", "corridor", "track", "construction", "proposed", "busway")
        for tipo in excluidas:
            with self.subTest(tipo=tipo):
                self.assertFalse(es_vehicular({"highway": tipo, "motorcar": "yes"}))

    def test_vehicular_incluye_todas_las_clases_permitidas(self):
        for tipo in TIPOS_VIA:
            with self.subTest(tipo=tipo):
                self.assertTrue(es_vehicular({"highway": tipo}))

    def test_tabla_highway_compatible_con_clases_de_calculo_velocidad(self):
        self.assertEqual(TIPOS_VIA, {
            "motorway": "via_rapida",
            "motorway_link": "via_rapida",
            "trunk": "via_rapida",
            "trunk_link": "via_rapida",
            "primary": "avenida_principal",
            "primary_link": "avenida_principal",
            "secondary": "avenida_principal",
            "secondary_link": "avenida_principal",
            "tertiary": "avenida_principal",
            "tertiary_link": "avenida_principal",
            "unclassified": "zona_residencial",
            "residential": "zona_residencial",
            "living_street": "zona_residencial",
            "service": "zona_residencial",
            "road": "zona_residencial",
        })

    def test_acceso_privado_y_vehicular_prohibido(self):
        for clave in ("access", "vehicle", "motor_vehicle", "motorcar"):
            for valor in ("no", "private", "destination", "delivery"):
                with self.subTest(clave=clave, valor=valor):
                    self.assertFalse(es_vehicular({"highway": "residential", clave: valor}))

    def test_sentidos_ambiguos_no_generan_rutas_inseguras(self):
        for valor in ("alternating", "reversible", "invalid"):
            with self.subTest(valor=valor):
                self.assertFalse(es_vehicular({"highway": "residential", "oneway": valor}))
        self.assertFalse(es_vehicular({"highway": "residential", "oneway:conditional": "yes @ (Mo-Fr 07:00-10:00)"}))
        self.assertFalse(es_vehicular({"highway": "residential", "motorcar:conditional": "no @ (Mo-Fr 07:00-10:00)"}))

    def test_nodo_repetido_en_una_via_es_interseccion(self):
        self.crear_osm({1: (.1, .1), 2: (.3, .3), 3: (.5, .4), 4: (.6, .6)}, [
            (101, [1, 2, 3, 2, 4], {"highway": "residential"}),
        ])
        red = self.ejecutar()
        self.assertEqual(len(red["tramos"]), 6)
        self.assertEqual(sum(t["desde"] == "osm:2" for t in red["tramos"]), 4)

    def test_regla_especifica_motorcar_prevalece_sobre_access(self):
        self.assertTrue(es_vehicular({"highway": "residential", "access": "no", "motorcar": "yes"}))
        self.assertFalse(es_vehicular({"highway": "residential", "access": "yes", "motorcar": "no"}))

    def test_excluye_aparcamientos_y_areas(self):
        self.assertFalse(es_vehicular({"highway": "service", "service": "parking_aisle"}))
        self.assertFalse(es_vehicular({"highway": "residential", "area": "yes"}))

    def test_rotunda_es_un_sentido_por_defecto(self):
        self.assertEqual(sentido_circulacion({"highway": "residential", "junction": "roundabout"}), "ida")
        self.assertEqual(sentido_circulacion({"highway": "residential", "junction": "roundabout", "oneway": "no"}), "ambos")

    def test_autopista_es_un_sentido_por_defecto(self):
        self.assertEqual(sentido_circulacion({"highway": "motorway"}), "ida")
        self.assertEqual(sentido_circulacion({"highway": "motorway", "oneway": "no"}), "ambos")

    def test_sentido_de_motorcar_prevalece(self):
        self.assertEqual(sentido_circulacion({"highway": "residential", "oneway": "yes", "oneway:motorcar": "no"}), "ambos")
        self.assertEqual(sentido_circulacion({"highway": "residential", "oneway": "no", "oneway:motor_vehicle": "-1"}), "vuelta")

    def test_nombre_sin_etiqueta_usa_ref_o_sin_nombre(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .1), 3: (.1, .2), 4: (.9, .2)}, [
            (101, [1, 2], {"highway": "trunk", "ref": "RN-01"}),
            (102, [3, 4], {"highway": "residential"}),
        ])
        red = self.ejecutar()
        self.assertEqual({t["nombre"] for t in red["tramos"]}, {"RN-01", "Sin nombre"})
        self.assertEqual(Counter(t["tipo_via"] for t in red["tramos"]), Counter({"via_rapida": 2, "zona_residencial": 2}))

    def test_nombre_utf8_en_json(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .1)}, [(101, [1, 2], {"highway": "residential", "name": "Avenida España"})])
        self.assertEqual(self.ejecutar()["tramos"][0]["nombre"], "Avenida España")
        self.assertIn("Avenida España", self.salida.read_text(encoding="utf-8"))

    def test_se_reproduce_de_modo_determinista(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .1)}, [(101, [1, 2], {"highway": "residential"})])
        self.ejecutar()
        primero = self.salida.read_bytes()
        self.ejecutar()
        self.assertEqual(self.salida.read_bytes(), primero)

    def test_fecha_del_extracto_y_reprocesamiento(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .1)}, [(101, [1, 2], {"highway": "residential"})])
        self.assertEqual(self.ejecutar()["fecha_extracto"], "2026-10-20")
        self.assertEqual(self.ejecutar("2026-10-21")["fecha_extracto"], "2026-10-21")
        self.assertEqual(json.loads(self.salida.read_text())["fecha_extracto"], "2026-10-21")

    def test_comprimido_gz_es_compatible(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .1)}, [(101, [1, 2], {"highway": "residential"})], nombre="ciudad.osm.gz")
        self.assertEqual(len(self.ejecutar()["tramos"]), 2)

    def test_comprimido_bz2_es_compatible(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .1)}, [(101, [1, 2], {"highway": "residential"})], nombre="ciudad.osm.bz2")
        self.assertEqual(len(self.ejecutar()["tramos"]), 2)

    def test_multipoligono_respeta_islas_separadas(self):
        self.crear_cobertura([[[0, 0], [.3, 0], [.3, .3], [0, .3], [0, 0]]], multipoligono=True)
        self.crear_osm({1: (-.1, .15), 2: (.5, .15)}, [(101, [1, 2], {"highway": "residential"})])
        self.assertEqual(len(self.ejecutar()["tramos"]), 2)

    def test_osm_con_identificadores_duplicados_falla(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .1)}, [
            (101, [1, 2], {"highway": "residential"}),
            (101, [2, 1], {"highway": "residential"}),
        ])
        with self.assertRaisesRegex(ValueError, "duplicados"):
            generar(self.osm, self.cobertura, self.salida)

    def test_fecha_faltante_exige_dato_explicito(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .1)}, [(101, [1, 2], {"highway": "residential"})], fecha=None)
        with self.assertRaisesRegex(ValueError, "fecha"):
            generar(self.osm, self.cobertura, self.salida)
        self.assertEqual(self.ejecutar("2026-10-20")["fecha_extracto"], "2026-10-20")

    def test_fecha_invalida_no_se_acepta(self):
        with self.assertRaises(ValueError):
            fecha_iso("2026-02-31")

    def test_osm_con_nodo_faltante_falla(self):
        self.crear_osm({1: (.1, .1)}, [(101, [1, 2], {"highway": "residential"})])
        with self.assertRaisesRegex(ValueError, "Falta el nodo"):
            generar(self.osm, self.cobertura, self.salida)

    def test_no_sobrescribe_salida_si_no_hay_calles(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .1)}, [(101, [1, 2], {"highway": "footway"})])
        self.salida.write_text("red anterior", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "No se encontraron"):
            generar(self.osm, self.cobertura, self.salida)
        self.assertEqual(self.salida.read_text(), "red anterior")

    def test_validador_detecta_longitud_inferior_a_recta(self):
        self.crear_osm({1: (.1, .1), 2: (.9, .1)}, [(101, [1, 2], {"highway": "residential"})])
        red = self.ejecutar()
        red["tramos"][0]["longitud_m"] = 1
        self.salida.write_text(json.dumps(red))
        with self.assertRaisesRegex(ValueError, "Longitud menor"):
            validar_red(self.salida, self.cobertura)

    def test_validador_detecta_calle_que_cruza_fuera(self):
        self.crear_cobertura([[[0, 0], [1, 0], [1, .2], [.2, .2], [.2, .8], [1, .8], [1, 1], [0, 1], [0, 0]]])
        self.crear_osm({1: (.1, .5), 2: (.1, .6)}, [(101, [1, 2], {"highway": "residential"})])
        red = self.ejecutar()
        tramo = red["tramos"][0]
        tramo["geometria"].insert(1, [.8, .5])
        self.salida.write_text(json.dumps(red))
        with self.assertRaisesRegex(ValueError, "fuera de cobertura"):
            validar_red(self.salida, self.cobertura)

    def test_aleatorio_recortes_longitudes_y_topologia(self):
        self.crear_cobertura([[[0, 0], [1, 0], [1, 1], [0, 1], [0, 0]],
                             [[.4, .4], [.6, .4], [.6, .6], [.4, .6], [.4, .4]]])
        aleatorio = random.Random(117)
        vias = []
        for indice in range(220):
            puntos = [Vertice(f"osm:{indice * 10 + j}", aleatorio.uniform(-.3, 1.3), aleatorio.uniform(-.3, 1.3))
                      for j in range(aleatorio.randint(2, 7))]
            etiquetas = {"highway": aleatorio.choice(("primary", "residential", "trunk")),
                         "oneway": aleatorio.choice(("yes", "no", "-1"))}
            vias.append(Via(indice + 1, etiquetas, tuple(puntos)))
        red = preparar_red(vias, cargar_poligono(self.cobertura), "2026-10-20")
        self.salida.write_text(json.dumps(red), encoding="utf-8")
        self.assertEqual(validar_red(self.salida, self.cobertura), (len(red["nodos"]), len(red["tramos"])))
        self.assertGreater(len(red["tramos"]), 100)

    def test_poligono_real_tarija_compatible(self):
        original = Path(os.environ.get("CHUROVIAJE_COBERTURA", Path(__file__).resolve().parents[3] / "datos" / "mancha_urbana_tarija.geojson"))
        if not original.exists():
            self.skipTest("El polígono original se comprueba en la integración con 04_imp/datos.")
        poligono = cargar_poligono(original)
        self.assertTrue(poligono.is_valid)
        self.assertTrue(poligono.bounds[0] < -64.7 < poligono.bounds[2])


if __name__ == "__main__":
    unittest.main()
