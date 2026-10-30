import argparse
from datetime import date
import json
import math
from pathlib import Path

from shapely.geometry import LineString, Point

from preparar_red import TIPOS_VIA, cargar_poligono, distancia_m, longitud_recorrido_m


def validar_red(ruta_red, ruta_cobertura):
    poligono = cargar_poligono(ruta_cobertura)
    with open(ruta_red, encoding="utf-8") as archivo:
        red = json.load(archivo)
    if red.get("version") != 1 or red.get("fuente") != "OpenStreetMap":
        raise ValueError("Versión o fuente de la red vial incorrectas.")
    fecha = red.get("fecha_extracto")
    if not isinstance(fecha, str) or date.fromisoformat(fecha).isoformat() != fecha:
        raise ValueError("La fecha del extracto no es una fecha ISO válida.")
    nodos = red.get("nodos")
    tramos = red.get("tramos")
    if not isinstance(nodos, list) or not nodos or not isinstance(tramos, list) or not tramos:
        raise ValueError("La red no contiene nodos y tramos.")
    por_id = {}
    cobertura_tolerancia = poligono.buffer(1e-9)
    for nodo in nodos:
        identificador = nodo["id"]
        coordenada = (nodo["lon"], nodo["lat"])
        if identificador in por_id:
            raise ValueError(f"Nodo duplicado: {identificador}")
        if not all(isinstance(valor, (int, float)) and math.isfinite(valor) for valor in coordenada):
            raise ValueError(f"Nodo con coordenadas inválidas: {identificador}")
        if not cobertura_tolerancia.covers(Point(coordenada)):
            raise ValueError(f"Nodo fuera de cobertura: {identificador}")
        por_id[identificador] = coordenada
    ids_tramos = set()
    for tramo in tramos:
        identificador = tramo["id"]
        if identificador in ids_tramos:
            raise ValueError(f"Tramo duplicado: {identificador}")
        ids_tramos.add(identificador)
        if tramo["desde"] not in por_id or tramo["hasta"] not in por_id:
            raise ValueError(f"Tramo con nodo inexistente: {identificador}")
        if tramo["tipo_via"] not in TIPOS_VIA.values():
            raise ValueError(f"Tipo vehicular incorrecto: {identificador}")
        if not isinstance(tramo["nombre"], str) or not tramo["nombre"].strip():
            raise ValueError(f"Nombre de vía vacío: {identificador}")
        geometria = tramo["geometria"]
        if not isinstance(geometria, list) or len(geometria) < 2:
            raise ValueError(f"Geometría incompleta: {identificador}")
        if any(len(p) != 2 or not all(isinstance(x, (int, float)) and math.isfinite(x) for x in p) for p in geometria):
            raise ValueError(f"Coordenadas de geometría inválidas: {identificador}")
        if distancia_m(geometria[0], por_id[tramo["desde"]]) > 0.001:
            raise ValueError(f"Inicio de geometría incorrecto: {identificador}")
        if distancia_m(geometria[-1], por_id[tramo["hasta"]]) > 0.001:
            raise ValueError(f"Final de geometría incorrecto: {identificador}")
        if not cobertura_tolerancia.covers(LineString(geometria)):
            raise ValueError(f"Tramo fuera de cobertura: {identificador}")
        longitud = tramo["longitud_m"]
        if not isinstance(longitud, (int, float)) or not math.isfinite(longitud) or longitud <= 0:
            raise ValueError(f"Longitud no válida: {identificador}")
        recorrido = longitud_recorrido_m(geometria)
        directo = distancia_m(geometria[0], geometria[-1])
        if longitud + 1e-6 < recorrido or longitud + 1e-6 < directo:
            raise ValueError(f"Longitud menor que el recorrido: {identificador}")
        if longitud - recorrido > 0.00101:
            raise ValueError(f"Longitud inconsistente con la geometría: {identificador}")
    return len(nodos), len(tramos)


def main():
    parser = argparse.ArgumentParser(description="Comprueba la integridad y la geometría de la red vial preparada.")
    parser.add_argument("--red", required=True, type=Path)
    parser.add_argument("--cobertura", required=True, type=Path)
    args = parser.parse_args()
    try:
        cantidad_nodos, cantidad_tramos = validar_red(args.red, args.cobertura)
    except (ValueError, OSError, KeyError, TypeError) as error:
        parser.exit(1, f"Error de validación: {error}\n")
    print(f"Validación correcta: {cantidad_nodos} nodos, {cantidad_tramos} tramos.")


if __name__ == "__main__":
    main()
