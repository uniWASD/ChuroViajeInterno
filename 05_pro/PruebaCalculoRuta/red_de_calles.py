"""Modelo del motor de ruteo (prototipo CU-03 — Calcular Ruta Óptima).

Red de calles dirigida + A* con multiplicadores de tráfico por evento.
Prototipo en Python para validar la lógica antes de llevarla al motor core en C++.
"""
import logging
import math
from dataclasses import dataclass, field
from enum import Enum
from typing import List, Optional

import networkx as nx

logger = logging.getLogger(__name__)


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


MENSAJE_SIN_RUTA = "No se encontró una ruta válida"          # CU-03, extensión 3b
MENSAJE_PUNTO_INVALIDO = "Origen o destino inválido"          # CU-03, extensión 1a


@dataclass(frozen=True)
class ResultadoRuta:
    """Resultado de calcular una ruta.

    Si `encontrada` es False, `ruta` queda vacía, `costo` es None y `mensaje`
    explica al conductor por qué no se pudo calcular (en lugar de lanzar una
    excepción de networkx).
    """
    encontrada: bool
    ruta: List[str] = field(default_factory=list)
    costo: Optional[float] = None
    mensaje: str = ""


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

        Un factor < 1.0 haría la calle más corta que la línea recta, y la
        heurística dejaría de ser admisible (podría sobreestimar), por eso
        se rechaza con ValueError.
        """
        if factor_curvatura < 1.0:
            raise ValueError(
                f"factor_curvatura debe ser >= 1.0 para que la heurística "
                f"Haversine siga siendo admisible (recibido: {factor_curvatura})")
        lat1, lon1 = self.G.nodes[u]["lat"], self.G.nodes[u]["lon"]
        lat2, lon2 = self.G.nodes[v]["lat"], self.G.nodes[v]["lon"]
        distancia = haversine_metros(lat1, lon1, lat2, lon2) * factor_curvatura
        self.G.add_edge(u, v, nombre=nombre_calle, distancia=distancia)

    def agregar_calle_doble(self, u, v, nombre_calle, factor_curvatura=1.0):
        self.agregar_calle(u, v, nombre_calle, factor_curvatura)
        self.agregar_calle(v, u, nombre_calle, factor_curvatura)

    def reportar_evento(self, u, v, tipo_evento):
        """Registra un evento de tráfico sobre la calle u -> v.

        Lanza ValueError si el tipo de evento no existe o si la calle u -> v
        no existe (por ejemplo, el sentido contrario de una calle de un solo
        sentido).
        """
        if tipo_evento not in EVENTO_A_NIVEL:
            raise ValueError(
                f"Tipo de evento desconocido: {tipo_evento!r}. "
                f"Válidos: {', '.join(EVENTO_A_NIVEL)}")
        if not self.existe_calle(u, v):
            raise ValueError(
                f"No existe la calle {u} -> {v}; no se puede reportar un evento "
                f"sobre ella (¿sentido contrario de una calle de un solo sentido?)")
        nivel, duracion = EVENTO_A_NIVEL[tipo_evento]
        self.eventos[(u, v)] = {"nivel": nivel, "expira_en": self.reloj + duracion}
        logger.info("[evento] %s en (%s->%s): nivel=%s (x%s), expira en el minuto %s",
                    tipo_evento, u, v, nivel.etiqueta, nivel.multiplicador,
                    self.reloj + duracion)

    def avanzar_reloj(self, minutos):
        self.reloj += minutos
        expirados = [par for par, ev in self.eventos.items() if ev["expira_en"] <= self.reloj]
        for par in expirados:
            logger.info("[reloj] minuto %s: evento en %s expiró, vuelve a FLUIDO",
                        self.reloj, par)
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
        """Calcula la ruta óptima con A* y devuelve un ResultadoRuta.

        Nunca deja escapar las excepciones de networkx:
        - NodeNotFound (origen o destino no existen)  -> MENSAJE_PUNTO_INVALIDO
        - NetworkXNoPath (no hay camino posible)      -> MENSAJE_SIN_RUTA
        """
        try:
            ruta = nx.astar_path(
                self.G, origen, destino,
                heuristic=self.heuristica,
                weight=self.peso_efectivo,
            )
        except nx.NodeNotFound as error:
            logger.warning("Ruta %s -> %s: %s", origen, destino, error)
            return ResultadoRuta(encontrada=False, mensaje=MENSAJE_PUNTO_INVALIDO)
        except nx.NetworkXNoPath:
            logger.warning("Ruta %s -> %s: no existe camino", origen, destino)
            return ResultadoRuta(encontrada=False, mensaje=MENSAJE_SIN_RUTA)
        return ResultadoRuta(encontrada=True, ruta=ruta, costo=self.costo_ruta(ruta))

    def costo_ruta(self, ruta):
        return sum(self.costo_tramo(ruta[i], ruta[i + 1]) for i in range(len(ruta) - 1))

    def costo_tramo(self, u, v):
        return self.peso_efectivo(u, v, self.G[u][v])

    def existe_calle(self, u, v):
        return self.G.has_edge(u, v)
