"""Prepara un CSV de estaciones URBANAS de Tarija a partir de una extracción ANH verificada.

NO marca revisiones ni aprobaciones, NO genera migración, NO inventa nombres, alias
ni posiciones y NO carga saldos de combustible. Requiere el límite urbano APROBADO.
"""
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

from validacion_anh_t089 import (ADICIONALES_PENDIENTES, PROTOTIPO_LEGADO,
                                  clave, dentro_cobertura, numero_coordenada,
                                  validar_cobertura_aprobada, verificar_snapshot_anh, fuente_ubicacion_registro)

COLUMNAS = (
    'estacion_id_anh', 'nombre', 'nombre_prototipo', 'aliases', 'longitud', 'latitud',
    'fuente_ubicacion', 'revision_franz', 'aprobado_equipo', 'decision',
    'motivo_exclusion', 'sha256_snapshot_anh',
)


def preparar(datos_anh: Path, geojson: Path, csv_destino: Path, informe_destino: Path) -> dict:
    datos, estaciones, sha256, incidencias = verificar_snapshot_anh(datos_anh)
    cobertura = json.loads(geojson.read_text(encoding='utf-8'))
    validar_cobertura_aprobada(cobertura)
    candidatos = []
    sin_coordenadas = []
    fuera_urbano = []
    sin_nombre = []
    claves_encontradas: dict[str, int] = {}
    prototipos = {clave(n): n for n in PROTOTIPO_LEGADO}
    adicionales = {clave(n) for n in ADICIONALES_PENDIENTES}
    posibles_adicionales_no_autorizadas = []
    conflictos: set[int] = set()
    for sid, registro in sorted(estaciones.items()):
        nombre = registro.get('nombre_estacion')
        lat = numero_coordenada(registro.get('latitud'), 90)
        lon = numero_coordenada(registro.get('longitud'), 180)
        if lat is None or lon is None or (lon == 0 and lat == 0):
            sin_coordenadas.append(sid)
            continue  # Su presencia en el departamento NO acredita que esté en Tarija ciudad.
        if not dentro_cobertura(cobertura, lon, lat):
            fuera_urbano.append(sid)
            continue
        if not isinstance(nombre, str) or not nombre.strip():
            sin_nombre.append(sid)
            continue
        llave = clave(nombre)
        if llave in adicionales:
            posibles_adicionales_no_autorizadas.append(sid)
            continue  # Incluso si está en ANH, T-089 no autoriza las seis adicionales.
        if llave in claves_encontradas:
            conflictos.update((sid, claves_encontradas[llave]))
            continue
        claves_encontradas[llave] = sid
        candidatos.append({
            'estacion_id_anh': str(sid),
            'nombre': nombre.strip(),        # Nombre reportado por ANH, sin renombrar.
            'nombre_prototipo': prototipos.get(llave, ''),  # Vacío si hace falta conciliación manual.
            'aliases': '',                   # La API no acredita sobrenombres de WhatsApp.
            'longitud': repr(lon),
            'latitud': repr(lat),
            'fuente_ubicacion': fuente_ubicacion_registro(registro),
            'revision_franz': 'NO',          # No suplantar revisión humana.
            'aprobado_equipo': 'NO',
            'decision': 'PENDIENTE',
            'motivo_exclusion': '',
            'sha256_snapshot_anh': sha256,
        })
    candidatos = [c for c in candidatos if int(c['estacion_id_anh']) not in conflictos]
    # Auditar ANTES de publicar el listado, y nunca permitir un CSV previo con datos viejos
    # cuando falla la validación.
    informe = {
        'version': 1,
        'fuente': 'ANH Abastecimiento, cliente T-115 v2.2, extracción verificada',
        'sha256_snapshot_anh': sha256,
        'observado_en_utc': datos['observado_en_utc'],
        'alcance_api': 'departamento Tarija',
        'alcance_csv': 'puntos dentro de cobertura URBANA aprobada; todavía sujetos a revisión',
        'estaciones_en_snapshot': datos['estaciones_distintas'],
        'estaciones_con_identidad_consistente': len(estaciones),
        'candidatas_urbanas_sin_aprobar': len(candidatos),
        'ids_sin_coordenadas': sin_coordenadas,
        'ids_fuera_zona_urbana': fuera_urbano,
        'ids_sin_nombre': sin_nombre,
        'ids_nombres_normalizados_ambiguos': sorted(conflictos),
        'ids_adicionales_fuera_de_alcance_t089': posibles_adicionales_no_autorizadas,
        'nombres_prototipo_sin_match_exacto': sorted(set(PROTOTIPO_LEGADO) -
            {c['nombre_prototipo'] for c in candidatos if c['nombre_prototipo']}),
        'ids_pendientes_conciliacion_con_prototipo': [int(c['estacion_id_anh'])
            for c in candidatos if not c['nombre_prototipo']],
        'incidencias': incidencias,
        'alias_colloquiales': None,
        'motivo_alias_vacios': 'La API de ANH no publica alias conversacionales; ningún alias se adivina.',
        'aprobacion_equipo': None,
        'salida_migracion': None,
    }
    csv_destino.parent.mkdir(parents=True, exist_ok=True)
    informe_destino.parent.mkdir(parents=True, exist_ok=True)
    # Evitar exponer resultados parcialmente creados si hay excepción de escritura.
    csv_temp = csv_destino.with_name(csv_destino.name + '.tmp')
    audit_temp = informe_destino.with_name(informe_destino.name + '.tmp')
    try:
        with csv_temp.open('w', encoding='utf-8', newline='') as archivo:
            writer = csv.DictWriter(archivo, fieldnames=COLUMNAS)
            writer.writeheader()
            writer.writerows(candidatos)
        audit_temp.write_text(json.dumps(informe, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        csv_temp.replace(csv_destino)
        audit_temp.replace(informe_destino)
    finally:
        csv_temp.unlink(missing_ok=True)
        audit_temp.unlink(missing_ok=True)
    return informe


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--datos-anh', type=Path, required=True, help='datos_tarija.json obtenido con TLS verificado')
    parser.add_argument('--geojson', type=Path, required=True, help='mancha urbana APROBADA por Marcelo')
    parser.add_argument('--csv-salida', type=Path, required=True)
    parser.add_argument('--informe-salida', type=Path, required=True)
    args = parser.parse_args()
    res = preparar(args.datos_anh, args.geojson, args.csv_salida, args.informe_salida)
    print('Candidatas urbanas: {}; sin coordenadas: {}; fuera de cobertura: {}; conflictos: {}'.format(
        res['candidatas_urbanas_sin_aprobar'], len(res['ids_sin_coordenadas']),
        len(res['ids_fuera_zona_urbana']), len(res['ids_nombres_normalizados_ambiguos'])))
    print('No se ha aprobado ninguna estación ni generado migración.')


if __name__ == '__main__':
    main()
