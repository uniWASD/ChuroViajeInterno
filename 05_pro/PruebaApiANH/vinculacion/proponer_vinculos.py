"""T-118 (#113): proponer los vínculos entre las estaciones de ANH y los surtidores del catálogo.

Dos pasos, con una revisión del equipo en medio (RN-09.10):

1. Proponer. Lee el CSV que deja el cliente de prueba (`anh_tarija.py`, archivo `estaciones_tarija.csv`), el
   catálogo de surtidores de la migración V2 y la mancha urbana. Para cada estación de ANH dentro de la mancha busca
   el surtidor del catálogo que le corresponde por nombre y distancia, y escribe `pares_propuestos.csv` para revisar.

       python proponer_vinculos.py proponer ruta/a/estaciones_tarija.csv

2. Generar la migración. Con el CSV ya revisado (columna `revisado` en «si» para cada par confirmado), escribe la
   migración de datos que carga los vínculos y sugiere P-31: la mayor distancia entre los pares confirmados.

       python proponer_vinculos.py migracion pares_propuestos.csv --revisado-por "Franz" --version 13

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

# Hasta dónde se busca un surtidor para una estación. No es P-31: P-31 sale de los pares que el equipo confirme.
RADIO_BUSQUEDA_M = 1000
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
        nueva = Estacion(anh_id, (fila.get("nombre_estacion") or "").strip(),
                         _numero(fila.get("latitud")), _numero(fila.get("longitud")))
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
            pares.append(Par(e, s, d, True, "vincular", "coincide el nombre"))
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


CAMPOS_REVISION = ["anh_id", "nombre_anh", "latitud_anh", "longitud_anh", "surtidor", "distancia_m",
                   "coincide_nombre", "propuesta", "motivo", "nota_v2", "revisado", "comentario"]


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
                "distancia_m": f"{p.distancia:.0f}" if p.distancia is not None else "",
                "coincide_nombre": "si" if p.por_nombre else "no",
                "propuesta": p.propuesta, "motivo": p.motivo,
                "nota_v2": p.surtidor.nota_v2 if p.surtidor else "",
                "revisado": "", "comentario": "",
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

SI = {"si", "sí", "s", "x", "yes", "ok"}


class ErrorRevision(Exception):
    pass


@dataclass
class Confirmado:
    anh_id: int
    surtidor: str
    distancia: float | None
    comentario: str = ""

    @property
    def excepcion(self) -> bool:
        """Un par que el equipo confirmó aunque pase P-31 (por ejemplo, porque ANH tiene mal el punto)."""
        return normalizar(self.comentario).startswith("excepcion")


def pares_confirmados(filas: list[dict[str, str]], catalogo: list[Surtidor]) -> list[Confirmado]:
    """Los pares con `revisado` en «si». Falla si un id o un surtidor se repite, o si el surtidor no existe."""
    nombres = {s.nombre for s in catalogo}
    confirmados: list[Confirmado] = []
    errores: list[str] = []
    for n, fila in enumerate(filas, start=2):
        if normalizar(fila.get("revisado")) not in {normalizar(s) for s in SI}:
            continue
        crudo, surtidor = (fila.get("anh_id") or "").strip(), (fila.get("surtidor") or "").strip()
        if not re.fullmatch(r"\d+", crudo) or int(crudo) <= 0:
            errores.append(f"línea {n}: anh_id {crudo!r} no es un entero positivo")
            continue
        if surtidor not in nombres:
            errores.append(f"línea {n}: el surtidor {surtidor!r} no está en el catálogo (V2)")
            continue
        confirmados.append(Confirmado(int(crudo), surtidor, _numero(fila.get("distancia_m")),
                                      (fila.get("comentario") or "").strip()))
    for i, c in enumerate(confirmados):
        for otro in confirmados[i + 1:]:
            if c.anh_id == otro.anh_id:
                errores.append(f"el id de ANH {c.anh_id} está confirmado para dos surtidores (RN-09.10)")
            if c.surtidor == otro.surtidor:
                errores.append(f"{c.surtidor} está confirmado para dos estaciones de ANH (RN-09.10)")
    if errores:
        raise ErrorRevision("\n".join(errores))
    return sorted(confirmados, key=lambda c: c.surtidor)


def p31_sugerida(confirmados: list[Confirmado]) -> int | None:
    """La mayor distancia de los pares confirmados, sin las excepciones, redondeada hacia arriba a 50 m."""
    distancias = [c.distancia for c in confirmados if c.distancia is not None and not c.excepcion]
    return int(math.ceil(max(distancias) / 50) * 50) if distancias else None


def sql_migracion(confirmados, revisado_por: str, fecha: dt.date) -> str:
    lineas = [
        "-- T-118 (CU-09): vinculos entre los surtidores del catalogo y las estaciones de ANH (RN-09.10).",
        f"-- Generado con 05_pro/PruebaApiANH/vinculacion/proponer_vinculos.py; revisado a mano por {revisado_por}"
        f" el {fecha.isoformat()}.",
        "-- Cada surtidor que no aparece aqui queda sin estacion de ANH, y CU-09 ignora sus datos (RN-09.9).",
    ]
    for c in confirmados:
        nota = f"a {c.distancia:.0f} m" if c.distancia is not None else "sin distancia"
        if c.comentario:
            nota += "; " + " ".join(c.comentario.split())
        nombre = c.surtidor.replace("'", "''")
        lineas.append(f"UPDATE surtidor SET anh_id = {c.anh_id} WHERE nombre = '{nombre}'; -- {nota}")
    lineas += [
        "",
        "-- Si un nombre del catalogo cambio y algun UPDATE no encontro su fila, la migracion falla en vez de seguir.",
        "DO $$",
        "BEGIN",
        f"    IF (SELECT count(*) FROM surtidor WHERE anh_id IS NOT NULL) <> {len(confirmados)} THEN",
        "        RAISE EXCEPTION 'No se cargaron todos los vinculos de ANH';",
        "    END IF;",
        "END $$;",
        "",
    ]
    return "\n".join(lineas)


# ---------------------------------------------------------------- consola

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    sub = parser.add_subparsers(dest="paso", required=True)
    p1 = sub.add_parser("proponer", help="Propone los pares a partir del CSV del prototipo")
    p1.add_argument("estaciones", type=Path, help="estaciones_tarija.csv que dejó anh_tarija.py")
    p1.add_argument("--salida", type=Path, default=None, help="Carpeta de salida (por defecto, vinculos_<fecha>)")
    p2 = sub.add_parser("migracion", help="Genera la migración con los pares revisados")
    p2.add_argument("revisados", type=Path, help="pares_propuestos.csv con la columna revisado llena")
    p2.add_argument("--revisado-por", required=True, help="Quién revisó los pares")
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

    try:
        confirmados = pares_confirmados(_abrir_csv(args.revisados), catalogo)
    except ErrorRevision as e:
        print(f"La revisión tiene problemas:\n{e}", file=sys.stderr)
        return 2
    if not confirmados:
        print("Ninguna fila tiene «si» en la columna revisado: no se genera nada.", file=sys.stderr)
        return 2
    destino = args.salida / f"V{args.version}__vinculos_anh.sql"
    if destino.exists():
        print(f"{destino} ya existe: no se sobrescribe.", file=sys.stderr)
        return 2
    destino.write_text(sql_migracion(confirmados, args.revisado_por, dt.date.today()), encoding="utf-8")
    print(f"{len(confirmados)} vínculos en {destino}")
    sugerida = p31_sugerida(confirmados)
    if sugerida is not None:
        print(f"P-31 sugerida: {sugerida} m (la mayor distancia confirmada sin las excepciones, redondeada a 50 m)")
    for c in confirmados:
        if c.excepcion:
            print(f"Excepción a P-31: {c.surtidor} (ANH {c.anh_id}) a {c.distancia:.0f} m")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
