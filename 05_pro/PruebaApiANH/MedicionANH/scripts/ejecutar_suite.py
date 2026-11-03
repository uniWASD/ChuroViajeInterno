#!/usr/bin/env python3
"""T-117: ejecución reproducible, offline y desde cero; sin dependencias pip.

Ejecuta tests Java (servidor simulado loopback), generación de calendario virtual
con trazabilidad y análisis OPCIONAL de CSV reales preexistentes. Ninguna
prueba del conjunto efectúa una llamada a la API pública de ANH.
"""
import argparse
import csv
import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORIGIN = ROOT / 'scripts'


def run_checked(args, cwd, target, timeout=180):
    env = os.environ.copy()
    env['PYTHONIOENCODING'] = 'utf-8'
    try:
        result = subprocess.run(args, cwd=cwd, env=env, capture_output=True, text=True,
                                encoding='utf-8', errors='replace', timeout=timeout)
        output = (result.stdout or '') + ('\n' if result.stdout and result.stderr else '') + (result.stderr or '')
        target.write_text(output, encoding='utf-8')
        return result.returncode, output
    except subprocess.TimeoutExpired as exc:
        target.write_text('TIEMPO MÁXIMO DE PRUEBAS SUPERADO\n'+str(exc)+'\n', encoding='utf-8')
        return 124, target.read_text(encoding='utf-8')


def csv_rows(path):
    with path.open('r', encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream))


def check_virtual(data):
    if not (data/'ESTO_ES_SIMULADO.json').is_file():
        raise ValueError('Falta marcador explícito de simulación')
    marker = json.loads((data/'ESTO_ES_SIMULADO.json').read_text(encoding='utf-8'))
    if not any(word in marker['advertencia'].upper() for word in ('SIMUL', 'VIRTUALES')):
        raise ValueError('No se distingue evidencia simulada')
    from sys import path
    path.insert(0, str(ORIGIN))
    from diagnostico_corto import verificar_mediciones
    from analizar_medicion import evaluate
    summary = {}
    for origen, n in [('local',288), ('oracle',144)]:
        folder = data / ('SIMULADO-datos-' + origen)
        med = csv_rows(folder / ('medicion-' + origen + '.csv'))
        stations = csv_rows(folder / ('estaciones-' + origen + '.csv'))
        if len(med) != 3*n or len(stations) != 6*n:
            raise ValueError(f'Simulación {origen}: cantidad de filas incorrecta')
        d = verificar_mediciones(folder, origen)
        a = evaluate(folder, origen, 48 if origen=='local' else 24)
        if d['tipo_registro'] != 'simulado_o_no_verificable' or d['CP-157_apto'] or a['etapa_valida']:
            raise ValueError('La simulación se evaluó indebidamente como evidencia real')
        for campo in ('ids_validos','referencias_estaciones_validas','datos_utc_validos',
                      'pausas_entre_peticiones_de_1s','ciclos_separados_10min',
                      'tiene_catalogo','peticion_estaciones_gasolina','peticion_estaciones_diesel',
                      'registros_estaciones_presentes'):
            if not d['controles'][campo]:
                raise ValueError(f'Integridad de calendario {origen}: {campo}')
        summary[origen] = {'ciclos':n, 'peticiones':len(med), 'filas_estacion':len(stations),
                           'controles_integridad':'aprobados','origen':'SIMULADO'}
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--datos-local', type=Path, help='Directorio opcional de CSV locales previos')
    parser.add_argument('--salida', type=Path, help='Directorio del informe; defecto analisis/ejecucion-fecha')
    parser.add_argument('--solo-pruebas', action='store_true', help='No procesar CSV preexistentes')
    args = parser.parse_args()
    if sys.version_info < (3,10):
        parser.error('Python 3.10 o superior requerido')
    if not shutil.which('java'):
        parser.error('No se encontró Java (JRE 17+). No es necesario Maven.')
    if not (ROOT/'medicion-anh.jar').exists():
        parser.error('Falta medicion-anh.jar: extraer el ZIP COMPLETO.')
    now = dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    dest = (args.salida or (Path(tempfile.gettempdir())/'ChuroViaje_T117'/'resultados'/('ejecucion-'+now))).resolve()
    dest.mkdir(parents=True,exist_ok=True)
    result = {'modalidad':'offline_simulada', 'fuente_de_respuestas':'servidor HTTP loopback',
              'fecha_ejecucion_utc':dt.datetime.now(dt.timezone.utc).isoformat(),
              'contacto_API_ANH':False, 'CP-157_apto':False, 'CP-158_apto':False}
    try:
        print('T-117: ejecutando pruebas automáticas Java/Python contra HTTP simulado...', flush=True)
        code, log = run_checked([sys.executable,'-m','unittest','discover','-s','tests','-v'],
                                ROOT,dest/'PRUEBAS_DETALLADAS.txt', timeout=180)
        match = re.search(r'Ran\s+(\d+)\s+tests?',log)
        result['pruebas_ejecutadas'] = int(match.group(1)) if match else None
        result['pruebas_aprobadas'] = code == 0 and re.search(r'(?m)^OK\s*$',log) is not None
        if not result['pruebas_aprobadas']:
            raise ValueError('Fallaron las pruebas automatizadas: revisar PRUEBAS_DETALLADAS.txt')
        print('Pruebas aprobadas:',result['pruebas_ejecutadas'], flush=True)
        print('T-117: generando y verificando calendario virtual...', flush=True)
        virtual=dest/'SIMULACION_IDENTIFICADA'
        code, log = run_checked([sys.executable,str(ORIGIN/'simular_72h.py'),'--salida',str(virtual)],
                                ROOT,dest/'SIMULACION_EJECUCION.txt', timeout=50)
        if code: raise ValueError('Fallo al crear datos sintéticos: '+log[-400:])
        result['calendario_virtual'] = check_virtual(virtual)
        print('Calendario virtual: 288 ciclos local + 144 ciclos Oracle; verificación aprobada.',flush=True)
        data = args.datos_local
        if data is None and (ROOT/'datos-local'/'medicion-local.csv').is_file():
            data = ROOT/'datos-local'
        if not args.solo_pruebas and data is not None:
            data = data.resolve()
            print('Analizando registros locales existentes (sin consultarlos de nuevo)...',flush=True)
            code, log = run_checked([sys.executable,str(ORIGIN/'diagnostico_corto.py'),
                                     '--local',str(data),'--salida',str(dest/'DATOS_LOCALES')],
                                    ROOT,dest/'DIAGNOSTICO_LOCAL_EJECUCION.txt',timeout=40)
            result['datos_locales'] = {'ruta':str(data),'analizados':code==0}
            if code: raise ValueError('No se validaron los CSV preexistentes: '+log[-500:])
        else:
            result['datos_locales'] = {'analizados':False,
                'razon':'No se adjuntaron CSV reales; simulación ejecutada desde cero.'}
        result['estado_suite'] = 'APROBADA'
        returncode=0
    except Exception as exc:
        result['estado_suite']='FALLIDA'
        result['error']=str(exc)
        print('ERROR:',exc,file=sys.stderr)
        returncode=1
    (dest/'RESULTADO_T117.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    doc=['# T-117 — Resultado de ejecución', '',
         f'**Estado de pruebas:** {result["estado_suite"]}.',
         f'**Número de pruebas:** {result.get("pruebas_ejecutadas", "No disponible")}.',
         '**Fuente:** pruebas funcionales con servidor HTTP local y calendario sintético.',
         '**Peticiones a ANH:** ninguna durante esta modalidad.',
         '**Autenticidad:** ningún dato simulado constituye una observación de la API.',
         '', '**CP-157 y CP-158:** no acreditados por este ensayo abreviado.', '',
         'Para confirmar P-29/P-30/P-32 se necesitan observaciones reales suficientes y revisión del equipo.', '',
         '## Archivos generados',
         '- PRUEBAS_DETALLADAS.txt: resultado completo de los casos.',
         '- SIMULACION_IDENTIFICADA/: ciclo artificial para comprobar integridad temporal.',
         '- RESULTADO_T117.json: resultado estructurado.',
         '- DATOS_LOCALES/: análisis de CSV anteriores, si se proporcionaron.', '']
    if 'error' in result: doc += ['## Fallo observado',result['error'],'']
    (dest/'INFORME_T117.md').write_text('\n'.join(doc),encoding='utf-8')
    print('Informe guardado en:',dest,flush=True)
    return returncode


if __name__=='__main__':
    raise SystemExit(main())
