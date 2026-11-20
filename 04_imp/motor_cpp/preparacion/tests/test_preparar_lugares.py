import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shapely.geometry import box

from preparar_lugares import agrupar_calles, categoria, deduplicar, generar, preparar_lugares

try:
    import osmium  # noqa: F401
    HAY_OSMIUM = True
except ImportError:
    HAY_OSMIUM = False

# Unos 1,1 km por lado alrededor de (0, 0).
ZONA = box(-0.005, -0.005, 0.005, 0.005)


def lugar(clave, nombre, tipo, lat, lon):
    return {"clave": clave, "nombre": nombre, "categoria": tipo, "latitud": lat, "longitud": lon}


class CategoriaTest(unittest.TestCase):
    def test_cp185_lugares_conocidos_y_surtidores_fuera(self):
        """CP-185: cada lugar cae en su categoría; las estaciones de servicio de OSM no entran."""
        casos = [
            ({"amenity": "hospital"}, "SALUD"),
            ({"amenity": "pharmacy"}, "SALUD"),
            ({"amenity": "school"}, "EDUCACION"),
            ({"amenity": "university"}, "EDUCACION"),
            ({"place": "square"}, "PLAZA"),
            ({"leisure": "park", "name": "Plazuela Sucre"}, "PLAZA"),
            ({"leisure": "park", "name": "Parque Bolívar"}, "PARQUE"),
            ({"amenity": "marketplace"}, "MERCADO"),
            ({"amenity": "townhall"}, "GOBIERNO"),
            ({"amenity": "bus_station"}, "TRANSPORTE"),
            ({"aeroway": "aerodrome"}, "TRANSPORTE"),
            ({"tourism": "museum"}, "TURISMO"),
            ({"tourism": "hotel"}, "HOTEL"),
            ({"amenity": "bank"}, "BANCO"),
            ({"amenity": "restaurant"}, "COMIDA"),
            ({"leisure": "stadium"}, "DEPORTE"),
            ({"amenity": "place_of_worship"}, "RELIGION"),
            ({"shop": "bakery"}, "COMERCIO"),
            ({"amenity": "fuel"}, None),
            ({"amenity": "fuel", "shop": "convenience"}, None),
            ({"amenity": "bench"}, None),
            ({"building": "yes"}, None),
        ]
        for etiquetas, esperado in casos:
            with self.subTest(etiquetas=etiquetas):
                self.assertEqual(esperado, categoria(etiquetas))


class CallesTest(unittest.TestCase):
    def test_cp186_una_entrada_por_calle(self):
        """CP-186: los tramos de una calle forman una sola entrada; dos calles homónimas lejanas, dos."""
        tramos = [
            (10, "Calle Sucre", [(-0.004, 0.0), (-0.002, 0.0)]),
            (11, "Calle Sucre", [(-0.002, 0.0), (0.0, 0.0)]),
            (12, "calle sucre", [(0.0, 0.0), (0.001, 0.0)]),
            # Otra "Calle Sucre" a más de 300 m: es otra calle.
            (20, "Calle Sucre", [(0.0, 0.004), (0.002, 0.004)]),
            (30, "Avenida Las Américas", [(0.0, -0.004), (0.0, 0.004)]),
        ]
        calles = agrupar_calles(tramos, ZONA)
        sucre = sorted((c for c in calles if c["nombre"] == "Calle Sucre"), key=lambda c: c["latitud"])
        self.assertEqual(2, len(sucre))
        self.assertEqual("calle:w10", sucre[0]["clave"])
        self.assertAlmostEqual(0.0, sucre[0]["latitud"], places=6)
        self.assertEqual(1, sum(c["nombre"] == "Avenida Las Américas" for c in calles))
        self.assertTrue(all(c["categoria"] == "CALLE" for c in calles))

    def test_cp186_se_recorta_a_la_cobertura(self):
        """CP-186: una calle que sale de la cobertura queda con su punto adentro; una que está afuera, no entra."""
        tramos = [
            (1, "Ruta 1", [(0.0, 0.0), (0.05, 0.0)]),
            (2, "Camino lejano", [(0.1, 0.1), (0.2, 0.1)]),
        ]
        calles = agrupar_calles(tramos, ZONA)
        self.assertEqual(["Ruta 1"], [c["nombre"] for c in calles])
        self.assertLessEqual(calles[0]["longitud"], 0.005)


class DuplicadosTest(unittest.TestCase):
    def test_cp186_mismo_lugar_cercano_una_vez(self):
        """CP-186: el nodo y el edificio del mismo hospital quedan una vez; el mismo nombre lejos se queda."""
        lugares = [
            lugar("n1", "Hospital San Juan de Dios", "SALUD", 0.0, 0.0),
            lugar("w2", "Hospital San Juan de Dios", "SALUD", 0.0005, 0.0),
            lugar("n3", "Farmacia Chávez", "SALUD", 0.0, 0.0),
            lugar("n4", "Farmacia Chavez", "SALUD", 0.004, 0.004),
        ]
        self.assertEqual(["n1", "n3", "n4"], [l["clave"] for l in deduplicar(lugares)])

    def test_preparar_quita_lo_de_afuera_y_ordena(self):
        sueltos = [lugar("n1", "Mercado Campesino", "MERCADO", 0.0, 0.0),
                   lugar("n2", "Mercado lejano", "MERCADO", 1.0, 1.0)]
        resultado = preparar_lugares([], sueltos, ZONA, "2026-11-19")
        self.assertEqual("2026-11-19", resultado["fecha_extracto"])
        self.assertEqual(["Mercado Campesino"], [l["nombre"] for l in resultado["lugares"]])


@unittest.skipUnless(HAY_OSMIUM, "requiere pyosmium (requirements.txt)")
class GenerarTest(unittest.TestCase):
    def test_cp186_de_osm_a_json(self):
        """CP-186: del extracto sale el JSON que carga el backend, sin estaciones de servicio."""
        with tempfile.TemporaryDirectory() as carpeta:
            base = Path(carpeta)
            cobertura = base / "cobertura.geojson"
            cobertura.write_text(json.dumps({"type": "FeatureCollection", "features": [
                {"type": "Feature", "properties": {}, "geometry": ZONA.__geo_interface__}]}), encoding="utf-8")
            osm = base / "ciudad.osm"
            osm.write_text("""<?xml version='1.0' encoding='UTF-8'?>
<osm version="0.6" generator="prueba" timestamp="2026-11-19T21:00:00Z">
  <node id="1" version="1" lat="0.0" lon="-0.002"/>
  <node id="2" version="1" lat="0.0" lon="0.002"/>
  <node id="3" version="1" lat="0.001" lon="0.001"><tag k="amenity" v="hospital"/><tag k="name" v="Hospital Obrero"/></node>
  <node id="4" version="1" lat="0.001" lon="0.002"><tag k="amenity" v="fuel"/><tag k="name" v="Surtidor OSM"/></node>
  <node id="5" version="1" lat="0.002" lon="0.002"><tag k="amenity" v="bench"/><tag k="name" v="Banco de plaza"/></node>
  <way id="100" version="1"><nd ref="1"/><nd ref="2"/><tag k="highway" v="residential"/><tag k="name" v="Calle Colón"/></way>
  <way id="101" version="1"><nd ref="1"/><nd ref="2"/><tag k="highway" v="service"/><tag k="name" v="Entrada"/></way>
</osm>
""", encoding="utf-8")
            salida = base / "lugares.json"
            generar(osm, cobertura, salida, "2026-11-19")
            dato = json.loads(salida.read_text(encoding="utf-8"))
            self.assertEqual("2026-11-19", dato["fecha_extracto"])
            nombres = {l["nombre"]: l["categoria"] for l in dato["lugares"]}
            self.assertEqual({"Calle Colón": "CALLE", "Hospital Obrero": "SALUD"}, nombres)


if __name__ == "__main__":
    unittest.main()
