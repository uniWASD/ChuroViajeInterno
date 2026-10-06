"""Redes de calles de prueba para el motor de ruteo (CU-03)."""

import random

from red_de_calles import RedDeCalles


# ---------------------------------------------------------------------------
# Grafo IRREGULAR: nodos sin alinear, calles de largo distinto, algunas de
# un solo sentido, un nodo casi aislado, y una calle curva (más larga que
# la línea recta entre sus dos extremos).
# ---------------------------------------------------------------------------
def construir_red_irregular():
    red = RedDeCalles()

    # Posiciones a mano, NO en cuadrícula. Los números son ilustrativos,
    # pensados para que las distancias entre nodos vecinos varíen bastante
    # (de ~180 m a ~1000 m) y para que el "layout" no sea simétrico.
    nodos = {
        "Plaza":      (-21.5355, -64.7296),  # centro, punto de referencia
        "Mercado":    (-21.5348, -64.7280),  # cerca, al noreste
        "Terminal":   (-21.5410, -64.7350),  # lejos, al suroeste
        "Hospital":   (-21.5330, -64.7310),  # cerca, al norte
        "Universidad":(-21.5300, -64.7260),  # más lejos, al noreste
        "Barrio_Sur": (-21.5420, -64.7270),  # lejos, al sur
        "Mirador":    (-21.5290, -64.7220),  # el más lejano, casi aislado
    }
    for nombre, (lat, lon) in nodos.items():
        red.agregar_nodo(nombre, lat, lon)

    # Calles con largos distintos y no todas dobles:

    # Plaza <-> Mercado: calle corta y directa, doble sentido.
    red.agregar_calle_doble("Plaza", "Mercado", "Calle Comercio")

    # Plaza <-> Hospital: doble sentido.
    red.agregar_calle_doble("Plaza", "Hospital", "Av. Circunvalación")

    # Mercado <-> Universidad: doble sentido.
    red.agregar_calle_doble("Mercado", "Universidad", "Av. Las Américas")

    # Hospital <-> Universidad: UN SOLO SENTIDO (calle de bajada, cuesta
    # arriba está prohibida / no existe ese carril en este modelo).
    red.agregar_calle("Hospital", "Universidad", "Calle La Cuesta (bajada)")

    # Universidad <-> Mirador: la única calle de acceso al Mirador, larga
    # y con curvas (factor_curvatura > 1: la calle da vueltas por la loma,
    # no es una línea recta como asumiría la heurística).
    red.agregar_calle_doble("Universidad", "Mirador", "Camino al Mirador", factor_curvatura=1.8)

    # Plaza <-> Terminal: doble sentido, avenida larga que no es recta
    # (factor_curvatura=1.2). Así el atajo Mercado -> Terminal gana con un
    # margen claro (~18 %) frente a ir por Plaza, y no por unos pocos metros:
    # Plaza está casi en la misma línea que Mercado y Terminal, así que sin
    # la curvatura la diferencia era de solo 11 m (T-058).
    red.agregar_calle_doble("Plaza", "Terminal", "Av. Domingo Paredes", factor_curvatura=1.2)

    # Terminal <-> Barrio_Sur: doble sentido.
    red.agregar_calle_doble("Terminal", "Barrio_Sur", "Calle del Sur")

    # Plaza <-> Barrio_Sur: NO hay calle directa (hay que pasar por Terminal).
    # (simplemente no se agrega esa arista)

    # Mercado <-> Terminal: atajo de un solo sentido (solo se puede ir de
    # Mercado hacia Terminal, no al revés — por ejemplo, una calle
    # peatonal/de bajada habilitada para tráfico en un solo sentido).
    red.agregar_calle("Mercado", "Terminal", "Pasaje El Atajo")

    return red


# ---------------------------------------------------------------------------
# Red GRANDE para medir rendimiento (CP-13).
# Cuadrícula deformada de filas x columnas intersecciones alrededor de la
# Plaza, con cuadras de ~100 m, calles de largo irregular y una parte de
# calles de un solo sentido. Usa una semilla fija para que la red sea siempre
# la misma y la prueba sea repetible.
# ---------------------------------------------------------------------------
LAT_CENTRO, LON_CENTRO = -21.5355, -64.7296   # Plaza principal
PASO_GRADOS = 0.0009                          # ~100 m entre intersecciones
DESPLAZAMIENTO_MAX = 0.0002                   # ~20 m: las esquinas no quedan alineadas


def nombre_interseccion(fila, columna):
    return f"I{fila}_{columna}"


def construir_red_grande(filas=100, columnas=100, semilla=2026,
                         prob_un_sentido=0.2, curvatura_max=1.4):
    rnd = random.Random(semilla)
    red = RedDeCalles()

    for f in range(filas):
        for c in range(columnas):
            lat = (LAT_CENTRO + (f - filas / 2) * PASO_GRADOS
                   + rnd.uniform(-DESPLAZAMIENTO_MAX, DESPLAZAMIENTO_MAX))
            lon = (LON_CENTRO + (c - columnas / 2) * PASO_GRADOS
                   + rnd.uniform(-DESPLAZAMIENTO_MAX, DESPLAZAMIENTO_MAX))
            red.agregar_nodo(nombre_interseccion(f, c), lat, lon)

    def conectar(a, b):
        curvatura = rnd.uniform(1.0, curvatura_max)
        if rnd.random() < prob_un_sentido:
            if rnd.random() < 0.5:
                a, b = b, a
            red.agregar_calle(a, b, f"{a}-{b}", curvatura)
        else:
            red.agregar_calle_doble(a, b, f"{a}-{b}", curvatura)

    for f in range(filas):
        for c in range(columnas):
            if c + 1 < columnas:
                conectar(nombre_interseccion(f, c), nombre_interseccion(f, c + 1))
            if f + 1 < filas:
                conectar(nombre_interseccion(f, c), nombre_interseccion(f + 1, c))

    return red
