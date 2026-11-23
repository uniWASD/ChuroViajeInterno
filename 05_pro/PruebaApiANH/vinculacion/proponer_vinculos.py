"""T-118 (#113): proponer los vínculos entre las estaciones de ANH y los surtidores del catálogo.

Dos pasos con revisión documentada (RN-09.10):

1. Proponer. Lee el CSV de `anh_tarija.py`, el catálogo V2 y la mancha urbana.
2. Generar la migración. Solo admite filas `revisado=si`, comentario de evidencia, coordenadas contrastadas
   contra la extracción y distancia efectiva <= P-31 (50 m). Si la coordenada cruda de ANH es errónea, se conserva
   en `latitud_anh_original`/`longitud_anh_original`, y la coordenada corregida requiere `coordenadas_corregidas=si`
   y `evidencia_georreferenciacion` verificable. No se altera la respuesta original de ANH.

Solo usa la biblioteca estándar de Python 3.10 o posterior. No llama a ANH: los datos salen del CSV del prototipo.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import math
import re
import sys
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[3]
CATALOGO_V2 = RAIZ / "04_imp/backend_java/src/main/resources/db/migration/V2__surtidores_tarija.sql"
MANCHA = RAIZ / "04_imp/datos/mancha_urbana_tarija.geojson"
MIGRACIONES = RAIZ / "04_imp/backend_java/src/main/resources/db/migration"

# Distancia para propuestas exploratorias; NO autoriza a vincular (P-31).
RADIO_BUSQUEDA_M = 1000
# P-31 aceptado por el solicitante el 23/11/2026; debe registrarse en el equipo T-118.
P31_MAXIMA_M = 50.0
# Sin coincidencia de nombre, un surtidor más cerca que esto se propone para revisar.
CERCA_SIN_NOMBRE_M = 300

# Palabras que no ayudan a reconocer una estación por su nombre.
PALABRAS_VACIAS = {
    "estacion", "estaciones", "servicio", "servicios", "ee", "ss", "eess", "es", "srl", "s", "r", "l", "ltda",
    "sa", "sociedad", "de", "del", "la", "las", "los", "el", "y", "surtidor", "gasolinera", "combustibles",
    "combustible", "tarija",
}


@dataclass
class Estacion:
    anh_id: int
    nombre: str
    latitud: float | None
    longitud: float | None


@dataclass
class Surtidor:
    nombre: str
    latitud: float
    longitud: float
    alias: set[str] = field(default_factory=set)
    nota_v2: str = ""


def normalizar(texto: str | None) -> str:
    """Minúsculas, sin tildes ni signos, con un espacio entre palabras."""
    if not texto:
        return ""
    sin_tildes = "".join(c for c in unicodedata.normalize("NFD", texto) if unicodedata.category(c) != "Mn")
    return " ".join(re.sub(r"[^a-z0-9]+", " ", sin_tildes.lower()).split())


def palabras_utiles(texto: str) -> list[str]:
    return [p for p in normalizar(texto).split() if p not in PALABRAS_VACIAS]


def distancia_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Haversine sobre una esfera de 6 371 km: alcanza para comparar distancias de cientos de metros."""
    r = 6_371_000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


# ---------------------------------------------------------------- lectura

def _numero(valor: str | None) -> float | None:
    if valor is None or str(valor).strip() == "":
        return None
    try:
        n = float(str(valor).replace(",", "."))
    except ValueError:
        return None
    return n if math.isfinite(n) else None


def _abrir_csv(ruta: Path) -> list[dict[str, str]]:
    """Lee un CSV en UTF-8 (con o sin BOM) o, si Excel lo guardó en Windows, en cp1252. Separador , o ;."""
    for codificacion in ("utf-8-sig", "cp1252"):
        try:
            texto = ruta.read_text(encoding=codificacion)
            break
        except UnicodeDecodeError:
            continue
    else:
        raise SystemExit(f"No se pudo leer {ruta}: codificación desconocida")
    primera = texto.splitlines()[0] if texto else ""
    separador = ";" if primera.count(";") > primera.count(",") else ","
    return list(csv.DictReader(texto.splitlines(), delimiter=separador))


def leer_estaciones(ruta: Path) -> tuple[list[Estacion], list[str]]:
    """Una estación por `estacion_id`: el CSV del prototipo trae una fila por estación y combustible."""
    avisos: list[str] = []
    estaciones: dict[int, Estacion] = {}
    for fila in _abrir_csv(ruta):
        crudo = (fila.get("estacion_id") or "").strip()
        if not re.fullmatch(r"\d+", crudo):
            avisos.append(f"Fila sin estacion_id entero: {crudo!r}; se omite")
            continue
        anh_id = int(crudo)
        if anh_id <= 0:
            avisos.append(f"Fila con estacion_id no positivo: {crudo!r}; se omite")
            continue
        latitud, longitud = _numero(fila.get("latitud")), _numero(fila.get("longitud"))
        if (latitud is None or longitud is None or not -90 <= latitud <= 90
                or not -180 <= longitud <= 180 or (latitud == 0 and longitud == 0)):
            if latitud is not None or longitud is not None:
                avisos.append(f"La estación {anh_id} trae coordenadas incompletas o fuera de rango; queda para revisión")
            latitud, longitud = None, None
        nueva = Estacion(anh_id, (fila.get("nombre_estacion") or "").strip(), latitud, longitud)
        vieja = estaciones.get(anh_id)
        if vieja is None:
            estaciones[anh_id] = nueva
            continue
        if vieja.latitud is None and nueva.latitud is not None:
            vieja.latitud, vieja.longitud = nueva.latitud, nueva.longitud
        elif (None not in (vieja.latitud, nueva.latitud)
              and distancia_m(vieja.latitud, vieja.longitud, nueva.latitud, nueva.longitud) > 1):
            avisos.append(f"La estación {anh_id} llega con dos ubicaciones distintas; se usa la primera")
        if not vieja.nombre and nueva.nombre:
            vieja.nombre = nueva.nombre
    return sorted(estaciones.values(), key=lambda e: e.anh_id), avisos


_INSERT = re.compile(
    r"INSERT INTO surtidor \(nombre, ubicacion\) VALUES \('((?:[^']|'')+)', "
    r"ST_SetSRID\(ST_MakePoint\((-?\d+(?:\.\d+)?), (-?\d+(?:\.\d+)?)\)")
_ALIAS = re.compile(r"SELECT id, '((?:[^']|'')+)' FROM surtidor WHERE nombre = '((?:[^']|'')+)'")
_ID_EN_NOTA = re.compile(r"(?:ANH|API)[^\n]*?\bid ?(\d{3,6})", re.IGNORECASE)


def leer_catalogo(ruta: Path = CATALOGO_V2) -> list[Surtidor]:
    """Los surtidores, sus alias y, si los comentarios de V2 nombran un id de ANH, esa nota para la revisión."""
    surtidores: dict[str, Surtidor] = {}
    notas: list[str] = []
    for linea in ruta.read_text(encoding="utf-8").splitlines():
        texto = linea.strip()
        if texto.startswith("--"):
            notas.append(texto[2:].strip())
            continue
        insert = _INSERT.search(texto)
        if insert:
            nombre = insert.group(1).replace("''", "'")
            ids = sorted({m.group(1) for nota in notas for m in _ID_EN_NOTA.finditer(nota)})
            surtidores[nombre] = Surtidor(nombre, float(insert.group(3)), float(insert.group(2)),
                                          {normalizar(nombre)},
                                          "V2 nombra ANH " + ", ".join(ids) if ids else "")
        alias = _ALIAS.search(texto)
        if alias and alias.group(2).replace("''", "'") in surtidores:
            surtidores[alias.group(2).replace("''", "'")].alias.add(normalizar(alias.group(1)))
        if texto:
            notas = []
    return list(surtidores.values())


def leer_mancha(ruta: Path = MANCHA) -> list[list[list[tuple[float, float]]]]:
    """Polígonos de la mancha urbana: cada uno es [anillo exterior, huecos...], con puntos (lon, lat)."""
    documento = json.loads(ruta.read_text(encoding="utf-8"))
    geometrias = []
    if documento.get("type") == "FeatureCollection":
        geometrias = [f["geometry"] for f in documento["features"]]
    elif documento.get("type") == "Feature":
        geometrias = [documento["geometry"]]
    else:
        geometrias = [documento]
    poligonos = []
    for g in geometrias:
        if g["type"] == "Polygon":
            poligonos.append([[tuple(p[:2]) for p in anillo] for anillo in g["coordinates"]])
        elif g["type"] == "MultiPolygon":
            for poligono in g["coordinates"]:
                poligonos.append([[tuple(p[:2]) for p in anillo] for anillo in poligono])
        else:
            raise SystemExit(f"Geometría no admitida en la mancha urbana: {g['type']}")
    return poligonos


def _en_anillo(lon: float, lat: float, anillo: list[tuple[float, float]]) -> bool:
    dentro = False
    j = len(anillo) - 1
    for i in range(len(anillo)):
        xi, yi = anillo[i]
        xj, yj = anillo[j]
        if (yi > lat) != (yj > lat) and lon < (xj - xi) * (lat - yi) / (yj - yi) + xi:
            dentro = not dentro
        j = i
    return dentro


def en_mancha(lat: float, lon: float, poligonos) -> bool:
    return any(_en_anillo(lon, lat, p[0]) and not any(_en_anillo(lon, lat, h) for h in p[1:]) for p in poligonos)


# ---------------------------------------------------------------- propuesta

def coincide_nombre(estacion: Estacion, surtidor: Surtidor) -> bool:
    """Algún alias del surtidor aparece entero en el nombre de ANH, o todas las palabras útiles de ANH en un alias."""
    anh = f" {normalizar(estacion.nombre)} "
    utiles = palabras_utiles(estacion.nombre)
    for alias in surtidor.alias:
        if alias and f" {alias} " in anh:
            return True
        if utiles and all(p in alias.split() for p in utiles):
            return True
    return False


@dataclass
class Par:
    estacion: Estacion
    surtidor: Surtidor | None
    distancia: float | None
    por_nombre: bool
    propuesta: str
    motivo: str


def proponer(estaciones: list[Estacion], catalogo: list[Surtidor], mancha) -> tuple[list[Par], list[Estacion]]:
    """Los pares de las estaciones dentro de la mancha y la lista de las que quedan fuera (RN-09.8)."""
    pares: list[Par] = []
    fuera: list[Estacion] = []
    for e in estaciones:
        if e.latitud is None or e.longitud is None:
            pares.append(Par(e, None, None, False, "revisar", "ANH no da la ubicación"))
            continue
        if not en_mancha(e.latitud, e.longitud, mancha):
            fuera.append(e)
            continue
        cercanos = sorted(((distancia_m(e.latitud, e.longitud, s.latitud, s.longitud), s) for s in catalogo),
                          key=lambda x: x[0])
        por_nombre = [(d, s) for d, s in cercanos if d <= RADIO_BUSQUEDA_M and coincide_nombre(e, s)]
        if por_nombre:
            d, s = por_nombre[0]
            if d <= P31_MAXIMA_M:
                pares.append(Par(e, s, d, True, "vincular", "coincide el nombre y respeta P-31"))
            else:
                pares.append(Par(e, s, d, True, "revisar",
                                 f"coincide el nombre, pero está a {d:.3f} m y excede P-31 ({P31_MAXIMA_M:g} m); "
                                 "resolver coordenadas antes de aprobar"))
        elif cercanos and cercanos[0][0] <= CERCA_SIN_NOMBRE_M:
            d, s = cercanos[0]
            pares.append(Par(e, s, d, False, "revisar", "cerca, pero el nombre no coincide"))
        else:
            d, s = cercanos[0] if cercanos else (None, None)
            pares.append(Par(e, None, d, False, "candidata",
                             f"sin surtidor del catálogo cerca (el más cercano, {s.nombre}, a {d:.0f} m)"
                             if s else "catálogo vacío"))
    # Un surtidor propuesto para dos estaciones no puede quedar así (RN-09.10): las dos se revisan.
    usados: dict[str, list[Par]] = {}
    for p in pares:
        if p.surtidor is not None:
            usados.setdefault(p.surtidor.nombre, []).append(p)
    for repetidos in usados.values():
        if len(repetidos) > 1:
            for p in repetidos:
                p.propuesta = "revisar"
                p.motivo += f"; {p.surtidor.nombre} también se propone para otra estación"
    return pares, fuera


CAMPOS_REVISION = [
    "anh_id", "nombre_anh", "latitud_anh", "longitud_anh", "surtidor", "distancia_m",
    "coincide_nombre", "propuesta", "motivo", "nota_v2", "revisado", "comentario",
    "coordenadas_corregidas", "latitud_anh_original", "longitud_anh_original",
    "distancia_original_m", "evidencia_georreferenciacion",
]


def escribir_propuesta(pares: list[Par], fuera: list[Estacion], catalogo: list[Surtidor], carpeta: Path) -> None:
    carpeta.mkdir(parents=True, exist_ok=True)
    with (carpeta / "pares_propuestos.csv").open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, CAMPOS_REVISION, delimiter=";")
        w.writeheader()
        orden = {"vincular": 0, "revisar": 1, "candidata": 2}
        for p in sorted(pares, key=lambda p: (orden[p.propuesta], p.estacion.anh_id)):
            w.writerow({
                "anh_id": p.estacion.anh_id, "nombre_anh": p.estacion.nombre,
                "latitud_anh": p.estacion.latitud, "longitud_anh": p.estacion.longitud,
                "surtidor": p.surtidor.nombre if p.surtidor else "",
                "distancia_m": f"{p.distancia:.6f}" if p.distancia is not None else "",
                "coincide_nombre": "si" if p.por_nombre else "no",
                "propuesta": p.propuesta, "motivo": p.motivo,
                "nota_v2": p.surtidor.nota_v2 if p.surtidor else "",
                "revisado": "", "comentario": "", "coordenadas_corregidas": "no",
                "latitud_anh_original": "", "longitud_anh_original": "",
                "distancia_original_m": "", "evidencia_georreferenciacion": "",
            })
    with (carpeta / "fuera_de_la_mancha.csv").open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["anh_id", "nombre_anh"])
        for e in fuera:
            w.writerow([e.anh_id, e.nombre])
    propuestos = {p.surtidor.nombre for p in pares if p.surtidor}
    with (carpeta / "surtidores_sin_estacion.csv").open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["surtidor", "nota_v2"])
        for s in sorted(catalogo, key=lambda s: s.nombre):
            if s.nombre not in propuestos:
                w.writerow([s.nombre, s.nota_v2])


# ---------------------------------------------------------------- migración

# Solo «si» / «sí» indica aprobación. No interpretamos «x» u «ok» como firma humana.
SI = {"si", "sí"}


class ErrorRevision(Exception):
    pass


def pares_confirmados(filas: list[dict[str, str]], catalogo: list[Surtidor]) -> list[tuple[int, str, float | None]]:
    """Valida aprobaciones explícitas, incluida una georreferenciación corregida con evidencia.

    `latitud_anh`/`longitud_anh` son las coordenadas efectivas usadas para P-31.
    Cuando `coordenadas_corregidas=si`, las coordenadas crudas de la API se conservan en
    `latitud_anh_original`/`longitud_anh_original`; se exige evidencia independiente y se
    valida también la distancia original. No se modifica ni se reescribe la respuesta ANH.
    """
    surtidores = {s.nombre: s for s in catalogo}
    confirmados: list[tuple[int, str, float | None]] = []
    errores: list[str] = []
    for n, fila in enumerate(filas, start=2):
        revision = normalizar(fila.get("revisado"))
        if revision not in {"", "no", "si"}:
            errores.append(f"línea {n}: 'revisado' debe ser 'si', 'no' o vacío; se recibió {fila.get('revisado')!r}")
            continue
        if revision != "si":
            continue

        crudo, nombre = (fila.get("anh_id") or "").strip(), (fila.get("surtidor") or "").strip()
        if not re.fullmatch(r"\d+", crudo) or not 0 < int(crudo) <= 2_147_483_647:
            errores.append(f"línea {n}: anh_id {crudo!r} no cabe como entero positivo PostgreSQL")
            continue
        if nombre not in surtidores:
            errores.append(f"línea {n}: el surtidor {nombre!r} no está en el catálogo (V2)")
            continue
        if not (fila.get("nombre_anh") or "").strip():
            errores.append(f"línea {n}: falta el nombre de la estación ANH para comprobar su identidad")
            continue
        if not (fila.get("comentario") or "").strip():
            errores.append(f"línea {n}: falta comentario con evidencia de la revisión (RN-09.10)")
            continue

        lat = _numero(fila.get("latitud_anh"))
        lon = _numero(fila.get("longitud_anh"))
        distancia = _numero(fila.get("distancia_m"))
        if (lat is None or lon is None or not -90 <= lat <= 90 or not -180 <= lon <= 180
                or (lat == 0 and lon == 0) or distancia is None or distancia < 0):
            errores.append(f"línea {n}: las coordenadas/distancia efectivas deben ser numéricas y válidas")
            continue

        s = surtidores[nombre]
        recalculada = distancia_m(lat, lon, s.latitud, s.longitud)
        if abs(recalculada - distancia) > 1:
            errores.append(f"línea {n}: distancia declarada {distancia:.3f} m, recalculada {recalculada:.3f} m; "
                           "CSV alterado o par incorrecto")
            continue

        corregidas_valor = normalizar(fila.get("coordenadas_corregidas"))
        corregidas = corregidas_valor == "si"
        if corregidas:
            lat_original = _numero(fila.get("latitud_anh_original"))
            lon_original = _numero(fila.get("longitud_anh_original"))
            distancia_original = _numero(fila.get("distancia_original_m"))
            evidencia = (fila.get("evidencia_georreferenciacion") or "").strip()
            if (lat_original is None or lon_original is None or not -90 <= lat_original <= 90
                    or not -180 <= lon_original <= 180 or (lat_original == 0 and lon_original == 0)
                    or distancia_original is None or distancia_original < 0 or not evidencia):
                errores.append(f"línea {n}: corrección geográfica sin coordenadas originales, distancia original o evidencia")
                continue
            distancia_original_recalculada = distancia_m(
                lat_original, lon_original, s.latitud, s.longitud
            )
            if abs(distancia_original_recalculada - distancia_original) > 1:
                errores.append(f"línea {n}: distancia original inconsistente para ANH {crudo}")
                continue
        else:
            if corregidas_valor not in {"", "no"}:
                errores.append(f"línea {n}: coordenadas_corregidas debe ser 'si' o 'no'")
                continue
            lat_original = _numero(fila.get("latitud_anh_original"))
            lon_original = _numero(fila.get("longitud_anh_original"))
            if lat_original is not None or lon_original is not None:
                if lat_original is None or lon_original is None or distancia_m(
                        lat_original, lon_original, s.latitud, s.longitud) > 1:
                    errores.append(f"línea {n}: campos de coordenadas originales no coinciden con las efectivas")
                    continue

        if recalculada > P31_MAXIMA_M:
            errores.append(f"línea {n}: distancia efectiva {recalculada:.3f} m excede P-31 "
                           f"({P31_MAXIMA_M:g} m). Resolver georreferenciación antes de migrar")
            continue
        confirmados.append((int(crudo), nombre, distancia))

    ids = [id for id, _, _ in confirmados]
    nombres = [nombre for _, nombre, _ in confirmados]
    if len(ids) != len(set(ids)):
        errores.append("el mismo anh_id está confirmado para dos surtidores (RN-09.10)")
    if len(nombres) != len(set(nombres)):
        errores.append("el mismo surtidor está confirmado para dos estaciones de ANH (RN-09.10)")
    if errores:
        raise ErrorRevision("\n".join(errores))
    return sorted(confirmados, key=lambda c: c[1])


def p31_sugerida(confirmados: list[tuple[int, str, float | None]]) -> int | None:
    """Sugerencia de análisis, nunca P-31 confirmada; 0 m no permite inferir un máximo útil."""
    distancias = [d for _, _, d in confirmados if d is not None]
    if not distancias or max(distancias) <= 0:
        return None
    return int(math.ceil(max(distancias) / 50) * 50)


def validar_con_fuente_anh(filas: list[dict[str, str]], ruta_fuente: Path) -> None:
    """Contrasta ID, nombre y coordenadas originales con la extracción ANH.

    En una corrección geográfica documentada, valida las coordenadas crudas contra la fuente,
    y mantiene la posición corregida en campos separados para comprobar P-31.
    """
    estaciones, avisos = leer_estaciones(ruta_fuente)
    por_id = {e.anh_id: e for e in estaciones}
    catalogo = {s.nombre: s for s in leer_catalogo()}
    errores: list[str] = []
    for n, fila in enumerate(filas, 2):
        if normalizar(fila.get("revisado")) != "si":
            continue
        crudo = (fila.get("anh_id") or "").strip()
        if not re.fullmatch(r"\d+", crudo):
            errores.append(f"línea {n}: identificador ANH inválido")
            continue
        estacion = por_id.get(int(crudo))
        if estacion is None:
            errores.append(f"línea {n}: ANH id {crudo} no existe en el CSV fuente")
            continue
        if normalizar(fila.get("nombre_anh")) != normalizar(estacion.nombre):
            errores.append(f"línea {n}: nombre ANH diferente del CSV fuente para id {crudo}")
            continue

        corregidas = normalizar(fila.get("coordenadas_corregidas")) in SI
        lat_raw = _numero(fila.get("latitud_anh_original")) if corregidas else _numero(fila.get("latitud_anh"))
        lon_raw = _numero(fila.get("longitud_anh_original")) if corregidas else _numero(fila.get("longitud_anh"))
        if lat_raw is None or lon_raw is None or estacion.latitud is None or estacion.longitud is None:
            errores.append(f"línea {n}: no hay coordenadas ANH originales comparables para id {crudo}")
            continue
        if distancia_m(lat_raw, lon_raw, estacion.latitud, estacion.longitud) > 1.0:
            errores.append(f"línea {n}: coordenadas ANH originales de id {crudo} cambiadas (>1 m) respecto de la extracción")
            continue

        if corregidas:
            evidencia = (fila.get("evidencia_georreferenciacion") or "").strip()
            lat_eff, lon_eff = _numero(fila.get("latitud_anh")), _numero(fila.get("longitud_anh"))
            nombre = (fila.get("surtidor") or "").strip()
            s = catalogo.get(nombre)
            distancia_eff = _numero(fila.get("distancia_m"))
            if not evidencia or s is None or lat_eff is None or lon_eff is None or distancia_eff is None:
                errores.append(f"línea {n}: corrección geográfica sin evidencia o catálogo para ANH {crudo}")
                continue
            if not -90 <= lat_eff <= 90 or not -180 <= lon_eff <= 180:
                errores.append(f"línea {n}: coordenada corregida fuera de rango para ANH {crudo}")
                continue
            if abs(distancia_m(lat_eff, lon_eff, s.latitud, s.longitud) - distancia_eff) > 1:
                errores.append(f"línea {n}: distancia efectiva incorrecta tras corrección geográfica para ANH {crudo}")
    if errores:
        raise ErrorRevision("\n".join(errores))


def siguiente_version_disponible(carpeta: Path, version_inicial: int) -> int:
    """Devuelve la primera versión Flyway libre desde version_inicial, aunque tenga otro sufijo."""
    version = version_inicial
    while any(carpeta.glob(f"V{version}__*.sql")):
        version += 1
    return version


def sql_migracion(confirmados, revisado_por: str, fecha: dt.date,
                  evidencias: dict[int, str] | None = None) -> str:
    """Genera SQL transaccional con validación explícita de cada vínculo.

    No presupone que todos los surtidores anteriormente vinculados pertenecen a este lote.
    No reemplaza un anh_id distinto que ya estuviera confirmado.
    """
    evidencias = evidencias or {}
    for anh_id, surtidor, distancia in confirmados:
        if distancia is None or not math.isfinite(distancia) or distancia > P31_MAXIMA_M or distancia < 0:
            raise ErrorRevision(f"El vínculo ANH {anh_id} / {surtidor} no respeta P-31 ({P31_MAXIMA_M:g} m)")
    lineas = [
        "-- T-118 / RN-09.10: vínculos aprobados en revisión técnica documental delegada por el solicitante.",
        f"-- Revisión declarada por {revisado_por} el {fecha.isoformat()}.",
        "-- La revisión es documental/geoespacial; no representa visita presencial ni inspección física.",
        "-- Las coordenadas ANH originales y las correcciones justificadas constan en el CSV y expediente T-118.",
        "-- Flyway ejecuta la migración como una transacción: cualquier error revierte el lote.",
        "DO $vinculos_anh$",
        "DECLARE",
        "    filas_modificadas integer;",
        "BEGIN",
    ]
    for anh_id, surtidor, distancia in confirmados:
        nombre_sql = surtidor.replace("'", "''")
        evidencia = " ".join((evidencias.get(anh_id) or "").replace("--", "—").split())
        if len(evidencia) > 700:
            evidencia = evidencia[:697] + "..."
        lineas += [
            f"    -- {surtidor}: ANH {anh_id}, distancia efectiva {distancia:.3f} m",
        ]
        if evidencia:
            lineas.append(f"    -- Evidencia: {evidencia}")
        lineas += [
            f"    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = '{nombre_sql}') THEN",
            f"        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', '{nombre_sql}';",
            "    END IF;",
            f"    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = '{nombre_sql}' "
            f"AND anh_id IS NOT NULL AND anh_id <> {anh_id}) THEN",
            f"        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', '{nombre_sql}';",
            "    END IF;",
            f"    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = {anh_id} "
            f"AND nombre <> '{nombre_sql}') THEN",
            f"        RAISE EXCEPTION 'El identificador ANH {anh_id} ya pertenece a otro surtidor';",
            "    END IF;",
            f"    UPDATE surtidor SET anh_id = {anh_id} WHERE nombre = '{nombre_sql}' "
            f"AND (anh_id IS NULL OR anh_id = {anh_id});",
            "    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;",
            "    IF filas_modificadas <> 1 THEN",
            f"        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', '{nombre_sql}';",
            "    END IF;",
        ]
    lineas += ["END $vinculos_anh$;", ""]
    return "\n".join(lineas)


# ---------------------------------------------------------------- consola

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    sub = parser.add_subparsers(dest="paso", required=True)
    p1 = sub.add_parser("proponer", help="Propone los pares a partir del CSV del prototipo")
    p1.add_argument("estaciones", type=Path, help="estaciones_tarija.csv que dejó anh_tarija.py")
    p1.add_argument("--salida", type=Path, default=None, help="Carpeta de salida (por defecto, vinculos_<fecha>)")
    p2 = sub.add_parser("migracion", help="Genera la migración con los pares revisados")
    p2.add_argument("revisados", type=Path, help="CSV revisado con decisiones explícitas y evidencia documentada")
    p2.add_argument("--fuente-anh", type=Path, required=True,
                    help="CSV fuente ANH de la extracción; admite snapshot de auditoría con origen documentado")
    p2.add_argument("--revisado-por", required=True, help="Responsable/delegación que realizó la revisión documentada")
    p2.add_argument("--version", type=int, required=True, help="Número de la migración Flyway, por ejemplo 13")
    p2.add_argument("--salida", type=Path, default=MIGRACIONES, help="Carpeta donde escribir la migración")
    args = parser.parse_args(argv)

    catalogo = leer_catalogo()
    if args.paso == "proponer":
        estaciones, avisos = leer_estaciones(args.estaciones)
        pares, fuera = proponer(estaciones, catalogo, leer_mancha())
        carpeta = args.salida or Path(f"vinculos_{dt.datetime.now():%Y%m%d_%H%M%S}")
        escribir_propuesta(pares, fuera, catalogo, carpeta)
        for aviso in avisos:
            print(f"aviso: {aviso}")
        cuenta = {k: sum(1 for p in pares if p.propuesta == k) for k in ("vincular", "revisar", "candidata")}
        print(f"{len(estaciones)} estaciones de ANH: {len(fuera)} fuera de la mancha urbana y {len(pares)} dentro.")
        print(f"Dentro: {cuenta['vincular']} para vincular, {cuenta['revisar']} para revisar y "
              f"{cuenta['candidata']} candidatas a entrar al catálogo.")
        print(f"Archivos en {carpeta.resolve()}")
        return 0

    if args.version < 1:
        print("--version debe ser un entero positivo.", file=sys.stderr)
        return 2
    revisado_por = " ".join(args.revisado_por.split())
    if not revisado_por:
        print("--revisado-por no puede estar vacío.", file=sys.stderr)
        return 2
    try:
        filas_revisadas = _abrir_csv(args.revisados)
        confirmados = pares_confirmados(filas_revisadas, catalogo)
        validar_con_fuente_anh(filas_revisadas, args.fuente_anh)
    except (ErrorRevision, OSError, ValueError) as e:
        print(f"La revisión tiene problemas:\n{e}", file=sys.stderr)
        return 2
    if not confirmados:
        print("Ninguna fila tiene «si» en la columna revisado: no se genera nada.", file=sys.stderr)
        return 2
    args.salida.mkdir(parents=True, exist_ok=True)
    if any(args.salida.glob("V*__vinculos_anh.sql")):
        print("Ya existe una migración de vínculos ANH; revisa su contenido e historial Flyway. "
              "No se crea una segunda migración automáticamente.", file=sys.stderr)
        return 2
    version = siguiente_version_disponible(args.salida, args.version)
    destino = args.salida / f"V{version}__vinculos_anh.sql"
    if version != args.version:
        print(f"V{args.version} ya está ocupada; se usará V{version}.")
    evidencias = {}
    for fila in filas_revisadas:
        if normalizar(fila.get("revisado")) == "si":
            anh_id = int((fila.get("anh_id") or "0").strip())
            comentario = (fila.get("comentario") or "").strip()
            if normalizar(fila.get("coordenadas_corregidas")) in SI:
                detalle = (f"Georreferenciación corregida; ANH original="
                           f"({fila.get('latitud_anh_original')},{fila.get('longitud_anh_original')}); "
                           f"distancia original={fila.get('distancia_original_m')} m; "
                           f"evidencia={fila.get('evidencia_georreferenciacion')}. ")
                comentario = detalle + comentario
            evidencias[anh_id] = comentario
    destino.write_text(sql_migracion(confirmados, revisado_por, dt.date.today(), evidencias), encoding="utf-8")
    print(f"{len(confirmados)} vínculos en {destino}")
    print(f"P-31 aceptado por solicitante: {P31_MAXIMA_M:g} m; "
          "se validaron las distancias efectivas y se preservaron las coordenadas ANH originales")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
