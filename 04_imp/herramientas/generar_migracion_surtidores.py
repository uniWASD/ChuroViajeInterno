import argparse
import csv
import json
import re
import unicodedata
from pathlib import Path

NOMBRES_PROTOTIPO = (
    "Las Vegas", "Don Daniel", "Tacuarandi", "Campesino", "YPFB Morros Blancos",
    "Agrupa", "El Portillo", "Moto Mendez", "Ex Terminal", "Sointa",
    "Panamericano", "San Jorge", "San Geronimo", "El Molle", "Surtidor Tarija",
    "San Martin", "Pimentel", "Villanueva", "YPFB Parada Chaco",
    "Domingo Savio", "Coliseo Universitario"
)


def normalizar(texto):
    base = unicodedata.normalize("NFD", texto)
    base = "".join(letra for letra in base if not unicodedata.combining(letra))
    clave = re.sub(r"[^a-z0-9 ]", " ", base.lower())
    clave = " ".join(clave.split())
    if not clave:
        raise ValueError("Un alias está vacío")
    return clave


def entre_segmentos(a, b, p):
    cruz = (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
    return abs(cruz) < 1e-12 and min(a[0], b[0]) - 1e-12 <= p[0] <= max(a[0], b[0]) + 1e-12 and min(a[1], b[1]) - 1e-12 <= p[1] <= max(a[1], b[1]) + 1e-12


def posicion_anillo(anillo, punto):
    dentro = False
    for i in range(len(anillo) - 1):
        a, b = anillo[i], anillo[i + 1]
        if entre_segmentos(a, b, punto):
            return "BORDE"
        if (a[1] > punto[1]) != (b[1] > punto[1]):
            interseccion = a[0] + (b[0] - a[0]) * (punto[1] - a[1]) / (b[1] - a[1])
            if punto[0] < interseccion:
                dentro = not dentro
    return "DENTRO" if dentro else "FUERA"


def dentro_poligono(anillos, punto):
    exterior = posicion_anillo(anillos[0], punto)
    if exterior == "FUERA":
        return False
    if exterior == "BORDE":
        return True
    for hueco in anillos[1:]:
        situacion = posicion_anillo(hueco, punto)
        if situacion == "BORDE":
            return True
        if situacion == "DENTRO":
            return False
    return True


def dentro_geojson(documento, punto):
    elementos = documento.get("features", [documento])
    for elemento in elementos:
        geometria = elemento.get("geometry", elemento)
        tipo = geometria.get("type")
        coordenadas = geometria.get("coordinates", [])
        poligonos = [coordenadas] if tipo == "Polygon" else coordenadas if tipo == "MultiPolygon" else []
        if any(dentro_poligono(poligono, punto) for poligono in poligonos):
            return True
    return False


def literal(texto):
    return "'" + texto.replace("'", "''") + "'"


def generar(archivo_csv, archivo_geojson, destino):
    cobertura = json.loads(archivo_geojson.read_text(encoding="utf-8"))
    if cobertura.get("estado_validacion") != "APROBADO":
        raise ValueError("La cobertura debe estar validada antes de generar la migración definitiva")
    if not all(feature.get("properties", {}).get("revision_equipo") == "APROBADO"
               for feature in cobertura.get("features", [])):
        raise ValueError("Falta la revisión cartográfica del equipo")
    with archivo_csv.open(encoding="utf-8-sig", newline="") as entrada:
        registros = list(csv.DictReader(entrada))
    encontrados = {fila["nombre"] for fila in registros}
    if len(registros) != len(encontrados) or not set(NOMBRES_PROTOTIPO).issubset(encontrados):
        raise ValueError("La revisión debe incluir las 21 entradas del prototipo sin duplicados; se permiten adicionales aprobadas")
    seleccionados = []
    aliases = {}
    for fila in registros:
        nombre = fila["nombre"]
        if fila["revision_franz"].upper() != "SI" or fila["aprobado_equipo"].upper() != "SI":
            raise ValueError(f"Falta revisión de Franz/equipo para {nombre}")
        decision = fila["decision"].upper()
        if decision == "EXCLUIR":
            if not fila["motivo_exclusion"].strip():
                raise ValueError(f"Debe justificarse la exclusión de {nombre}")
            continue
        if decision != "INCLUIR":
            raise ValueError(f"No se ha decidido sobre {nombre}")
        try:
            longitud = float(fila["longitud"])
            latitud = float(fila["latitud"])
        except ValueError as exc:
            raise ValueError(f"No hay coordenadas verificables para {nombre}") from exc
        if not (-180 <= longitud <= 180 and -90 <= latitud <= 90):
            raise ValueError(f"Coordenadas fuera de rango para {nombre}")
        if not fila["fuente_ubicacion"].strip() or fila["fuente_ubicacion"].startswith("GUIA_SIN_VERIFICAR"):
            raise ValueError(f"Falta fuente geográfica confiable para {nombre}")
        if not dentro_geojson(cobertura, (longitud, latitud)):
            raise ValueError(f"El surtidor {nombre} queda fuera de la cobertura validada")
        lista_aliases = [nombre] + [alias.strip() for alias in fila["aliases"].split("|") if alias.strip()]
        for alias in lista_aliases:
            clave = normalizar(alias)
            anterior = aliases.get(clave)
            if anterior is not None and anterior != nombre:
                raise ValueError(f"Alias ambiguo {clave}: {anterior} y {nombre}")
            aliases[clave] = nombre
        seleccionados.append((nombre, longitud, latitud))
    if not seleccionados:
        raise ValueError("No hay surtidores aprobados para publicar")
    lineas = []
    for nombre, longitud, latitud in seleccionados:
        lineas.append(
            f"INSERT INTO surtidor (nombre, ubicacion) VALUES ({literal(nombre)}, "
            f"ST_SetSRID(ST_MakePoint({longitud:.8f}, {latitud:.8f}), 4326)::geography);"
        )
    for alias, nombre in sorted(aliases.items()):
        lineas.append(
            f"INSERT INTO surtidor_alias (surtidor_id, alias) "
            f"SELECT id, {literal(alias)} FROM surtidor WHERE nombre = {literal(nombre)};"
        )
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_text("\n".join(lineas) + "\n", encoding="utf-8")
    return len(seleccionados), len(aliases)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", type=Path, required=True)
    parser.add_argument("--geojson", type=Path, required=True)
    parser.add_argument("--salida", type=Path, required=True)
    args = parser.parse_args()
    cantidad, cantidad_aliases = generar(args.csv, args.geojson, args.salida)
    print(f"Migración generada: {cantidad} surtidores y {cantidad_aliases} alias")


if __name__ == "__main__":
    main()
