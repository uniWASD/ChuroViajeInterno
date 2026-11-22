"""Un usuario fantasma (CU-07, HU-09): obtiene su sesión de prueba y recorre una ruta enviando telemetría y, si navega,
pidiendo la revisión de su ruta cada 5 segundos, como la app. Todo por la misma API (RN-07.3)."""

import random
import threading
from datetime import datetime, timezone

from simulador.cliente import Cliente
from simulador.recorrido import Punto, Recorrido

#: P-13: una lectura del GPS cada 3 segundos, enviadas en grupo cada 15.
LECTURA_CADA_S = 3
TELEMETRIA_CADA_S = 15
#: D-12 y RN-04.14: con una ruta activa, la app la hace revisar cada 5 segundos.
REVISION_CADA_S = 5
#: Velocidad normal y la de una racha lenta. La racha dura más de 2 minutos y avanza más de 25 m, así que el motor la
#: cuenta como congestión (P-02, P-36, P-37).
VELOCIDAD_NORMAL_MS = 30 / 3.6
VELOCIDAD_LENTA_MS = 4 / 3.6
RACHA_LENTA_S = 150
TIPOS_EVENTO = ("ACCIDENTE", "CONTROL_POLICIAL", "BLOQUEO", "OTRO")


def iso(instante: datetime) -> str:
    return instante.astimezone(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def ubicacion(p: Punto) -> dict:
    return {"latitud": round(p[0], 7), "longitud": round(p[1], 7)}


class UsuarioFantasma:
    def __init__(self, numero: int, cliente: Cliente, credencial: str, prueba: str, recorridos: list[Recorrido],
                 navega: bool, reloj, rng: random.Random):
        self.numero = numero
        self.cliente = cliente
        self._credencial = credencial
        self.prueba = prueba
        self.recorridos = recorridos
        self.navega = navega
        self.reloj = reloj
        self.rng = rng
        self.jwt: str | None = None
        self.posicion: Punto | None = None
        self._candado = threading.Lock()

    # --- sesión ---------------------------------------------------------------------------------------------------

    def iniciar_sesion(self) -> bool:
        """RN-07.4: cada usuario fantasma entra con la credencial de prueba y el identificador de la prueba."""
        r = self.cliente.pedir("sesion_prueba", "POST", "/sesion/prueba",
                               {"credencial": self._credencial, "pruebaId": self.prueba, "usuario": self.numero})
        if r.ok and isinstance(r.cuerpo, dict) and r.cuerpo.get("jwt"):
            self.jwt = r.cuerpo["jwt"]
            return True
        return False

    def pedir(self, operacion: str, metodo: str, ruta: str, cuerpo=None):
        """Con el JWT del usuario. Si venció (dura 1 hora), pide otra sesión de prueba y repite una vez."""
        r = self.cliente.pedir(operacion, metodo, ruta, cuerpo, self.jwt)
        if r.estado == 401 and self.iniciar_sesion():
            r = self.cliente.pedir(operacion, metodo, ruta, cuerpo, self.jwt)
        return r

    # --- acciones -------------------------------------------------------------------------------------------------

    def reportar_evento(self) -> bool:
        """Un evento donde está ahora, de un tipo al azar. El backend aplica las reglas de siempre (RN-07.7)."""
        with self._candado:
            p = self.posicion
        if p is None:
            return False
        cuerpo = {"tipo": self.rng.choice(TIPOS_EVENTO), "ubicacion": ubicacion(p),
                  "horaConfirmacion": iso(self.reloj.ahora())}
        r = self.pedir("eventos", "POST", "/eventos", cuerpo)
        return r.ok

    def _pedir_ruta(self, base: Recorrido) -> Recorrido:
        """Los que navegan piden su ruta con POST /rutas, como la app; si no llega, siguen el trazado ya preparado."""
        cuerpo = {"origen": ubicacion(base.trazado[0]), "destino": ubicacion(base.destino),
                  "paradas": [ubicacion(p) for p in base.paradas]}
        r = self.pedir("rutas", "POST", "/rutas", cuerpo)
        c = r.cuerpo if isinstance(r.cuerpo, dict) else {}
        if r.ok and c.get("estado") == "ENCONTRADA" and len(c.get("trazado") or []) >= 2:
            return Recorrido(base.nombre, [(p["latitud"], p["longitud"]) for p in c["trazado"]],
                             [t["id"] for t in c.get("tramos", [])], base.destino, list(base.paradas))
        return base

    def recorrer(self, detener: threading.Event) -> None:
        """Hasta que termine la simulación: una ruta tras otra, empezando por una distinta para cada usuario."""
        indice = self.numero % len(self.recorridos)
        while not detener.is_set():
            base = self.recorridos[indice % len(self.recorridos)]
            ruta = self._pedir_ruta(base) if self.navega else base
            self._una_ruta(ruta, detener)
            indice += 1

    def _una_ruta(self, ruta: Recorrido, detener: threading.Event) -> None:
        avanzado = 0.0
        lecturas: list[dict] = []
        avisados: list[int] = []
        # Una racha lenta por ruta, después de venir manejando (RN-04.22), en un punto al azar.
        inicio_lento = self.rng.uniform(30, max(31, ruta.largo_m / VELOCIDAD_NORMAL_MS * 0.6))
        t = 0.0
        ultima_lectura = ultima_telemetria = ultima_revision = 0.0
        while avanzado < ruta.largo_m and not detener.is_set():
            lento = inicio_lento <= t < inicio_lento + RACHA_LENTA_S
            if lento and t - 1 < inicio_lento:
                self.cliente.metricas.contar("rachas_lentas")
            avanzado += VELOCIDAD_LENTA_MS if lento else VELOCIDAD_NORMAL_MS
            t += 1
            punto = ruta.punto_en(avanzado)
            with self._candado:
                self.posicion = punto
            if t - ultima_lectura >= LECTURA_CADA_S:
                ultima_lectura = t
                lecturas.append({"ubicacion": ubicacion(punto), "precisionM": 6, "momento": iso(self.reloj.ahora())})
            if t - ultima_telemetria >= TELEMETRIA_CADA_S and lecturas:
                ultima_telemetria = t
                self.pedir("telemetria", "POST", "/telemetria", {"lecturas": lecturas})
                lecturas = []
            if self.navega and ruta.tramos and t - ultima_revision >= REVISION_CADA_S:
                ultima_revision = t
                self._revisar(ruta, avanzado, punto, avisados)
            self.reloj.dormir(1, detener)

    def _revisar(self, ruta: Recorrido, avanzado: float, punto: Punto, avisados: list[int]) -> None:
        cuerpo = {"posicion": ubicacion(punto), "tramos": ruta.tramos_que_faltan(avanzado),
                  "destino": ubicacion(ruta.destino), "paradas": [ubicacion(p) for p in ruta.paradas],
                  "eventosAvisados": avisados}
        r = self.pedir("revision_ruta", "POST", "/rutas/revision", cuerpo)
        c = r.cuerpo if isinstance(r.cuerpo, dict) else {}
        if c.get("eventoId") is not None:
            # D-19: el usuario fantasma no responde el aviso y sigue su ruta; no se le vuelve a avisar.
            avisados.append(c["eventoId"])
            self.cliente.metricas.contar("avisos_de_ruta")
