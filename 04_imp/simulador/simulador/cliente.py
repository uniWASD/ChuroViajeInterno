"""Cliente HTTP del simulador: la misma API que usa la app (RN-07.3), con la biblioteca estándar de Python. Cada
petición queda medida en las métricas: operación, estado HTTP y tiempo de respuesta."""

import json
import time
import urllib.error
import urllib.request
from dataclasses import dataclass

from simulador.metricas import Metricas

#: Sin respuesta en este tiempo, la petición cuenta como error (el backend debe responder en 3 s, RN-03.15).
ESPERA_S = 10


@dataclass
class Respuesta:
    estado: int  # 0 si no hubo respuesta (sin red, tiempo de espera)
    cuerpo: object

    @property
    def ok(self) -> bool:
        return 200 <= self.estado < 300


class Cliente:
    def __init__(self, backend: str, metricas: Metricas, espera_s: float = ESPERA_S):
        self.backend = backend.rstrip("/")
        self.metricas = metricas
        self.espera_s = espera_s

    def pedir(self, operacion: str, metodo: str, ruta: str, cuerpo: object = None, jwt: str | None = None) -> Respuesta:
        datos = None if cuerpo is None else json.dumps(cuerpo).encode("utf-8")
        cabeceras = {"Accept": "application/json"}
        if datos is not None:
            cabeceras["Content-Type"] = "application/json"
        if jwt:
            cabeceras["Authorization"] = f"Bearer {jwt}"
        peticion = urllib.request.Request(self.backend + ruta, data=datos, headers=cabeceras, method=metodo)
        inicio = time.monotonic()
        estado, texto = 0, ""
        try:
            with urllib.request.urlopen(peticion, timeout=self.espera_s) as r:
                estado, texto = r.status, r.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            estado, texto = e.code, e.read().decode("utf-8", errors="replace")
        except (urllib.error.URLError, TimeoutError, OSError):
            estado = 0
        ms = (time.monotonic() - inicio) * 1000
        self.metricas.anotar(operacion, estado, ms)
        try:
            leido = json.loads(texto) if texto else None
        except json.JSONDecodeError:
            leido = None
        return Respuesta(estado, leido)
