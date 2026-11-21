"""Línea de comandos del simulador (RN-07.2): los parámetros van por argumentos, por un archivo TOML o por los dos;
un argumento manda sobre el archivo."""

import argparse
import json
import os
import re
import sys
from datetime import datetime
from pathlib import Path

from simulador import limites
from simulador.metricas import texto_resumen
from simulador.parametros import BACKEND_POR_DEFECTO, CARGA_NORMAL, Parametros, ParametrosInvalidos, leer_archivo, validar
from simulador.simulacion import Simulacion, borrar_datos_simulados

#: Códigos de salida.
OK = 0
PARAMETROS_INVALIDOS = 2
SIN_SESION = 3
BORRADO_FALLIDO = 4
INTERRUMPIDA = 130

#: La credencial de prueba del ambiente (RN-07.5) viene de una variable de entorno: nunca de un archivo del
#: repositorio ni de la línea de comandos, que queda en el historial.
VARIABLE_CREDENCIAL = "SIMULACION_CREDENCIAL"
PATRON_PRUEBA = re.compile(r"[A-Za-z0-9._-]{1,64}")


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
    p.add_argument("--prueba", help="identificador de la prueba de carga (por defecto carga-AAAAMMDD-HHMMSS)")
    p.add_argument("--reporte", help="archivo JSON donde dejar el resumen al terminar")
    p.add_argument("--solo-validar", action="store_true", help="valida los parámetros y muestra el plan, sin ejecutar")
    p.add_argument("--borrar", action="store_true",
                   help="no simula: borra los datos simulados con el comando del backend (RN-07.8)")
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


def main(argv: list[str] | None = None, salida=sys.stdout, errores=sys.stderr, reloj=None) -> int:
    args = argumentos().parse_args(argv)
    credencial = os.environ.get(VARIABLE_CREDENCIAL, "")
    if args.borrar:
        return borrar(args, credencial, salida, errores)
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
    prueba = args.prueba or datetime.now().strftime("carga-%Y%m%d-%H%M%S")
    problemas = []
    if not PATRON_PRUEBA.fullmatch(prueba):
        problemas.append("prueba: solo letras, números, punto, guion y guion bajo, hasta 64")
    if not args.solo_validar and not credencial:
        problemas.append(f"Falta la credencial de prueba en la variable {VARIABLE_CREDENCIAL} (RN-07.5)")
    if problemas:
        print("La simulación no se ejecuta:", file=errores)
        for problema in problemas:
            print(f"  - {problema}", file=errores)
        return PARAMETROS_INVALIDOS
    print(plan(par) + f"\n  prueba de carga     {prueba}", file=salida)
    if args.solo_validar:
        return OK

    simulacion = Simulacion(par, credencial, prueba, reloj=reloj, escribir=lambda t: print(t, file=salida, flush=True))
    resultado = simulacion.ejecutar()
    print(texto_resumen(resultado.resumen, prueba, resultado.interrumpida), file=salida)
    if args.reporte:
        Path(args.reporte).write_text(json.dumps({
            "prueba": prueba, "interrumpida": resultado.interrumpida,
            "usuarios_con_sesion": resultado.usuarios_con_sesion, **resultado.resumen,
        }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Para borrar los datos simulados: python -m simulador --borrar --backend {par.backend}"
          f"{' --ambiente-remoto' if par.ambiente_remoto else ''}", file=salida)
    if resultado.usuarios_con_sesion == 0:
        print("Ningún usuario fantasma obtuvo su sesión: revisa la dirección del backend, la credencial y que el "
              "ambiente tenga habilitadas las sesiones de prueba.", file=errores)
        return SIN_SESION
    return INTERRUMPIDA if resultado.interrumpida else OK


def borrar(args, credencial: str, salida, errores) -> int:
    """RN-07.8: el comando del backend que borra todo lo simulado, sin tocar los datos reales."""
    try:
        par = validar({k: v for k, v in {"backend": args.backend, "ambiente_remoto": args.ambiente_remoto}.items()
                       if v is not None})
    except ParametrosInvalidos as e:
        print("No se borra nada:", file=errores)
        for problema in e.errores:
            print(f"  - {problema}", file=errores)
        return PARAMETROS_INVALIDOS
    if not credencial:
        print(f"No se borra nada: falta la credencial de prueba en la variable {VARIABLE_CREDENCIAL}.", file=errores)
        return PARAMETROS_INVALIDOS
    ok, respuesta = borrar_datos_simulados(par.backend, credencial)
    if not ok:
        print(f"No se pudieron borrar los datos simulados: {respuesta}", file=errores)
        return BORRADO_FALLIDO
    print(f"Datos simulados borrados en {par.backend}: {respuesta.get('votos', 0)} votos, "
          f"{respuesta.get('eventos', 0)} eventos y {respuesta.get('conductores', 0)} usuarios fantasma.", file=salida)
    return OK
