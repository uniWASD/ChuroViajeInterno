#!/usr/bin/env python3
"""T-117: analítica reproducible CP-157/CP-158, sin datos inventados.
Admite registros reales de dos ubicaciones; RECHAZA pruebas loopback para confirmar.
"""
import argparse
import csv
import json
import math
import statistics
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

OFFICIAL = 'https://abastecimiento.prod.anh.gob.bo/api/'
REQUIRED_MED = {'consulta_id','hora_utc','fin_utc','tipo','producto','intento','estado_http',
                'tiempo_ms','n_estaciones','retry_after','error_codigo'}
REQUIRED_STA = {'consulta_id','hora_utc','id_estacion','producto_id','saldo','updated_at','zona_en_updated_at'}


def utc(s):
    dt = datetime.fromisoformat(s.replace('Z','+00:00'))
    if dt.tzinfo is None: raise ValueError('Se exige fecha UTC con zona.')
    if dt.utcoffset() != timedelta(): raise ValueError('Se exige UTC, no una hora local.')
    return dt


def read_csv(path, required):
    if not path.is_file(): raise FileNotFoundError(path)
    with path.open(newline='', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        if not reader.fieldnames or not required.issubset(reader.fieldnames):
            raise ValueError(f'CSV incompatible: {path}')
        return list(reader)


def _unescape_java_property(value):
    r"""Convierte escapes que emite java.util.Properties.store(Writer).

    Sin este paso se interpretan mal fechas ISO (`T23\:21`) y base_url
    (`https\://...`) y se rechazan corridas reales válidas.
    """
    out = []
    i = 0
    escapes = {'t': '\t', 'r': '\r', 'n': '\n', 'f': '\f'}
    while i < len(value):
        char = value[i]
        if char != "\\":
            out.append(char)
            i += 1
            continue
        if i + 1 >= len(value):
            raise ValueError('Escape final inválido en archivo .properties')
        next_char = value[i + 1]
        if next_char == 'u':
            if i + 6 > len(value):
                raise ValueError('Unicode incompleto en archivo .properties')
            try:
                out.append(chr(int(value[i+2:i+6],16)))
            except ValueError:
                raise ValueError('Unicode inválido en archivo .properties') from None
            i += 6
        else:
            out.append(escapes.get(next_char, next_char))
            i += 2
    return ''.join(out)


def read_properties(path):
    """Lee el subconjunto estándar key=value generado por Java Properties.store."""
    lines = path.read_text(encoding='utf-8').splitlines()
    values = {}
    for line in lines:
        if not line.strip() or line.lstrip().startswith(('#','!')) or '=' not in line:
            continue
        k, v = line.split('=', 1)
        values[_unescape_java_property(k.strip())] = _unescape_java_property(v.strip())
    return values


def percentile(values, pct):
    if not values: return None
    values = sorted(values)
    index = (len(values)-1) * pct/100
    lo = int(index); hi = math.ceil(index)
    return round(values[lo] + (values[hi]-values[lo])*(index-lo), 2)


def evaluate(dir, origin, hours):
    med = read_csv(dir / f'medicion-{origin}.csv', REQUIRED_MED)
    st = read_csv(dir / f'estaciones-{origin}.csv', REQUIRED_STA)
    props = read_properties(dir / 'estado.properties')
    if props.get('origen') != origin or props.get('duracion_horas') != str(hours):
        raise ValueError(f'La carpeta de {origin} tiene un origen o duración incompatibles.')
    declared_start = utc(props['inicio_utc'])
    declared_end = declared_start + timedelta(hours=hours)
    times = [utc(r['hora_utc']) for r in med]
    finishes = [utc(r['fin_utc']) for r in med]
    if times != sorted(times): raise ValueError(f'Fechas no ordenadas en {origin}.')
    if len({r['consulta_id'] for r in med}) != len(med): raise ValueError('ID de consulta duplicado.')
    if any(end < start for start, end in zip(times, finishes)):
        raise ValueError('Hora de fin anterior al inicio.')
    if any(t < declared_start - timedelta(seconds=2) or t > declared_end + timedelta(minutes=1) for t in times):
        raise ValueError('Peticiones fuera de la ventana declarada.')
    # Un ciclo empieza por un catálogo producto 0. Los catálogos de reintento/fallback no crean ciclo.
    starts = [utc(r['hora_utc']) for r in med if r['tipo']=='catalogo' and r['producto']=='0' and r['intento']=='1']
    gaps = [(b-a).total_seconds() for a,b in zip(starts, starts[1:])]
    request_pauses = [(b-a).total_seconds() for a,b in zip(finishes, times[1:])]
    overlap = all(p >= 0.99 for p in request_pauses)
    counters = Counter(r['estado_http'] or 'sin_http' for r in med)
    errors = sum(bool(r['error_codigo']) or r['estado_http'] != '200' for r in med)
    okstations = {r['consulta_id'] for r in med if r['tipo']=='estaciones' and
                  r['estado_http']=='200' and not r['error_codigo']}
    rows = [r for r in st if r['consulta_id'] in okstations]
    by_station = defaultdict(list)
    for r in rows:
        when = utc(r['hora_utc'])
        key = (r['id_estacion'], r['producto_id'])
        by_station[key].append((when, r['saldo'], r['updated_at']))
    intervals_saldo, intervals_updated = [], []
    changes_saldo = changes_updated = 0
    for values in by_station.values():
        values.sort()
        for i in range(1, len(values)):
            prev, new = values[i-1], values[i]
            if prev[1] != '' and new[1] != '' and prev[1] != new[1]:
                changes_saldo += 1
                intervals_saldo.append(round((new[0]-prev[0]).total_seconds()/60, 2))
            if prev[2] != '' and new[2] != '' and prev[2] != new[2]:
                changes_updated += 1
                intervals_updated.append(round((new[0]-prev[0]).total_seconds()/60, 2))
    durations = [float(r['tiempo_ms']) for r in med if r['tiempo_ms']]
    status_tz = Counter(r['zona_en_updated_at'] for r in rows)
    duration_ok = datetime.now(timezone.utc) >= declared_end
    coverage_ok = bool(starts) and (starts[0]-declared_start).total_seconds() <= 10*60 and (
        declared_end - starts[-1]).total_seconds() <= 20*60
    count_ok = len(starts) >= math.floor(hours*6*.9)
    gap_ok = all(598 <= g <= 1200 for g in gaps)
    real = props.get('base_url') == OFFICIAL and props.get('pruebas') == 'false'
    sufficient = real and duration_ok and coverage_ok and count_ok and gap_ok and overlap and bool(rows)
    return {
        'origen': origin, 'horas_objetivo':hours, 'inicio': declared_start.isoformat(),
        'fin_programado':declared_end.isoformat(), 'horas_cubiertas': (times[-1]-times[0]).total_seconds()/3600 if len(times)>1 else 0,
        'es_entorno_real':real, 'ventana_terminada':duration_ok, 'ciclos':len(starts), 'peticiones':len(med),
        'estaciones_registros':len(rows), 'estaciones_distintas':len(by_station),
        'http':dict(counters), 'errores':errors,
        'tasa_error_pct': round(100*errors/len(med),2) if med else 0,
        'latencia_media_ms':round(statistics.mean(durations),2) if durations else None,
        'latencia_max_ms': max(durations) if durations else None,
        'latencia_p95_ms':percentile(durations,95),
        'cambios_saldo':changes_saldo, 'cambios_updated_at':changes_updated,
        'intervalos_observados_cambios_saldo_min':intervals_saldo,
        'intervalos_observados_cambios_updated_at_min':intervals_updated,
        'zona_updated_at':dict(status_tz),
        'retry_after_observados':[{'http':r['estado_http'],'valor':r['retry_after']} for r in med if r['retry_after']],
        'pausa_peticion_min_s':round(min(request_pauses),3) if request_pauses else None,
        'gap_max_ciclo_min':round(max(gaps)/60,2) if gaps else None,
        'control_cobertura': {'primera_ultima':coverage_ok,'cantidad_ciclos':count_ok,
                             'cadencia':gap_ok,'pausa':overlap,'hay_filas_estacion':bool(rows)},
        'etapa_valida':sufficient,
        'primer_ciclo_utc':starts[0].isoformat() if starts else None,
        'ultimo_ciclo_utc':starts[-1].isoformat() if starts else None,
    }


def report(args):
    root = Path(args.salida)
    root.mkdir(parents=True,exist_ok=True)
    a = evaluate(Path(args.local), 'local', 48)
    b = evaluate(Path(args.oracle), 'oracle', 24)
    handoff = abs((utc(b['inicio'])-utc(a['fin_programado'])).total_seconds()) <= 600
    ready = a['etapa_valida'] and b['etapa_valida'] and handoff
    full = {'CP-157_apto':ready, 'continuidad_transicion_hasta_10min':handoff,
            'nota':'El análisis automático no prueba la autenticidad del origen; exige verificar evidencias y autorizar decisiones.',
            'etapas': {'local':a, 'oracle':b}}
    (root/'resumen_medicion.json').write_text(json.dumps(full,ensure_ascii=False,indent=2), encoding='utf-8')
    lines = ['# T-117 · Análisis de mediciones CP-157',
             '', f'**Cobertura documental suficiente:** {"SÍ, requiere revisión humana" if ready else "NO, pendiente"}.',
             'Los datos medidos se distinguen de las decisiones de arquitectura, que deben aprobarse después.', '']
    for r in (a,b):
        lines += [f'## {r["origen"]}',
                  f'- Inicio: {r["inicio"]}. Fin programado: {r["fin_programado"]}.',
                  f'- Ciclos: {r["ciclos"]}, peticiones: {r["peticiones"]}; HTTP: {r["http"]}.',
                  f'- Respuesta: promedio {r["latencia_media_ms"]} ms, p95 {r["latencia_p95_ms"]} ms, máximo {r["latencia_max_ms"]} ms.',
                  f'- Errores: {r["errores"]} ({r["tasa_error_pct"]}%). Retry-After: {r["retry_after_observados"]}.',
                  f'- Estaciones-clave: {r["estaciones_distintas"]}; cambios observados de saldo: {r["cambios_saldo"]}; cambios `updated_at`: {r["cambios_updated_at"]}.',
                  f'- Clasificación de `updated_at`: {r["zona_updated_at"]}.',
                  f'- Control de cobertura: {r["control_cobertura"]}; etapa apta: {r["etapa_valida"]}.', '']
    lines += ['## Decisiones pendientes de corroboración',
              '- **P-29:** medir frecuencia de cambios y elegir un ciclo que respete mínimo 10 minutos; la observación es discreta, no demuestra el instante exacto de cambio.',
              '- **P-30:** evaluar mantener o revisar la vigencia de 6 horas, contada desde la última lectura válida del backend; el cambio de `updated_at` por sí solo no prueba el tiempo de validez.',
              '- **P-32:** comparar latencias local/Oracle, errores y `Retry-After` antes de confirmar timeout/reintentos.',
              '- **Zona horaria:** si `updated_at` llega sin zona, CU-09 RN-09.13 ya fija la de Tarija (America/La_Paz). Confirmar la forma de los valores reales.',
              '- **Condiciones de uso:** se necesita evidencia del GET `/api/Disclaimer` y verificación de información institucional.',
              '', 'No elevar parámetros a «Confirmada» a partir de datos simulados o incompletos.']
    (root/'informe_medicion.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print(json.dumps({'CP-157_apto':ready,'local':a['etapa_valida'],'oracle':b['etapa_valida'],'informe':str(root/'informe_medicion.md')},ensure_ascii=False))
    return 0 if ready else 2


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--local',required=True,help='Carpeta salida medición local, no archivo CSV')
    parser.add_argument('--oracle',required=True,help='Carpeta salida medición Oracle, no archivo CSV')
    parser.add_argument('--salida',required=True,help='Carpeta para informe, fuera del repo')
    args=parser.parse_args()
    try: raise SystemExit(report(args))
    except (ValueError,FileNotFoundError,KeyError,OverflowError) as e:
        parser.exit(2,'Error en registros: '+str(e)+'\n')
