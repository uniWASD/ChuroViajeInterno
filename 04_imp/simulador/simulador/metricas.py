"""Lo que mide el simulador: cuántas peticiones envió por operación, cuántas fallaron y cuánto tardaron. Se comparte
entre los hilos de los usuarios fantasma."""

import threading
from collections import Counter, defaultdict


def percentil(valores: list[float], p: float) -> float:
    if not valores:
        return 0.0
    ordenados = sorted(valores)
    k = min(len(ordenados) - 1, max(0, round(p / 100 * (len(ordenados) - 1))))
    return ordenados[k]


class Metricas:
    def __init__(self):
        self._candado = threading.Lock()
        self.peticiones: Counter = Counter()
        self.estados: dict[str, Counter] = defaultdict(Counter)
        self.tiempos: dict[str, list[float]] = defaultdict(list)
        self.notas: Counter = Counter()

    def anotar(self, operacion: str, estado: int, ms: float) -> None:
        with self._candado:
            self.peticiones[operacion] += 1
            self.estados[operacion][estado] += 1
            self.tiempos[operacion].append(ms)

    def contar(self, nota: str, cuanto: int = 1) -> None:
        """Algo que no es una petición: eventos aceptados, congestiones forzadas, alternativas…"""
        with self._candado:
            self.notas[nota] += cuanto

    def errores(self) -> int:
        """Sin respuesta o con un 5xx: lo que cuenta como fallo del backend."""
        with self._candado:
            return sum(n for c in self.estados.values() for estado, n in c.items() if estado == 0 or estado >= 500)

    def rechazos(self) -> int:
        """4xx: el backend aplicó una regla (duplicado, límite de reportes…), no es un fallo."""
        with self._candado:
            return sum(n for c in self.estados.values() for estado, n in c.items() if 400 <= estado < 500)

    def resumen(self) -> dict:
        with self._candado:
            operaciones = {}
            for op in sorted(self.peticiones):
                t = self.tiempos[op]
                operaciones[op] = {
                    "peticiones": self.peticiones[op],
                    "estados": {str(k): v for k, v in sorted(self.estados[op].items())},
                    "promedio_ms": round(sum(t) / len(t), 1) if t else 0,
                    "p95_ms": round(percentil(t, 95), 1),
                    "maximo_ms": round(max(t), 1) if t else 0,
                }
            todos = [x for t in self.tiempos.values() for x in t]
            total = sum(self.peticiones.values())
        return {
            "peticiones": total,
            "errores": self.errores(),
            "rechazos": self.rechazos(),
            "promedio_ms": round(sum(todos) / len(todos), 1) if todos else 0,
            "p95_ms": round(percentil(todos, 95), 1),
            "maximo_ms": round(max(todos), 1) if todos else 0,
            "operaciones": operaciones,
            "notas": dict(self.notas),
        }


def texto_resumen(r: dict, prueba: str, interrumpida: bool) -> str:
    filas = [f"Resumen de la prueba {prueba}{' (interrumpida)' if interrumpida else ''}:",
             f"  peticiones enviadas  {r['peticiones']}",
             f"  errores              {r['errores']} (sin respuesta o 5xx)",
             f"  rechazos             {r['rechazos']} (4xx: reglas del backend, no son fallos)",
             f"  tiempo de respuesta  promedio {r['promedio_ms']} ms · p95 {r['p95_ms']} ms · máximo {r['maximo_ms']} ms",
             "  por operación:"]
    for op, d in r["operaciones"].items():
        estados = ", ".join(f"{k}: {v}" for k, v in d["estados"].items())
        filas.append(f"    {op:<20} {d['peticiones']:>6}  p95 {d['p95_ms']:>7} ms  ({estados})")
    for nota, n in sorted(r["notas"].items()):
        filas.append(f"  {nota:<20} {n}")
    return "\n".join(filas)
