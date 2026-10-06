"""Demostración por pantalla del ruteo sobre la red irregular (CU-03).

Las pruebas verificables están en tests/test_cu03_ruteo.py (se ejecutan con
pytest); este script solo muestra los casos para explicarlos en clase.

Uso: python demo_ruteo.py
"""

import logging

from redes_de_prueba import construir_red_irregular


def formatear(ruta):
    return " -> ".join(ruta)


if __name__ == "__main__":
    # Los eventos del modelo se registran con logging; aquí se muestran en pantalla.
    logging.basicConfig(level=logging.INFO, format="  %(message)s")

    print("=" * 60)
    print("  PRUEBAS DE RUTEO — grafo IRREGULAR (no cuadrícula)")
    print("=" * 60)

    red = construir_red_irregular()

    print("\n--- CP-14: ruta sin eventos, con calles de largo desigual ---")
    resultado = red.calcular_ruta("Plaza", "Universidad")
    print(f"  Ruta elegida: {formatear(resultado.ruta)}")
    print(f"  Costo: {resultado.costo:.0f} m-equivalentes")
    print(f"  Alternativa por Hospital: "
          f"{red.costo_ruta(['Plaza', 'Hospital', 'Universidad']):.0f} m")
    print("  Motivo: no hay calle directa Plaza -> Universidad; A* compara los")
    print("  dos caminos posibles (por Mercado o por Hospital) y elige el más corto.")

    print("\n--- CP-15: el atajo de un solo sentido SOLO sirve en una dirección ---")
    print(f"  ¿Existe Mercado->Terminal? {red.existe_calle('Mercado', 'Terminal')}")
    print(f"  ¿Existe Terminal->Mercado? {red.existe_calle('Terminal', 'Mercado')}")
    ida = red.calcular_ruta("Mercado", "Terminal")
    vuelta = red.calcular_ruta("Terminal", "Mercado")
    print(f"  Ruta Mercado->Terminal (usa el atajo): {formatear(ida.ruta)} "
          f"({ida.costo:.0f} m)")
    print(f"  Ir por Plaza costaría: "
          f"{red.costo_ruta(['Mercado', 'Plaza', 'Terminal']):.0f} m")
    print(f"  Ruta Terminal->Mercado (el atajo no existe en ese sentido): "
          f"{formatear(vuelta.ruta)}")

    print("\n--- CP-16: nodo casi aislado (Mirador) con calle curva ---")
    print("  El Mirador solo se conecta por 'Camino al Mirador', que da vueltas")
    print("  (factor_curvatura=1.8): la calle real es más larga que la línea recta.")
    resultado = red.calcular_ruta("Plaza", "Mirador")
    print(f"  Ruta elegida: {formatear(resultado.ruta)}")
    print(f"  Costo real del tramo Universidad->Mirador: "
          f"{red.costo_tramo('Universidad', 'Mirador'):.0f} m")
    print(f"  (línea recta equivalente sería: "
          f"{red.heuristica('Universidad', 'Mirador'):.0f} m)")
    print("  La heurística subestima el tramo final, nunca lo sobreestima:")
    print("  por eso es admisible y A* sigue encontrando la ruta óptima.")

    print("\n--- CP-17: no hay calle directa Plaza-Barrio_Sur, hay que rodear ---")
    resultado = red.calcular_ruta("Plaza", "Barrio_Sur")
    print(f"  Ruta elegida: {formatear(resultado.ruta)}")
    print(f"  Alternativa por Mercado y el atajo: "
          f"{red.costo_ruta(['Plaza', 'Mercado', 'Terminal', 'Barrio_Sur']):.0f} m")
    print("  Motivo: no existe arista directa; A* rodea por otros nodos. Barrio_Sur")
    print("  solo se alcanza desde Terminal, y de los caminos que llegan a Terminal")
    print("  elige el más barato.")

    print("\n--- CP-18: congestión fuerte en Hospital->Universidad obliga a desviarse ---")
    costo_directo_sin_evento = red.costo_tramo("Hospital", "Universidad")
    red.reportar_evento("Hospital", "Universidad", "congestion_fuerte")
    costo_directo_con_evento = red.costo_tramo("Hospital", "Universidad")
    costo_rodeo = red.costo_ruta(["Hospital", "Plaza", "Mercado", "Universidad"])
    resultado = red.calcular_ruta("Hospital", "Universidad")
    print(f"  Costo directo sin evento:                    {costo_directo_sin_evento:.0f} m")
    print(f"  Costo directo con congestión fuerte (x2.5):  {costo_directo_con_evento:.0f} m")
    print(f"  Costo del rodeo por Plaza->Mercado:          {costo_rodeo:.0f} m")
    print(f"  Ruta elegida: {formatear(resultado.ruta)}")
    print("  Motivo: el evento encarece tanto el tramo directo que el rodeo por")
    print("  Plaza y Mercado sale más barato, y A* lo elige. Esto no depende de que")
    print("  la calle sea de un solo sentido: pasaría igual en una calle doble,")
    print("  porque cada sentido tiene su propio costo.")

    print("\n--- CP-19: el mismo evento se agrava a choque ---")
    red.reportar_evento("Hospital", "Universidad", "choque")
    resultado = red.calcular_ruta("Hospital", "Universidad")
    print(f"  Costo directo con choque (x4.0): {red.costo_tramo('Hospital', 'Universidad'):.0f} m")
    print(f"  Ruta elegida: {formatear(resultado.ruta)}")
    print("  Motivo: el choque encarece aún más el tramo directo; el rodeo ya era")
    print("  la mejor opción y sigue siéndolo, así que la ruta no cambia.")

    print("\n--- CP-09 / CP-10: casos sin ruta ---")
    red.agregar_nodo("Aislado", -21.5500, -64.7400)
    sin_camino = red.calcular_ruta("Plaza", "Aislado")
    print(f"  Plaza -> Aislado: {sin_camino.mensaje}")
    inexistente = red.calcular_ruta("Plaza", "NoExiste")
    print(f"  Plaza -> NoExiste: {inexistente.mensaje}")
