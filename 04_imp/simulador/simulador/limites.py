"""Límites del simulador: propuesta de P-18 (08_decisiones_parametros.md).

Se confirman con las pruebas de carga del sprint 7. Mientras tanto, nadie corre una simulación que no los cumpla
(RN-07.1).
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class Rango:
    minimo: int
    maximo: int
    unidad: str
    motivo: str

    def contiene(self, valor: int) -> bool:
        return self.minimo <= valor <= self.maximo

    def texto(self) -> str:
        return f"de {self.minimo} a {self.maximo} {self.unidad}"


USUARIOS = Rango(1, 200, "usuarios fantasma",
                 "El backend, el motor y la base comparten una instancia de Oracle Cloud (D-16).")
NAVEGANDO = Rango(0, 100, "usuarios navegando",
                  "Cada uno pide una ruta y envía telemetría cada 15 s (P-13); no puede pasar de los usuarios.")
RUTAS = Rango(1, 50, "rutas", "Rutas distintas que recorren los que navegan.")
EVENTOS_POR_MINUTO = Rango(0, 60, "eventos por minuto",
                           "Además, a lo sumo la mitad de los usuarios por minuto: cada conductor reporta 5 en "
                           "10 minutos como máximo (RN-01.9).")
DURACION_MINUTOS = Rango(1, 120, "minutos", "Una prueba larga se divide en varias.")

#: RN-01.9: 5 reportes cada 10 minutos por conductor.
EVENTOS_POR_USUARIO_Y_MINUTO = 0.5

TODOS = {
    "usuarios": USUARIOS,
    "navegando": NAVEGANDO,
    "rutas": RUTAS,
    "eventos_por_minuto": EVENTOS_POR_MINUTO,
    "duracion_minutos": DURACION_MINUTOS,
}


def tabla() -> str:
    """Los límites permitidos, para mostrarlos cuando los parámetros no sirven."""
    filas = [f"  {nombre:<20} {rango.texto():<32} {rango.motivo}" for nombre, rango in TODOS.items()]
    filas.append(f"  {'eventos_por_minuto':<20} {'≤ 0,5 × usuarios':<32} RN-01.9")
    filas.append(f"  {'navegando':<20} {'≤ usuarios':<32} Un usuario navegando también es un usuario fantasma.")
    filas.append(f"  {'backend':<20} {'http://127.0.0.1 o localhost':<32} Otro servidor pide --ambiente-remoto (RN-07.12).")
    return "Límites permitidos (propuesta de P-18):\n" + "\n".join(filas)
