#!/usr/bin/env python3
"""Genera 72 horas VIRTUALES en segundos: NO CONTACTA ANH ni reemplaza CP-157.

Útil para comprobar funciones analíticas sobre 432 ciclos artificiales sin ocupar la laptop.
Las carpetas llevan el nombre SIMULADO y el estado declara pruebas=true.
"""
import argparse
import csv
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

HEAD_M = ['consulta_id','hora_utc','fin_utc','tipo','producto','intento','estado_http','tiempo_ms','n_estaciones',
          'retry_after','error_codigo','updated_at_con_zona','updated_at_sin_zona','updated_at_ausente','updated_at_invalido']
HEAD_E = ['consulta_id','hora_utc','id_estacion','producto_id','saldo','updated_at','zona_en_updated_at']
START = datetime(2026, 10, 1, tzinfo=timezone.utc)


def generar(base: Path):
    base.mkdir(parents=True, exist_ok=True)
    info = {}
    for origin, ciclos, shift in [('local', 288, 0),('oracle',144,288)]:
        target = base/f'SIMULADO-datos-{origin}'
        target.mkdir(exist_ok=True)
        with (target/f'medicion-{origin}.csv').open('w',encoding='utf-8',newline='') as fa, \
             (target/f'estaciones-{origin}.csv').open('w',encoding='utf-8',newline='') as fb:
            w = csv.DictWriter(fa, fieldnames=HEAD_M); w.writeheader()
            st = csv.DictWriter(fb, fieldnames=HEAD_E); st.writeheader()
            for cycle in range(ciclos):
                global_cycle = shift+cycle
                start = START + timedelta(seconds=global_cycle * 600)
                for request_index, (typ, product) in enumerate([('catalogo','0'),('estaciones','0'),('estaciones','1')]):
                    begin = start + timedelta(milliseconds=request_index * 1300)
                    fin = begin + timedelta(milliseconds=100)
                    id_ = f'SIMULADO-{origin}-{global_cycle:03d}-{request_index}'
                    # En esta simulación todas las respuestas son HTTP 200, con 3 estaciones por combustible.
                    w.writerow({'consulta_id':id_,'hora_utc':begin.isoformat(), 'fin_utc':fin.isoformat(),
                                'tipo':typ, 'producto':product,'intento':'1','estado_http':'200','tiempo_ms':'100',
                                'n_estaciones': '' if typ=='catalogo' else '3', 'retry_after':'','error_codigo':'',
                                'updated_at_con_zona': '' if typ=='catalogo' or product=='1' else '3',
                                'updated_at_sin_zona': '' if typ=='catalogo' or product=='0' else '3',
                                'updated_at_ausente':'0', 'updated_at_invalido':'0'})
                    if typ=='estaciones':
                        # Cambio sintético cada 6 ciclos (= 1 h virtual); no es frecuencia observada de ANH.
                        for num in range(3):
                            change = global_cycle // 6
                            hora_cambio = (START + timedelta(hours=change)).replace(tzinfo=None)
                            updated = (hora_cambio.isoformat()+'-04:00') if product=='0' else hora_cambio.isoformat()
                            st.writerow({'consulta_id':id_, 'hora_utc':begin.isoformat(),
                                         'id_estacion':str(1000+num),'producto_id':product,
                                         'saldo':str(1500-(change * (num+1))),
                                         'updated_at':updated,
                                         'zona_en_updated_at':'SI' if product=='0' else 'NO'})
        (target/'estado.properties').write_text(f'### DATOS SINTETICOS; NO SON EVIDENCIA\n'
            f'inicio_utc={ (START+timedelta(seconds=shift*600)).isoformat()}\n'
            f'origen={origin}\nduracion_horas={48 if origin=="local" else 24}\n'
            'base_url=http://127.0.0.1:9999/api/\npruebas=true\n', encoding='utf-8')
        info[origin] = {'ciclos_virtuales':ciclos,'directorio':str(target),'sin_uso_de_ANH':True}
    (base/'ESTO_ES_SIMULADO.json').write_text(json.dumps({'advertencia':'432 ciclos VIRTUALES; ningún registro corresponde a ANH real. CP-157: NO.',
                                                         'etapas':info},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return info


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--salida',type=Path,required=True)
    args=ap.parse_args()
    resumen=generar(args.salida)
    print('SIMULACIÓN TEMPORAL TERMINADA: 432 ciclos ficticios, cero solicitudes a ANH.')
    print('NO reemplaza las 72 horas CP-157. Datos en: '+str(args.salida))


if __name__=='__main__': main()
