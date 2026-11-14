# Simulador: Locust o un simulador propio (T-102, #78)

**Decisión:** simulador propio en Python, solo con la biblioteca estándar. Propuesta del 14/11/2026, a confirmar por el equipo.

## Qué tiene que hacer el simulador

Lo dice CU-07: cada usuario fantasma entra con una credencial de prueba (RN-07.4, RN-07.5), reporta eventos que pasan por las reglas de siempre, como el límite de 5 reportes en 10 minutos (RN-07.7, RN-01.9), y, si navega, pide una ruta y la recorre mandando telemetría: una lectura cada 3 segundos, en grupos cada 15 (P-13), con la revisión de la ruta activa (D-12). Todo va por la API de la app (RN-07.3) y al final se borra lo simulado aparte (RN-07.8).

## Comparación

| Criterio | Locust | Simulador propio |
| --- | --- | --- |
| Para qué está hecho | Medir cuántas peticiones por segundo aguanta un servidor | Lo que se le programe |
| Usuario con estado (sesión, ruta, posición, lecturas acumuladas) | Posible, pero su modelo es «tareas al azar con esperas» y hay que forzarlo | Directo: cada usuario fantasma es un objeto que avanza por su ruta |
| Validar los parámetros antes de empezar y mostrar los límites (RN-07.1) | Hay que agregarlo por fuera de su línea de comandos | Es lo primero que hace |
| Dependencias | `locust` trae `gevent`, `greenlet`, Flask y más; en el servidor ARM de Oracle (D-16) puede necesitar compilar | Ninguna: Python 3.11 o posterior |
| Métricas de carga (latencia, errores, peticiones por segundo) | Las da hechas, con interfaz web | Hay que contarlas; para el sprint 7 alcanza con percentiles de latencia por ruta |
| Repetir una simulación | No trae semilla para el comportamiento | `--semilla` |

## Por qué

- Lo difícil del simulador es el comportamiento de cada usuario fantasma, no generar muchas peticiones: 200 usuarios con telemetría cada 15 s son unas 13 peticiones por segundo, que un proceso de Python maneja sin ayuda.
- Sin dependencias corre igual en las máquinas del equipo, en la CI y en el servidor.
- La validación de parámetros (RN-07.1) y el ambiente de pruebas por defecto (RN-07.12) quedan en el mismo programa.

## Qué se pierde y cuándo revisarlo

Las métricas y la interfaz web de Locust. Si en las pruebas de carga del sprint 7 (cuando se confirman los límites de P-18) hace falta medir el backend con miles de peticiones por segundo, se puede escribir un `locustfile` aparte solo para esa medición, sin cambiar este simulador.
