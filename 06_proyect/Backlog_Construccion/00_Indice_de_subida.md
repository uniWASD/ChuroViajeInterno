# Índice de subida del backlog

[Índice del backlog](README.md)

Este archivo dice en qué orden se crean los 53 issues del backlog en GitHub y con qué Milestone y etiquetas. El texto de cada issue sigue en su archivo por tecnología; aquí solo está el orden, para que cada issue se cree después de los issues de los que depende y se pueda enlazar con su número.

Los issues todavía llevan `T-XXX` y `CP-XX`. Los números se asignan al subirlos: son 24 tareas técnicas y 131 casos de prueba, a partir del primer número libre en ese momento.

## Pasos para subir

1. Revisar en [TRAZABILIDAD.md](../TRAZABILIDAD.md) y en GitHub cuál es la última tarea (T) y el último caso de prueba (CP) usados.
2. Crear las etiquetas `tec:flutter`, `tec:java`, `tec:cpp`, `tec:whatsapp-llm`, `tec:simulador` y `tec:servidor`, si no existen.
3. Crear los 7 Milestones de la tabla de abajo, con su fecha de cierre.
4. Crear los issues en el orden de la tabla. Al crear cada uno:
   - reemplazar `T-XXX` por la siguiente tarea libre y `TXX` de la rama por el mismo número (T-086 → `feature/T86-...`);
   - reemplazar cada `CP-XX` por el siguiente caso de prueba libre;
   - agregar el número de GitHub (`#N`) junto a cada issue de la línea «Depende de», que ya existe porque se creó antes;
   - asignar el Milestone y las etiquetas.
5. Escribir en los archivos por tecnología los números asignados (T, CP y `#N`) y anotarlos en la columna «Número en GitHub» de esta tabla.

## Milestones

| Milestone | Fechas | Fecha de cierre | Issues |
| --- | --- | --- | --- |
| Sprint 1 | 15/10 – 21/10 | 2026-10-21 | 6 |
| Sprint 2 | 22/10 – 28/10 | 2026-10-28 | 8 |
| Sprint 3 | 29/10 – 04/11 | 2026-11-04 | 8 |
| Sprint 4 | 05/11 – 11/11 | 2026-11-11 | 8 |
| Sprint 5 | 12/11 – 18/11 | 2026-11-18 | 11 |
| Sprint 6 | 19/11 – 25/11 | 2026-11-25 | 8 |
| Sprint 7 | 26/11 – 30/11 | 2026-11-30 | 4 |

## Orden de creación

La columna «Depende de» usa el número de orden de esta tabla.

| Orden | Issue | Archivo | Milestone | Etiquetas | Depende de | CP | Número en GitHub |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | [T-XXX][Flutter] Estructura base de la app | [01_Flutter.md](01_Flutter.md) | Sprint 1 | tipo:tarea, fase:construccion, tec:flutter | — | 1 | |
| 2 | [T-XXX][Java] Estructura base del backend | [02_Java.md](02_Java.md) | Sprint 1 | tipo:tarea, fase:construccion, tec:java | — | 1 | |
| 3 | [T-XXX][Java] Base de datos, catálogos y zona de cobertura | [02_Java.md](02_Java.md) | Sprint 1 | tipo:tarea, fase:construccion, tec:java | 2 | 2 | |
| 4 | [T-XXX][C++] Estructura del núcleo y protocolo por pipe | [03_Cpp.md](03_Cpp.md) | Sprint 1 | tipo:tarea, fase:construccion, tec:cpp | — | 2 | |
| 5 | [T-XXX][WhatsApp/LLM] Estructura del servicio | [04_WhatsApp_LLM.md](04_WhatsApp_LLM.md) | Sprint 1 | tipo:tarea, fase:construccion, tec:whatsapp-llm | — | 1 | |
| 6 | [T-XXX][Servidor] Preparar la instancia de Oracle Cloud | [06_Servidor.md](06_Servidor.md) | Sprint 1 | tipo:tarea, fase:construccion, tec:servidor | — | 2 | |
| 7 | [T-XXX][Flutter] Mapa base con Mapbox y ubicación | [01_Flutter.md](01_Flutter.md) | Sprint 2 | tipo:tarea, fase:construccion, tec:flutter | 1 | 2 | |
| 8 | [HU-10][CU-08][Java] Iniciar sesión con Google y emitir el JWT | [02_Java.md](02_Java.md) | Sprint 2 | caso-uso:CU-08, tipo:historia-usuario, fase:construccion, tec:java | 3 | 5 | |
| 9 | [T-XXX][Java] Catálogos para la app | [02_Java.md](02_Java.md) | Sprint 2 | tipo:tarea, fase:construccion, tec:java | 3, 8 | 1 | |
| 10 | [HU-10][CU-08][Flutter] Iniciar sesión con Google | [01_Flutter.md](01_Flutter.md) | Sprint 2 | caso-uso:CU-08, tipo:historia-usuario, fase:construccion, tec:flutter | 1, 8, 9 | 4 | |
| 11 | [T-XXX][Java] Wrapper MotorDeRutas | [02_Java.md](02_Java.md) | Sprint 2 | tipo:tarea, fase:construccion, tec:java, tec:cpp | 2, 4 | 4 | |
| 12 | [T-XXX][C++] Red vial desde OpenStreetMap | [03_Cpp.md](03_Cpp.md) | Sprint 2 | tipo:tarea, fase:construccion, tec:cpp | 4, 3 | 3 | |
| 13 | [HU-08][CU-06][WhatsApp/LLM] Leer los grupos autorizados | [04_WhatsApp_LLM.md](04_WhatsApp_LLM.md) | Sprint 2 | caso-uso:CU-06, tipo:historia-usuario, fase:construccion, tec:whatsapp-llm | 5 | 3 | |
| 14 | [T-XXX][Servidor] Dominio, Nginx y HTTPS | [06_Servidor.md](06_Servidor.md) | Sprint 2 | tipo:tarea, fase:construccion, tec:servidor | 6 | 1 | |
| 15 | [HU-01][CU-01][Java] Registrar reportes de eventos viales | [02_Java.md](02_Java.md) | Sprint 3 | caso-uso:CU-01, tipo:historia-usuario, fase:construccion, tec:java | 8, 9 | 5 | |
| 16 | [HU-01][CU-01][Flutter] Reportar un evento vial | [01_Flutter.md](01_Flutter.md) | Sprint 3 | caso-uso:CU-01, tipo:historia-usuario, fase:construccion, tec:flutter | 7, 10, 15 | 3 | |
| 17 | [HU-03][CU-02][Java] Consultar eventos vigentes y vencerlos | [02_Java.md](02_Java.md) | Sprint 3 | caso-uso:CU-02, tipo:historia-usuario, fase:construccion, tec:java | 15 | 4 | |
| 18 | [HU-03][CU-02][Flutter] Ver los eventos en el mapa | [01_Flutter.md](01_Flutter.md) | Sprint 3 | caso-uso:CU-02, tipo:historia-usuario, fase:construccion, tec:flutter | 7, 10, 9, 17 | 3 | |
| 19 | [HU-08][CU-06][Java] Recibir el análisis de WhatsApp | [02_Java.md](02_Java.md) | Sprint 3 | caso-uso:CU-06, tipo:historia-usuario, fase:construccion, tec:java | 3, 8 | 3 | |
| 20 | [HU-05][CU-03][C++] Calcular la ruta óptima con A* | [03_Cpp.md](03_Cpp.md) | Sprint 3 | caso-uso:CU-03, tipo:historia-usuario, fase:construccion, tec:cpp | 12 | 3 | |
| 21 | [HU-08][CU-06][WhatsApp/LLM] Analizar los mensajes por lotes y enviar el resultado | [04_WhatsApp_LLM.md](04_WhatsApp_LLM.md) | Sprint 3 | caso-uso:CU-06, tipo:historia-usuario, fase:construccion, tec:whatsapp-llm | 13, 19 | 5 | |
| 22 | [T-XXX][Servidor] Desplegar el backend con el núcleo C++ | [06_Servidor.md](06_Servidor.md) | Sprint 3 | tipo:tarea, fase:construccion, tec:servidor | 14, 8, 11, 12 | 2 | |
| 23 | [HU-02][CU-01][Flutter] Guardar y reintentar reportes sin conexión | [01_Flutter.md](01_Flutter.md) | Sprint 4 | caso-uso:CU-01, tipo:historia-usuario, fase:construccion, tec:flutter | 16 | 3 | |
| 24 | [HU-04][CU-02][Flutter] Ver eventos guardados sin conexión | [01_Flutter.md](01_Flutter.md) | Sprint 4 | caso-uso:CU-02, tipo:historia-usuario, fase:construccion, tec:flutter | 18 | 2 | |
| 25 | [HU-11][CU-02][Java] Votar eventos | [02_Java.md](02_Java.md) | Sprint 4 | caso-uso:CU-02, tipo:historia-usuario, fase:construccion, tec:java | 17 | 4 | |
| 26 | [HU-11][CU-02][Flutter] Lista de eventos y votos | [01_Flutter.md](01_Flutter.md) | Sprint 4 | caso-uso:CU-02, tipo:historia-usuario, fase:construccion, tec:flutter | 18, 25 | 2 | |
| 27 | [HU-05][CU-03][Java] Calcular la ruta | [02_Java.md](02_Java.md) | Sprint 4 | caso-uso:CU-03, tipo:historia-usuario, fase:construccion, tec:java | 11, 20 | 3 | |
| 28 | [HU-05][CU-03][C++] Tráfico de los eventos en los tramos | [03_Cpp.md](03_Cpp.md) | Sprint 4 | caso-uso:CU-03, tipo:historia-usuario, fase:construccion, tec:cpp | 20 | 4 | |
| 29 | [T-XXX][Java] Mantener al día el tráfico del motor | [02_Java.md](02_Java.md) | Sprint 4 | tipo:tarea, fase:construccion, tec:java | 11, 25, 28 | 2 | |
| 30 | [T-XXX][Servidor] Desplegar el servicio de WhatsApp/LLM | [06_Servidor.md](06_Servidor.md) | Sprint 4 | tipo:tarea, fase:construccion, tec:servidor | 22, 21 | 1 | |
| 31 | [HU-07][CU-05][Java] Consultar surtidores cercanos | [02_Java.md](02_Java.md) | Sprint 5 | caso-uso:CU-05, tipo:historia-usuario, fase:construccion, tec:java | 19 | 3 | |
| 32 | [HU-05][CU-03][Flutter] Cómo llegar y navegación activa | [01_Flutter.md](01_Flutter.md) | Sprint 5 | caso-uso:CU-03, tipo:historia-usuario, fase:construccion, tec:flutter | 7, 27, 31 | 3 | |
| 33 | [HU-07][CU-05][Flutter] Consultar surtidores cercanos | [01_Flutter.md](01_Flutter.md) | Sprint 5 | caso-uso:CU-05, tipo:historia-usuario, fase:construccion, tec:flutter | 7, 31 | 2 | |
| 34 | [HU-12][CU-05][Java] Registrar confirmaciones de recarga | [02_Java.md](02_Java.md) | Sprint 5 | caso-uso:CU-05, tipo:historia-usuario, fase:construccion, tec:java | 31 | 3 | |
| 35 | [HU-13][CU-04][C++] Detectar congestión con la telemetría | [03_Cpp.md](03_Cpp.md) | Sprint 5 | caso-uso:CU-04, tipo:historia-usuario, fase:construccion, tec:cpp | 28 | 4 | |
| 36 | [HU-13][CU-04][Java] Recibir telemetría y registrar congestión | [02_Java.md](02_Java.md) | Sprint 5 | caso-uso:CU-04, tipo:historia-usuario, fase:construccion, tec:java | 29, 35 | 3 | |
| 37 | [HU-13][CU-04][Flutter] Enviar telemetría con la app abierta | [01_Flutter.md](01_Flutter.md) | Sprint 5 | caso-uso:CU-04, tipo:historia-usuario, fase:construccion, tec:flutter | 7, 36 | 2 | |
| 38 | [HU-06][CU-04][C++] Revisar la ruta activa | [03_Cpp.md](03_Cpp.md) | Sprint 5 | caso-uso:CU-04, tipo:historia-usuario, fase:construccion, tec:cpp | 28 | 3 | |
| 39 | [T-XXX][WhatsApp/LLM] Prueba con mensajes reales | [04_WhatsApp_LLM.md](04_WhatsApp_LLM.md) | Sprint 5 | tipo:prueba, fase:construccion, tec:whatsapp-llm | 21, 30, 31, 33 | 1 | |
| 40 | [T-XXX][Simulador] Estructura y parámetros del simulador | [05_Simulador.md](05_Simulador.md) | Sprint 5 | tipo:tarea, fase:construccion, tec:simulador | — | 1 | |
| 41 | [T-XXX][Servidor] Respaldos de la base de datos y registros | [06_Servidor.md](06_Servidor.md) | Sprint 5 | tipo:tarea, fase:construccion, tec:servidor | 22 | 1 | |
| 42 | [HU-12][CU-05][Flutter] Preguntar por la recarga | [01_Flutter.md](01_Flutter.md) | Sprint 6 | caso-uso:CU-05, tipo:historia-usuario, fase:construccion, tec:flutter | 33, 34 | 3 | |
| 43 | [T-XXX][Flutter] Política de privacidad | [01_Flutter.md](01_Flutter.md) | Sprint 6 | tipo:tarea, fase:construccion, tec:flutter | 21, 37 | 1 | |
| 44 | [HU-06][CU-04][Java] Revisar la ruta activa | [02_Java.md](02_Java.md) | Sprint 6 | caso-uso:CU-04, tipo:historia-usuario, fase:construccion, tec:java | 27, 38 | 4 | |
| 45 | [HU-06][CU-04][Flutter] Aviso de ruta alternativa | [01_Flutter.md](01_Flutter.md) | Sprint 6 | caso-uso:CU-04, tipo:historia-usuario, fase:construccion, tec:flutter | 32, 44 | 2 | |
| 46 | [HU-09][CU-07][Java] Soporte para el simulador | [02_Java.md](02_Java.md) | Sprint 6 | caso-uso:CU-07, tipo:historia-usuario, fase:construccion, tec:java | 36 | 3 | |
| 47 | [T-XXX][Java] Limpieza de datos vencidos | [02_Java.md](02_Java.md) | Sprint 6 | tipo:tarea, fase:construccion, tec:java | 34 | 1 | |
| 48 | [T-XXX][Servidor] Ambiente de pruebas para el simulador | [06_Servidor.md](06_Servidor.md) | Sprint 6 | tipo:tarea, fase:construccion, tec:servidor | 22 | 1 | |
| 49 | [HU-09][CU-07][Simulador] Usuarios fantasma que envían eventos y telemetría | [05_Simulador.md](05_Simulador.md) | Sprint 6 | caso-uso:CU-07, tipo:historia-usuario, fase:construccion, tec:simulador | 40, 46, 48 | 3 | |
| 50 | [T-XXX][Java] Pruebas de punta a punta | [02_Java.md](02_Java.md) | Sprint 7 | tipo:prueba, fase:construccion, tec:java | Todos los de Java | 2 | |
| 51 | [T-XXX][Simulador] Pruebas de carga y reporte | [05_Simulador.md](05_Simulador.md) | Sprint 7 | tipo:prueba, fase:construccion, tec:simulador | 49 | 2 | |
| 52 | [T-XXX][C++] Rendimiento con la red real de Tarija | [03_Cpp.md](03_Cpp.md) | Sprint 7 | tipo:prueba, fase:construccion, tec:cpp | 38, 51 | 1 | |
| 53 | [T-XXX][Servidor] Despliegue final y revisión de seguridad | [06_Servidor.md](06_Servidor.md) | Sprint 7 | tipo:tarea, fase:construccion, tec:servidor | Todos los de sprints anteriores | 1 | |
