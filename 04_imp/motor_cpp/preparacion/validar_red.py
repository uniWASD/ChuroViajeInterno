"""Comprobación del contrato T-111: JSON v1, [lat, lon] y opuestos recíprocos."""
import argparse
from datetime import date
import json
import math
from pathlib import Path

from shapely.geometry import LineString, Point

from preparar_red import TIPOS_VIA, cargar_poligono, distancia_m, longitud_recorrido_m


def numero_finite(valor):
    return type(valor) in (int, float) and math.isfinite(valor)


def coordenada_json(punto):
    """Transforma el par del contrato [lat, lon] al formato interno (lon, lat)."""
    if not isinstance(punto, list) or len(punto) != 2 or not all(map(numero_finite, punto)):
        raise ValueError("Par de coordenadas inválido; se exige [latitud, longitud].")
    lat, lon = punto
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        raise ValueError("Coordenadas fuera del rango WGS84.")
    return (lon, lat)


def validar_red(ruta_red, ruta_cobertura):
    poligono = cargar_poligono(ruta_cobertura)
    with open(ruta_red, encoding="utf-8") as archivo:
        red = json.load(archivo)
    if not isinstance(red, dict) or type(red.get("version")) is not int or red["version"] != 1 or red.get("fuente") != "OpenStreetMap":
        raise ValueError("Versión o fuente de la red vial incorrectas.")
    fecha = red.get("fecha_extracto")
    try:
        if not isinstance(fecha, str) or date.fromisoformat(fecha).isoformat() != fecha:
            raise ValueError
    except ValueError as error:
        raise ValueError("La fecha del extracto no es una fecha ISO válida.") from error
    nodos = red.get("nodos")
    tramos = red.get("tramos")
    if not isinstance(nodos, list) or not nodos or not isinstance(tramos, list) or not tramos:
        raise ValueError("La red no contiene nodos y tramos.")
    por_id = {}
    cobertura_tolerancia = poligono.buffer(1e-9)
    for nodo in nodos:
        if not isinstance(nodo, dict) or not isinstance(nodo.get("id"), str) or not nodo["id"]:
            raise ValueError("Nodo sin ID válido.")
        identificador = nodo["id"]
        if identificador in por_id:
            raise ValueError(f"Nodo duplicado: {identificador}")
        if not numero_finite(nodo.get("lon")) or not numero_finite(nodo.get("lat")):
            raise ValueError(f"Nodo con coordenadas inválidas: {identificador}")
        coordenada = (nodo["lon"], nodo["lat"])
        if not (-180 <= coordenada[0] <= 180 and -90 <= coordenada[1] <= 90):
            raise ValueError(f"Nodo fuera del rango WGS84: {identificador}")
        if not cobertura_tolerancia.covers(Point(coordenada)):
            raise ValueError(f"Nodo fuera de cobertura: {identificador}")
        por_id[identificador] = coordenada

    por_tramo = {}
    for tramo in tramos:
        if not isinstance(tramo, dict) or not isinstance(tramo.get("id"), str) or not tramo["id"]:
            raise ValueError("Tramo sin ID válido.")
        identificador = tramo["id"]
        if identificador in por_tramo:
            raise ValueError(f"Tramo duplicado: {identificador}")
        por_tramo[identificador] = tramo
        if tramo.get("desde") not in por_id or tramo.get("hasta") not in por_id:
            raise ValueError(f"Tramo con nodo inexistente: {identificador}")
        if tramo.get("tipo_via") not in TIPOS_VIA.values():
            raise ValueError(f"Tipo vehicular incorrecto: {identificador}")
        if not isinstance(tramo.get("nombre"), str) or not tramo["nombre"].strip():
            raise ValueError(f"Nombre de vía vacío: {identificador}")
        if "opuesto" not in tramo or (tramo["opuesto"] is not None and (not isinstance(tramo["opuesto"], str) or not tramo["opuesto"])):
            raise ValueError(f"Campo opuesto ausente o inválido: {identificador}")
        geometria = tramo.get("geometria")
        if not isinstance(geometria, list) or len(geometria) < 2:
            raise ValueError(f"Geometría incompleta: {identificador}")
        try:
            puntos = [coordenada_json(p) for p in geometria]
        except ValueError as error:
            raise ValueError(f"Geometría inválida en {identificador}: {error}") from error
        if distancia_m(puntos[0], por_id[tramo["desde"]]) > 0.001:
            raise ValueError(f"Inicio de geometría incorrecto: {identificador}")
        if distancia_m(puntos[-1], por_id[tramo["hasta"]]) > 0.001:
            raise ValueError(f"Final de geometría incorrecto: {identificador}")
        if not cobertura_tolerancia.covers(LineString(puntos)):
            raise ValueError(f"Tramo fuera de cobertura: {identificador}")
        longitud = tramo.get("longitud_m")
        if not numero_finite(longitud) or longitud <= 0:
            raise ValueError(f"Longitud no válida: {identificador}")
        recorrido = longitud_recorrido_m(puntos)
        directo = distancia_m(puntos[0], puntos[-1])
        if longitud + 1e-6 < recorrido or longitud + 1e-6 < directo:
            raise ValueError(f"Longitud menor que el recorrido: {identificador}")
        if longitud - recorrido > 0.00101:
            raise ValueError(f"Longitud inconsistente con la geometría: {identificador}")

    for identificador, tramo in por_tramo.items():
        opuesto_id = tramo["opuesto"]
        if opuesto_id is None:
            continue
        if opuesto_id == identificador or opuesto_id not in por_tramo:
            raise ValueError(f"Opuesto inexistente o autorreferenciado: {identificador}")
        pareja = por_tramo[opuesto_id]
        if pareja["opuesto"] != identificador:
            raise ValueError(f"Opuesto no recíproco: {identificador}")
        if tramo["desde"] != pareja["hasta"] or tramo["hasta"] != pareja["desde"]:
            raise ValueError(f"Extremos no invertidos en opuesto: {identificador}")
        if tramo["geometria"] != pareja["geometria"][::-1]:
            raise ValueError(f"Geometría del opuesto no está invertida: {identificador}")
        if any(tramo[campo] != pareja[campo] for campo in ("longitud_m", "nombre", "tipo_via")):
            raise ValueError(f"Atributos distintos en el opuesto: {identificador}")
    return len(nodos), len(tramos)


def diagnostico_conectividad(red):
    """Componentes fuertes (dirección) y débiles (conectividad ignorando dirección)."""
    vertices = {nodo["id"] for nodo in red["nodos"]}
    adyacentes = {vertice: [] for vertice in vertices}
    inversos = {vertice: [] for vertice in vertices}
    for tramo in red["tramos"]:
        a, b = tramo["desde"], tramo["hasta"]
        adyacentes[a].append(b)
        inversos[b].append(a)

    # Kosaraju iterativo: evita límite de recursión en redes extensas.
    visitados = set()
    orden_salida = []
    for vertice in sorted(vertices):
        if vertice in visitados:
            continue
        visitados.add(vertice)
        pila = [(vertice, iter(adyacentes[vertice]))]
        while pila:
            actual, vecinos = pila[-1]
            siguiente = next(vecinos, None)
            if siguiente is None:
                orden_salida.append(actual)
                pila.pop()
            elif siguiente not in visitados:
                visitados.add(siguiente)
                pila.append((siguiente, iter(adyacentes[siguiente])))

    visitados.clear()
    fuertes = []
    for vertice in reversed(orden_salida):
        if vertice in visitados:
            continue
        pila = [vertice]
        visitados.add(vertice)
        tamano = 0
        while pila:
            actual = pila.pop()
            tamano += 1
            for vecino in inversos[actual]:
                if vecino not in visitados:
                    visitados.add(vecino)
                    pila.append(vecino)
        fuertes.append(tamano)

    visitados.clear()
    debiles = []
    for vertice in vertices:
        if vertice in visitados:
            continue
        pila = [vertice]
        visitados.add(vertice)
        tamano = 0
        while pila:
            actual = pila.pop()
            tamano += 1
            for vecino in adyacentes[actual] + inversos[actual]:
                if vecino not in visitados:
                    visitados.add(vecino)
                    pila.append(vecino)
        debiles.append(tamano)
    return {"componentes_fuertes": len(fuertes), "mayor_fuerte": max(fuertes, default=0),
            "componentes_debiles": len(debiles), "mayor_debil": max(debiles, default=0)}


def main():
    parser = argparse.ArgumentParser(description="Comprueba integridad, geometría, opuestos y conectividad de la red.")
    parser.add_argument("--red", required=True, type=Path)
    parser.add_argument("--cobertura", required=True, type=Path)
    args = parser.parse_args()
    try:
        cantidad_nodos, cantidad_tramos = validar_red(args.red, args.cobertura)
        with open(args.red, encoding="utf-8") as archivo:
            conectividad = diagnostico_conectividad(json.load(archivo))
    except (ValueError, OSError, KeyError, TypeError) as error:
        parser.exit(1, f"Error de validación: {error}\n")
    print(f"Validación correcta: {cantidad_nodos} nodos, {cantidad_tramos} tramos.")
    print("Conectividad dirigida (solo informe, no criterio de rechazo):")
    print(f"  Componentes fuertemente conexas: {conectividad['componentes_fuertes']}; "
          f"la mayor contiene {conectividad['mayor_fuerte']} nodos.")
    print(f"  Componentes débilmente conexas: {conectividad['componentes_debiles']}; "
          f"la mayor contiene {conectividad['mayor_debil']} nodos.")


if __name__ == "__main__":
    main()
