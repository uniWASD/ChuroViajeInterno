import argparse
import bz2
from collections import Counter
from dataclasses import dataclass
from datetime import date, datetime
import gzip
import json
import math
import os
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET

from shapely.geometry import LineString, shape
from shapely.ops import unary_union


TIPOS_VIA = {
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
}

ACCESOS_RESTRINGIDOS = {"no", "private", "agricultural", "forestry", "delivery", "destination", "customers", "permit"}
SENTIDOS_POSITIVOS = {"yes", "true", "1"}
SENTIDOS_NEGATIVOS = {"-1", "reverse"}
SENTIDOS_DOBLES = {"no", "false", "0"}
RADIO_TERRESTRE_M = 6371008.8


@dataclass(frozen=True)
class Vertice:
    identificador: str
    lon: float
    lat: float


@dataclass(frozen=True)
class Via:
    identificador: int
    etiquetas: dict
    nodos: tuple


def es_vehicular(etiquetas):
    highway = etiquetas.get("highway", "").strip().lower()
    if highway not in TIPOS_VIA or etiquetas.get("area", "").lower() == "yes":
        return False
    if etiquetas.get("service", "").lower() in {"parking_aisle", "driveway", "drive-through"}:
        return False
    if sentido_circulacion(etiquetas) is None:
        return False
    if any(clave in etiquetas for clave in ("oneway:conditional", "oneway:motorcar:conditional",
                                           "motorcar:conditional", "motor_vehicle:conditional", "access:conditional")):
        return False
    for clave in ("motorcar", "motor_vehicle", "vehicle", "access"):
        if clave in etiquetas:
            return etiquetas[clave].strip().lower() not in ACCESOS_RESTRINGIDOS
    return True


def sentido_circulacion(etiquetas):
    for clave in ("oneway:motorcar", "oneway:motor_vehicle", "oneway:vehicle", "oneway"):
        valor = etiquetas.get(clave, "").strip().lower()
        if valor in SENTIDOS_POSITIVOS:
            return "ida"
        if valor in SENTIDOS_NEGATIVOS:
            return "vuelta"
        if valor in SENTIDOS_DOBLES:
            return "ambos"
        if valor:
            return None
    if etiquetas.get("junction", "").lower() == "roundabout":
        return "ida"
    if etiquetas.get("highway", "").lower() in {"motorway", "motorway_link"}:
        return "ida"
    return "ambos"


def nombre_via(etiquetas):
    return etiquetas.get("name", "").strip() or etiquetas.get("ref", "").strip() or "Sin nombre"


def distancia_m(a, b):
    lon1, lat1 = a
    lon2, lat2 = b
    lat1 = math.radians(lat1)
    lat2 = math.radians(lat2)
    delta_lat = lat2 - lat1
    delta_lon = math.radians(lon2 - lon1)
    h = math.sin(delta_lat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(delta_lon / 2) ** 2
    return 2 * RADIO_TERRESTRE_M * math.asin(min(1.0, math.sqrt(h)))


def longitud_recorrido_m(puntos):
    return math.fsum(distancia_m(a, b) for a, b in zip(puntos, puntos[1:]))


def cargar_poligono(ruta):
    with open(ruta, encoding="utf-8") as archivo:
        dato = json.load(archivo)
    if dato.get("type") == "FeatureCollection":
        geometrias = [shape(elemento["geometry"]) for elemento in dato.get("features", []) if elemento.get("geometry")]
        if not geometrias:
            raise ValueError("La cobertura GeoJSON no contiene polígonos.")
        poligono = unary_union(geometrias)
    elif dato.get("type") == "Feature":
        poligono = shape(dato["geometry"])
    else:
        poligono = shape(dato)
    if poligono.geom_type not in {"Polygon", "MultiPolygon"} or poligono.is_empty or not poligono.is_valid:
        raise ValueError("La cobertura debe ser un polígono o multipolígono válido.")
    return poligono


def coordenadas_validas(lon, lat):
    return math.isfinite(lon) and math.isfinite(lat) and -180 <= lon <= 180 and -90 <= lat <= 90


def intersecta_caja(nodos, limites):
    min_lon, min_lat, max_lon, max_lat = limites
    return not (
        max(n.lon for n in nodos) < min_lon
        or min(n.lon for n in nodos) > max_lon
        or max(n.lat for n in nodos) < min_lat
        or min(n.lat for n in nodos) > max_lat
    )


def cargar_xml(ruta, limites):
    nodos = {}
    vias = []
    metadatos = {}
    nombre = ruta.name.lower()
    if nombre.endswith(".gz"):
        abrir = gzip.open
    elif nombre.endswith(".bz2"):
        abrir = bz2.open
    else:
        abrir = open
    with abrir(ruta, "rb") as archivo:
        raiz = None
        for evento, elemento in ET.iterparse(archivo, events=("start", "end")):
            etiqueta = elemento.tag.rsplit("}", 1)[-1]
            if raiz is None and evento == "start":
                if etiqueta != "osm":
                    raise ValueError("El archivo XML no tiene raíz osm.")
                raiz = elemento
                metadatos = dict(raiz.attrib)
            if evento != "end":
                continue
            if etiqueta == "node":
                identificador = int(elemento.attrib["id"])
                lon = float(elemento.attrib["lon"])
                lat = float(elemento.attrib["lat"])
                if not coordenadas_validas(lon, lat):
                    raise ValueError(f"Coordenadas inválidas en nodo OSM {identificador}.")
                nodos[identificador] = (lon, lat)
                elemento.clear()
            elif etiqueta == "way":
                etiquetas = {h.attrib["k"]: h.attrib["v"] for h in elemento if h.tag.rsplit("}", 1)[-1] == "tag"}
                if es_vehicular(etiquetas):
                    referencias = [int(h.attrib["ref"]) for h in elemento if h.tag.rsplit("}", 1)[-1] == "nd"]
                    if len(referencias) > 1:
                        vias.append((int(elemento.attrib["id"]), etiquetas, referencias))
                elemento.clear()
            elif etiqueta == "relation":
                elemento.clear()
            if raiz is not None and etiqueta in {"node", "way", "relation"}:
                raiz.clear()
    resultado = []
    for identificador, etiquetas, referencias in vias:
        try:
            puntos = tuple(Vertice(f"osm:{ref}", *nodos[ref]) for ref in referencias)
        except KeyError as error:
            raise ValueError(f"Falta el nodo {error.args[0]} de la vía OSM {identificador}; utilice un extracto con nodos completos.") from error
        if intersecta_caja(puntos, limites):
            resultado.append(Via(identificador, etiquetas, puntos))
    return resultado, metadatos.get("osmosis_replication_timestamp") or metadatos.get("timestamp")


def cargar_pbf(ruta, limites):
    try:
        import osmium
    except ImportError as error:
        raise RuntimeError("Para procesar .osm.pbf instale los requisitos: python -m pip install -r requirements.txt") from error

    lector = osmium.io.Reader(str(ruta))
    try:
        cabecera = lector.header()
        fecha = cabecera.get("osmosis_replication_timestamp") or cabecera.get("timestamp")
    finally:
        lector.close()

    class Recolector(osmium.SimpleHandler):
        def __init__(self):
            super().__init__()
            self.vias = []

        def way(self, way):
            etiquetas = {tag.k: tag.v for tag in way.tags}
            if not es_vehicular(etiquetas):
                return
            try:
                puntos = tuple(Vertice(f"osm:{n.ref}", float(n.location.lon), float(n.location.lat)) for n in way.nodes)
            except Exception as error:
                raise ValueError(f"Vía OSM {way.id} con nodos incompletos; utilice un extracto con nodos completos.") from error
            if len(puntos) < 2:
                return
            if any(not coordenadas_validas(p.lon, p.lat) for p in puntos):
                raise ValueError(f"Coordenadas inválidas en vía OSM {way.id}.")
            if intersecta_caja(puntos, limites):
                self.vias.append(Via(int(way.id), etiquetas, puntos))

    recolector = Recolector()
    recolector.apply_file(str(ruta), locations=True)
    return recolector.vias, fecha


def fecha_iso(valor):
    if not valor:
        raise ValueError("No se encontró la fecha del extracto. Indique --fecha-extracto AAAA-MM-DD.")
    try:
        if isinstance(valor, date):
            return valor.isoformat()
        return datetime.fromisoformat(str(valor).replace("Z", "+00:00")).date().isoformat()
    except ValueError as error:
        raise ValueError("La fecha del extracto debe tener formato ISO 8601.") from error


def fragmentos_recortados(origen, destino, poligono):
    a = (origen.lon, origen.lat)
    b = (destino.lon, destino.lat)
    if a == b:
        return []
    interseccion = LineString((a, b)).intersection(poligono)
    pendientes = [interseccion]
    lineas = []
    while pendientes:
        actual = pendientes.pop()
        if actual.geom_type == "LineString":
            coordenadas = list(actual.coords)
            if len(coordenadas) >= 2 and actual.length > 0:
                lineas.append(coordenadas)
        elif hasattr(actual, "geoms"):
            pendientes.extend(actual.geoms)
    dx = b[0] - a[0]
    dy = b[1] - a[1]
    divisor = dx * dx + dy * dy

    def posicion(punto):
        return ((punto[0] - a[0]) * dx + (punto[1] - a[1]) * dy) / divisor

    for linea in lineas:
        if posicion(linea[0]) > posicion(linea[-1]):
            linea.reverse()
    return sorted(lineas, key=lambda linea: posicion(linea[0]))


def extremo(coordenada, origen, destino, identificador):
    lon, lat = coordenada
    if abs(lon - origen.lon) <= 1e-12 and abs(lat - origen.lat) <= 1e-12:
        return origen
    if abs(lon - destino.lon) <= 1e-12 and abs(lat - destino.lat) <= 1e-12:
        return destino
    return Vertice(identificador, lon, lat)


def preparar_red(vias, poligono, fecha):
    vias = sorted(vias, key=lambda via: via.identificador)
    if len({via.identificador for via in vias}) != len(vias):
        raise ValueError("El extracto contiene vías OSM con identificadores duplicados.")
    compartidos = Counter()
    for via in vias:
        compartidos.update(n.identificador for n in via.nodos)
    nodos = {}
    tramos = []

    for via in vias:
        recorrido = []
        inicio = None
        fin = None
        numero = 0
        sentido = sentido_circulacion(via.etiquetas)
        tipo = TIPOS_VIA[via.etiquetas["highway"].strip().lower()]
        nombre = nombre_via(via.etiquetas)

        def terminar():
            nonlocal recorrido, inicio, fin, numero
            if inicio is None or fin is None or len(recorrido) < 2:
                recorrido = []
                inicio = None
                fin = None
                return
            geometria = [[round(x, 10), round(y, 10)] for x, y in recorrido]
            geometria[0] = [round(inicio.lon, 10), round(inicio.lat, 10)]
            geometria[-1] = [round(fin.lon, 10), round(fin.lat, 10)]
            longitud = longitud_recorrido_m(geometria)
            if longitud <= 0:
                recorrido = []
                inicio = None
                fin = None
                return
            longitud = math.ceil(longitud * 1000) / 1000
            nodos[inicio.identificador] = {"id": inicio.identificador, "lon": geometria[0][0], "lat": geometria[0][1]}
            nodos[fin.identificador] = {"id": fin.identificador, "lon": geometria[-1][0], "lat": geometria[-1][1]}
            numero += 1
            base = f"osm:{via.identificador}:{numero}"
            if sentido in {"ida", "ambos"}:
                tramos.append({"id": base + ":ida", "desde": inicio.identificador, "hasta": fin.identificador,
                               "nombre": nombre, "tipo_via": tipo, "longitud_m": longitud, "geometria": geometria})
            if sentido in {"vuelta", "ambos"}:
                tramos.append({"id": base + ":vuelta", "desde": fin.identificador, "hasta": inicio.identificador,
                               "nombre": nombre, "tipo_via": tipo, "longitud_m": longitud, "geometria": geometria[::-1]})
            recorrido = []
            inicio = None
            fin = None

        for indice, (a, b) in enumerate(zip(via.nodos, via.nodos[1:])):
            pedazos = fragmentos_recortados(a, b, poligono)
            if not pedazos:
                terminar()
                continue
            for indice_pedazo, puntos in enumerate(pedazos):
                primero = extremo(puntos[0], a, b, f"corte:{via.identificador}:{indice}:{indice_pedazo}:i")
                ultimo = extremo(puntos[-1], a, b, f"corte:{via.identificador}:{indice}:{indice_pedazo}:f")
                if fin is None or fin.identificador != primero.identificador:
                    terminar()
                    inicio = primero
                    recorrido = list(puntos)
                else:
                    recorrido.extend(puntos[1:])
                fin = ultimo
                if compartidos[ultimo.identificador] > 1:
                    terminar()
        terminar()

    return {"version": 1, "fuente": "OpenStreetMap", "fecha_extracto": fecha,
            "nodos": sorted(nodos.values(), key=lambda nodo: nodo["id"]), "tramos": tramos}


def generar(ruta_osm, ruta_cobertura, ruta_salida, fecha_extracto=None):
    ruta_osm = Path(ruta_osm)
    ruta_salida = Path(ruta_salida)
    if not ruta_osm.is_file():
        raise ValueError(f"No existe el extracto OSM: {ruta_osm}")
    if ruta_osm.resolve() == ruta_salida.resolve():
        raise ValueError("La salida debe ser distinta del extracto de entrada.")
    poligono = cargar_poligono(ruta_cobertura)
    if ruta_osm.name.lower().endswith(".osm.pbf") or ruta_osm.suffix.lower() == ".pbf":
        vias, fecha_cabecera = cargar_pbf(ruta_osm, poligono.bounds)
    elif ruta_osm.name.lower().endswith((".osm", ".osm.xml", ".xml", ".osm.gz", ".osm.bz2", ".xml.gz", ".xml.bz2")):
        vias, fecha_cabecera = cargar_xml(ruta_osm, poligono.bounds)
    else:
        raise ValueError("Formato OSM no admitido. Use .osm.pbf, .osm, .osm.xml, .osm.gz o .osm.bz2.")
    fecha = fecha_iso(fecha_extracto or fecha_cabecera)
    resultado = preparar_red(vias, poligono, fecha)
    if not resultado["tramos"]:
        raise ValueError("No se encontraron vías vehiculares en el polígono; no se reemplazó la salida.")
    ruta_salida.parent.mkdir(parents=True, exist_ok=True)
    temporal = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=ruta_salida.parent,
                                         prefix=".red_vial_", suffix=".tmp", delete=False) as archivo:
            temporal = Path(archivo.name)
            json.dump(resultado, archivo, ensure_ascii=False, separators=(",", ":"))
            archivo.write("\n")
        from validar_red import validar_red
        validar_red(temporal, ruta_cobertura)
        os.replace(temporal, ruta_salida)
    finally:
        if temporal is not None and temporal.exists():
            temporal.unlink()
    return resultado


def main():
    parser = argparse.ArgumentParser(description="Prepara la red vial vehicular de Tarija a partir de OpenStreetMap.")
    parser.add_argument("--osm", required=True, type=Path, help="Extracto .osm.pbf o .osm/.osm.gz/.osm.bz2")
    parser.add_argument("--cobertura", required=True, type=Path, help="GeoJSON de la mancha urbana de Tarija")
    parser.add_argument("--salida", required=True, type=Path, help="Archivo JSON para la carga futura en C++")
    parser.add_argument("--fecha-extracto", help="Fecha ISO AAAA-MM-DD si el extracto no trae metadatos")
    args = parser.parse_args()
    try:
        resultado = generar(args.osm, args.cobertura, args.salida, args.fecha_extracto)
    except (OSError, ValueError, RuntimeError, ET.ParseError) as error:
        parser.exit(1, f"Error: {error}\n")
    print(f"Red preparada: {len(resultado['nodos'])} nodos, {len(resultado['tramos'])} tramos, "
          f"extracto {resultado['fecha_extracto']}. Archivo: {args.salida}")


if __name__ == "__main__":
    main()
