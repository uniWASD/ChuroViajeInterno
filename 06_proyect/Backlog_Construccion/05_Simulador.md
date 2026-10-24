# Backlog de Construcción — Simulador de tráfico

[Índice del backlog](README.md)

Responsable: Antonio. Código en `05_pro/Simulador`, junto con las demás pruebas del proyecto.

El simulador crea usuarios fantasma que envían eventos y telemetría por la misma API que usa la app, para probar el sistema bajo carga. Nunca escribe directo en la base de datos y todo lo suyo queda marcado como simulado ([CU-07](../../03_des/02_Arquitectura/04_reglas/CU-07_EjecutarSimuladorTrafico.md)). Antonio también trabaja en el backend ([02_Java.md](02_Java.md)) durante toda la Construcción; los sprints 1 a 4 son solo de Java.

| Sprint | Issues |
| --- | --- |
| S5 (12/11–18/11) | Estructura y parámetros del simulador |
| S6 (19/11–25/11) | HU-09 Usuarios fantasma que envían eventos y telemetría |
| S7 (26/11–30/11) | Pruebas de carga y reporte |

---

## Sprint 5

### [T-102][Simulador] Estructura y parámetros del simulador

**Tipo:** Tarea de infraestructura (base de CU-07)
**Tecnología:** Python · **Sprint:** Sprint 5 (12/11–18/11)
**Depende de:** nada
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:simulador`

**Descripción:** Crear el simulador con su configuración y su validación de parámetros, para que en el sprint 6 solo falte el comportamiento de los usuarios fantasma.

**Criterios de aceptación**
- [ ] Los parámetros (cantidad de usuarios fantasma, rutas, eventos por minuto, duración, dirección del backend) se dan por línea de comandos o en un archivo de configuración (RN-07.2).
- [ ] Antes de empezar, el simulador valida los parámetros; si son inválidos o superan los límites, no se ejecuta e indica los límites permitidos (RN-07.1).
- [ ] Los límites iniciales quedan anotados como propuesta de P-18 y se confirman con las pruebas del sprint 7.
- [ ] Queda definida la «carga normal» que usarán las pruebas (propuesta: 50 usuarios fantasma a la vez, 10 de ellos navegando una ruta).
- [ ] Por defecto apunta al ambiente de pruebas, nunca al de producción (RN-07.12).
- [ ] Hay una decisión escrita sobre usar Locust o un simulador propio, con sus motivos.
- [ ] Las pruebas corren con un solo comando y el README explica cómo ejecutarlo.

**Tareas**
- [ ] Comparar Locust con un simulador propio y anotar la elección.
- [ ] Crear el proyecto con su lectura de parámetros y archivo de ejemplo.
- [ ] Implementar la validación con mensajes que indiquen los límites.
- [ ] Escribir el README.

**Casos de prueba**
- [ ] CP-128 Caso de prueba: con −5 usuarios fantasma, el simulador no arranca y muestra los límites permitidos.

**Relacionado:** RF-10 · RNF-12 · CU-07 · RN-07.1, RN-07.2, RN-07.12 · P-18

Lo subes en una nueva rama llamada "feature/T102-simulador-estructura" (No olvides que el source de la rama es la de develop)

---

## Sprint 6

### [HU-09][CU-07][Simulador] Usuarios fantasma que envían eventos y telemetría

**Caso de uso:** CU-07 — Ejecutar Simulador de Tráfico
**Tecnología:** Python · **Sprint:** Sprint 6 (19/11–25/11)
**Depende de:** [T-102][Simulador] Estructura y parámetros del simulador #78 · [HU-09][CU-07][Java] Soporte para el simulador #84 · [T-106][Servidor] Ambiente de pruebas para el simulador #86
**Etiquetas:** `caso-uso:CU-07` · `tipo:historia-usuario` · `fase:construccion` · `tec:simulador`

**Historia de usuario:** Como desarrollador del equipo, quiero ejecutar un simulador que genere «usuarios fantasma» con eventos y telemetría, para validar el rendimiento y la estabilidad del sistema bajo carga antes de un despliegue.

**Criterios de aceptación**
- [ ] Cada usuario fantasma obtiene su sesión con `POST /sesion/prueba`, enviando el identificador de la prueba de carga, y usa ese JWT en todas sus peticiones (RN-07.4, RN-07.11).
- [ ] Cada usuario fantasma recorre una ruta dentro de Tarija y envía telemetría y eventos por la misma API que la app; los que navegan también piden `POST /rutas/revision` cada 5 segundos, como la app. El simulador nunca toca la base de datos (RN-07.3).
- [ ] El backend procesa esos datos con las mismas reglas que los reales (RN-07.7), y todos quedan marcados como simulados (RN-07.6).
- [ ] Si la simulación se interrumpe (Ctrl+C o un error), se detiene limpia y ningún dato real queda alterado (RN-07.9).
- [ ] Al terminar, el simulador muestra un resumen: peticiones enviadas, errores y tiempos de respuesta.
- [ ] Hay una forma documentada de borrar los datos simulados al final, con el comando del backend (RN-07.8).

**Tareas**
- [ ] Generar rutas plausibles con `POST /rutas` o con recorridos guardados.
- [ ] Implementar el comportamiento de cada usuario fantasma: telemetría cada 15 segundos, eventos de vez en cuando y, para los que navegan, revisión de ruta cada 5 segundos.
- [ ] Implementar la detención limpia y el resumen.
- [ ] Probar contra el ambiente de pruebas con pocos usuarios.

**Casos de prueba**
- [ ] CP-146 Caso de prueba: 10 usuarios fantasma durante 5 minutos generan eventos y congestiones simuladas visibles en modo demostración.
- [ ] CP-147 Caso de prueba: se interrumpe la simulación a la mitad y los datos reales siguen iguales.
- [ ] CP-148 Caso de prueba: después del borrado no queda ningún dato simulado.

**Relacionado:** RF-10 · RNF-12 · CU-07 · RN-07.3, RN-07.4, RN-07.6 a RN-07.9

Lo subes en una nueva rama llamada "feature/HU09-CU07-simulador-usuarios-fantasma" (No olvides que el source de la rama es la de develop)

---

## Sprint 7

### [T-108][Simulador] Pruebas de carga y reporte

**Tipo:** Tarea de pruebas (cubre CU-07 y los requisitos de rendimiento)
**Tecnología:** Python · **Sprint:** Sprint 7 (26/11–30/11) · **Apoyo:** Marcelo
**Depende de:** [HU-09][CU-07][Simulador] Usuarios fantasma que envían eventos y telemetría #87
**Etiquetas:** `tipo:prueba` · `fase:construccion` · `tec:simulador`

**Descripción:** Ejecutar las pruebas de carga en el ambiente de pruebas, comprobar los requisitos de rendimiento y fijar los límites del simulador.

**Criterios de aceptación**
- [ ] Se ejecutan al menos tres escenarios: carga normal, el doble de la carga normal y carga creciente hasta que el backend de pruebas se sature. La prueba de saturación se hace fuera de los horarios de demostración, porque comparte la máquina con producción.
- [ ] Se mide que un evento llega a los demás en 5 a 10 segundos (RNF-01), que la ruta tarda menos de 3 segundos (RNF-02) y que la lista de surtidores tarda menos de 3 segundos (RNF-03).
- [ ] Si el backend se satura, baja su rendimiento sin caerse y lo deja en los registros (RN-07.10, RN-07.11).
- [ ] Los límites del simulador quedan fijados en P-18 de `08_decisiones_parametros.md`.
- [ ] El reporte de resultados queda en `05_pro/Simulador/resultados`.
- [ ] Al terminar, los datos simulados se borran.

**Tareas**
- [ ] Definir los escenarios con el equipo.
- [ ] Ejecutar las pruebas y reunir los registros del backend.
- [ ] Escribir el reporte y actualizar P-18.

**Casos de prueba**
- [ ] CP-151 Caso de prueba: con la carga normal, el 95 % de las rutas responde en menos de 3 segundos.
- [ ] CP-152 Caso de prueba: con la carga creciente, el backend no se cae y la saturación queda registrada.

**Relacionado:** RNF-01 · RNF-02 · RNF-03 · RNF-12 · CU-07 · RN-07.10, RN-07.11 · P-18

Lo subes en una nueva rama llamada "feature/T108-simulador-pruebas-carga" (No olvides que el source de la rama es la de develop)
