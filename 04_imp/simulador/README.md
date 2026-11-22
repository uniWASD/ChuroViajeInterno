# Simulador de tráfico de ChuroViaje

Simulador de CU-07 (HU-09, RF-10, RNF-12): usuarios fantasma que usan la misma API que la app (RN-07.3) y nunca tocan la base de datos. T-102 (#78) dejó los parámetros y su validación; #87 les da el comportamiento: cada usuario fantasma entra con una sesión de prueba, recorre rutas de Tarija enviando telemetría, reporta eventos y, si navega, pide la revisión de su ruta como la app.

Por qué un simulador propio y no Locust: [DECISION_locust_o_propio.md](DECISION_locust_o_propio.md).

## Requisitos

Python 3.11 o posterior. No usa paquetes externos.

## Cómo se ejecuta

Desde `04_imp/simulador/`:

```bash
python -m simulador --solo-validar                       # valida y muestra el plan, sin enviar nada
read -rs SIMULACION_CREDENCIAL && export SIMULACION_CREDENCIAL   # la credencial de prueba del ambiente
python -m simulador --usuarios 10 --navegando 4 --eventos-por-minuto 4 --duracion 5 --prueba carga-2026-11-22-a
python -m simulador --borrar                              # al terminar: borra los datos simulados
python -m simulador --help                                # parámetros y límites
```

Contra el ambiente de pruebas del servidor (T-106, #86): `--backend https://pruebas.churoviaje.orelqu.com --ambiente-remoto`. En Windows, `py -m simulador ...` y la credencial con `$env:SIMULACION_CREDENCIAL = Read-Host`.

La credencial de prueba (RN-07.5) se lee solo de la variable `SIMULACION_CREDENCIAL`: no va en el archivo TOML ni en la línea de comandos, para que no quede en el historial ni en el repositorio, y el simulador nunca la imprime. Sin ella, solo funciona `--solo-validar`.

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
| — | `--prueba` | `carga-AAAAMMDD-HHMMSS` | Identificador de la prueba de carga: el backend registra la prueba con él (RN-07.11) |
| — | `--reporte` | ninguno | Archivo JSON donde queda el resumen, como evidencia |
| — | `--borrar` | — | No simula: borra los datos simulados con el comando del backend (RN-07.8) |

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

## Qué hace cada usuario fantasma

1. **Sesión (RN-07.4).** Pide `POST /sesion/prueba` con la credencial, el identificador de la prueba y su número (1, 2, 3…). Usa ese JWT en todas sus peticiones; si vence (dura 1 hora), pide otra sesión y repite la petición.
2. **Rutas.** Al empezar, el simulador calcula cada ruta del archivo con `POST /rutas` y los usuarios fantasma recorren esos trazados, sobre las calles. Los que navegan, además, piden su propia ruta con `POST /rutas` cada vez que empiezan una. Una ruta que el backend no pudo calcular se recorre en línea recta.
3. **Telemetría (P-13).** Una lectura cada 3 segundos, enviadas en grupos de 5 a `POST /telemetria` cada 15 segundos. Van a 30 km/h y, una vez por ruta, en un punto al azar, bajan a 4 km/h durante 2,5 minutos: esa racha lenta es la que el motor reconoce como congestión (P-02, P-36, P-37).
4. **Revisión de la ruta.** Los que navegan piden `POST /rutas/revision` cada 5 segundos con lo que les falta. Si llega un aviso, no lo aceptan y siguen su ruta (D-19), y ese evento va en `eventosAvisados`.
5. **Eventos.** Entre todos reportan `eventos_por_minuto` eventos de un tipo al azar en su posición, por turno, para que nadie pase el límite de 5 cada 10 minutos (RN-01.9). El backend aplica las reglas de siempre y los marca como simulados (RN-07.6, RN-07.7).
6. Al terminar la ruta, empiezan otra, hasta que pase la duración.

## Detención y resumen (RN-07.9)

Con Ctrl+C, o al pasar la duración, el simulador deja de repartir eventos, avisa a los usuarios fantasma y espera a que terminen la petición en curso: después no sale nada más. Lo que ya se envió queda marcado como simulado en el backend, y ningún dato real se toca. Al final muestra el resumen: peticiones enviadas por operación, errores (sin respuesta o 5xx), rechazos (4xx, que son reglas del backend y no fallos), tiempos de respuesta (promedio, p95 y máximo), eventos aceptados, rachas lentas y avisos de ruta. Con `--reporte` deja lo mismo en JSON.

Códigos de salida: 0 terminó, 2 parámetros inválidos o falta la credencial, 3 ningún usuario fantasma obtuvo su sesión, 4 el borrado falló, 130 se interrumpió.

## Borrar los datos simulados (RN-07.8)

```bash
python -m simulador --borrar                                                   # ambiente local
python -m simulador --borrar --backend https://pruebas.churoviaje.orelqu.com --ambiente-remoto
```

Llama a `POST /simulacion/borrar` del backend con la credencial de prueba, y el backend borra todas las filas simuladas en una sola transacción: votos, eventos y usuarios fantasma. Muestra cuántas borró. En producción ese comando no existe y responde 403.

## Pruebas

```bash
python -m unittest -v
```

CP-128 (`tests/test_parametros.py`, clase `Cp128`): con `--usuarios -5` el simulador no arranca, sale con código 2 y muestra los límites. La prueba imprime el reporte de esa ejecución.

`tests/test_simulacion.py` corre el simulador contra un backend falso (`tests/backend_falso.py`) con el tiempo acelerado: la sesión de prueba con el identificador, la telemetría en grupos de 5, la revisión solo de los que navegan, los eventos, que nunca use otra ruta que la API, que Ctrl+C lo detenga limpio (CP-147) y que `--borrar` use el comando del backend (CP-148). CP-146 a CP-148 de punta a punta se prueban contra el ambiente de pruebas del servidor (#86): ver la guía del servidor.
