"""Línea de comandos del simulador (RN-07.2): los parámetros van por argumentos, por un archivo TOML o por los dos;
un argumento manda sobre el archivo."""

import argparse
import sys

from simulador import limites
from simulador.parametros import BACKEND_POR_DEFECTO, CARGA_NORMAL, Parametros, ParametrosInvalidos, leer_archivo, validar

#: Códigos de salida.
OK = 0
PARAMETROS_INVALIDOS = 2


def argumentos() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="python -m simulador",
        description="Simulador de tráfico de ChuroViaje (CU-07). Valida los parámetros antes de empezar (RN-07.1).",
        epilog=limites.tabla(), formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--config", help="archivo TOML con los parámetros (ver simulador.ejemplo.toml)")
    p.add_argument("--usuarios", type=str, help=f"usuarios fantasma a la vez (por defecto {CARGA_NORMAL['usuarios']})")
    p.add_argument("--navegando", type=str, help=f"cuántos de ellos navegan una ruta (por defecto {CARGA_NORMAL['navegando']})")
    p.add_argument("--rutas", help="archivo JSON con la lista «viajes» (por defecto 04_imp/datos/rutas_referencia.json)")
    p.add_argument("--eventos-por-minuto", dest="eventos_por_minuto", type=str,
                   help=f"eventos viales por minuto entre todos (por defecto {CARGA_NORMAL['eventos_por_minuto']})")
    p.add_argument("--duracion", dest="duracion_minutos", type=str,
                   help=f"duración en minutos (por defecto {CARGA_NORMAL['duracion_minutos']})")
    p.add_argument("--backend", help=f"dirección del backend (por defecto {BACKEND_POR_DEFECTO}, el ambiente de pruebas)")
    p.add_argument("--ambiente-remoto", dest="ambiente_remoto", action="store_true", default=None,
                   help="permite un backend de pruebas en otro servidor, solo por HTTPS (RN-07.12)")
    p.add_argument("--semilla", type=int, help="semilla para repetir la misma simulación")
    p.add_argument("--solo-validar", action="store_true", help="valida los parámetros y muestra el plan, sin ejecutar")
    return p


def plan(par: Parametros) -> str:
    rutas = "\n".join(f"    - {r.nombre}" + (f" ({len(r.paradas)} parada{'s' if len(r.paradas) > 1 else ''})" if r.paradas else "") for r in par.rutas)
    return (f"Simulación válida:\n"
            f"  usuarios fantasma   {par.usuarios} ({par.navegando} navegando)\n"
            f"  eventos por minuto  {par.eventos_por_minuto}\n"
            f"  duración            {par.duracion_minutos} min\n"
            f"  backend             {par.backend}{' (remoto)' if par.ambiente_remoto else ' (ambiente de pruebas local)'}\n"
            f"  semilla             {par.semilla if par.semilla is not None else 'al azar'}\n"
            f"  rutas               {len(par.rutas)} de {par.archivo_rutas}\n{rutas}")


def main(argv: list[str] | None = None, salida=sys.stdout, errores=sys.stderr) -> int:
    args = argumentos().parse_args(argv)
    try:
        datos = leer_archivo(args.config) if args.config else {}
        desde_linea = {k: getattr(args, k) for k in ("usuarios", "navegando", "rutas", "eventos_por_minuto",
                                                       "duracion_minutos", "backend", "ambiente_remoto", "semilla")}
        datos.update({k: v for k, v in desde_linea.items() if v is not None})
        par = validar(datos)
    except ParametrosInvalidos as e:
        # RN-07.1: no se ejecuta y se indican los límites permitidos.
        print("La simulación no se ejecuta: los parámetros no son válidos.", file=errores)
        for problema in e.errores:
            print(f"  - {problema}", file=errores)
        print(limites.tabla(), file=errores)
        return PARAMETROS_INVALIDOS
    print(plan(par), file=salida)
    if not args.solo_validar:
        # T-102 deja la estructura; el comportamiento de los usuarios fantasma llega en el sprint 6.
        print("Los usuarios fantasma todavía no tienen comportamiento (sprint 6): no se envió nada al backend.",
              file=salida)
    return OK
