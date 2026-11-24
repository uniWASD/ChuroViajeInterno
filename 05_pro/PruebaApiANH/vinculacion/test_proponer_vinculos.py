"""Pruebas de proponer_vinculos.py. Usan el catálogo de V2 y la mancha urbana reales del repositorio.

    python -m unittest discover -s 05_pro/PruebaApiANH/vinculacion
"""

import csv
import datetime as dt
import tempfile
import unittest
from pathlib import Path

import proponer_vinculos as pv

CABECERA = ["estado_consulta", "estacion_id", "nombre_estacion", "latitud", "longitud", "producto_id",
            "cantidad_litros"]


def escribir_csv(carpeta: Path, filas: list[list]) -> Path:
    ruta = carpeta / "estaciones_tarija.csv"
    with ruta.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(CABECERA)
        w.writerows(filas)
    return ruta


class Lectura(unittest.TestCase):

    def test_catalogo_de_v2_con_alias_y_notas(self):
        catalogo = {s.nombre: s for s in pv.leer_catalogo()}
        self.assertEqual(len(catalogo), 14)
        self.assertIn("panamericano", catalogo["Panamericano"].alias)
        self.assertIn("san geronimo", catalogo["Panamericano"].alias)
        self.assertIn("2341", catalogo["Ex Terminal"].nota_v2)
        self.assertIn("2974", catalogo["YPFB Parada Chaco"].nota_v2)
        self.assertEqual(catalogo["Las Vegas"].nota_v2, "")

    def test_todos_los_surtidores_del_catalogo_caen_en_la_mancha(self):
        mancha = pv.leer_mancha()
        for s in pv.leer_catalogo():
            self.assertTrue(pv.en_mancha(s.latitud, s.longitud, mancha), s.nombre)

    def test_yacuiba_queda_fuera_de_la_mancha(self):
        self.assertFalse(pv.en_mancha(-22.0144, -63.6778, pv.leer_mancha()))

    def test_una_estacion_por_id_aunque_venga_una_fila_por_combustible(self):
        with tempfile.TemporaryDirectory() as d:
            ruta = escribir_csv(Path(d), [
                ["completa", "2001", "EL MOLLE S.R.L.", "-21.4530516", "-64.7533344", "0", "26194"],
                ["completa", "2001", "EL MOLLE S.R.L.", "-21.4530516", "-64.7533344", "1", "0"],
                ["completa", "abc", "SIN ID", "-21.5", "-64.7", "0", "1"],
            ])
            estaciones, avisos = pv.leer_estaciones(ruta)
        self.assertEqual([e.anh_id for e in estaciones], [2001])
        self.assertEqual(len(avisos), 1)

    def test_lee_el_csv_que_excel_guarda_con_punto_y_coma_en_cp1252(self):
        with tempfile.TemporaryDirectory() as d:
            ruta = Path(d) / "revisado.csv"
            ruta.write_bytes("anh_id;surtidor;revisado\n2341;Ex Terminal;sí\n".encode("cp1252"))
            filas = pv._abrir_csv(ruta)
        self.assertEqual(filas, [{"anh_id": "2341", "surtidor": "Ex Terminal", "revisado": "sí"}])


class Propuesta(unittest.TestCase):

    def setUp(self):
        self.catalogo = pv.leer_catalogo()
        self.mancha = pv.leer_mancha()

    def proponer(self, *estaciones):
        return pv.proponer(list(estaciones), self.catalogo, self.mancha)

    def test_coincide_el_nombre_y_esta_cerca(self):
        pares, fuera = self.proponer(pv.Estacion(2001, "EL MOLLE S.R.L.", -21.4530516, -64.7533344))
        self.assertEqual(fuera, [])
        self.assertEqual(pares[0].surtidor.nombre, "El Molle")
        self.assertEqual(pares[0].propuesta, "vincular")
        self.assertLess(pares[0].distancia, 1)

    def test_coincide_por_alias_aunque_el_punto_de_anh_este_lejos(self):
        # V2: el punto de ANH de Panamericano (id 2388) está a 651 m del predio.
        pares, _ = self.proponer(pv.Estacion(2388, "SAN GERONIMO", -21.5441213, -64.711626))
        self.assertEqual(pares[0].surtidor.nombre, "Panamericano")
        self.assertEqual(pares[0].propuesta, "vincular")
        self.assertGreater(pares[0].distancia, 600)

    def test_cerca_sin_el_mismo_nombre_se_revisa(self):
        pares, _ = self.proponer(pv.Estacion(3001, "ESTACION NUEVA", -21.52350, -64.72480))
        self.assertEqual(pares[0].surtidor.nombre, "Las Vegas")
        self.assertEqual(pares[0].propuesta, "revisar")

    def test_sin_surtidor_cerca_es_candidata(self):
        pares, _ = self.proponer(pv.Estacion(3002, "OTRA", -21.4950, -64.7300))
        self.assertIsNone(pares[0].surtidor)
        self.assertEqual(pares[0].propuesta, "candidata")

    def test_fuera_de_la_mancha_no_se_propone(self):
        pares, fuera = self.proponer(pv.Estacion(2979, "SAN MARTIN", -22.0144, -63.6778))
        self.assertEqual(pares, [])
        self.assertEqual([e.anh_id for e in fuera], [2979])

    def test_un_surtidor_para_dos_estaciones_se_revisa(self):
        pares, _ = self.proponer(pv.Estacion(2001, "EL MOLLE", -21.4530516, -64.7533344),
                                 pv.Estacion(2002, "EL MOLLE II", -21.4531, -64.7534))
        self.assertEqual({p.propuesta for p in pares}, {"revisar"})

    def test_sin_ubicacion_se_revisa(self):
        pares, _ = self.proponer(pv.Estacion(2003, "EL MOLLE", None, None))
        self.assertEqual(pares[0].propuesta, "revisar")

    def test_escribe_los_tres_archivos_para_revisar(self):
        pares, fuera = self.proponer(pv.Estacion(2001, "EL MOLLE", -21.4530516, -64.7533344),
                                     pv.Estacion(2979, "SAN MARTIN", -22.0144, -63.6778))
        with tempfile.TemporaryDirectory() as d:
            pv.escribir_propuesta(pares, fuera, self.catalogo, Path(d))
            revision = pv._abrir_csv(Path(d) / "pares_propuestos.csv")
            sin_estacion = pv._abrir_csv(Path(d) / "surtidores_sin_estacion.csv")
        self.assertEqual(revision[0]["surtidor"], "El Molle")
        self.assertEqual(revision[0]["revisado"], "")
        self.assertEqual(len(sin_estacion), 13)


class Migracion(unittest.TestCase):

    def setUp(self):
        self.catalogo = pv.leer_catalogo()

    def test_solo_entran_los_pares_con_si(self):
        filas = [
            {"anh_id": "2341", "surtidor": "Ex Terminal", "distancia_m": "40", "revisado": "Sí"},
            {"anh_id": "2388", "surtidor": "Panamericano", "distancia_m": "651", "revisado": "si"},
            {"anh_id": "2001", "surtidor": "El Molle", "distancia_m": "0", "revisado": ""},
            {"anh_id": "2002", "surtidor": "Agrupa", "distancia_m": "10", "revisado": "no"},
        ]
        confirmados = pv.pares_confirmados(filas, self.catalogo)
        self.assertEqual([(c.anh_id, c.surtidor) for c in confirmados], [(2341, "Ex Terminal"), (2388, "Panamericano")])
        self.assertEqual(pv.p31_sugerida(confirmados), 700)

    def test_una_excepcion_no_cuenta_para_p31(self):
        filas = [
            {"anh_id": "2341", "surtidor": "Ex Terminal", "distancia_m": "28", "revisado": "si"},
            {"anh_id": "2627", "surtidor": "Moto Mendez", "distancia_m": "221", "revisado": "si"},
            {"anh_id": "2388", "surtidor": "Panamericano", "distancia_m": "651", "revisado": "si",
             "comentario": "Excepción a P-31: ANH tiene mal el punto"},
        ]
        confirmados = pv.pares_confirmados(filas, self.catalogo)
        self.assertEqual(pv.p31_sugerida(confirmados), 250)
        self.assertEqual([c.surtidor for c in confirmados if c.excepcion], ["Panamericano"])

    def test_un_id_o_un_surtidor_repetido_no_genera_nada(self):
        with self.assertRaises(pv.ErrorRevision) as e:
            pv.pares_confirmados([
                {"anh_id": "2341", "surtidor": "Ex Terminal", "revisado": "si"},
                {"anh_id": "2341", "surtidor": "Sointa", "revisado": "si"},
                {"anh_id": "2400", "surtidor": "Sointa", "revisado": "si"},
            ], self.catalogo)
        self.assertIn("2341", str(e.exception))
        self.assertIn("Sointa", str(e.exception))

    def test_un_surtidor_que_no_esta_en_el_catalogo_no_genera_nada(self):
        with self.assertRaises(pv.ErrorRevision):
            pv.pares_confirmados([{"anh_id": "2341", "surtidor": "Inventado", "revisado": "si"}], self.catalogo)

    def test_la_migracion_actualiza_y_comprueba_cuantos_quedaron(self):
        sql = pv.sql_migracion([pv.Confirmado(2341, "Ex Terminal", 40.0),
                                pv.Confirmado(2388, "Panamericano", 651.0, "Excepción a P-31")], "Franz",
                               dt.date(2026, 11, 24))
        self.assertIn("UPDATE surtidor SET anh_id = 2341 WHERE nombre = 'Ex Terminal'; -- a 40 m", sql)
        self.assertIn("-- a 651 m; Excepción a P-31", sql)
        self.assertIn("revisado a mano por Franz el 2026-11-24", sql)
        self.assertIn("<> 2 THEN", sql)

    def test_la_consola_no_sobrescribe_una_migracion(self):
        with tempfile.TemporaryDirectory() as d:
            revisados = Path(d) / "revisados.csv"
            revisados.write_text("anh_id;surtidor;distancia_m;revisado\n2341;Ex Terminal;40;si\n", encoding="utf-8")
            self.assertEqual(pv.main(["migracion", str(revisados), "--revisado-por", "Franz", "--version", "13",
                                      "--salida", d]), 0)
            self.assertTrue((Path(d) / "V13__vinculos_anh.sql").exists())
            self.assertEqual(pv.main(["migracion", str(revisados), "--revisado-por", "Franz", "--version", "13",
                                      "--salida", d]), 2)


if __name__ == "__main__":
    unittest.main()
