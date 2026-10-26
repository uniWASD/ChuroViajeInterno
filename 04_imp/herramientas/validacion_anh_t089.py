"""Validaciones compartidas para el inventario ANH y la cobertura urbana T-089.

No consulta internet, no asigna aprobaciones y no usa datos de ejemplo.
"""
from __future__ import annotations

import hashlib
import json
import math
import re
import unicodedata
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

ORIGEN_ANH = 'abastecimiento.prod.anh.gob.bo'
# Nombres SOLO para conciliar las 21 entradas del prototipo con los datos ANH.
# No representan un inventario real y NUNCA se usan como nombres en los INSERT.
PROTOTIPO_LEGADO = (
    'Las Vegas', 'Don Daniel', 'Tacuarandi', 'Campesino', 'YPFB Morros Blancos',
    'Agrupa', 'El Portillo', 'Moto Mendez', 'Ex Terminal', 'Sointa',
    'Panamericano', 'San Jorge', 'San Geronimo', 'El Molle', 'Surtidor Tarija',
    'San Martin', 'Pimentel', 'Villanueva', 'YPFB Parada Chaco',
    'Domingo Savio', 'Coliseo Universitario',
)
# 6 adicionales explícitamente excluidas del alcance T-089 por Franz.
ADICIONALES_PENDIENTES = (
    'Copacabana', 'El Chorro', 'La Terminal', 'Las Américas', 'San Jorge II',
    'YPFB Panamericana',
)


def clave(texto: str) -> str:
    if not isinstance(texto, str):
        raise ValueError('El nombre/alias debe ser un texto reportado')
    letras = ''.join(c for c in unicodedata.normalize('NFD', texto)
                     if not unicodedata.combining(c)).lower()
    resultado = ' '.join(re.sub(r'[^a-z0-9 ]', ' ', letras).split())
    if not resultado:
        raise ValueError('Nombre/alias vacío o no identificable')
    return resultado


def numero_coordenada(valor, limite: int) -> float | None:
    if valor is None or type(valor) not in (float, int):
        return None
    if not math.isfinite(valor) or not -limite <= valor <= limite:
        return None
    return float(valor)


def _producto_fuente_valido(fuente_url: str, dep: int, prod: int) -> bool:
    try:
        url = urlsplit(fuente_url)
        qs = parse_qs(url.query)
        return (url.scheme == 'https' and url.hostname == ORIGEN_ANH
                and url.username is None and url.password is None
                and url.port is None and not url.fragment
                and url.path == '/api/estaciones/'
                and set(qs) == {'departamento', 'producto'}
                and qs.get('departamento') == [str(dep)]
                and qs.get('producto') == [str(prod)])
    except (TypeError, ValueError):
        return False



def _fecha_utc_acreditada(valor) -> bool:
    if not isinstance(valor, str):
        return False
    try:
        fecha = datetime.fromisoformat(valor.replace('Z', '+00:00'))
        return fecha.tzinfo is not None and fecha.utcoffset() is not None
    except ValueError:
        return False


def fuente_ubicacion_registro(registro: dict) -> str:
    """Fuente HTTPS EXACTA de las coordenadas usadas; detalle si ANH las completó.

    La fuente del listado no puede atribuirse falsamente a una posición obtenida
    de `/estaciones/{id}`. Este valor queda trazado hasta la migración SQL.
    """
    if 'fuente_ubicacion_t089' in registro:
        return registro['fuente_ubicacion_t089']  # Campo derivado al conciliar productos.
    suplemento = registro.get('fuente_complemento_identidad')
    if not suplemento:
        return registro['fuente_url']
    campos = suplemento['campos']
    if 'latitud' in campos or 'longitud' in campos or 'url_mapa' in campos:
        return suplemento['url']
    return registro['fuente_url']


def _verificar_detalle(reg: dict, sid: int) -> None:
    suplemento = reg.get('fuente_complemento_identidad')
    if suplemento is None:
        return
    if not isinstance(suplemento, dict):
        raise ValueError(f'Estación {sid}: evidencia del detalle ANH inválida')
    url = suplemento.get('url')
    if not isinstance(url, str):
        raise ValueError(f'Estación {sid}: URL de detalle ausente')
    u = urlsplit(url)
    if (u.scheme != 'https' or u.hostname != ORIGEN_ANH or u.port is not None
            or u.username is not None or u.password is not None or u.query
            or u.fragment or u.path != f'/api/estaciones/{sid}'):
        raise ValueError(f'Estación {sid}: detalle de origen no acreditado')
    campos = suplemento.get('campos')
    permitidos = {'nombre_estacion','direccion','zona','latitud','longitud','url_mapa'}
    if (not isinstance(campos, list) or not campos
            or any(not isinstance(c, str) or c not in permitidos for c in campos)
            or len(set(campos)) != len(campos)
            or suplemento.get('tls_verificado') is not True
            or not _fecha_utc_acreditada(suplemento.get('observado_en_utc'))):
        raise ValueError(f'Estación {sid}: detalle sin procedencia TLS/fecha/campos válidos')
    if ('latitud' in campos) != ('longitud' in campos):
        raise ValueError(f'Estación {sid}: detalle con una sola coordenada alterada')


def _identidad_conciliada(sid: int, filas: list[dict], incidencias: list[str]) -> dict | None:
    """Unifica campos presentes sin fabricar datos; contradicciones reales excluyen."""
    nombres = {f.get('nombre_estacion') for f in filas if f.get('nombre_estacion') is not None}
    coordenadas = set()
    filas_completas = []
    for f in filas:
        lat = numero_coordenada(f.get('latitud'), 90)
        lon = numero_coordenada(f.get('longitud'), 180)
        if lat is not None and lon is not None and (lat != 0 or lon != 0):
            coordenadas.add((lat, lon))
            filas_completas.append(f)
        elif lat is not None or lon is not None:
            incidencias.append(f'Estación ANH id={sid}: coordenada incompleta en producto {f["producto_id"]}; se ignora esa posición')
    if len(nombres) > 1 or len(coordenadas) > 1:
        incidencias.append(f'Estación ANH id={sid}: nombre o posición contradictorios entre productos; excluida')
        return None
    origen = filas_completas[0] if filas_completas else filas[0]
    conciliada = dict(origen)
    if nombres:
        conciliada['nombre_estacion'] = next(iter(nombres))
    else:
        conciliada['nombre_estacion'] = None
    if coordenadas:
        conciliada['latitud'], conciliada['longitud'] = next(iter(coordenadas))
    else:
        conciliada['latitud'] = None
        conciliada['longitud'] = None
    conciliada['fuente_ubicacion_t089'] = fuente_ubicacion_registro(origen)
    if len(filas_completas) < len(filas):
        incidencias.append(f'Estación ANH id={sid}: se utilizó únicamente la coordenada completa acreditada por la API')
    return conciliada


def verificar_snapshot_anh(ruta: Path) -> tuple[dict, dict[int, dict], str, list[str]]:
    """Exige datos obtenidos por el cliente ANH con HTTPS verificado y dos combustibles completos.

    Devuelve un registro CANÓNICO por id ANH, o rechaza contradicciones sin conjeturas.
    """
    fuente = ruta.read_bytes()
    digest = hashlib.sha256(fuente).hexdigest()
    datos = json.loads(fuente)
    if not isinstance(datos, dict):
        raise ValueError('JSON ANH: raíz inválida')
    if (datos.get('estado') != 'completa' or datos.get('tls_verificado') is not True
            or datos.get('uso_datos') != 'fuente_reportada'
            or datos.get('departamento') != 'Tarija'
            or datos.get('alcance') != 'departamento_de_tarija_no_solo_ciudad'):
        raise ValueError('Solo se acepta una extracción ANH completa, verificada y del departamento Tarija')
    if not _fecha_utc_acreditada(datos.get('observado_en_utc')):
        raise ValueError('Fecha UTC del snapshot ANH ausente o sin zona horaria')
    dep = datos.get('departamento_id')
    if type(dep) is not int or dep <= 0:
        raise ValueError('Departamento ANH sin id verificado')
    consultas = datos.get('consultas_productos')
    if not isinstance(consultas, list) or len(consultas) != 2 or (
            {q.get('producto_id') for q in consultas if isinstance(q, dict)} != {0, 1}):
        raise ValueError('Deben constar las consultas completas de gasolina y diésel')
    if any(q.get('estado') != 'correcta' or q.get('tls_verificado') is not True or
           type(q.get('registros')) is not int or q['registros'] < 0 or
           not _producto_fuente_valido(q.get('fuente_url'), dep, q['producto_id'])
           for q in consultas):
        raise ValueError('Consultas ANH fallidas, TLS no verificado o URL no acreditada')
    registros = datos.get('registros')
    if not isinstance(registros, list) or not registros:
        raise ValueError('Sin registros reales; una API sin respuesta no equivale a cero surtidores')
    if (datos.get('registros_totales') != len(registros)
            or sum(q['registros'] for q in consultas) != len(registros)):
        raise ValueError('Extracción parcial o conteos ANH contradictorios')
    agrupados: dict[int, list[dict]] = defaultdict(list)
    vistos: set[tuple[int, int]] = set()
    incidencias: list[str] = []
    for reg in registros:
        if not isinstance(reg, dict):
            raise ValueError('Registro ANH no estructurado')
        sid, prod = reg.get('estacion_id'), reg.get('producto_id')
        if type(sid) is not int or sid <= 0 or type(prod) is not int or prod not in (0, 1):
            raise ValueError('ID de estación/producto inválido')
        llave = (sid, prod)
        if llave in vistos:
            raise ValueError(f'Estación/producto duplicado en el snapshot ANH: {llave}')
        vistos.add(llave)
        if (reg.get('departamento') != 'Tarija' or reg.get('departamento_id') != dep
                or reg.get('alcance') != datos['alcance']
                or reg.get('tls_verificado') is not True
                or reg.get('uso_datos') != 'fuente_reportada'
                or reg.get('verificacion_departamento') not in ('campo_y_filtro', 'solo_filtro_api')
                or not _producto_fuente_valido(reg.get('fuente_url'), dep, prod)):
            raise ValueError(f'Estación ANH {sid}: departamento, origen o TLS inválido')
        _verificar_detalle(reg, sid)
        agrupados[sid].append(reg)
    if datos.get('estaciones_distintas') != len(agrupados):
        raise ValueError('Conteo de estaciones ANH inconsistente')
    estaciones: dict[int, dict] = {}
    for sid, filas in agrupados.items():
        # Se aprovechan campos no nulos si otra consulta ANH los acredita;
        # posiciones/nombres contradictorios sí bloquean la estación.
        conciliada = _identidad_conciliada(sid, filas, incidencias)
        if conciliada is None:
            continue
        estaciones[sid] = conciliada
        if any(f.get('verificacion_departamento') == 'solo_filtro_api' for f in filas):
            incidencias.append(f'Estación ANH id={sid}: departamento sustentado únicamente por el filtro API')
    return datos, estaciones, digest, incidencias


def _orientacion(a, b, c):
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])


def _segmento(a, b, p):
    return (abs(_orientacion(a, b, p)) <= 1e-12
            and min(a[0], b[0]) - 1e-12 <= p[0] <= max(a[0], b[0]) + 1e-12
            and min(a[1], b[1]) - 1e-12 <= p[1] <= max(a[1], b[1]) + 1e-12)


def _cruza(a, b, c, d):
    o1, o2, o3, o4 = (_orientacion(a, b, c), _orientacion(a, b, d),
                      _orientacion(c, d, a), _orientacion(c, d, b))
    return (_segmento(a, b, c) or _segmento(a, b, d)
            or _segmento(c, d, a) or _segmento(c, d, b)
            or ((o1 > 1e-12 and o2 < -1e-12 or o1 < -1e-12 and o2 > 1e-12)
                and (o3 > 1e-12 and o4 < -1e-12 or o3 < -1e-12 and o4 > 1e-12)))


def _validar_anillo(anillo):
    if not isinstance(anillo, list) or len(anillo) < 4:
        raise ValueError('Anillo inválido: faltan vértices')
    for punto in anillo:
        if (not isinstance(punto, list) or len(punto) != 2
                or numero_coordenada(punto[0], 180) is None
                or numero_coordenada(punto[1], 90) is None):
            raise ValueError('Vértice inválido; GeoJSON exige [longitud, latitud]')
    if anillo[0] != anillo[-1]:
        raise ValueError('Anillo sin cerrar')
    if any(anillo[i] == anillo[i + 1] for i in range(len(anillo) - 1)):
        raise ValueError('Anillo con aristas de longitud cero')
    area = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(anillo, anillo[1:]))
    if abs(area) <= 1e-12:
        raise ValueError('Anillo sin área')
    for i in range(len(anillo) - 1):
        for j in range(i + 2, len(anillo) - 1):
            if i == 0 and j == len(anillo) - 2:
                continue
            if _cruza(anillo[i], anillo[i + 1], anillo[j], anillo[j + 1]):
                raise ValueError('Anillo con autointersección')


def _posicion_anillo(anillo, punto):
    dentro = False
    for a, b in zip(anillo, anillo[1:]):
        if _segmento(a, b, punto):
            return 'BORDE'
        if (a[1] > punto[1]) != (b[1] > punto[1]):
            x = a[0] + (b[0] - a[0]) * (punto[1] - a[1]) / (b[1] - a[1])
            if punto[0] < x:
                dentro = not dentro
    return 'DENTRO' if dentro else 'FUERA'


def _validar_poligono(anillos):
    if not isinstance(anillos, list) or not anillos:
        raise ValueError('Polígono sin anillos')
    for a in anillos:
        _validar_anillo(a)
    exterior = anillos[0]
    for hueco in anillos[1:]:
        if any(_posicion_anillo(exterior, v) != 'DENTRO' for v in hueco[:-1]):
            raise ValueError('Hueco fuera del perímetro')
        if any(_cruza(a, b, c, d) for a, b in zip(exterior, exterior[1:])
               for c, d in zip(hueco, hueco[1:])):
            raise ValueError('Hueco cruza contorno')
    for i, a in enumerate(anillos[1:]):
        for b in anillos[i + 2:]:
            if any(_cruza(u, v, w, x) for u, v in zip(a, a[1:])
                   for w, x in zip(b, b[1:])) or _posicion_anillo(a, b[0]) != 'FUERA' \
                    or _posicion_anillo(b, a[0]) != 'FUERA':
                raise ValueError('Huecos superpuestos')


def validar_cobertura_aprobada(doc: dict):
    if (not isinstance(doc, dict) or doc.get('type') != 'FeatureCollection'
            or doc.get('estado_validacion') != 'APROBADO'):
        raise ValueError('GeoJSON no aprobado por el equipo: no se permite generar V2')
    features = doc.get('features')
    if not isinstance(features, list) or not features:
        raise ValueError('GeoJSON no contiene polígonos aprobados')
    for feature in features:
        if (not isinstance(feature, dict) or feature.get('type') != 'Feature'
                or feature.get('properties', {}).get('revision_equipo') != 'APROBADO'):
            raise ValueError('Falta aprobación individual de cada geometría')
        geometria = feature.get('geometry')
        if not isinstance(geometria, dict):
            raise ValueError('Falta geometría válida')
        if geometria.get('type') == 'Polygon':
            lista = [geometria.get('coordinates')]
        elif geometria.get('type') == 'MultiPolygon':
            lista = geometria.get('coordinates')
        else:
            raise ValueError('Solo se admite Polygon o MultiPolygon')
        if not isinstance(lista, list) or not lista:
            raise ValueError('Geometría sin coordenadas')
        for anillos in lista:
            _validar_poligono(anillos)


def dentro_cobertura(doc: dict, lon: float, lat: float) -> bool:
    punto = (lon, lat)
    for f in doc['features']:
        g = f['geometry']
        poligonos = [g['coordinates']] if g['type'] == 'Polygon' else g['coordinates']
        for anillos in poligonos:
            exterior = _posicion_anillo(anillos[0], punto)
            if exterior == 'FUERA':
                continue
            if exterior == 'BORDE':
                return True
            if any(_posicion_anillo(hueco, punto) == 'DENTRO' for hueco in anillos[1:]):
                continue
            return True
    return False
