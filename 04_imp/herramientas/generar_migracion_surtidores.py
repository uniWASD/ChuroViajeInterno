"""Genera la V2 de Flyway EXCLUSIVAMENTE desde estación ANH y aprobación humana.

Ningún CSV del prototipo (con coordenadas guía o apodos no verificados)
puede atravesar este validador; el destino queda intacto si una prueba falla.
"""
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

from validacion_anh_t089 import (ADICIONALES_PENDIENTES, PROTOTIPO_LEGADO,
                                  clave, dentro_cobertura, numero_coordenada,
                                  validar_cobertura_aprobada, verificar_snapshot_anh, fuente_ubicacion_registro)


def literal(texto: str) -> str:
    return "'" + texto.replace("'", "''") + "'"


def validar_acta_21(acta: Path, nombres_incluidos: set[str]) -> None:
    """Toda la lista del prototipo debe tener INCLUIR/EXCLUIR y doble revisión."""
    with acta.open(encoding='utf-8-sig', newline='') as archivo:
        lector = csv.DictReader(archivo)
        if not lector.fieldnames or not {
                'nombre', 'revision_franz', 'aprobado_equipo', 'decision',
                'motivo_exclusion'}.issubset(lector.fieldnames):
            raise ValueError('Acta del prototipo: columnas obligatorias ausentes')
        filas = list(lector)
    if len(filas) != len(PROTOTIPO_LEGADO) or {r['nombre'] for r in filas} != set(PROTOTIPO_LEGADO):
        raise ValueError('El acta debe resolver EXACTAMENTE las 21 filas del prototipo')
    for fila in filas:
        nombre = fila['nombre']
        if fila['revision_franz'].strip().upper() != 'SI' or fila['aprobado_equipo'].strip().upper() != 'SI':
            raise ValueError(f'Acta del prototipo sin doble aprobación: {nombre}')
        decision = fila['decision'].strip().upper()
        if decision == 'EXCLUIR':
            if not fila['motivo_exclusion'].strip() or nombre in nombres_incluidos:
                raise ValueError(f'Acta del prototipo: exclusión incoherente de {nombre}')
        elif decision == 'INCLUIR':
            if nombre not in nombres_incluidos or fila['motivo_exclusion'].strip():
                raise ValueError(f'Acta del prototipo: inclusión sin ANH/conciliación: {nombre}')
        else:
            raise ValueError(f'Acta del prototipo con decisión pendiente: {nombre}')


def generar(archivo_csv: Path, archivo_geojson: Path, archivo_api_json: Path,
            acta_prototipo: Path, destino: Path) -> tuple[int, int]:
    cobertura = json.loads(archivo_geojson.read_text(encoding='utf-8'))
    validar_cobertura_aprobada(cobertura)
    _, estaciones, sha256, _ = verificar_snapshot_anh(archivo_api_json)
    with archivo_csv.open(encoding='utf-8-sig', newline='') as entrada:
        lector = csv.DictReader(entrada)
        obligatorias = {'estacion_id_anh', 'nombre', 'nombre_prototipo', 'aliases', 'longitud', 'latitud',
                        'fuente_ubicacion', 'revision_franz', 'aprobado_equipo', 'decision',
                        'motivo_exclusion', 'sha256_snapshot_anh'}
        if not lector.fieldnames or not obligatorias.issubset(lector.fieldnames):
            raise ValueError('Formato CSV inválido: use preparar_revision_anh.py')
        filas = list(lector)
    if not filas:
        raise ValueError('Sin surtidores aprobados: prohibido crear una V2 vacía')

    seleccionados = []
    nombres: set[str] = set()
    prototipos_conciliados: set[str] = set()
    ids: set[int] = set()
    aliases: dict[str, int] = {}
    for fila in filas:
        try:
            sid = int(fila['estacion_id_anh'])
        except (ValueError, TypeError) as exc:
            raise ValueError('Estación sin ID ANH verificable') from exc
        if sid in ids or sid not in estaciones:
            raise ValueError(f'ID ANH duplicado o ausente del snapshot: {sid}')
        ids.add(sid)
        reg = estaciones[sid]
        if fila['sha256_snapshot_anh'] != sha256:
            raise ValueError(f'Snapshot ANH cambió desde la revisión de estación {sid}')
        if (fila['nombre'] != reg.get('nombre_estacion') or not fila['nombre'].strip()
                or fila['fuente_ubicacion'] != fuente_ubicacion_registro(reg)):
            raise ValueError(f'Estación {sid}: nombre/fuente no coinciden con la API ANH')
        lat = numero_coordenada(reg.get('latitud'), 90)
        lon = numero_coordenada(reg.get('longitud'), 180)
        if lat is None or lon is None or (lat == 0 and lon == 0):
            raise ValueError(f'Estación {sid}: la API no proporciona coordenadas utilizables')
        try:
            misma_posicion = (float(fila['longitud']) == lon and float(fila['latitud']) == lat)
        except (ValueError, TypeError) as exc:
            raise ValueError(f'Estación {sid}: coordenadas vacías') from exc
        if not misma_posicion:
            raise ValueError(f'Estación {sid}: las coordenadas no coinciden con la API ANH')
        if not dentro_cobertura(cobertura, lon, lat):
            raise ValueError(f'Estación {sid} fuera de la cobertura urbana APROBADA de Tarija')
        if clave(fila['nombre']) in {clave(n) for n in ADICIONALES_PENDIENTES}:
            raise ValueError(f'Estación {sid}: propuesta adicional expresamente fuera del alcance T-089')
        # No se tienen alias coloquiales en ANH. Solo nombre oficial normalizado,
        # como clave de búsqueda derivada y documentada; NO inventar sobrenombres.
        if fila['aliases'].strip():
            raise ValueError(f'Estación {sid}: el cliente ANH no acredita alias; deje este campo vacío')
        decision = fila['decision'].strip().upper()
        if decision == 'PENDIENTE':
            # Publicación parcial necesita decisión humana; no ignorar registros inconclusos.
            raise ValueError(f'Estación {sid}: decisión pendiente de revisión')
        if (fila['revision_franz'].strip().upper() != 'SI'
                or fila['aprobado_equipo'].strip().upper() != 'SI'):
            raise ValueError(f'Estación {sid}: falta revisión de Franz o aprobación de Marcelo')
        if decision == 'EXCLUIR':
            if not fila['motivo_exclusion'].strip():
                raise ValueError(f'Estación {sid}: exclusión sin motivo')
            continue
        if decision != 'INCLUIR':
            raise ValueError(f'Estación {sid}: decisión no válida: {decision}')
        if fila['motivo_exclusion'].strip():
            raise ValueError(f'Estación {sid}: motivo de exclusión en fila incluida')
        nombre_prototipo = fila['nombre_prototipo'].strip()
        if nombre_prototipo not in PROTOTIPO_LEGADO:
            raise ValueError(f'Estación {sid}: falta conciliación con uno de los 21 surtidores del prototipo')
        if nombre_prototipo in prototipos_conciliados:
            raise ValueError(f'Estación {sid}: prototipo duplicado en conciliación: {nombre_prototipo}')
        prototipos_conciliados.add(nombre_prototipo)
        nombre = fila['nombre']
        if len(nombre) > 120 or any(ord(caracter) < 32 for caracter in nombre):
            raise ValueError(f'Estación {sid}: nombre ANH supera el límite VARCHAR(120) o contiene controles')
        if nombre in nombres:
            raise ValueError(f'Nombre oficial duplicado para IDs distintos: {nombre}')
        nombres.add(nombre)
        alias_oficial = clave(nombre)
        anterior = aliases.get(alias_oficial)
        if anterior is not None and anterior != sid:
            raise ValueError(f'Nombres normalizados ambiguos: ANH ids {anterior} y {sid}')
        if len(alias_oficial) > 120:
            raise ValueError(f'Estación {sid}: clave normalizada supera VARCHAR(120)')
        aliases[alias_oficial] = sid
        seleccionados.append((sid, nombre, lon, lat))
    validar_acta_21(acta_prototipo, prototipos_conciliados)
    if not seleccionados:
        raise ValueError('Sin surtidores verificados APROBADOS; prohibido crear una V2 vacía')
    # Estaciones posteriores a la revisión NO se agregan automáticamente.
    lineas = [
        '-- T-089 / Issue #41: estaciones urbanas de Tarija extraídas de ANH,',
        '-- georreferenciadas y aprobadas por Franz y Marcelo antes de generar.',
        f'-- SHA-256 del JSON ANH usado como fuente: {sha256}',
        '-- Coordenadas según API ANH [longitud, latitud] para PostGIS 4326.',
        '-- No contiene saldo, estado ni alias conversacionales no reportados por ANH.',
    ]
    for sid, nombre, lon, lat in seleccionados:
        lineas.extend([
            f'-- ANH estacion_id={sid}',
            f'INSERT INTO surtidor (nombre, ubicacion) VALUES ({literal(nombre)}, '
            f'ST_SetSRID(ST_MakePoint({repr(lon)}, {repr(lat)}), 4326)::geography);',
        ])
    for alias, sid in sorted(aliases.items()):
        nombre = next(n for identificador, n, _, _ in seleccionados if identificador == sid)
        lineas.append(
            'INSERT INTO surtidor_alias (surtidor_id, alias) '
            f'SELECT id, {literal(alias)} FROM surtidor WHERE nombre = {literal(nombre)};'
        )
    if destino.exists():
        raise FileExistsError('Migración V2 existente: Flyway prohíbe sobrescribir migraciones publicadas; revise el historial')
    destino.parent.mkdir(parents=True, exist_ok=True)
    temp = destino.with_name(destino.name + '.tmp')
    try:
        temp.write_text('\n'.join(lineas) + '\n', encoding='utf-8')
        temp.replace(destino)
    finally:
        temp.unlink(missing_ok=True)
    return len(seleccionados), len(aliases)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--csv', type=Path, required=True, help='CSV preparado desde ANH, luego aprobado')
    parser.add_argument('--geojson', type=Path, required=True, help='cobertura aprobada')
    parser.add_argument('--api-json', type=Path, required=True, help='mismo JSON ANH que originó el CSV')
    parser.add_argument('--acta-prototipo', type=Path, required=True, help='21 entradas revisadas por Franz y Marcelo')
    parser.add_argument('--salida', type=Path, required=True)
    args = parser.parse_args()
    cantidad, aliases = generar(args.csv, args.geojson, args.api_json, args.acta_prototipo, args.salida)
    print(f'V2 preparada desde ANH con {cantidad} estaciones y {aliases} claves oficiales normalizadas.')


if __name__ == '__main__':
    main()
