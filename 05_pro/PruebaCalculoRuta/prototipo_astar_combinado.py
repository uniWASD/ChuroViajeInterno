"""
Prototipo combinado (versión 2) — junta lo mejor de las dos exploraciones:

De nuestro prototipo (Python / A*):
  - A* real, con heurística admisible (en vez de Dijkstra puro).
  - Los eventos EXPIRAN solos después de un tiempo (como ya define CU-01).
  - El caso extremo (choque) nunca bloquea del todo la ruta.

De la exploración de [tu compañero] (Java):
  - TrafficLevel como categoría con multiplicador, en vez de números sueltos
    por tipo de evento — más prolijo y más fácil de justificar en la defensa.
  - Fórmula de Haversine para trabajar con coordenadas GPS reales.
  - Casos de prueba narrados con el razonamiento impreso en pantalla
    (mucho más claro para mostrar en una review o en la defensa que un
    print seco de la ruta).

Coordenadas de esta versión: aproximadas al centro de Tarija (no Camiri).
Son ilustrativas, no direcciones exactas — sirven para probar la lógica,
no para navegación real todavía.

Requisito: pip install networkx
"""

import math
from enum import Enum
import networkx as nx


# ---------------------------------------------------------------------------
# Niveles de tráfico (idea de tu compañero: categoría + multiplicador)
# ---------------------------------------------------------------------------
class NivelTrafico(Enum):
    FLUIDO = ("Fluido", 1.0)
    MODERADO = ("Moderado", 1.5)
    CONGESTIONADO = ("Congestionado", 2.5)
    MUY_CONGESTIONADO = ("Muy congestionado", 4.0)

    def __init__(self, etiqueta, multiplicador):
        self.etiqueta = etiqueta
        self.multiplicador = multiplicador


# Qué nivel de tráfico dispara cada tipo de evento reportado (CU-01) y
# cuánto dura activo (minutos simulados) antes de expirar solo.
EVENTO_A_NIVEL = {
    "congestion_leve": (NivelTrafico.MODERADO, 20),
    "control_policial": (NivelTrafico.MODERADO, 30),
    "congestion_fuerte": (NivelTrafico.CONGESTIONADO, 25),
    "choque": (NivelTrafico.MUY_CONGESTIONADO, 40),
}


# ---------------------------------------------------------------------------
# Haversine (rescatado tal cual de la exploración en Java, pasado a Python)
# ---------------------------------------------------------------------------
def haversine_metros(lat1, lon1, lat2, lon2):
    r = 6_371_000
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = (math.sin(d_lat / 2) ** 2
         + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2))
         * math.sin(d_lon / 2) ** 2)
    return r * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


# ---------------------------------------------------------------------------
# Grafo con coordenadas reales de Tarija (aproximadas) + eventos con reloj
# ---------------------------------------------------------------------------
class RedDeCalles:
    def __init__(self):
        self.G = nx.DiGraph()
        self.eventos = {}   # (u, v) -> {"nivel": NivelTrafico, "expira_en": minuto}
        self.reloj = 0       # minuto simulado actual

    def agregar_nodo(self, nombre, lat, lon):
        self.G.add_node(nombre, lat=lat, lon=lon)

    def agregar_calle(self, u, v, nombre_calle):
        lat1, lon1 = self.G.nodes[u]["lat"], self.G.nodes[u]["lon"]
        lat2, lon2 = self.G.nodes[v]["lat"], self.G.nodes[v]["lon"]
        distancia = haversine_metros(lat1, lon1, lat2, lon2)
        self.G.add_edge(u, v, nombre=nombre_calle, distancia=distancia)

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
        # heurística admisible de A*: distancia geográfica real en línea recta
        lat1, lon1 = self.G.nodes[a]["lat"], self.G.nodes[a]["lon"]
        lat2, lon2 = self.G.nodes[b]["lat"], self.G.nodes[b]["lon"]
        return haversine_metros(lat1, lon1, lat2, lon2)

    def calcular_ruta(self, origen, destino):
        return nx.astar_path(
            self.G, origen, destino,
            heuristic=self.heuristica,
            weight=self.peso_efectivo,
        )

    def costo_tramo(self, u, v):
        return self.peso_efectivo(u, v, self.G[u][v])


def formatear(ruta):
    return " -> ".join(ruta)


# ---------------------------------------------------------------------------
# Casos de prueba (formato narrado, inspirado en los 4 casos de tu compañero)
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    print("=" * 60)
    print("  PRUEBAS DE RUTEO CON TRÁFICO — versión combinada")
    print("=" * 60)

    red = RedDeCalles()
    # Coordenadas aproximadas al centro de Tarija (ilustrativas)
    red.agregar_nodo("A", -21.5355, -64.7296)   # origen (plaza principal, aprox.)
    red.agregar_nodo("B", -21.5335, -64.7275)   # intermedio, calle directa
    red.agregar_nodo("D", -21.5320, -64.7250)   # destino
    red.agregar_nodo("C", -21.5390, -64.7330)   # intermedio, ruta alternativa

    red.agregar_calle("A", "B", "Calle Directa")
    red.agregar_calle("B", "D", "Calle Directa")
    red.agregar_calle("A", "C", "Circunvalación")
    red.agregar_calle("C", "D", "Circunvalación")

    print("\n--- Caso 1: sin eventos, elige la ruta más corta ---")
    ruta = red.calcular_ruta("A", "D")
    print(f"  Ruta elegida: {formatear(ruta)}")

    print("\n--- Caso 2: ruta corta con evento fuerte vs. alternativa libre ---")
    red.reportar_evento("A", "B", "choque")
    red.reportar_evento("B", "D", "choque")
    ruta = red.calcular_ruta("A", "D")
    print(f"  Costo A->B->D con choque: {red.costo_tramo('A','B') + red.costo_tramo('B','D'):.0f} m-equivalentes")
    print(f"  Costo A->C->D (libre):    {red.costo_tramo('A','C') + red.costo_tramo('C','D'):.0f} m-equivalentes")
    print(f"  Ruta elegida: {formatear(ruta)}")
    print("  Motivo: aunque A->B->D es geográficamente más corta, el choque")
    print("  la penaliza tanto que conviene desviarse.")

    print("\n--- Caso 3: el evento expira solo con el tiempo ---")
    red.avanzar_reloj(45)  # más que la duración del choque (40 min)
    ruta = red.calcular_ruta("A", "D")
    print(f"  Ruta elegida después de expirar: {formatear(ruta)}")

    print("\n--- Caso 4: congestión leve no justifica un desvío exagerado ---")
    red.reportar_evento("A", "B", "congestion_leve")
    red.reportar_evento("B", "D", "congestion_leve")
    ruta = red.calcular_ruta("A", "D")
    print(f"  Costo A->B->D con congestión leve: {red.costo_tramo('A','B') + red.costo_tramo('B','D'):.0f}")
    print(f"  Costo A->C->D (desvío):            {red.costo_tramo('A','C') + red.costo_tramo('C','D'):.0f}")
    print(f"  Ruta elegida: {formatear(ruta)}")
    print("  Motivo: la congestión leve no alcanza a justificar el desvío largo.")
