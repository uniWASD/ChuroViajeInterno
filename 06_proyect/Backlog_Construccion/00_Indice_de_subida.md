# Índice de subida del backlog

[Índice del backlog](README.md)

Este archivo dice en qué orden se crean los 59 issues del backlog en GitHub y con qué Milestone y etiquetas. El texto de cada issue sigue en su archivo por tecnología; aquí solo está el orden, para que cada issue se cree después de los issues de los que depende y se pueda enlazar con su número.

Los 53 primeros se crearon el 13/10/2026 con [subir_issues.py](subir_issues.py), que les asignó T-087 a T-110 y CP-20 a CP-154. Las filas 54 y 55 salieron después, al dividir #50 y #70 según la [estimación](07_Estimacion.md), y se crean con [sincronizar_issues.py](sincronizar_issues.py). Por eso dos filas anteriores, la 12 y la 45, dependen de ellas. Las filas 56 a 59 salieron el 23/10/2026, al incorporar la API de ANH como fuente complementaria (T-116), y también se crean con sincronizar_issues.py; sus tareas son T-117 a T-119 y sus casos de prueba, CP-156 a CP-165. La fila 50 recibió además el CP-166.

## Pasos para subir

El script [subir_issues.py](subir_issues.py) hace los pasos 2 a 5 con GitHub CLI (`gh`); las instrucciones están al principio del script. A mano, los pasos son:

1. Revisar en [TRAZABILIDAD.md](../TRAZABILIDAD.md) y en GitHub cuál es la última tarea (T) y el último caso de prueba (CP) usados.
2. Crear las etiquetas `tec:flutter`, `tec:java`, `tec:cpp`, `tec:whatsapp-llm`, `tec:simulador` y `tec:servidor`, si no existen.
3. Crear los 7 Milestones de la tabla de abajo, con su fecha de cierre.
4. Crear los issues en el orden de la tabla. Al crear cada uno:
   - reemplazar `T-XXX` por la siguiente tarea libre y `TXX` de la rama por el mismo número (T-087 → `feature/T87-...`);
   - reemplazar cada `CP-XX` por el siguiente caso de prueba libre;
   - agregar el número de GitHub (`#N`) junto a cada issue de la línea «Depende de», que ya existe porque se creó antes;
   - asignar el Milestone y las etiquetas.
5. Escribir en los archivos por tecnología los números asignados (T, CP y `#N`) y anotarlos en la columna «Número en GitHub» de esta tabla.

## Milestones

| Milestone | Fechas | Fecha de cierre | Issues |
| --- | --- | --- | --- |
| Sprint 1 | 15/10 – 21/10 | 2026-10-21 | 6 |
| Sprint 2 | 22/10 – 28/10 | 2026-10-28 | 10 |
| Sprint 3 | 29/10 – 04/11 | 2026-11-04 | 10 |
| Sprint 4 | 05/11 – 11/11 | 2026-11-11 | 9 |
| Sprint 5 | 12/11 – 18/11 | 2026-11-18 | 11 |
| Sprint 6 | 19/11 – 25/11 | 2026-11-25 | 9 |
| Sprint 7 | 26/11 – 30/11 | 2026-11-30 | 4 |

## Orden de creación

La columna «Depende de» usa el número de orden de esta tabla.

| Orden | Issue | Archivo | Milestone | Etiquetas | Depende de | CP | Número en GitHub |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | [T-087][Flutter] Estructura base de la app | [01_Flutter.md](01_Flutter.md) | Sprint 1 | tipo:tarea, fase:construccion, tec:flutter | — | 1 | #39 |
| 2 | [T-088][Java] Estructura base del backend | [02_Java.md](02_Java.md) | Sprint 1 | tipo:tarea, fase:construccion, tec:java | — | 1 | #40 |
| 3 | [T-089][Java] Base de datos, catálogos y zona de cobertura | [02_Java.md](02_Java.md) | Sprint 1 | tipo:tarea, fase:construccion, tec:java | 2 | 2 | #41 |
| 4 | [T-090][C++] Estructura del núcleo y protocolo por pipe | [03_Cpp.md](03_Cpp.md) | Sprint 1 | tipo:tarea, fase:construccion, tec:cpp | — | 2 | #42 |
| 5 | [T-091][WhatsApp/LLM] Estructura del servicio | [04_WhatsApp_LLM.md](04_WhatsApp_LLM.md) | Sprint 1 | tipo:tarea, fase:construccion, tec:whatsapp-llm | — | 1 | #43 |
| 6 | [T-092][Servidor] Preparar la instancia de Oracle Cloud | [06_Servidor.md](06_Servidor.md) | Sprint 1 | tipo:tarea, fase:construccion, tec:servidor | — | 2 | #44 |
| 7 | [T-093][Flutter] Mapa base con Mapbox y ubicación | [01_Flutter.md](01_Flutter.md) | Sprint 2 | tipo:tarea, fase:construccion, tec:flutter | 1 | 2 | #45 |
| 8 | [HU-10][CU-08][Java] Iniciar sesión con Google y emitir el JWT | [02_Java.md](02_Java.md) | Sprint 2 | caso-uso:CU-08, tipo:historia-usuario, fase:construccion, tec:java | 3 | 5 | #46 |
| 9 | [T-094][Java] Catálogos para la app | [02_Java.md](02_Java.md) | Sprint 2 | tipo:tarea, fase:construccion, tec:java | 3, 8 | 1 | #47 |
| 10 | [HU-10][CU-08][Flutter] Iniciar sesión con Google | [01_Flutter.md](01_Flutter.md) | Sprint 2 | caso-uso:CU-08, tipo:historia-usuario, fase:construccion, tec:flutter | 1, 8, 9 | 4 | #48 |
| 11 | [T-095][Java] Wrapper MotorDeRutas | [02_Java.md](02_Java.md) | Sprint 2 | tipo:tarea, fase:construccion, tec:java, tec:cpp | 2, 4 | 4 | #49 |
| 12 | [T-096][C++] Cargar la red vial en el núcleo | [03_Cpp.md](03_Cpp.md) | Sprint 2 | tipo:tarea, fase:construccion, tec:cpp | 4, 54 | 1 | #50 |
| 13 | [HU-08][CU-06][WhatsApp/LLM] Leer los grupos autorizados | [04_WhatsApp_LLM.md](04_WhatsApp_LLM.md) | Sprint 2 | caso-uso:CU-06, tipo:historia-usuario, fase:construccion, tec:whatsapp-llm | 5 | 3 | #51 |
| 14 | [T-097][Servidor] Dominio, Nginx y HTTPS | [06_Servidor.md](06_Servidor.md) | Sprint 2 | tipo:tarea, fase:construccion, tec:servidor | 6 | 1 | #52 |
| 15 | [HU-01][CU-01][Java] Registrar reportes de eventos viales | [02_Java.md](02_Java.md) | Sprint 3 | caso-uso:CU-01, tipo:historia-usuario, fase:construccion, tec:java | 8, 9 | 5 | #53 |
| 16 | [HU-01][CU-01][Flutter] Reportar un evento vial | [01_Flutter.md](01_Flutter.md) | Sprint 3 | caso-uso:CU-01, tipo:historia-usuario, fase:construccion, tec:flutter | 7, 10, 15 | 4 | #54 |
| 17 | [HU-03][CU-02][Java] Consultar eventos vigentes y vencerlos | [02_Java.md](02_Java.md) | Sprint 3 | caso-uso:CU-02, tipo:historia-usuario, fase:construccion, tec:java | 15 | 4 | #55 |
| 18 | [HU-03][CU-02][Flutter] Ver los eventos en el mapa | [01_Flutter.md](01_Flutter.md) | Sprint 3 | caso-uso:CU-02, tipo:historia-usuario, fase:construccion, tec:flutter | 7, 10, 9, 17 | 3 | #56 |
| 19 | [HU-08][CU-06][Java] Recibir el análisis de WhatsApp | [02_Java.md](02_Java.md) | Sprint 3 | caso-uso:CU-06, tipo:historia-usuario, fase:construccion, tec:java | 3, 8 | 3 | #57 |
| 20 | [HU-05][CU-03][C++] Calcular la ruta óptima con A* | [03_Cpp.md](03_Cpp.md) | Sprint 3 | caso-uso:CU-03, tipo:historia-usuario, fase:construccion, tec:cpp | 12 | 3 | #58 |
| 21 | [HU-08][CU-06][WhatsApp/LLM] Analizar los mensajes por lotes y enviar el resultado | [04_WhatsApp_LLM.md](04_WhatsApp_LLM.md) | Sprint 3 | caso-uso:CU-06, tipo:historia-usuario, fase:construccion, tec:whatsapp-llm | 13, 19 | 5 | #59 |
| 22 | [T-098][Servidor] Desplegar el backend con el núcleo C++ | [06_Servidor.md](06_Servidor.md) | Sprint 3 | tipo:tarea, fase:construccion, tec:servidor | 14, 8, 11, 12, 54 | 2 | #60 |
| 23 | [HU-02][CU-01][Flutter] Guardar y reintentar reportes sin conexión | [01_Flutter.md](01_Flutter.md) | Sprint 4 | caso-uso:CU-01, tipo:historia-usuario, fase:construccion, tec:flutter | 16 | 3 | #61 |
| 24 | [HU-04][CU-02][Flutter] Ver eventos guardados sin conexión | [01_Flutter.md](01_Flutter.md) | Sprint 4 | caso-uso:CU-02, tipo:historia-usuario, fase:construccion, tec:flutter | 18 | 2 | #62 |
| 25 | [HU-11][CU-02][Java] Votar eventos | [02_Java.md](02_Java.md) | Sprint 4 | caso-uso:CU-02, tipo:historia-usuario, fase:construccion, tec:java | 17 | 4 | #63 |
| 26 | [HU-11][CU-02][Flutter] Lista de eventos y votos | [01_Flutter.md](01_Flutter.md) | Sprint 4 | caso-uso:CU-02, tipo:historia-usuario, fase:construccion, tec:flutter | 18, 25 | 2 | #64 |
| 27 | [HU-05][CU-03][Java] Calcular la ruta | [02_Java.md](02_Java.md) | Sprint 4 | caso-uso:CU-03, tipo:historia-usuario, fase:construccion, tec:java | 11, 20 | 3 | #65 |
| 28 | [HU-05][CU-03][C++] Tráfico de los eventos en los tramos | [03_Cpp.md](03_Cpp.md) | Sprint 4 | caso-uso:CU-03, tipo:historia-usuario, fase:construccion, tec:cpp | 20 | 4 | #66 |
| 29 | [T-099][Java] Mantener al día el tráfico del motor | [02_Java.md](02_Java.md) | Sprint 4 | tipo:tarea, fase:construccion, tec:java | 11, 25, 28 | 2 | #67 |
| 30 | [T-100][Servidor] Desplegar el servicio de WhatsApp/LLM | [06_Servidor.md](06_Servidor.md) | Sprint 4 | tipo:tarea, fase:construccion, tec:servidor | 22, 21 | 1 | #68 |
| 31 | [HU-07][CU-05][Java] Consultar surtidores cercanos | [02_Java.md](02_Java.md) | Sprint 5 | caso-uso:CU-05, tipo:historia-usuario, fase:construccion, tec:java | 19 | 3 | #69 |
| 32 | [HU-05][CU-03][Flutter] Cómo llegar | [01_Flutter.md](01_Flutter.md) | Sprint 5 | caso-uso:CU-03, tipo:historia-usuario, fase:construccion, tec:flutter | 7, 27, 31 | 2 | #70 |
| 33 | [HU-07][CU-05][Flutter] Consultar surtidores cercanos | [01_Flutter.md](01_Flutter.md) | Sprint 5 | caso-uso:CU-05, tipo:historia-usuario, fase:construccion, tec:flutter | 7, 31 | 3 | #71 |
| 34 | [HU-12][CU-05][Java] Registrar confirmaciones de recarga | [02_Java.md](02_Java.md) | Sprint 5 | caso-uso:CU-05, tipo:historia-usuario, fase:construccion, tec:java | 31 | 3 | #72 |
| 35 | [HU-13][CU-04][C++] Detectar congestión con la telemetría | [03_Cpp.md](03_Cpp.md) | Sprint 5 | caso-uso:CU-04, tipo:historia-usuario, fase:construccion, tec:cpp | 28 | 5 | #73 |
| 36 | [HU-13][CU-04][Java] Recibir telemetría y registrar congestión | [02_Java.md](02_Java.md) | Sprint 5 | caso-uso:CU-04, tipo:historia-usuario, fase:construccion, tec:java | 29, 35 | 3 | #74 |
| 37 | [HU-13][CU-04][Flutter] Enviar telemetría con la app abierta | [01_Flutter.md](01_Flutter.md) | Sprint 5 | caso-uso:CU-04, tipo:historia-usuario, fase:construccion, tec:flutter | 7, 36 | 2 | #75 |
| 38 | [HU-06][CU-04][C++] Revisar la ruta activa | [03_Cpp.md](03_Cpp.md) | Sprint 5 | caso-uso:CU-04, tipo:historia-usuario, fase:construccion, tec:cpp | 28 | 3 | #76 |
| 39 | [T-101][WhatsApp/LLM] Prueba con mensajes reales | [04_WhatsApp_LLM.md](04_WhatsApp_LLM.md) | Sprint 5 | tipo:prueba, fase:construccion, tec:whatsapp-llm | 21, 30, 31, 33 | 1 | #77 |
| 40 | [T-102][Simulador] Estructura y parámetros del simulador | [05_Simulador.md](05_Simulador.md) | Sprint 5 | tipo:tarea, fase:construccion, tec:simulador | — | 1 | #78 |
| 41 | [T-103][Servidor] Respaldos de la base de datos y registros | [06_Servidor.md](06_Servidor.md) | Sprint 6 | tipo:tarea, fase:construccion, tec:servidor | 22 | 1 | #79 |
| 42 | [HU-12][CU-05][Flutter] Preguntar por la recarga | [01_Flutter.md](01_Flutter.md) | Sprint 6 | caso-uso:CU-05, tipo:historia-usuario, fase:construccion, tec:flutter | 33, 34 | 3 | #80 |
| 43 | [T-104][Flutter] Política de privacidad | [01_Flutter.md](01_Flutter.md) | Sprint 6 | tipo:tarea, fase:construccion, tec:flutter | 14, 21, 37 | 1 | #81 |
| 44 | [HU-06][CU-04][Java] Revisar la ruta activa | [02_Java.md](02_Java.md) | Sprint 6 | caso-uso:CU-04, tipo:historia-usuario, fase:construccion, tec:java | 27, 38 | 4 | #82 |
| 45 | [HU-06][CU-04][Flutter] Aviso de ruta alternativa | [01_Flutter.md](01_Flutter.md) | Sprint 6 | caso-uso:CU-04, tipo:historia-usuario, fase:construccion, tec:flutter | 55, 44 | 3 | #83 |
| 46 | [HU-09][CU-07][Java] Soporte para el simulador | [02_Java.md](02_Java.md) | Sprint 6 | caso-uso:CU-07, tipo:historia-usuario, fase:construccion, tec:java | 36 | 3 | #84 |
| 47 | [T-105][Java] Limpieza de datos vencidos | [02_Java.md](02_Java.md) | Sprint 6 | tipo:tarea, fase:construccion, tec:java | 34 | 1 | #85 |
| 48 | [T-106][Servidor] Ambiente de pruebas para el simulador | [06_Servidor.md](06_Servidor.md) | Sprint 6 | tipo:tarea, fase:construccion, tec:servidor | 22 | 1 | #86 |
| 49 | [HU-09][CU-07][Simulador] Usuarios fantasma que envían eventos y telemetría | [05_Simulador.md](05_Simulador.md) | Sprint 6 | caso-uso:CU-07, tipo:historia-usuario, fase:construccion, tec:simulador | 40, 46, 48 | 3 | #87 |
| 50 | [T-107][Java] Pruebas de punta a punta | [02_Java.md](02_Java.md) | Sprint 7 | tipo:prueba, fase:construccion, tec:java | Todos los de Java | 3 | #88 |
| 51 | [T-108][Simulador] Pruebas de carga y reporte | [05_Simulador.md](05_Simulador.md) | Sprint 7 | tipo:prueba, fase:construccion, tec:simulador | 49 | 2 | #89 |
| 52 | [T-109][C++] Rendimiento con la red real de Tarija | [03_Cpp.md](03_Cpp.md) | Sprint 7 | tipo:prueba, fase:construccion, tec:cpp | 38, 51 | 1 | #90 |
| 53 | [T-110][Servidor] Despliegue final y revisión de seguridad | [06_Servidor.md](06_Servidor.md) | Sprint 7 | tipo:tarea, fase:construccion, tec:servidor | Todos los de sprints anteriores | 1 | #91 |
| 54 | [T-111][C++] Preparar la red vial de Tarija desde OpenStreetMap | [03_Cpp.md](03_Cpp.md) | Sprint 2 | tipo:tarea, fase:construccion, tec:cpp | 3 | 2 | #93 |
| 55 | [HU-05][CU-03][Flutter] Navegación activa | [01_Flutter.md](01_Flutter.md) | Sprint 5 | caso-uso:CU-03, tipo:historia-usuario, fase:construccion, tec:flutter | 32 | 2 | #94 |
| 56 | [T-119][Servidor] Comprobar la salida del servidor hacia la API de ANH | [06_Servidor.md](06_Servidor.md) | Sprint 2 | tipo:tarea, fase:construccion, tec:servidor | 6 | 1 |  |
| 57 | [T-117][Java] Medir la API de ANH y fijar el ciclo de consulta y la vigencia | [02_Java.md](02_Java.md) | Sprint 3 | tipo:prueba, fase:construccion, tec:java | 56 | 2 |  |
| 58 | [T-118][Java] Vincular las estaciones de ANH con los surtidores del catálogo | [02_Java.md](02_Java.md) | Sprint 3 | tipo:tarea, fase:construccion, tec:java | 3, 19 | 2 |  |
| 59 | [HU-14][CU-09][Java] Consultar el estado de combustible desde ANH | [02_Java.md](02_Java.md) | Sprint 4 | caso-uso:CU-09, tipo:historia-usuario, fase:construccion, tec:java | 19, 57, 58 | 5 |  |
