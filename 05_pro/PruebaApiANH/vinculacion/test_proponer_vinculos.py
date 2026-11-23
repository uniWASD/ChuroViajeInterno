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

    def test_rechaza_id_cero_y_deja_coordenadas_invalidas_para_revision(self):
        with tempfile.TemporaryDirectory() as d:
            ruta = escribir_csv(Path(d), [
                ["completa", "0", "ID CERO", "-21.5", "-64.7", "0", "1"],
                ["completa", "2002", "COORDENADA INVALIDA", "300", "-64.7", "0", "1"],
                ["completa", "2003", "COORDENADA INCOMPLETA", "-21.5", "", "0", "1"],
            ])
            estaciones, avisos = pv.leer_estaciones(ruta)
        self.assertEqual([e.anh_id for e in estaciones], [2002, 2003])
        self.assertTrue(all(e.latitud is None and e.longitud is None for e in estaciones))
        self.assertEqual(len(avisos), 3)

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
        self.assertEqual(pares[0].propuesta, "revisar")
        self.assertIn("excede P-31", pares[0].motivo)
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
        self.assertAlmostEqual(float(revision[0]["distancia_m"]), pares[0].distancia, places=5)
        self.assertEqual(revision[0]["revisado"], "")
        self.assertEqual(len(sin_estacion), 13)


class Migracion(unittest.TestCase):

    def setUp(self):
        self.catalogo = pv.leer_catalogo()
        self.c = {s.nombre: s for s in self.catalogo}

    def fila(self, anh_id="2341", surtidor="Ex Terminal", revisado="si", comentario="Comprobado con plano y registro ANH"):
        s = self.c.get(surtidor)
        lat = s.latitud if s else -21.5420
        lon = s.longitud if s else -64.7240
        return {"anh_id": anh_id, "nombre_anh": "ESTACION DOCUMENTADA", "surtidor": surtidor,
                "latitud_anh": str(lat), "longitud_anh": str(lon), "distancia_m": "0.000000",
                "revisado": revisado, "comentario": comentario}

    def test_solo_entran_los_pares_con_si(self):
        filas = [self.fila(), self.fila("2388", "Panamericano", "Sí"),
                 self.fila("2001", "El Molle", ""), self.fila("2002", "Agrupa", "no")]
        confirmados = pv.pares_confirmados(filas, self.catalogo)
        self.assertEqual([(c[0], c[1]) for c in confirmados], [(2341, "Ex Terminal"), (2388, "Panamericano")])

    def test_rechaza_aprobacion_ambigua(self):
        for valor in ("x", "ok", "yes", "tal vez"):
            with self.subTest(valor=valor), self.assertRaises(pv.ErrorRevision):
                pv.pares_confirmados([self.fila(revisado=valor)], self.catalogo)

    def test_falta_comentario_no_genera_migracion(self):
        with self.assertRaises(pv.ErrorRevision) as e:
            pv.pares_confirmados([self.fila(comentario="")], self.catalogo)
        self.assertIn("comentario", str(e.exception))

    def test_un_id_o_un_surtidor_repetido_no_genera_nada(self):
        filas = [self.fila(), self.fila("2341", "Sointa"), self.fila("2400", "Sointa")]
        with self.assertRaises(pv.ErrorRevision) as e:
            pv.pares_confirmados(filas, self.catalogo)
        self.assertIn("anh_id", str(e.exception))
        self.assertIn("surtidor", str(e.exception))

    def test_p31_rechaza_aprobacion_a_mas_de_50_metros(self):
        fila = self.fila("2388", "Panamericano")
        fila["latitud_anh"] = str(self.c["Panamericano"].latitud - 0.005)
        fila["distancia_m"] = str(pv.distancia_m(float(fila["latitud_anh"]),
                                                  float(fila["longitud_anh"]),
                                                  self.c["Panamericano"].latitud,
                                                  self.c["Panamericano"].longitud))
        with self.assertRaises(pv.ErrorRevision) as e:
            pv.pares_confirmados([fila], self.catalogo)
        self.assertIn("P-31", str(e.exception))

    def test_georreferenciacion_corregida_con_evidencia_permite_p31(self):
        fila = self.fila("2388", "Panamericano")
        s = self.c["Panamericano"]
        lat_original = s.latitud - 0.005
        lon_original = s.longitud
        distancia_original = pv.distancia_m(lat_original, lon_original, s.latitud, s.longitud)
        fila.update({
            "coordenadas_corregidas": "si",
            "latitud_anh_original": str(lat_original),
            "longitud_anh_original": str(lon_original),
            "distancia_original_m": str(distancia_original),
            "evidencia_georreferenciacion": "ANH dirección oficial + OSM node 1753664990",
            "latitud_anh": str(s.latitud),
            "longitud_anh": str(s.longitud),
            "distancia_m": "0.000000",
        })
        confirmados = pv.pares_confirmados([fila], self.catalogo)
        self.assertEqual([(x[0], x[1]) for x in confirmados], [(2388, "Panamericano")])

    def test_georreferenciacion_corregida_sin_evidencia_se_rechaza(self):
        fila = self.fila("2388", "Panamericano")
        s = self.c["Panamericano"]
        lat_original = s.latitud - 0.005
        fila.update({
            "coordenadas_corregidas": "si",
            "latitud_anh_original": str(lat_original),
            "longitud_anh_original": str(s.longitud),
            "distancia_original_m": str(pv.distancia_m(lat_original, s.longitud, s.latitud, s.longitud)),
            "latitud_anh": str(s.latitud),
            "longitud_anh": str(s.longitud),
            "distancia_m": "0",
            "evidencia_georreferenciacion": "",
        })
        with self.assertRaises(pv.ErrorRevision):
            pv.pares_confirmados([fila], self.catalogo)

    def test_sql_directo_tambien_bloquea_distancias_fuera_de_p31(self):
        with self.assertRaises(pv.ErrorRevision):
            pv.sql_migracion([(2388, "Panamericano", 651.191)], "Responsable", dt.date(2026, 11, 23))

    def test_no_acepta_id_fuera_de_rango_integer(self):
        with self.assertRaises(pv.ErrorRevision):
            pv.pares_confirmados([self.fila("2147483648")], self.catalogo)

    def test_un_surtidor_que_no_esta_en_el_catalogo_no_genera_nada(self):
        with self.assertRaises(pv.ErrorRevision):
            pv.pares_confirmados([self.fila(surtidor="Inventado")], self.catalogo)

    def test_distancia_inconsistente_con_coordenadas_no_genera_nada(self):
        fila = self.fila()
        fila["distancia_m"] = "650.0"
        with self.assertRaises(pv.ErrorRevision) as e:
            pv.pares_confirmados([fila], self.catalogo)
        self.assertIn("recalculada", str(e.exception))

    def test_las_coordenadas_faltantes_o_invalidas_no_generan_nada(self):
        for lat in ("", "999", "NaN", "abc"):
            fila = self.fila()
            fila["latitud_anh"] = lat
            with self.subTest(lat=lat), self.assertRaises(pv.ErrorRevision):
                pv.pares_confirmados([fila], self.catalogo)

    def test_no_confirma_un_vinculo_sin_distancia_valida(self):
        for distancia in ("", "-1", "NaN", "Infinity", "abc"):
            fila = self.fila()
            fila["distancia_m"] = distancia
            with self.subTest(distancia=distancia), self.assertRaises(pv.ErrorRevision):
                pv.pares_confirmados([fila], self.catalogo)

    def test_detecta_id_anh_cambiado_frente_al_csv_real(self):
        with tempfile.TemporaryDirectory() as d:
            origen = escribir_csv(Path(d), [["completa", "2341", "ESTACION DOCUMENTADA", "-21.542", "-64.724", "0", "1"]])
            fila = self.fila(anh_id="9999")
            with self.assertRaises(pv.ErrorRevision):
                pv.validar_con_fuente_anh([fila], origen)

    def test_detecta_coordenadas_anh_editadas(self):
        with tempfile.TemporaryDirectory() as d:
            s = self.c["Ex Terminal"]
            origen = escribir_csv(Path(d), [["completa", "2341", "ESTACION DOCUMENTADA", str(s.latitud),
                                             str(s.longitud), "0", "1"]])
            fila = self.fila()
            fila["longitud_anh"] = str(s.longitud + 0.01)
            with self.assertRaises(pv.ErrorRevision):
                pv.validar_con_fuente_anh([fila], origen)

    def test_validacion_fuente_usa_coordenadas_originales_si_hay_override(self):
        with tempfile.TemporaryDirectory() as d:
            s = self.c["Panamericano"]
            lat_original = s.latitud - 0.005
            lon_original = s.longitud
            origen = escribir_csv(Path(d), [["completa", "2388", "ESTACION DOCUMENTADA",
                                             str(lat_original), str(lon_original), "0", "1"]])
            fila = self.fila("2388", "Panamericano")
            fila.update({
                "coordenadas_corregidas": "si",
                "latitud_anh_original": str(lat_original),
                "longitud_anh_original": str(lon_original),
                "distancia_original_m": str(pv.distancia_m(lat_original, lon_original, s.latitud, s.longitud)),
                "evidencia_georreferenciacion": "ANH official address + OSM node 1753664990",
                "latitud_anh": str(s.latitud),
                "longitud_anh": str(s.longitud),
                "distancia_m": "0",
            })
            pv.validar_con_fuente_anh([fila], origen)
            pv.pares_confirmados([fila], self.catalogo)

    def test_sugerencia_p31_no_confirma_automaticamente_cero(self):
        self.assertIsNone(pv.p31_sugerida([(2339, "Tacuarandi", 0.0)]))
        self.assertEqual(pv.p31_sugerida([(2388, "Panamericano", 651.191)]), 700)

    def test_sql_valida_cada_vinculo_sin_suponer_el_total_de_los_anteriores(self):
        sql = pv.sql_migracion([(2341, "Ex Terminal", 0.0), (2974, "YPFB Parada Chaco", 37.759)], "Revisor verdadero",
                               dt.date(2026, 11, 24))
        self.assertIn("UPDATE surtidor SET anh_id = 2341 WHERE nombre = 'Ex Terminal' AND (anh_id IS NULL OR anh_id = 2341);", sql)
        self.assertIn("Revisión declarada por Revisor verdadero el 2026-11-24", sql)
        self.assertIn("filas_modificadas <> 1", sql)
        self.assertIn("ya tiene un vínculo ANH diferente", sql)
        self.assertIn("ya pertenece a otro surtidor", sql)
        self.assertNotIn("count(*) FROM surtidor WHERE anh_id IS NOT NULL", sql)

    def test_la_consola_elige_la_siguiente_version_flyway_libre(self):
        with tempfile.TemporaryDirectory() as d:
            carpeta = Path(d)
            migracion_precision = carpeta / "V13__preservar_precision_litros_anh.sql"
            migracion_precision.write_text("-- no sobrescribir\n", encoding="utf-8")
            revisados = carpeta / "revisados.csv"
            campos = list(self.fila().keys())
            with revisados.open("w", encoding="utf-8", newline="") as f:
                w = csv.DictWriter(f, fieldnames=campos, delimiter=";")
                w.writeheader()
                w.writerow(self.fila())

            fuente = escribir_csv(carpeta, [["completa", "2341", "ESTACION DOCUMENTADA",
                                            str(self.c["Ex Terminal"].latitud), str(self.c["Ex Terminal"].longitud),
                                            "0", "0"]])
            self.assertEqual(pv.main(["migracion", str(revisados), "--fuente-anh", str(fuente),
                                      "--revisado-por", "Franz", "--version", "13", "--salida", d]), 0)
            self.assertEqual(migracion_precision.read_text(encoding="utf-8"), "-- no sobrescribir\n")
            self.assertTrue((carpeta / "V14__vinculos_anh.sql").exists())
            self.assertEqual(pv.main(["migracion", str(revisados), "--fuente-anh", str(fuente),
                                      "--revisado-por", "Franz", "--version", "13", "--salida", d]), 2)
            self.assertFalse((carpeta / "V15__vinculos_anh.sql").exists())



if __name__ == "__main__":
    unittest.main()
