# Simulador de tráfico de ChuroViaje

Simulador de CU-07 (HU-09, RF-10, RNF-12): usuarios fantasma que usan la misma API que la app (RN-07.3). Este sprint (T-102, #78) deja la estructura: los parámetros, su validación y la configuración. El comportamiento de los usuarios fantasma llega en el sprint 6; hasta entonces el simulador valida, muestra el plan y no envía nada al backend.

Por qué un simulador propio y no Locust: [DECISION_locust_o_propio.md](DECISION_locust_o_propio.md).

## Requisitos

Python 3.11 o posterior. No usa paquetes externos.

## Cómo se ejecuta

Desde `04_imp/simulador/`:

```bash
python -m simulador --solo-validar                       # carga normal contra el ambiente de pruebas local
python -m simulador --config simulador.ejemplo.toml --solo-validar
python -m simulador --usuarios 20 --navegando 5 --eventos-por-minuto 4 --duracion 10
python -m simulador --help                                # parámetros y límites
```

En Windows, `py -m simulador ...`.

## Parámetros (RN-07.2)

Van por línea de comandos, por un archivo TOML (`--config`) o por los dos; un argumento manda sobre el archivo. Una clave desconocida en el archivo es un error.

| Parámetro | Argumento | Por defecto | Qué es |
| --- | --- | --- | --- |
| `usuarios` | `--usuarios` | 50 | Usuarios fantasma a la vez |
| `navegando` | `--navegando` | 10 | Cuántos de ellos navegan una ruta y envían telemetría |
| `rutas` | `--rutas` | `../datos/rutas_referencia.json` | Archivo con la lista `viajes`: `nombre`, `origen` y `destino` como `[latitud, longitud]`, y `paradas` opcionales (hasta 10) |
| `eventos_por_minuto` | `--eventos-por-minuto` | 6 | Eventos viales por minuto entre todos |
| `duracion_minutos` | `--duracion` | 15 | Duración de la simulación |
| `backend` | `--backend` | `http://127.0.0.1:8080` | Dirección base del backend |
| `ambiente_remoto` | `--ambiente-remoto` | no | Permite un backend de pruebas en otro servidor, solo por HTTPS |
| `semilla` | `--semilla` | al azar | Para repetir la misma simulación |

**Carga normal** de las pruebas: 50 usuarios fantasma a la vez, 10 de ellos navegando una ruta, 6 eventos por minuto, 15 minutos. Son los valores por defecto.

## Validación (RN-07.1)

Antes de empezar se validan todos los parámetros juntos. Si alguno no sirve, el simulador no se ejecuta, termina con código 2, lista cada problema y muestra los límites. Los límites son la **propuesta de P-18**; se confirman con las pruebas de carga del sprint 7:

| Parámetro | Límite | Motivo |
| --- | --- | --- |
| `usuarios` | 1 a 200 | El backend, el motor y la base comparten una instancia de Oracle Cloud (D-16) |
| `navegando` | 0 a 100, y no más que `usuarios` | Cada uno pide una ruta y envía telemetría cada 15 s (P-13) |
| `rutas` | 1 a 50, con todos los puntos dentro de la zona de cobertura de Tarija | El backend rechaza los puntos de afuera (RN-03.3) |
| `eventos_por_minuto` | 0 a 60, y no más que la mitad de `usuarios` | Cada conductor reporta 5 eventos en 10 minutos como máximo (RN-01.9); más serían rechazos, no carga |
| `duracion_minutos` | 1 a 120 | Una prueba más larga se divide en varias |

## Ambiente (RN-07.12)

Por defecto el simulador apunta al backend del ambiente de pruebas en la misma máquina, nunca al de producción. Otro servidor se acepta solo con `--ambiente-remoto` y por HTTPS, y debe ser un ambiente de pruebas con las credenciales de prueba habilitadas (RN-07.5, P-20).

## Pruebas

```bash
python -m unittest -v
```

CP-128 (`tests/test_parametros.py`, clase `Cp128`): con `--usuarios -5` el simulador no arranca, sale con código 2 y muestra los límites. La prueba imprime el reporte de esa ejecución.
