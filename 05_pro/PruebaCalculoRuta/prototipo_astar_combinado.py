

import math
from enum import Enum
import networkx as nx


class NivelTrafico(Enum):
    FLUIDO = ("Fluido", 1.0)
    MODERADO = ("Moderado", 1.5)
    CONGESTIONADO = ("Congestionado", 2.5)
    MUY_CONGESTIONADO = ("Muy congestionado", 4.0)

    def __init__(self, etiqueta, multiplicador):
        self.etiqueta = etiqueta
        self.multiplicador = multiplicador


EVENTO_A_NIVEL = {
    "congestion_leve": (NivelTrafico.MODERADO, 20),
    "control_policial": (NivelTrafico.MODERADO, 30),
    "congestion_fuerte": (NivelTrafico.CONGESTIONADO, 25),
    "choque": (NivelTrafico.MUY_CONGESTIONADO, 40),
}


def haversine_metros(lat1, lon1, lat2, lon2):
    r = 6_371_000
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = (math.sin(d_lat / 2) ** 2
         + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2))
         * math.sin(d_lon / 2) ** 2)
    return r * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


class RedDeCalles:
    def __init__(self):
        self.G = nx.DiGraph()
        self.eventos = {}
        self.reloj = 0

    def agregar_nodo(self, nombre, lat, lon):
        self.G.add_node(nombre, lat=lat, lon=lon)

    def agregar_calle(self, u, v, nombre_calle, factor_curvatura=1.0):
        """Calle de UN SOLO SENTIDO: u -> v únicamente.

        factor_curvatura: por defecto 1.0 (la calle es tan larga como la
        línea recta Haversine entre los dos nodos). Un valor > 1.0 simula
        una calle que da vueltas / no es recta, por lo tanto es más larga
        que la distancia geográfica directa. Esto es clave en un grafo
        irregular: la heurística sigue siendo Haversine (línea recta) pero
        el costo real del tramo puede ser mayor.
        """
        lat1, lon1 = self.G.nodes[u]["lat"], self.G.nodes[u]["lon"]
        lat2, lon2 = self.G.nodes[v]["lat"], self.G.nodes[v]["lon"]
        distancia = haversine_metros(lat1, lon1, lat2, lon2) * factor_curvatura
        self.G.add_edge(u, v, nombre=nombre_calle, distancia=distancia)

    def agregar_calle_doble(self, u, v, nombre_calle, factor_curvatura=1.0):
        self.agregar_calle(u, v, nombre_calle, factor_curvatura)
        self.agregar_calle(v, u, nombre_calle, factor_curvatura)

    def reportar_evento(self, u, v, tipo_evento):
        nivel, duracion = EVENTO_A_NIVEL[tipo_evento]
        self.eventos[(u, v)] = {"nivel": nivel, "expira_en": self.reloj + duracion}
        print(f"  [evento] {tipo_evento} en ({u}->{v}): nivel={nivel.etiqueta} "
              f"(x{nivel.multiplicador}), expira en el minuto {self.reloj + duracion}")

    def avanzar_reloj(self, minutos):
        self.reloj += minutos
        expirados = [par for par, ev in self.eventos.items() if ev["expira_en"] <= self.reloj]
        for par in expirados:
            print(f"  [reloj] minuto {self.reloj}: evento en {par} expiró, vuelve a FLUIDO")
            del self.eventos[par]

    def nivel_actual(self, u, v):
        ev = self.eventos.get((u, v))
        return ev["nivel"] if ev else NivelTrafico.FLUIDO

    def peso_efectivo(self, u, v, datos):
        return datos["distancia"] * self.nivel_actual(u, v).multiplicador

    def heuristica(self, a, b):
        lat1, lon1 = self.G.nodes[a]["lat"], self.G.nodes[a]["lon"]
        lat2, lon2 = self.G.nodes[b]["lat"], self.G.nodes[b]["lon"]
        return haversine_metros(lat1, lon1, lat2, lon2)

    def calcular_ruta(self, origen, destino):
        return nx.astar_path(
            self.G, origen, destino,
            heuristic=self.heuristica,
            weight=self.peso_efectivo,
        )

    def costo_ruta(self, ruta):
        return sum(self.costo_tramo(ruta[i], ruta[i + 1]) for i in range(len(ruta) - 1))

    def costo_tramo(self, u, v):
        return self.peso_efectivo(u, v, self.G[u][v])

    def existe_calle(self, u, v):
        return self.G.has_edge(u, v)


def formatear(ruta):
    return " -> ".join(ruta)


# ---------------------------------------------------------------------------
# Grafo IRREGULAR: nodos sin alinear, calles de largo distinto, algunas de
# un solo sentido, un nodo casi aislado, y una calle curva (más larga que
# la línea recta entre sus dos extremos).
# ---------------------------------------------------------------------------
def construir_red_irregular():
    red = RedDeCalles()

    # Posiciones a mano, NO en cuadrícula. Los números son ilustrativos,
    # pensados para que las distancias entre nodos vecinos varíen bastante
    # (de ~120m a ~500m) y para que el "layout" no sea simétrico.
    nodos = {
        "Plaza":      (-21.5355, -64.7296),  # centro, punto de referencia
        "Mercado":    (-21.5348, -64.7280),  # cerca, al noreste
        "Terminal":   (-21.5410, -64.7350),  # lejos, al suroeste
        "Hospital":   (-21.5330, -64.7310),  # cerca, al norte
        "Universidad":(-21.5300, -64.7260),  # más lejos, al noreste
        "Barrio_Sur": (-21.5420, -64.7270),  # lejos, al sur
        "Mirador":    (-21.5290, -64.7220),  # el más lejano, casi aislado
    }
    for nombre, (lat, lon) in nodos.items():
        red.agregar_nodo(nombre, lat, lon)

    # Calles con largos distintos y no todas dobles:

    # Plaza <-> Mercado: calle corta y directa, doble sentido.
    red.agregar_calle_doble("Plaza", "Mercado", "Calle Comercio")

    # Plaza <-> Hospital: doble sentido.
    red.agregar_calle_doble("Plaza", "Hospital", "Av. Circunvalación")

    # Mercado <-> Universidad: doble sentido.
    red.agregar_calle_doble("Mercado", "Universidad", "Av. Las Américas")

    # Hospital <-> Universidad: UN SOLO SENTIDO (calle de bajada, cuesta
    # arriba está prohibida / no existe ese carril en este modelo).
    red.agregar_calle("Hospital", "Universidad", "Calle La Cuesta (bajada)")

    # Universidad <-> Mirador: la única calle de acceso al Mirador, larga
    # y con curvas (factor_curvatura > 1: la calle da vueltas por la loma,
    # no es una línea recta como asumiría la heurística).
    red.agregar_calle_doble("Universidad", "Mirador", "Camino al Mirador", factor_curvatura=1.8)

    # Plaza <-> Terminal: doble sentido, calle larga.
    red.agregar_calle_doble("Plaza", "Terminal", "Av. Domingo Paredes")

    # Terminal <-> Barrio_Sur: doble sentido.
    red.agregar_calle_doble("Terminal", "Barrio_Sur", "Calle del Sur")

    # Plaza <-> Barrio_Sur: NO hay calle directa (hay que pasar por Terminal).
    # (simplemente no se agrega esa arista)

    # Mercado <-> Terminal: atajo de un solo sentido (solo se puede ir de
    # Mercado hacia Terminal, no al revés — por ejemplo, una calle
    # peatonal/de bajada habilitada para tráfico en un solo sentido).
    red.agregar_calle("Mercado", "Terminal", "Pasaje El Atajo")

    return red


# ---------------------------------------------------------------------------
# Casos de prueba
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    print("=" * 60)
    print("  PRUEBAS DE RUTEO — grafo IRREGULAR (no cuadrícula)")
    print("=" * 60)

    red = construir_red_irregular()

    print("\n--- Caso 1: ruta sin eventos, con calles de largo desigual ---")
    ruta = red.calcular_ruta("Plaza", "Universidad")
    print(f"  Ruta elegida: {formatear(ruta)}")
    print(f"  Costo: {red.costo_ruta(ruta):.0f} m-equivalentes")
    print("  Motivo: A* debe elegir entre ir directo o por un nodo intermedio;")
    print("  con distancias irregulares, la ruta más corta no es obvia a simple vista.")

    print("\n--- Caso 2: el atajo de un solo sentido SOLO sirve en una dirección ---")
    print(f"  ¿Existe Mercado->Terminal? {red.existe_calle('Mercado', 'Terminal')}")
    print(f"  ¿Existe Terminal->Mercado? {red.existe_calle('Terminal', 'Mercado')}")
    ruta_ida = red.calcular_ruta("Mercado", "Terminal")
    ruta_vuelta = red.calcular_ruta("Terminal", "Mercado")
    print(f"  Ruta Mercado->Terminal (usa el atajo): {formatear(ruta_ida)}")
    print(f"  Ruta Terminal->Mercado (atajo no existe, da la vuelta): {formatear(ruta_vuelta)}")

    print("\n--- Caso 3: nodo casi aislado (Mirador) con calle curva ---")
    print("  El Mirador solo se conecta por 'Camino al Mirador', que da vueltas")
    print("  (factor_curvatura=1.8): la calle real es más larga que la línea recta.")
    ruta = red.calcular_ruta("Plaza", "Mirador")
    print(f"  Ruta elegida: {formatear(ruta)}")
    print(f"  Costo real del tramo Universidad->Mirador: "
          f"{red.costo_tramo('Universidad', 'Mirador'):.0f} m")
    print(f"  (línea recta equivalente sería: "
          f"{red.heuristica('Universidad', 'Mirador'):.0f} m)")
    print("  A* sigue encontrando la única ruta posible aunque la heurística")
    print("  subestime el tramo final — eso es justamente lo que la hace admisible.")

    print("\n--- Caso 4: no hay calle directa Plaza-Barrio_Sur, hay que rodear ---")
    ruta = red.calcular_ruta("Plaza", "Barrio_Sur")
    print(f"  Ruta elegida: {formatear(ruta)}")
    print("  Motivo: no existe arista directa; A* explora el grafo y encuentra")
    print("  el único camino posible pasando por Terminal.")

    print("\n--- Caso 5: evento en la única calle de bajada Hospital->Universidad ---")
    print("  Como es de un solo sentido, si se congestiona no hay 'carril libre'")
    print("  alternativo en esa misma calle — A* debe decidir entre quedarse")
    print("  en el tramo directo (más corto en línea recta) o rodear por Plaza.")
    costo_directo_sin_evento = red.costo_tramo("Hospital", "Universidad")
    red.reportar_evento("Hospital", "Universidad", "congestion_fuerte")
    costo_directo_con_evento = red.costo_tramo("Hospital", "Universidad")
    costo_rodeo = (red.costo_tramo("Hospital", "Plaza")
                   + red.costo_tramo("Plaza", "Mercado")
                   + red.costo_tramo("Mercado", "Universidad"))
    ruta = red.calcular_ruta("Hospital", "Universidad")
    print(f"  Costo directo sin evento:         {costo_directo_sin_evento:.0f} m")
    print(f"  Costo directo con congestión fuerte (x2.5): {costo_directo_con_evento:.0f} m")
    print(f"  Costo del rodeo por Plaza->Mercado:          {costo_rodeo:.0f} m")
    print(f"  Ruta elegida: {formatear(ruta)}")
    print("  Motivo: la congestión fuerte ya encarece tanto el tramo directo que,")
    print("  aunque geográficamente sea el más corto, el rodeo por Plaza y Mercado")
    print("  termina siendo más barato — y como la calle es de un solo sentido,")
    print("  no existe la opción de 'usar el carril contrario' como en un caso")
    print("  de doble sentido; A* tiene que recalcular toda la ruta.")

    print("\n--- Caso 6: mismo evento, pero ahora agrava a un choque ---")
    red.reportar_evento("Hospital", "Universidad", "choque")
    ruta = red.calcular_ruta("Hospital", "Universidad")
    print(f"  Costo directo con choque (x4.0): {red.costo_tramo('Hospital', 'Universidad'):.0f} m")
    print(f"  Ruta elegida: {formatear(ruta)}")
    print("  Motivo: el choque empeora aún más el tramo directo, pero la ruta")
    print("  elegida no cambia respecto al Caso 5 — el rodeo por Plaza y Mercado")
    print("  ya era la mejor opción y sigue siéndolo (el choque solo la refuerza).")
