#!/usr/bin/env python3
"""Manda mensajes de prueba al núcleo por pipe, igual que lo hará el wrapper Java.

Uso:
  python scripts/enviar_mensajes.py --nucleo build/motor_nucleo            mensajes de ejemplo
  python scripts/enviar_mensajes.py --nucleo build/motor_nucleo --verificar    además comprueba las respuestas (CTest)
  python scripts/enviar_mensajes.py --nucleo build/motor_nucleo -m '{"id":1,"operacion":"consultar_estado"}'

Sin -m se envían los mensajes de ejemplo. Cada línea se escribe en la entrada estándar del
núcleo, se espera su respuesta y se imprime. Al final se cierra la entrada y se comprueba que
el núcleo termine solo con código 0.
"""
import argparse
import json
import subprocess
import sys

# (mensaje, qué se espera de la respuesta: (ok, código de error o None))
EJEMPLOS = [
    ('{"id":1,"operacion":"consultar_estado"}', (True, None)),
    ('esto no es json', (False, "JSON_INVALIDO")),
    ('{"id":2,"operacion":"consultar_estado"}', (True, None)),  # sigue funcionando tras el error (CP-25)
    ('{"id":3,"operacion":"hacer_cafe"}', (False, "OPERACION_DESCONOCIDA")),
    ('{"id":4,"operacion":"calcular_ruta","origen":[-21.5355,-64.7296],"destino":[-21.5300,-64.7260],"paradas":[]}',
     (False, "NO_IMPLEMENTADA")),
    ('{"operacion":"consultar_estado"}', (False, "PETICION_INVALIDA")),
]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--nucleo", required=True, help="ruta del ejecutable motor_nucleo")
    ap.add_argument("-m", "--mensaje", action="append", help="mensaje a enviar (se puede repetir)")
    ap.add_argument("--verificar", action="store_true", help="comprobar las respuestas de los ejemplos; falla si algo no coincide")
    args = ap.parse_args()

    casos = [(m, None) for m in args.mensaje] if args.mensaje else EJEMPLOS
    verificar = args.verificar and not args.mensaje
    fallos = []

    # El núcleo escribe sus registros en stderr; aquí se dejan pasar a la terminal.
    # Pipes binarios: así Windows no convierte "\n" en "\r\n" ni cambia la codificación (UTF-8).
    proc = subprocess.Popen([args.nucleo], stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    for mensaje, esperado in casos:
        proc.stdin.write((mensaje + "\n").encode("utf-8"))
        proc.stdin.flush()
        linea = proc.stdout.readline().decode("utf-8")
        print(f">> {mensaje}\n<< {linea.rstrip()}")
        if not linea:
            fallos.append(f"el núcleo cerró la salida al recibir: {mensaje}")
            break
        if verificar:
            try:
                r = json.loads(linea)
            except ValueError:
                fallos.append(f"respuesta que no es JSON: {linea!r}")
                continue
            ok, codigo = esperado
            if r.get("ok") != ok or (codigo and r.get("error", {}).get("codigo") != codigo):
                fallos.append(f"respuesta inesperada para {mensaje!r}: {linea.strip()}")

    proc.stdin.close()  # al cerrarse la entrada, el núcleo debe terminar solo
    try:
        salida = proc.wait(timeout=5)
    except subprocess.TimeoutExpired:
        proc.kill()
        fallos.append("el núcleo no terminó al cerrar la entrada")
        salida = None
    if salida not in (0, None):
        fallos.append(f"el núcleo terminó con código {salida}")
    resto = proc.stdout.read().decode("utf-8")
    if resto.strip():
        fallos.append(f"salida inesperada tras la última respuesta: {resto!r}")

    if fallos:
        print("\nFALLÓ:\n  " + "\n  ".join(fallos), file=sys.stderr)
        return 1
    print("\nOK: el núcleo respondió y terminó limpio al cerrarse la entrada.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
