"""Prepara los lugares que se pueden buscar en ChuroViaje (D-47): calles con nombre y lugares conocidos de Tarija.

Lee el mismo extracto de OpenStreetMap que preparar_red.py y deja un JSON fuera del repositorio. El backend lo carga
en la tabla ``lugar`` al arrancar (variable LUGARES_PATH). Los surtidores no salen de aquí: vienen del catálogo
propio (V2), así que las estaciones de servicio de OSM se descartan.
"""

import argparse
from collections import Counter, defaultdict
import json
import os
from pathlib import Path
import tempfile
import time
import unicodedata

from shapely.geometry import LineString, MultiLineString, Point, Polygon
from shapely.ops import nearest_points
from shapely.prepared import prep

from preparar_red import TIPOS_VIA, cargar_poligono, distancia_m, fecha_iso

# Dos tramos con el mismo nombre se tratan como la misma calle si quedan a menos de esta distancia.
DISTANCIA_MISMA_CALLE_M = 300
# Dos lugares con el mismo nombre y categoría a menos de esta distancia son el mismo (un nodo y su edificio, p. ej.).
DISTANCIA_MISMO_LUGAR_M = 150
# Calles que no se buscan aunque tengan nombre.
VIAS_SIN_BUSQUEDA = {"service"}

SALUD = {"hospital", "clinic", "doctors", "dentist", "pharmacy"}
EDUCACION = {"school", "university", "college", "kindergarten"}
GOBIERNO = {"townhall", "police", "fire_station", "courthouse", "post_office"}
COMIDA = {"restaurant", "cafe", "fast_food", "bar", "pub", "ice_cream"}
TURISMO = {"attraction", "museum", "viewpoint", "gallery", "zoo"}
HOSPEDAJE = {"hotel", "hostel", "guest_house", "motel", "apartment"}
PARQUES = {"park", "garden", "nature_reserve"}
DEPORTE = {"stadium", "sports_centre", "pitch", "track"}

CATEGORIAS = ("CALLE", "PLAZA", "PARQUE", "SALUD", "EDUCACION", "MERCADO", "GOBIERNO", "TRANSPORTE", "TURISMO",
              "HOTEL", "BANCO", "COMIDA", "DEPORTE", "RELIGION", "COMERCIO")


def categoria(etiquetas):
    """La categoría de un lugar de OSM, o None si no se ofrece en la búsqueda."""
    amenity = etiquetas.get("amenity", "")
    if amenity == "fuel":
        return None
    se_llama_plaza = etiquetas.get("name", "").strip().lower().startswith(("plaza", "plazuela"))
    if etiquetas.get("place") == "square" or (se_llama_plaza and (etiquetas.get("highway") == "pedestrian"
                                                                  or etiquetas.get("leisure") in PARQUES)):
        return "PLAZA"
    if etiquetas.get("leisure") in PARQUES:
        return "PARQUE"
    if amenity in SALUD or etiquetas.get("healthcare"):
        return "SALUD"
    if amenity in EDUCACION:
        return "EDUCACION"
    if amenity == "marketplace" or etiquetas.get("shop") in {"mall", "supermarket", "department_store"}:
        return "MERCADO"
    if amenity in GOBIERNO or etiquetas.get("office") == "government":
        return "GOBIERNO"
    if (amenity == "bus_station" or etiquetas.get("aeroway") in {"aerodrome", "terminal"}
            or etiquetas.get("public_transport") == "station"):
        return "TRANSPORTE"
    if etiquetas.get("tourism") in TURISMO or etiquetas.get("historic"):
        return "TURISMO"
    if etiquetas.get("tourism") in HOSPEDAJE:
        return "HOTEL"
    if amenity == "bank":
        return "BANCO"
    if amenity in COMIDA:
        return "COMIDA"
    if etiquetas.get("leisure") in DEPORTE:
        return "DEPORTE"
    if amenity == "place_of_worship":
        return "RELIGION"
    if etiquetas.get("shop"):
        return "COMERCIO"
    return None


def es_calle(etiquetas):
    via = etiquetas.get("highway", "")
    return via in TIPOS_VIA and via not in VIAS_SIN_BUSQUEDA and bool(etiquetas.get("name", "").strip())


def clave_nombre(nombre):
    """Solo para agrupar en este script; el backend normaliza por su cuenta para buscar."""
    sin_tildes = unicodedata.normalize("NFD", nombre)
    sin_tildes = "".join(c for c in sin_tildes if unicodedata.category(c) != "Mn")
    return " ".join(sin_tildes.lower().split())


def _distancia_lineas_m(a, b):
    p, q = nearest_points(a, b)
    return distancia_m((p.x, p.y), (q.x, q.y))


def agrupar_calles(tramos, poligono):
    """Una entrada por calle: los tramos con el mismo nombre que se tocan o quedan cerca forman una sola.

    ``tramos`` es una lista de (id_via, nombre, [(lon, lat), ...]). El punto de cada calle es el de la calle más
    cercano a su centro, para que siempre caiga sobre la calle.
    """
    zona = prep(poligono)
    por_nombre = defaultdict(list)
    for id_via, nombre, puntos in tramos:
        linea = LineString(puntos)
        if not zona.intersects(linea):
            continue
        dentro = linea.intersection(poligono)
        partes = [dentro] if dentro.geom_type == "LineString" else [g for g in getattr(dentro, "geoms", [])
                                                                     if g.geom_type == "LineString"]
        for parte in partes:
            if not parte.is_empty and parte.length > 0:
                por_nombre[clave_nombre(nombre)].append((id_via, nombre.strip(), parte))

    calles = []
    for grupo in por_nombre.values():
        padre = list(range(len(grupo)))

        def raiz(i):
            while padre[i] != i:
                padre[i] = padre[padre[i]]
                i = padre[i]
            return i

        for i in range(len(grupo)):
            for j in range(i + 1, len(grupo)):
                if raiz(i) != raiz(j) and _distancia_lineas_m(grupo[i][2], grupo[j][2]) <= DISTANCIA_MISMA_CALLE_M:
                    padre[raiz(j)] = raiz(i)
        conjuntos = defaultdict(list)
        for i, elemento in enumerate(grupo):
            conjuntos[raiz(i)].append(elemento)
        for miembros in conjuntos.values():
            union = MultiLineString([m[2] for m in miembros])
            punto = nearest_points(union, union.centroid)[0]
            nombre = Counter(m[1] for m in miembros).most_common(1)[0][0]
            calles.append({"clave": f"calle:w{min(m[0] for m in miembros)}", "nombre": nombre, "categoria": "CALLE",
                           "latitud": round(punto.y, 7), "longitud": round(punto.x, 7)})
    return calles


def deduplicar(lugares):
    """Quita los repetidos: mismo nombre y categoría a menos de DISTANCIA_MISMO_LUGAR_M. Se queda con el primero."""
    elegidos = []
    vistos = defaultdict(list)
    for lugar in lugares:
        llave = (clave_nombre(lugar["nombre"]), lugar["categoria"])
        punto = (lugar["longitud"], lugar["latitud"])
        if any(distancia_m(punto, otro) < DISTANCIA_MISMO_LUGAR_M for otro in vistos[llave]):
            continue
        vistos[llave].append(punto)
        elegidos.append(lugar)
    return elegidos


def centro_de_via(puntos):
    if len(puntos) >= 4 and puntos[0] == puntos[-1]:
        figura = Polygon(puntos)
        if figura.is_valid and figura.area > 0:
            centro = figura.representative_point()
            return centro.x, centro.y
    centro = LineString(puntos).interpolate(0.5, normalized=True) if len(puntos) > 1 else Point(puntos[0])
    return centro.x, centro.y


def leer_osm(ruta, limites):
    """Devuelve (tramos de calle, lugares sueltos, fecha del extracto). Lee .osm.pbf y .osm con pyosmium."""
    try:
        import osmium
    except ImportError as error:
        raise RuntimeError("Instale los requisitos: python -m pip install -r requirements.txt") from error

    min_lon, min_lat, max_lon, max_lat = limites

    def en_caja(lon, lat):
        return min_lon <= lon <= max_lon and min_lat <= lat <= max_lat

    lector = osmium.io.Reader(str(ruta))
    try:
        cabecera = lector.header()
        fecha = cabecera.get("osmosis_replication_timestamp") or cabecera.get("timestamp")
    finally:
        lector.close()

    # El filtro por nombre corre en C++: Python solo ve los objetos con "name" (unos segundos para toda Bolivia).
    tramos, lugares = [], []
    procesador = (osmium.FileProcessor(str(ruta), osmium.osm.NODE | osmium.osm.WAY)
                  .with_locations().with_filter(osmium.filter.KeyFilter("name")))
    for objeto in procesador:
        etiquetas = {t.k: t.v for t in objeto.tags}
        nombre = etiquetas.get("name", "").strip()
        if not nombre:
            continue
        if objeto.is_node():
            tipo = categoria(etiquetas)
            if tipo and objeto.location.valid() and en_caja(objeto.location.lon, objeto.location.lat):
                lugares.append({"clave": f"n{objeto.id}", "nombre": nombre, "categoria": tipo,
                                "latitud": round(objeto.location.lat, 7), "longitud": round(objeto.location.lon, 7)})
            continue
        try:
            puntos = [(n.location.lon, n.location.lat) for n in objeto.nodes]
        except osmium.InvalidLocationError:
            continue
        if len(puntos) < 2 or not any(en_caja(lon, lat) for lon, lat in puntos):
            continue
        if es_calle(etiquetas):
            tramos.append((objeto.id, nombre, puntos))
            continue
        tipo = categoria(etiquetas)
        if tipo:
            lon, lat = centro_de_via(puntos)
            lugares.append({"clave": f"w{objeto.id}", "nombre": nombre, "categoria": tipo,
                            "latitud": round(lat, 7), "longitud": round(lon, 7)})
    return tramos, lugares, fecha


def preparar_lugares(tramos, sueltos, poligono, fecha):
    zona = prep(poligono)
    dentro = [l for l in sueltos if zona.contains(Point(l["longitud"], l["latitud"]))]
    lugares = agrupar_calles(tramos, poligono) + deduplicar(dentro)
    lugares.sort(key=lambda l: (l["categoria"], clave_nombre(l["nombre"]), l["clave"]))
    claves = Counter(l["clave"] for l in lugares)
    repetidas = [c for c, n in claves.items() if n > 1]
    if repetidas:
        raise ValueError(f"Claves repetidas: {repetidas[:5]}")
    return {"fecha_extracto": fecha, "fuente": "OpenStreetMap (ODbL)", "lugares": lugares}


def generar(ruta_osm, ruta_cobertura, ruta_salida, fecha_extracto=None):
    ruta_osm = Path(ruta_osm)
    ruta_salida = Path(ruta_salida)
    if not ruta_osm.is_file():
        raise ValueError(f"No existe el extracto OSM: {ruta_osm}")
    poligono = cargar_poligono(ruta_cobertura)
    tramos, sueltos, fecha_cabecera = leer_osm(ruta_osm, poligono.bounds)
    resultado = preparar_lugares(tramos, sueltos, poligono, fecha_iso(fecha_extracto or fecha_cabecera))
    if not resultado["lugares"]:
        raise ValueError("No se encontraron lugares en la cobertura; no se reemplazó la salida.")
    ruta_salida.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=ruta_salida.parent, prefix=".lugares_",
                                     suffix=".tmp", delete=False) as archivo:
        temporal = Path(archivo.name)
        json.dump(resultado, archivo, ensure_ascii=False, separators=(",", ":"))
        archivo.write("\n")
    os.replace(temporal, ruta_salida)
    return resultado


def main():
    parser = argparse.ArgumentParser(description="Prepara los lugares que se pueden buscar en ChuroViaje.")
    parser.add_argument("--osm", required=True, type=Path, help="Extracto .osm.pbf o .osm")
    parser.add_argument("--cobertura", required=True, type=Path, help="GeoJSON de la mancha urbana de Tarija")
    parser.add_argument("--salida", required=True, type=Path, help="JSON que carga el backend (LUGARES_PATH)")
    parser.add_argument("--fecha-extracto", help="Fecha ISO AAAA-MM-DD si el extracto no trae metadatos")
    args = parser.parse_args()
    inicio = time.perf_counter()
    try:
        resultado = generar(args.osm, args.cobertura, args.salida, args.fecha_extracto)
    except (OSError, ValueError, RuntimeError) as error:
        parser.exit(1, f"Error: {error}\n")
    por_categoria = Counter(l["categoria"] for l in resultado["lugares"])
    print(f"Lugares preparados: {len(resultado['lugares'])}, extracto {resultado['fecha_extracto']}. "
          f"Archivo: {args.salida} ({args.salida.stat().st_size} bytes, {time.perf_counter() - inicio:.1f} s).")
    for codigo in CATEGORIAS:
        if por_categoria[codigo]:
            print(f"  {codigo}: {por_categoria[codigo]}")


if __name__ == "__main__":
    main()
