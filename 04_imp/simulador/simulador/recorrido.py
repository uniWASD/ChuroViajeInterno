"""Un recorrido sobre el trazado de una ruta: dónde está el usuario fantasma después de avanzar tantos metros y qué
tramos le faltan."""

import math
from dataclasses import dataclass, field

Punto = tuple[float, float]  # (latitud, longitud)


def distancia_m(a: Punto, b: Punto) -> float:
    """Haversine: suficiente para las distancias de una ciudad."""
    r = 6_371_000.0
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


@dataclass
class Recorrido:
    nombre: str
    trazado: list[Punto]
    tramos: list[str] = field(default_factory=list)
    destino: Punto | None = None
    paradas: list[Punto] = field(default_factory=list)

    def __post_init__(self):
        if len(self.trazado) < 2:
            raise ValueError("Un recorrido necesita al menos dos puntos")
        self._acumulado = [0.0]
        for a, b in zip(self.trazado, self.trazado[1:]):
            self._acumulado.append(self._acumulado[-1] + distancia_m(a, b))
        if self.destino is None:
            self.destino = self.trazado[-1]

    @property
    def largo_m(self) -> float:
        return self._acumulado[-1]

    def punto_en(self, metros: float) -> Punto:
        metros = max(0.0, min(metros, self.largo_m))
        for i in range(1, len(self._acumulado)):
            if metros <= self._acumulado[i] or i == len(self._acumulado) - 1:
                tramo = self._acumulado[i] - self._acumulado[i - 1]
                f = 0.0 if tramo == 0 else (metros - self._acumulado[i - 1]) / tramo
                a, b = self.trazado[i - 1], self.trazado[i]
                return (a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f)
        return self.trazado[-1]

    def tramos_que_faltan(self, metros: float) -> list[str]:
        """Aproximado por la parte recorrida: el contrato pide los tramos que faltan, en orden (D-12)."""
        if not self.tramos:
            return []
        hecho = 0 if self.largo_m == 0 else metros / self.largo_m
        desde = min(len(self.tramos) - 1, int(hecho * len(self.tramos)))
        return self.tramos[desde:]


def recorrido_recto(nombre: str, origen: Punto, destino: Punto, paradas: list[Punto]) -> Recorrido:
    """Si el backend no dio la ruta: una línea entre los puntos. Sirve como carga, aunque el motor descarte las
    lecturas que caen lejos de una calle (RN-04.8)."""
    return Recorrido(nombre, [origen, *paradas, destino], [], destino, list(paradas))
