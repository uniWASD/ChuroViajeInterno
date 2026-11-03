#!/usr/bin/env python3
"""Diagnóstico abreviado T-117 para CSV reales de local y Oracle.

No sustituye CP-157/72 h, no certifica P-29/P-30/P-32 y no edita arquitectura.
Solo usa biblioteca estándar y no hace conexiones de red.
"""
from __future__ import annotations
import argparse
import csv
import json
import statistics
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

from analizar_medicion import (read_csv, read_properties, utc, percentile,
                              REQUIRED_MED, REQUIRED_STA, OFFICIAL)


def verificar_mediciones(carpeta: Path, origen: str) -> dict:
    if origen not in ('local', 'oracle'):
        raise ValueError('Origen desconocido')
    m = read_csv(carpeta / f'medicion-{origen}.csv', REQUIRED_MED)
    e = read_csv(carpeta / f'estaciones-{origen}.csv', REQUIRED_STA)
    estado = read_properties(carpeta / 'estado.properties')
    if estado.get('origen') != origen:
        raise ValueError(f'{carpeta}: origen del estado incompatible con {origen}')
    if not m:
        raise ValueError(f'{carpeta}: todavía no hay peticiones registradas')
    # Fecha/fin por petición, identificador único y asociación de estaciones.
    inicios = [utc(r['hora_utc']) for r in m]
    fines = [utc(r['fin_utc']) for r in m]
    if inicios != sorted(inicios):
        raise ValueError('Solicitudes desordenadas por hora')
    if any(fin < inicio for inicio, fin in zip(inicios, fines)):
        raise ValueError('Una solicitud termina antes de iniciarse')
    ids = [r['consulta_id'] for r in m]
    if not all(ids) or len(ids) != len(set(ids)):
        raise ValueError('ID de consulta vacío o duplicado')
    por_id = {r['consulta_id']: r for r in m}
    por_consulta = Counter()
    claves_estacion = set()
    for row in e:
        consulta = por_id.get(row['consulta_id'])
        if consulta is None or consulta['tipo'] != 'estaciones' or consulta['estado_http'] != '200' or consulta['error_codigo']:
            raise ValueError('Estación asociada a una petición desconocida o fallida')
        if utc(row['hora_utc']) != utc(consulta['hora_utc']):
            raise ValueError('Hora de estación incompatible con petición')
        if row['producto_id'] != consulta['producto']:
            raise ValueError('Producto de estación incompatible')
        if row['zona_en_updated_at'] not in {'SI','NO','AUSENTE','INVALIDO'}:
            raise ValueError('Clasificación de updated_at desconocida')
        key = (row['consulta_id'], row['id_estacion'], row['producto_id'])
        if key in claves_estacion:
            raise ValueError('Fila duplicada de estación en una petición')
        claves_estacion.add(key)
        por_consulta[row['consulta_id']] += 1
    for r in m:
        try: lat = float(r['tiempo_ms'])
        except ValueError: raise ValueError('Latencia no numérica') from None
        if lat < 0 or not (lat < float('inf')):
            raise ValueError('Latencia fuera de rango')
        if r['tipo'] not in {'catalogo','estaciones'} or r['producto'] not in {'0','1'}:
            raise ValueError('Tipo/producto incorrecto')
        if r['tipo'] == 'estaciones' and r['estado_http'] == '200' and not r['error_codigo']:
            if r['n_estaciones'] != str(por_consulta[r['consulta_id']]):
                raise ValueError('n_estaciones y filas CSV no coinciden')
    pausas = [(inicio-fin).total_seconds() for fin, inicio in zip(fines, inicios[1:])]
    # Precisamente cada primer GET catálogo producto 0 intento 1 define un nuevo ciclo.
    ciclos = [utc(r['hora_utc']) for r in m if (r['tipo'], r['producto'], r['intento']) == ('catalogo','0','1')]
    ciclo_gaps = [(sig-ant).total_seconds() for ant, sig in zip(ciclos, ciclos[1:])]
    errores = [r for r in m if r['error_codigo'] or r['estado_http'] != '200']
    latencias = [float(r['tiempo_ms']) for r in m]
    por_http = Counter((r['estado_http'] or 'sin_http') for r in m)
    por_zona = Counter(row['zona_en_updated_at'] for row in e)
    por_clave = defaultdict(list)
    for row in e:
        por_clave[(row['id_estacion'], row['producto_id'])].append((utc(row['hora_utc']), row['saldo'], row['updated_at']))
    cambios_saldo = cambios_updated = 0
    for valores in por_clave.values():
        valores.sort()
        for anterior, siguiente in zip(valores, valores[1:]):
            if anterior[1] and siguiente[1] and anterior[1] != siguiente[1]: cambios_saldo += 1
            if anterior[2] and siguiente[2] and anterior[2] != siguiente[2]: cambios_updated += 1
    real = estado.get('pruebas') == 'false' and estado.get('base_url') == OFFICIAL
    # La etiqueta de origen NO es prueba criptográfica de que la ejecución sucedió en Oracle.
    pruebas = {
        'ids_validos': True,
        'referencias_estaciones_validas': True,
        'datos_utc_validos': True,
        'pausas_entre_peticiones_de_1s': all(x >= 0.99 for x in pausas),
        'ciclos_separados_10min': all(x >= 598 for x in ciclo_gaps),
        'tiene_catalogo': bool(ciclos),
        'peticion_estaciones_gasolina': any(r['tipo']=='estaciones' and r['producto']=='0' for r in m),
        'peticion_estaciones_diesel': any(r['tipo']=='estaciones' and r['producto']=='1' for r in m),
        'registros_estaciones_presentes': bool(e)
    }
    result = {
        'origen_declarado': origen,
        'tipo_registro': 'real_declarado' if real else 'simulado_o_no_verificable',
        'archivo_validado': True,
        'primera_peticion_utc': inicios[0].isoformat(),
        'ultima_peticion_utc': inicios[-1].isoformat(),
        'horas_observadas': round((fines[-1] - inicios[0]).total_seconds()/3600, 4),
        'ciclos_observados': len(ciclos),
        'peticiones_registradas': len(m),
        'estaciones_registradas': len(e),
        'pares_estacion_producto': len(por_clave),
        'respuestas_http': dict(por_http),
        'cantidad_errores': len(errores),
        'latencia_media_ms': round(statistics.mean(latencias), 2),
        'latencia_p95_ms': percentile(latencias,95),
        'latencia_max_ms': round(max(latencias), 2),
        'pausa_minima_segundos': round(min(pausas), 3) if pausas else None,
        'cambios_saldo_observados': cambios_saldo,
        'cambios_updated_at_observados': cambios_updated,
        'zonas_updated_at': dict(por_zona),
        'retry_after': [{'http':r['estado_http'],'valor':r['retry_after']} for r in m if r['retry_after']],
        'controles': pruebas,
        'prueba_corta_completa': real and not errores and all(pruebas.values()),
        'CP-157_apto': False,
    }
    return result


def construir_informe(local: dict, oracle: dict | None) -> tuple[dict,str]:
    evaluadas = {'local':local}
    if oracle is not None: evaluadas['oracle'] = oracle
    completo = bool(oracle) and local['prueba_corta_completa'] and oracle['prueba_corta_completa']
    resultado = {
        'modalidad':'DIAGNOSTICO_ABREVIADO',
        'prueba_funcional_y_conectividad_satisfactoria': bool(completo),
        'CP-157_apto': False,
        'CP-158_apto': False,
        'P-29_estado': 'NO_CONFIRMADO_por_muestreo_corto',
        'P-30_estado': 'NO_CONFIRMADO_por_muestreo_corto',
        'P-32_estado': 'NO_CONFIRMADO_por_muestreo_corto',
        'advertencia':'No equivale a 72 horas reales. No confirma frecuencia de actualización, vigencia ni disponibilidad a largo plazo.',
        'etapas': evaluadas,
    }
    lines = [
        '# T-117 — Informe de diagnóstico abreviado', '',
        '**Modalidad:** pruebas simuladas + observaciones reales breves en dos entornos (cuando existan).',
        '**CP-157:** NO acreditado; la especificación exige 48 h locales + 24 h en Oracle.',
        '**CP-158:** NO acreditado; P-29, P-30 y P-32 siguen pendientes de evidencia/decisión.',
        '**Precaución:** los datos simulados NO son observaciones de ANH y las etiquetas de origen dependen de trazabilidad externa.', '',
    ]
    for clave, val in evaluadas.items():
        lines += [f'## {clave}',
                  f'- Tipo de registros: **{val["tipo_registro"]}**.',
                  f'- Primer y último GET UTC: `{val["primera_peticion_utc"]}` / `{val["ultima_peticion_utc"]}`.',
                  f'- Ciclos: {val["ciclos_observados"]}; peticiones: {val["peticiones_registradas"]}; estaciones registradas: {val["estaciones_registradas"]}.',
                  f'- Códigos HTTP: {val["respuestas_http"]}; errores: {val["cantidad_errores"]}.',
                  f'- Latencia media: {val["latencia_media_ms"]} ms; p95: {val["latencia_p95_ms"]} ms; máxima: {val["latencia_max_ms"]} ms.',
                  f'- Pausa mínima entre solicitudes: {val["pausa_minima_segundos"]} s; criterios de integridad: {val["controles"]}.',
                  f'- `updated_at` por clasificación: {val["zonas_updated_at"]}; cambios observados de saldo: {val["cambios_saldo_observados"]}; de `updated_at`: {val["cambios_updated_at_observados"]}.',
                  f'- **Diagnóstico breve satisfactorio:** {"SÍ" if val["prueba_corta_completa"] else "NO / REVISAR"}.', '']
    if oracle:
        delta = round(oracle['latencia_media_ms'] - local['latencia_media_ms'],2)
        lines += ['## Comparación entre entornos',
                  f'- Diferencia de latencia media Oracle menos local: **{delta} ms**.',
                  '- La comparación es descriptiva: una ronda no demuestra el percentil de latencia ni la estabilidad de ANH.', '']
    else:
        lines += ['## Comparación entre entornos', '- Pendiente cargar la carpeta con la única ronda real ejecutada en Oracle.', '']
    lines += ['## Decisiones de ingeniería',
              '- P-29: no derivar un periodo de actualización ANH a partir de una o dos rondas.',
              '- P-30: las seis horas de RN-05.5 siguen como valor de partida, NO confirmadas mediante este diagnóstico.',
              '- P-32: HTTP, latencias y reintentos de pocas rondas son indicios funcionales, no una caracterización de colas ni tasas de fallos.',
              '- Los casos 429/Retry-After se verifican con un servidor simulado, sin provocar 429 de la API externa.',
              '- Solicitar al equipo que acepte explícitamente cualquier modificación del criterio de CP-157.',
              '- No modificar `03_des/02_Arquitectura/` con el actualizador de parámetros a partir de este informe.', '']
    return resultado, '\n'.join(lines)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--local', required=True, type=Path)
    ap.add_argument('--oracle', type=Path)
    ap.add_argument('--salida', required=True, type=Path)
    args = ap.parse_args()
    try:
        local = verificar_mediciones(args.local, 'local')
        oracle = verificar_mediciones(args.oracle, 'oracle') if args.oracle else None
        result, md = construir_informe(local, oracle)
        args.salida.mkdir(parents=True,exist_ok=True)
        (args.salida/'resumen_diagnostico_corto.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        (args.salida/'informe_diagnostico_corto.md').write_text(md+'\n',encoding='utf-8')
        print(json.dumps({'informe':str(args.salida/'informe_diagnostico_corto.md'),
                          'local_ok':local['prueba_corta_completa'],
                          'oracle_ok':oracle['prueba_corta_completa'] if oracle else 'pendiente',
                          'CP-157_apto':False},ensure_ascii=False))
    except (FileNotFoundError, OSError, ValueError, KeyError) as exc:
        ap.exit(2, 'Diagnóstico no realizado: '+str(exc)+'\n')


if __name__ == '__main__':
    main()
