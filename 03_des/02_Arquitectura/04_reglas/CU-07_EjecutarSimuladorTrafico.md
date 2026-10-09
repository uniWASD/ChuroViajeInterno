# CU-07 — Ejecutar simulador de tráfico

[Índice de reglas](README.md) · [Índice de la arquitectura](../README.md)

Cubre HU-09 y los requisitos RF-10 y RNF-12.

## Reglas

| Código | Regla | Objeto responsable | Fuente |
| --- | --- | --- | --- |
| RN-07.1 | Antes de empezar, el simulador valida sus parámetros: cantidad de usuarios fantasma, rutas y eventos. Si son inválidos o superan los límites, no se ejecuta e indica los límites permitidos. | Simulación | CU-07 paso 1 y ext. 1a, HU-09 c.2 |
| RN-07.2 | Los parámetros se dan por línea de comandos o en un archivo de configuración. | Simulación | CU-07 sección 7 |
| RN-07.3 | El simulador envía todo por la misma API que usa la app. Nunca escribe directo en la base de datos. | Simulación | CU-07 sección 8 |
| RN-07.4 | Cada usuario fantasma entra con credenciales de prueba, y el backend lo trata como un `Conductor` con marca de simulado. | UsuarioFantasma, Conductor | CU-07 sección 8, HU-09 c.5 |
| RN-07.5 | Las credenciales de prueba no pasan por Google y solo funcionan en un ambiente que las tenga habilitadas. | Sesión | Propuesta [P-20](../08_decisiones_parametros.md) |
| RN-07.6 | Todo evento, voto y lectura de telemetría de un usuario fantasma queda marcado como simulado. | EventoVial, Voto, LecturaTelemetría | CU-07 sección 8, HU-09 c.5 |
| RN-07.7 | Los datos simulados pasan por las mismas reglas que los reales: validación, duplicados, límites y vigencia. | EventoVial | CU-07 paso 3 y sección 8, HU-09 c.1 |
| RN-07.8 | Los datos simulados se pueden eliminar aparte, sin tocar un solo dato real. | Simulación | CU-07 sección 8, HU-09 c.5, RNF-12 |
| RN-07.9 | Si la simulación falla o se interrumpe, ningún dato real queda alterado. Lo que ya se envió sigue marcado como simulado. | Simulación | CU-07 garantía mínima, HU-09 c.4 |
| RN-07.10 | Si el backend no soporta la carga, baja su rendimiento sin que eso cuente como fallo del proceso, y lo deja escrito en los registros. | Simulación | CU-07 ext. 3a, HU-09 c.3 |
| RN-07.11 | El backend registra cada prueba de carga para revisar después sus resultados. | Simulación | CU-07 paso 4 |
| RN-07.12 | Siempre que se pueda, la simulación corre en un ambiente separado del de producción. | Simulación | CU-07 sección 7 |
| RN-07.13 | Un conductor real ve datos simulados solo si el ambiente está en modo demostración. | EventoVial | Propuesta [P-20](../08_decisiones_parametros.md) |

## Recorrido por Vista, Controlador y Modelo

El simulador no tiene vista en la app: el desarrollador lo maneja desde la terminal.

1. **Desarrollador.** Define los parámetros y ejecuta el simulador (RN-07.2).
2. **Simulador.** Valida los parámetros y, si no sirven, termina indicando los límites (RN-07.1).
3. **Simulador.** Crea los usuarios fantasma y cada uno obtiene su sesión de prueba en el backend (RN-07.4, RN-07.5).
4. **Simulador.** Cada usuario fantasma envía eventos y telemetría por la API (RN-07.3).
5. **Controladores REST (backend).** Son los mismos de CU-01 y CU-04. Comprueban el JWT y el formato, y llaman al modelo.
6. **Modelo (backend y motor core).** Aplica las reglas de siempre (RN-07.7) y marca todo como simulado (RN-07.6).
7. **Backend.** Registra la prueba y, si se satura, lo deja anotado (RN-07.10, RN-07.11).
8. **Desarrollador.** Revisa los resultados y, al terminar, elimina los datos simulados (RN-07.8).
