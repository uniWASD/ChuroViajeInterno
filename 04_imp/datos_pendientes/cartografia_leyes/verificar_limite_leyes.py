#!/usr/bin/env python3
"""Comprobaciones independientes del GeoJSON legal de referencia y CSV UTM.
Requiere pyproj y shapely. No modifica archivos ni bases de datos.
"""
import csv
import json
import math
import sys
from pathlib import Path
from shapely.geometry import shape, Polygon
from pyproj import Transformer

carpeta = Path(__file__).resolve().parent
csv_arch = carpeta / 'vertices_286_ley110_ley118_utm.csv'
json_arch = carpeta / 'limite_urbano_ley110_ley118_PENDIENTE.geojson'
geo = json.loads(json_arch.read_text(encoding='utf-8'))
with csv_arch.open(encoding='utf-8-sig', newline='') as f:
    rows = list(csv.DictReader(f))
assert len(rows) == 286, f'Esperados 286 puntos; encontrados {len(rows)}'
for i, r in enumerate(rows, 1):
    assert r['punto'] == f'P{i}', f'Orden de puntos incorrecto en índice {i}'
points = [(float(r['este_m_utm20s_wgs84']), float(r['norte_m_utm20s_wgs84'])) for r in rows]
p = Polygon(points)
assert p.is_valid, 'UTM: geometría inválida'
assert len(set(points)) == len(points), 'Vértices UTM duplicados'
assert abs(p.area / 10000 - 10456.64) < 0.01, 'Área discordante con Ley 110'
for i, esperado in {253: (316847.964, 7621253.785), 254: (317486.776, 7621398.344), 255: (317511.531, 7621270.513)}.items():
    assert all(math.isclose(a, b, abs_tol=1e-9) for a, b in zip(points[i-1], esperado)), f'P{i} distinto de Ley 118'
assert geo.get('estado_validacion') == 'PROVISIONAL', 'Referencia debe seguir PROVISIONAL'
assert geo['features'][0]['properties']['revision_equipo'] == 'PENDIENTE', 'Revisión del equipo debe seguir PENDIENTE'
g = shape(geo['features'][0]['geometry'])
assert g.geom_type == 'Polygon' and g.is_valid
assert len(g.exterior.coords) == 287
reproj = Transformer.from_crs('EPSG:32720', 'EPSG:4326', always_xy=True)
ref = [reproj.transform(*pt) for pt in points]
observed = list(g.exterior.coords)[:-1]
assert max(math.dist(a, b) for a, b in zip(ref, observed)) < 1e-8, 'Reproyección WGS84 incompatible'
assert g.exterior.coords[0] == g.exterior.coords[-1], 'Anillo no cerrado'
print(f'PRUEBAS CORRECTAS: 286 vértices; 287 posiciones con cierre; superficie {p.area/10000:.6f} ha; sin autointersecciones; P253–P255 cotejados; PROVISIONAL/PENDIENTE preservados.')
