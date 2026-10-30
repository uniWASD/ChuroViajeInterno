import argparse
from collections import defaultdict
import json
import math
from pathlib import Path
import re
import unicodedata
import xml.etree.ElementTree as ET

from shapely.geometry import LineString, Point

from preparar_red import cargar_poligono, es_vehicular, fecha_iso, sentido_circulacion
from validar_red import validar_red


CENTRO_TARIJA = (-64.73425, -21.53390)
RADIO_CENTRO_M = 1500
PEATONALES = {"pasaje baldiviezo", "pasaje baldivieso", "pje baldiviezo", "pje baldivieso"}
SENTIDO_UNICO = {"calle general trigo", "calle gral trigo", "general trigo", "gral trigo"}
NOMBRES_CASOS = PEATONALES | SENTIDO_UNICO
TIPOS_PEATONALES = {"pedestrian", "footway", "steps", "path"}
PATRON_TRAMO = re.compile(r"^osm:(\d+):(\d+):(ida|vuelta)$")


def nombre_normalizado(nombre):
    texto = unicodedata.normalize("NFKD", nombre).casefold()
    texto = "".join(caracter for caracter in texto if not unicodedata.combining(caracter))
    return " ".join(re.sub(r"[^a-z0-9]+", " ", texto).split())


def cerca_del_centro(coordenadas):
    escala_lon = 111320 * math.cos(math.radians(CENTRO_TARIJA[1]))
    puntos = [((lon - CENTRO_TARIJA[0]) * escala_lon, (lat - CENTRO_TARIJA[1]) * 111195)
              for lon, lat in coordenadas]
    return LineString(puntos).distance(Point(0, 0)) <= RADIO_CENTRO_M


def registrar_via(vias_peatonales, vias_unicas, identificador, etiquetas, coordenadas, poligono):
    nombre = nombre_normalizado(etiquetas.get("name", ""))
    if nombre not in NOMBRES_CASOS:
        return
    if len(coordenadas) < 2:
        return
    geometria = LineString(coordenadas)
    if geometria.intersection(poligono).length <= 0 or not cerca_del_centro(coordenadas):
        return
    registro = (int(identificador), etiquetas)
    if nombre in PEATONALES:
        vias_peatonales.append(registro)
    if nombre in SENTIDO_UNICO:
        vias_unicas.append(registro)


def leer_vias_reales(ruta_osm, poligono):
    if str(ruta_osm).lower().endswith((".osm", ".osm.xml", ".xml")):
        raiz = ET.parse(ruta_osm).getroot()
        nodos = {int(nodo.attrib["id"]): (float(nodo.attrib["lon"]), float(nodo.attrib["lat"]))
                 for nodo in raiz.findall("node")}
        peatonales = []
        unicos = []
        for via in raiz.findall("way"):
            etiquetas = {etiqueta.attrib["k"]: etiqueta.attrib["v"] for etiqueta in via.findall("tag")}
            if nombre_normalizado(etiquetas.get("name", "")) not in NOMBRES_CASOS:
                continue
            try:
                coordenadas = [nodos[int(nodo.attrib["ref"])] for nodo in via.findall("nd")]
            except KeyError as error:
                raise ValueError(f"Falta el nodo {error.args[0]} en la vía OSM {via.attrib['id']}.") from error
            registrar_via(peatonales, unicos, via.attrib["id"], etiquetas, coordenadas, poligono)
        return peatonales, unicos

    try:
        import osmium
    except ImportError as error:
        raise RuntimeError("Falta pyosmium. Instale las dependencias de requirements.txt.") from error

    class Lector(osmium.SimpleHandler):
        def __init__(self):
            super().__init__()
            self.peatonales = []
            self.unicos = []

        def way(self, via):
            etiquetas = {etiqueta.k: etiqueta.v for etiqueta in via.tags}
            if nombre_normalizado(etiquetas.get("name", "")) not in NOMBRES_CASOS:
                return
            try:
                coordenadas = [(float(nodo.location.lon), float(nodo.location.lat)) for nodo in via.nodes]
            except Exception as error:
                raise ValueError(f"La vía OSM {via.id} no tiene coordenadas completas.") from error
            registrar_via(self.peatonales, self.unicos, via.id, etiquetas, coordenadas, poligono)

    lector = Lector()
    lector.apply_file(str(ruta_osm), locations=True)
    return lector.peatonales, lector.unicos


def obtener_fecha_pbf(ruta_osm):
    if not str(ruta_osm).lower().endswith(".pbf"):
        return None
    import osmium
    lector = osmium.io.Reader(str(ruta_osm))
    try:
        cabecera = lector.header()
        fecha = cabecera.get("osmosis_replication_timestamp") or cabecera.get("timestamp")
        return fecha_iso(fecha) if fecha else None
    finally:
        lector.close()


def tramos_por_via(red):
    tramos = defaultdict(list)
    for tramo in red["tramos"]:
        coincidencia = PATRON_TRAMO.fullmatch(tramo["id"])
        if not coincidencia:
            raise ValueError(f"ID de tramo no reconocido: {tramo['id']}")
        osm_id, fragmento, sentido = coincidencia.groups()
        tramos[int(osm_id)].append((int(fragmento), sentido))
    return tramos


def verificar_casos(ruta_osm, ruta_red, ruta_cobertura):
    ruta_osm = Path(ruta_osm)
    if not ruta_osm.is_file():
        raise ValueError(f"No existe el extracto OSM: {ruta_osm}")
    validar_red(ruta_red, ruta_cobertura)
    with open(ruta_red, encoding="utf-8") as archivo:
        red = json.load(archivo)
    fecha_osm = obtener_fecha_pbf(ruta_osm)
    if fecha_osm and red["fecha_extracto"] != fecha_osm:
        raise ValueError(f"El extracto OSM es del {fecha_osm} y el JSON es del {red['fecha_extracto']}.")
    peatonales, sentidos_unicos = leer_vias_reales(ruta_osm, cargar_poligono(ruta_cobertura))
    registrados = tramos_por_via(red)
    if not peatonales:
        raise ValueError("CP-45: Pasaje Baldiviezo no aparece en el extracto dentro del centro de Tarija.")
    for identificador, etiquetas in peatonales:
        if etiquetas.get("highway", "").lower() not in TIPOS_PEATONALES or es_vehicular(etiquetas):
            raise ValueError(f"CP-45: la vía OSM {identificador} no está clasificada como peatonal.")
        if registrados.get(identificador):
            raise ValueError(f"CP-45: la vía peatonal OSM {identificador} aparece en la red vehicular.")
    unicos_validos = [(identificador, etiquetas) for identificador, etiquetas in sentidos_unicos
                     if es_vehicular(etiquetas) and sentido_circulacion(etiquetas) in {"ida", "vuelta"}]
    if not unicos_validos:
        raise ValueError("CP-46: no hay tramos OSM vehiculares de sentido único de Calle General Trigo en el centro.")
    cantidad = 0
    for identificador, etiquetas in unicos_validos:
        sentido_esperado = sentido_circulacion(etiquetas)
        tramos = registrados.get(identificador, [])
        if not tramos:
            raise ValueError(f"CP-46: falta la vía de sentido único OSM {identificador} en la red.")
        sentidos_por_fragmento = defaultdict(list)
        for fragmento, sentido in tramos:
            sentidos_por_fragmento[fragmento].append(sentido)
        for fragmento, sentidos in sentidos_por_fragmento.items():
            if sentidos != [sentido_esperado]:
                raise ValueError(f"CP-46: sentido incorrecto o duplicado en OSM {identificador}, fragmento {fragmento}.")
        cantidad += len(tramos)
    return {
        "fecha_extracto": red["fecha_extracto"],
        "cp45": {
            "calle": "Pasaje Baldiviezo",
            "vias_osm": sorted({identificador for identificador, _ in peatonales}),
            "tramos_en_red": 0,
        },
        "cp46": {
            "calle": "Calle General Trigo",
            "vias_osm": sorted({identificador for identificador, _ in unicos_validos}),
            "tramos_unidireccionales": cantidad,
            "tramos_en_sentido_contrario": 0,
        },
    }


def main():
    parser = argparse.ArgumentParser(description="Comprueba CP-45 y CP-46 con calles reales del centro de Tarija.")
    parser.add_argument("--osm", type=Path, required=True)
    parser.add_argument("--red", type=Path, required=True)
    parser.add_argument("--cobertura", type=Path, required=True)
    argumentos = parser.parse_args()
    try:
        resultado = verificar_casos(argumentos.osm, argumentos.red, argumentos.cobertura)
    except (ValueError, OSError, RuntimeError, KeyError, TypeError) as error:
        parser.exit(1, f"Verificación pendiente o fallida: {error}\n")
    print(f"Extracto: {resultado['fecha_extracto']}")
    print(f"CP-45 OK: {resultado['cp45']['calle']}; vías OSM: {resultado['cp45']['vias_osm']}; "
          "0 tramos vehiculares.")
    print(f"CP-46 OK: {resultado['cp46']['calle']}; vías OSM: {resultado['cp46']['vias_osm']}; "
          f"{resultado['cp46']['tramos_unidireccionales']} tramos dirigidos, 0 contrarios.")


if __name__ == "__main__":
    main()
