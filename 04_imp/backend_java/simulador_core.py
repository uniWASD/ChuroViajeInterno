import sys
import json
import time

def main():
    # Usamos while True y readline() para evitar el bloqueo por búfer de Python
    while True:
        linea = sys.stdin.readline()
        # Si readline() devuelve vacío, significa que el pipe se cerró (el proceso padre murió o lo mató)
        if not linea:
            break
            
        linea = linea.strip()
        if not linea:
            continue
            
        try:
            peticion = json.loads(linea)
            id_peticion = peticion.get("id")
            operacion = peticion.get("operacion")

            # CP-41: Respuesta normal de estado
            if operacion == "consultarEstado":
                respuesta = {"id": id_peticion, "estado": "cargada"}
                
            # CP-42 y CP-43: Forzar una demora que supere los 3 segundos (RN-03.15)
            elif operacion == "simular_demora":
                time.sleep(4)
                respuesta = {"id": id_peticion, "estado": "completado_con_demora"}
                
            # CP-44: Simular la caída repentina del proceso C++
            elif operacion == "simular_caida":
                sys.stderr.write("Error fatal simulado en el nucleo C++\n")
                sys.stderr.flush()
                sys.exit(1)
                
            # Operaciones normales (calcularRuta, revisarRuta, etc.)
            else:
                respuesta = {"id": id_peticion, "resultado": f"{operacion} ejecutada correctamente"}

            # Imprimir como JSON en una sola línea y forzar el vaciado del buffer (flush)
            print(json.dumps(respuesta))
            sys.stdout.flush()

        except json.JSONDecodeError:
            sys.stderr.write("Error: entrada no es un JSON valido\n")
            sys.stderr.flush()

if __name__ == "__main__":
    main()