"""Pruebas del motor de ruteo — CU-03 Calcular Ruta Óptima (HU-05).

CP-14 a CP-19 vienen de la issue #15 (grafo irregular).
CP-09 a CP-13 cubren el manejo de errores, las validaciones y el rendimiento.

Uso (desde 05_pro/PruebaCalculoRuta):  python -m pytest -v
"""

import contextlib
import io
import itertools
import random
import time

import networkx as nx
import pytest

from red_de_calles import (MENSAJE_PUNTO_INVALIDO, MENSAJE_SIN_RUTA,
                           RedDeCalles)
from redes_de_prueba import (construir_red_grande, construir_red_irregular,
                             nombre_interseccion)

# CU-03, requisitos especiales: el cálculo debe completarse en menos de 3 s.
TIEMPO_MAXIMO_SEGUNDOS = 3.0

# Tamaño de la red para CP-13. PROVISIONAL: el equipo debe fijar el tamaño
# definitivo (por ejemplo, el número de intersecciones de la mancha urbana).
FILAS_RED_GRANDE = 100
COLUMNAS_RED_GRANDE = 100
CONSULTAS_RENDIMIENTO = 20


# ---------------------------------------------------------------------------
# Casos de prueba de la issue #15 (grafo irregular)
# ---------------------------------------------------------------------------
def test_cp14_ruta_sin_eventos_con_calles_de_largo_desigual():
    red = construir_red_irregular()

    resultado = red.calcular_ruta("Plaza", "Universidad")

    assert not red.existe_calle("Plaza", "Universidad")
    assert resultado.encontrada
    assert resultado.ruta == ["Plaza", "Mercado", "Universidad"]
    assert resultado.costo < red.costo_ruta(["Plaza", "Hospital", "Universidad"])


def test_cp15_atajo_de_un_solo_sentido_solo_sirve_de_ida():
    red = construir_red_irregular()

    assert red.existe_calle("Mercado", "Terminal")
    assert not red.existe_calle("Terminal", "Mercado")
    assert red.calcular_ruta("Mercado", "Terminal").ruta == ["Mercado", "Terminal"]
    assert red.calcular_ruta("Terminal", "Mercado").ruta == ["Terminal", "Plaza", "Mercado"]

    # El atajo debe ganar con margen claro (>= 10 %), no por unos pocos metros.
    atajo = red.costo_ruta(["Mercado", "Terminal"])
    por_plaza = red.costo_ruta(["Mercado", "Plaza", "Terminal"])
    assert por_plaza >= 1.10 * atajo


def test_cp16_nodo_casi_aislado_con_calle_curva():
    red = construir_red_irregular()

    resultado = red.calcular_ruta("Plaza", "Mirador")

    assert resultado.ruta == ["Plaza", "Mercado", "Universidad", "Mirador"]
    tramo_real = red.costo_tramo("Universidad", "Mirador")
    linea_recta = red.heuristica("Universidad", "Mirador")
    assert tramo_real == pytest.approx(1.8 * linea_recta)


def test_cp17_sin_calle_directa_la_ruta_debe_rodear():
    red = construir_red_irregular()

    resultado = red.calcular_ruta("Plaza", "Barrio_Sur")

    assert not red.existe_calle("Plaza", "Barrio_Sur")
    assert resultado.ruta == ["Plaza", "Terminal", "Barrio_Sur"]
    assert resultado.costo < red.costo_ruta(["Plaza", "Mercado", "Terminal", "Barrio_Sur"])


def test_cp18_congestion_fuerte_obliga_a_desviarse():
    red = construir_red_irregular()
    assert red.calcular_ruta("Hospital", "Universidad").ruta == ["Hospital", "Universidad"]

    red.reportar_evento("Hospital", "Universidad", "congestion_fuerte")
    resultado = red.calcular_ruta("Hospital", "Universidad")

    rodeo = ["Hospital", "Plaza", "Mercado", "Universidad"]
    assert resultado.ruta == rodeo
    assert red.costo_ruta(rodeo) < red.costo_tramo("Hospital", "Universidad")


def test_cp19_pasar_de_congestion_a_choque_no_cambia_una_ruta_optima():
    red = construir_red_irregular()
    red.reportar_evento("Hospital", "Universidad", "congestion_fuerte")
    ruta_con_congestion = red.calcular_ruta("Hospital", "Universidad").ruta

    red.reportar_evento("Hospital", "Universidad", "choque")
    ruta_con_choque = red.calcular_ruta("Hospital", "Universidad").ruta

    assert ruta_con_choque == ruta_con_congestion == ["Hospital", "Plaza", "Mercado", "Universidad"]


# ---------------------------------------------------------------------------
# Criterios generales de la issue #15, comprobados sobre todos los pares
# ---------------------------------------------------------------------------
def test_ninguna_ruta_recorre_una_calle_en_contramano():
    red = construir_red_irregular()
    for origen, destino in itertools.permutations(red.G.nodes, 2):
        resultado = red.calcular_ruta(origen, destino)
        for u, v in zip(resultado.ruta, resultado.ruta[1:]):
            assert red.existe_calle(u, v), f"{origen}->{destino} usa {u}->{v} en contramano"


def test_la_heuristica_nunca_sobreestima_el_costo_real():
    red = construir_red_irregular()
    for origen, destino in itertools.permutations(red.G.nodes, 2):
        if nx.has_path(red.G, origen, destino):
            costo_real = nx.dijkstra_path_length(red.G, origen, destino,
                                                 weight=red.peso_efectivo)
            assert red.heuristica(origen, destino) <= costo_real


def test_el_modelo_no_imprime_en_pantalla():
    red = construir_red_irregular()
    salida = io.StringIO()
    with contextlib.redirect_stdout(salida):
        red.reportar_evento("Hospital", "Universidad", "choque")
        red.avanzar_reloj(60)
        red.calcular_ruta("Plaza", "Universidad")
    assert salida.getvalue() == ""


# ---------------------------------------------------------------------------
# Casos de prueba nuevos: errores, validaciones y rendimiento
# ---------------------------------------------------------------------------
def test_cp09_sin_ruta_posible_se_informa_ruta_no_encontrada():
    red = construir_red_irregular()
    red.agregar_nodo("Aislado", -21.5500, -64.7400)

    resultado = red.calcular_ruta("Plaza", "Aislado")

    assert not resultado.encontrada
    assert resultado.mensaje == MENSAJE_SIN_RUTA
    assert resultado.ruta == []
    assert resultado.costo is None


def test_cp10_destino_u_origen_inexistente_se_informa_como_invalido():
    red = construir_red_irregular()

    destino_invalido = red.calcular_ruta("Plaza", "NoExiste")
    origen_invalido = red.calcular_ruta("NoExiste", "Plaza")

    for resultado in (destino_invalido, origen_invalido):
        assert not resultado.encontrada
        assert resultado.mensaje == MENSAJE_PUNTO_INVALIDO
        assert resultado.ruta == []


def test_cp11_se_rechaza_factor_curvatura_menor_a_uno():
    red = construir_red_irregular()

    with pytest.raises(ValueError, match="factor_curvatura"):
        red.agregar_calle("Plaza", "Universidad", "Calle imposible", factor_curvatura=0.5)

    assert not red.existe_calle("Plaza", "Universidad")


def test_cp12_se_rechaza_evento_sobre_calle_en_contramano():
    red = construir_red_irregular()

    with pytest.raises(ValueError, match="No existe la calle"):
        red.reportar_evento("Universidad", "Hospital", "choque")

    assert red.eventos == {}


def test_se_rechaza_tipo_de_evento_desconocido():
    red = construir_red_irregular()

    with pytest.raises(ValueError, match="Tipo de evento desconocido"):
        red.reportar_evento("Hospital", "Universidad", "inundacion")

    assert red.eventos == {}


def test_cp13_calculo_de_ruta_en_menos_de_3_segundos_en_red_grande():
    red = construir_red_grande(FILAS_RED_GRANDE, COLUMNAS_RED_GRANDE)
    rnd = random.Random(13)

    def interseccion_al_azar():
        return nombre_interseccion(rnd.randrange(FILAS_RED_GRANDE),
                                   rnd.randrange(COLUMNAS_RED_GRANDE))

    # Peor caso: de una esquina a la opuesta, más consultas al azar.
    consultas = [(nombre_interseccion(0, 0),
                  nombre_interseccion(FILAS_RED_GRANDE - 1, COLUMNAS_RED_GRANDE - 1))]
    consultas += [(interseccion_al_azar(), interseccion_al_azar())
                  for _ in range(CONSULTAS_RENDIMIENTO)]

    for origen, destino in consultas:
        inicio = time.perf_counter()
        red.calcular_ruta(origen, destino)
        duracion = time.perf_counter() - inicio
        assert duracion < TIEMPO_MAXIMO_SEGUNDOS, (
            f"{origen}->{destino} tardó {duracion:.2f} s")
