#!/usr/bin/env python3
"""Cruza un snapshot local ANH con una geometría GeoJSON de referencia.

No modifica los originales, no aprueba registros, no genera SQL.
Uso: python comparar_snapshot_ANH_solo_referencia.py --api-json datos_tarija.json --geojson limite_urbano_ley110_ley118_PENDIENTE.geojson --salida informe.csv
"""
import argparse
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path
from decimal import Decimal, InvalidOperation

from shapely.geometry import Point, shape


def numero(valor):
    if valor is None or isinstance(valor, bool) or str(valor).strip() == '':
        return None
    try:
        return float(Decimal(str(valor)))
    except (InvalidOperation, ValueError):
        return None


def revisar(api_doc, geo_doc):
    assert isinstance(api_doc, dict) and isinstance(geo_doc, dict)
    registros = api_doc.get('registros')
    if not isinstance(registros, list):
        raise ValueError('El snapshot no contiene el arreglo registros')
    if geo_doc.get('type') != 'FeatureCollection':
        raise ValueError('GeoJSON no es FeatureCollection')
    if geo_doc.get('estado_validacion') != 'PROVISIONAL':
        raise ValueError('Se espera geometría legal de REFERENCIA sin aprobación operativa')
    feats=geo_doc.get('features')
    if not isinstance(feats,list) or len(feats)!=1:
        raise ValueError('El GeoJSON de referencia debe tener exactamente una geometría')
    g=shape(feats[0]['geometry'])
    if not g.is_valid or g.is_empty:
        raise ValueError('Geometría GeoJSON inválida')
    por_id = defaultdict(list)
    for r in registros:
        if not isinstance(r, dict):continue
        id_=r.get('estacion_id')
        if id_ is None or str(id_).strip()=='':continue
        por_id[str(id_)].append(r)
    salida=[]
    for id_, rr in sorted(por_id.items(), key=lambda x:(not x[0].isdigit(),int(x[0]) if x[0].isdigit() else x[0])):
        nombres=sorted({str(x.get('nombre_estacion')).strip() for x in rr if x.get('nombre_estacion')})
        coords=set()
        for r in rr:
            lat=numero(r.get('latitud'))
            lon=numero(r.get('longitud'))
            if lat is None or lon is None:continue
            if -90<=lat<=90 and -180<=lon<=180:coords.add((lat,lon))
        estatus='SIN_COORDENADAS'
        if len(coords)>1:estatus='COORDENADAS_DISCREPANTES'
        elif len(coords)==1:
            lat,lon=next(iter(coords))
            point=Point(lon,lat)
            estatus='EN_BORDE' if g.boundary.distance(point) < 1e-10 else ('DENTRO_REFERENCIA' if g.covers(point) else 'FUERA_REFERENCIA')
        salida.append({
            'estacion_id_anh':id_,
            'nombre_anh':' / '.join(nombres),
            'latitud':next(iter(coords))[0] if len(coords)==1 else '',
            'longitud':next(iter(coords))[1] if len(coords)==1 else '',
            'estatus_geografico_preliminar':estatus,
            'revision_franz':'PENDIENTE',
            'aprobado_equipo':'PENDIENTE',
            'fuente_datos':'Snapshot API ANH local proporcionado en --api-json',
            'no_usable_en_migracion':'SI',
        })
    return salida


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--api-json',type=Path,required=True)
    p.add_argument('--geojson',type=Path,required=True)
    p.add_argument('--salida',type=Path,required=True)
    a=p.parse_args()
    if a.salida.resolve() in {a.api_json.resolve(),a.geojson.resolve()}:
        p.error('La salida no puede sobrescribir las entradas')
    api=json.loads(a.api_json.read_text(encoding='utf-8-sig'))
    geo=json.loads(a.geojson.read_text(encoding='utf-8-sig'))
    resultados=revisar(api,geo)
    if not resultados:
        raise SystemExit('No se encontraron estaciones identificables')
    a.salida.parent.mkdir(parents=True,exist_ok=True)
    with a.salida.open('w',newline='',encoding='utf-8-sig') as f:
        w=csv.DictWriter(f,fieldnames=list(resultados[0]));w.writeheader();w.writerows(resultados)
    print('Estaciones identificadas:',len(resultados))
    for k,v in sorted(Counter(r['estatus_geografico_preliminar'] for r in resultados).items()):
        print(f'{k}: {v}')
    print('Reporte de REFERENCIA; NO apto para V2 sin aprobaciones:',a.salida)

if __name__=='__main__':main()
