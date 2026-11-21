"""La simulación completa (CU-07): prepara las rutas, crea los usuarios fantasma, reparte los eventos y, al terminar o
al interrumpirse, se detiene limpia y deja el resumen (RN-07.9)."""

import random
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

from simulador.cliente import Cliente
from simulador.fantasma import UsuarioFantasma, ubicacion
from simulador.metricas import Metricas
from simulador.parametros import Parametros
from simulador.recorrido import Recorrido, recorrido_recto


class Reloj:
    """El tiempo de la simulación. Con [factor] mayor que 1 pasa más rápido; solo lo usan las pruebas automáticas
    contra un backend falso."""

    def __init__(self, factor: float = 1.0):
        self.factor = factor
        self._inicio_real = time.monotonic()
        self._inicio = datetime.now(timezone.utc)

    def ahora(self) -> datetime:
        return self._inicio + timedelta(seconds=(time.monotonic() - self._inicio_real) * self.factor)

    def transcurrido_s(self) -> float:
        return (time.monotonic() - self._inicio_real) * self.factor

    def dormir(self, segundos: float, detener: threading.Event) -> None:
        detener.wait(segundos / self.factor)


@dataclass
class Resultado:
    prueba: str
    interrumpida: bool
    usuarios_con_sesion: int
    resumen: dict = field(default_factory=dict)


def preparar_recorridos(par: Parametros, fantasma: UsuarioFantasma) -> list[Recorrido]:
    """Las rutas del archivo, calculadas una vez con POST /rutas. Una que no se pudo calcular se recorre en línea
    recta, para no frenar la prueba."""
    recorridos = []
    for r in par.rutas:
        cuerpo = {"origen": ubicacion(r.origen), "destino": ubicacion(r.destino),
                  "paradas": [ubicacion(p) for p in r.paradas]}
        resp = fantasma.pedir("rutas", "POST", "/rutas", cuerpo)
        c = resp.cuerpo if isinstance(resp.cuerpo, dict) else {}
        if resp.ok and c.get("estado") == "ENCONTRADA" and len(c.get("trazado") or []) >= 2:
            recorridos.append(Recorrido(r.nombre, [(p["latitud"], p["longitud"]) for p in c["trazado"]],
                                        [t["id"] for t in c.get("tramos", [])], r.destino, list(r.paradas)))
        else:
            fantasma.cliente.metricas.contar("rutas_en_linea_recta")
            recorridos.append(recorrido_recto(r.nombre, r.origen, r.destino, list(r.paradas)))
    return recorridos


class Simulacion:
    def __init__(self, par: Parametros, credencial: str, prueba: str, reloj: Reloj | None = None,
                 escribir=print):
        self.par = par
        self.prueba = prueba
        self._credencial = credencial
        self.reloj = reloj or Reloj()
        self.escribir = escribir
        self.metricas = Metricas()
        self.detener = threading.Event()
        self.rng = random.Random(par.semilla)

    def ejecutar(self) -> Resultado:
        cliente = Cliente(self.par.backend, self.metricas)
        fantasmas = [UsuarioFantasma(n, cliente, self._credencial, self.prueba, [], n <= self.par.navegando,
                                     self.reloj, random.Random(self.rng.random()))
                     for n in range(1, self.par.usuarios + 1)]
        interrumpida = False
        hilos: list[threading.Thread] = []
        try:
            con_sesion = [f for f in fantasmas if not self.detener.is_set() and f.iniciar_sesion()]
            self.escribir(f"Prueba {self.prueba}: {len(con_sesion)} de {len(fantasmas)} usuarios fantasma con sesión.")
            if not con_sesion:
                return Resultado(self.prueba, False, 0, self.metricas.resumen())
            recorridos = preparar_recorridos(self.par, con_sesion[0])
            for f in con_sesion:
                f.recorridos = recorridos
                hilo = threading.Thread(target=self._vivir, args=(f,), name=f"fantasma-{f.numero}", daemon=True)
                hilos.append(hilo)
                hilo.start()
            self._repartir_eventos(con_sesion)
        except KeyboardInterrupt:
            interrumpida = True
        finally:
            # RN-07.9: se detiene limpia; lo que ya se envió queda marcado como simulado en el backend.
            self.detener.set()
            for hilo in hilos:
                hilo.join(timeout=20)
        return Resultado(self.prueba, interrumpida, len([f for f in fantasmas if f.jwt]), self.metricas.resumen())

    def interrumpir(self) -> None:
        self.detener.set()

    def _vivir(self, fantasma: UsuarioFantasma) -> None:
        try:
            fantasma.recorrer(self.detener)
        except Exception as e:  # un hilo que falla no tumba la prueba; queda anotado
            self.metricas.contar("fallos_de_hilo")
            self.escribir(f"El usuario fantasma {fantasma.numero} se detuvo por un error: {e}")

    def _repartir_eventos(self, fantasmas: list[UsuarioFantasma]) -> None:
        """Hasta que pase la duración: los eventos por minuto, repartidos entre todos por turno, para que nadie pase
        el límite de 5 reportes en 10 minutos (RN-01.9)."""
        duracion_s = self.par.duracion_minutos * 60
        cada_s = 60 / self.par.eventos_por_minuto if self.par.eventos_por_minuto else None
        proximo = cada_s if cada_s else duracion_s
        turno = 0
        while not self.detener.is_set() and self.reloj.transcurrido_s() < duracion_s:
            espera = min(proximo, duracion_s) - self.reloj.transcurrido_s()
            if espera > 0:
                self.reloj.dormir(min(espera, 1), self.detener)
                continue
            if cada_s:
                f = fantasmas[turno % len(fantasmas)]
                turno += 1
                if f.reportar_evento():
                    self.metricas.contar("eventos_aceptados")
                proximo += cada_s
            else:
                proximo = duracion_s


def borrar_datos_simulados(backend: str, credencial: str) -> tuple[bool, dict | str]:
    """RN-07.8: el comando del backend que borra todo lo simulado (POST /simulacion/borrar)."""
    r = Cliente(backend, Metricas()).pedir("borrar", "POST", "/simulacion/borrar", {"credencial": credencial})
    if r.ok and isinstance(r.cuerpo, dict):
        return True, r.cuerpo
    mensaje = r.cuerpo.get("mensaje") if isinstance(r.cuerpo, dict) else None
    return False, mensaje or (f"el backend respondió {r.estado}" if r.estado else "el backend no respondió")
