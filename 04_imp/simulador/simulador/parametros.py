"""Parámetros de una simulación (CU-07, RN-07.1, RN-07.2): se leen del archivo y de la línea de comandos, y se
validan todos antes de empezar."""

import json
import math
import tomllib
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlparse

from simulador import limites
from simulador.cobertura import Cobertura

RAIZ = Path(__file__).resolve().parent.parent
DATOS = RAIZ.parent / "datos"
RUTAS_POR_DEFECTO = DATOS / "rutas_referencia.json"
COBERTURA = DATOS / "mancha_urbana_tarija.geojson"
#: RN-07.12: por defecto, el backend del ambiente de pruebas en la misma máquina; nunca el de producción.
BACKEND_POR_DEFECTO = "http://127.0.0.1:8080"
HOSTS_LOCALES = {"127.0.0.1", "localhost", "::1"}

#: La «carga normal» de las pruebas: 50 usuarios fantasma a la vez, 10 de ellos navegando una ruta.
CARGA_NORMAL = {"usuarios": 50, "navegando": 10, "eventos_por_minuto": 6, "duracion_minutos": 15}

CAMPOS = {"usuarios", "navegando", "rutas", "eventos_por_minuto", "duracion_minutos", "backend", "ambiente_remoto",
          "semilla"}


@dataclass(frozen=True)
class Ruta:
    nombre: str
    origen: tuple[float, float]
    destino: tuple[float, float]
    paradas: tuple[tuple[float, float], ...] = ()


@dataclass(frozen=True)
class Parametros:
    usuarios: int
    navegando: int
    rutas: tuple[Ruta, ...]
    eventos_por_minuto: int
    duracion_minutos: int
    backend: str
    ambiente_remoto: bool = False
    semilla: int | None = None
    archivo_rutas: str = field(default="", compare=False)


class ParametrosInvalidos(Exception):
    """Lleva todos los problemas encontrados, no solo el primero."""

    def __init__(self, errores: list[str]):
        super().__init__("; ".join(errores))
        self.errores = errores


def leer_archivo(ruta: Path) -> dict:
    """Un archivo de configuración TOML (simulador.ejemplo.toml). Una clave desconocida es un error, no se ignora."""
    try:
        datos = tomllib.loads(Path(ruta).read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise ParametrosInvalidos([f"No existe el archivo de configuración {ruta}"]) from None
    except tomllib.TOMLDecodeError as e:
        raise ParametrosInvalidos([f"El archivo de configuración no es TOML válido: {e}"]) from None
    desconocidas = sorted(set(datos) - CAMPOS)
    if desconocidas:
        raise ParametrosInvalidos([f"Parámetro desconocido en el archivo: {', '.join(desconocidas)}"])
    return datos


def _punto(valor, donde: str, errores: list[str]) -> tuple[float, float] | None:
    if (not isinstance(valor, (list, tuple)) or len(valor) != 2
            or not all(isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v) for v in valor)):
        errores.append(f"{donde}: debe ser [latitud, longitud]")
        return None
    return float(valor[0]), float(valor[1])


def leer_rutas(ruta: Path, cobertura: Cobertura, errores: list[str]) -> tuple[Ruta, ...]:
    """Un archivo con {"viajes": [{"nombre", "origen", "destino", "paradas"?}]}, el formato de rutas_referencia.json.
    Cada punto debe caer en la zona de cobertura, como exige el backend (RN-03.3)."""
    try:
        datos = json.loads(Path(ruta).read_text(encoding="utf-8"))
    except FileNotFoundError:
        errores.append(f"No existe el archivo de rutas {ruta}")
        return ()
    except json.JSONDecodeError:
        errores.append(f"El archivo de rutas {ruta} no es JSON válido")
        return ()
    viajes = datos.get("viajes") if isinstance(datos, dict) else None
    if not isinstance(viajes, list):
        errores.append(f"El archivo de rutas {ruta} no tiene la lista «viajes»")
        return ()
    rutas = []
    for i, v in enumerate(viajes):
        donde = f"ruta {i + 1}"
        if not isinstance(v, dict):
            errores.append(f"{donde}: debe ser un objeto")
            continue
        nombre = v.get("nombre") if isinstance(v.get("nombre"), str) and v.get("nombre").strip() else donde
        puntos = [("origen", v.get("origen")), ("destino", v.get("destino"))]
        paradas_crudas = v.get("paradas", [])
        if not isinstance(paradas_crudas, list) or len(paradas_crudas) > 10:
            errores.append(f"{nombre}: las paradas deben ser una lista de hasta 10 puntos")
            paradas_crudas = []
        puntos += [(f"parada {j + 1}", p) for j, p in enumerate(paradas_crudas)]
        leidos = []
        for etiqueta, crudo in puntos:
            p = _punto(crudo, f"{nombre}, {etiqueta}", errores)
            if p is not None and not cobertura.contiene(*p):
                errores.append(f"{nombre}, {etiqueta}: {p[0]}, {p[1]} está fuera de la zona de cobertura de Tarija")
            leidos.append(p)
        if all(p is not None for p in leidos):
            rutas.append(Ruta(nombre, leidos[0], leidos[1], tuple(leidos[2:])))
    return tuple(rutas)


def _entero(datos: dict, clave: str, errores: list[str]) -> int | None:
    valor = datos[clave]
    if isinstance(valor, bool) or not isinstance(valor, int):
        if isinstance(valor, str) and valor.strip().lstrip("+-").isdigit():
            return int(valor)
        errores.append(f"{clave}: debe ser un número entero, no «{valor}»")
        return None
    return valor


def validar(datos: dict, cobertura: Cobertura | None = None) -> Parametros:
    """RN-07.1: valida todo junto y, si algo no sirve, lanza ParametrosInvalidos con la lista completa."""
    valores = {**CARGA_NORMAL, "rutas": str(RUTAS_POR_DEFECTO), "backend": BACKEND_POR_DEFECTO,
               "ambiente_remoto": False, "semilla": None}
    valores.update({k: v for k, v in datos.items() if v is not None})
    errores: list[str] = []

    numeros = {}
    for clave in ("usuarios", "navegando", "eventos_por_minuto", "duracion_minutos"):
        n = _entero(valores, clave, errores)
        if n is not None and not limites.TODOS[clave].contiene(n):
            errores.append(f"{clave}: {n} está fuera del límite permitido ({limites.TODOS[clave].texto()})")
            n = None
        numeros[clave] = n
    if numeros["usuarios"] is not None and numeros["navegando"] is not None \
            and numeros["navegando"] > numeros["usuarios"]:
        errores.append(f"navegando: {numeros['navegando']} es más que los {numeros['usuarios']} usuarios fantasma")
    if numeros["usuarios"] is not None and numeros["eventos_por_minuto"] is not None:
        tope = math.floor(numeros["usuarios"] * limites.EVENTOS_POR_USUARIO_Y_MINUTO)
        if numeros["eventos_por_minuto"] > tope:
            errores.append(f"eventos_por_minuto: {numeros['eventos_por_minuto']} es más de lo que {numeros['usuarios']} "
                           f"usuarios pueden reportar ({tope} por minuto, RN-01.9)")

    semilla = valores["semilla"]
    if semilla is not None and (isinstance(semilla, bool) or not isinstance(semilla, int)):
        errores.append("semilla: debe ser un número entero")

    backend = valores["backend"]
    remoto = valores["ambiente_remoto"] is True
    url = urlparse(backend) if isinstance(backend, str) else None
    if url is None or url.scheme not in ("http", "https") or not url.hostname or url.path not in ("", "/") \
            or url.query or url.fragment or url.username:
        errores.append(f"backend: «{backend}» no es la dirección base de un backend (por ejemplo {BACKEND_POR_DEFECTO})")
    elif url.hostname not in HOSTS_LOCALES:
        if not remoto:
            errores.append(f"backend: {url.hostname} no es esta máquina. El simulador corre contra el ambiente de "
                           "pruebas (RN-07.12); para otro servidor de pruebas agrega --ambiente-remoto")
        elif url.scheme != "https":
            errores.append("backend: un servidor remoto se usa solo por HTTPS")

    archivo_rutas = valores["rutas"]
    rutas: tuple[Ruta, ...] = ()
    if not isinstance(archivo_rutas, str) or not archivo_rutas.strip():
        errores.append("rutas: debe ser la ruta de un archivo JSON con la lista «viajes»")
    else:
        antes = len(errores)
        rutas = leer_rutas(Path(archivo_rutas), cobertura or Cobertura.desde_archivo(COBERTURA), errores)
        if len(errores) == antes and not limites.RUTAS.contiene(len(rutas)):
            errores.append(f"rutas: {len(rutas)} está fuera del límite permitido ({limites.RUTAS.texto()})")
        if numeros["navegando"] and not rutas:
            errores.append("rutas: los usuarios que navegan necesitan al menos una ruta")

    if errores:
        raise ParametrosInvalidos(errores)
    return Parametros(usuarios=numeros["usuarios"], navegando=numeros["navegando"], rutas=rutas,
                      eventos_por_minuto=numeros["eventos_por_minuto"], duracion_minutos=numeros["duracion_minutos"],
                      backend=backend.rstrip("/"), ambiente_remoto=remoto, semilla=semilla,
                      archivo_rutas=archivo_rutas)
