# Contexto del Proyecto — ChuroViaje

Este documento resume la metodología, la estructura, los identificadores y el flujo de trabajo de ChuroViaje. Lo detallado está en `06_proyect/CONTRIBUTING.md`, en la arquitectura (`03_des/02_Arquitectura`) y en el backlog (`06_proyect/Backlog_Construccion`). Última revisión: 24/11/2026.

## 1. Metodología

- El proyecto sigue **RUP** (Inicio, Elaboración, Construcción y Transición). La Elaboración cerró con la arquitectura comprobada.
- La **Construcción** (02/10/2026 – 30/11/2026) es ágil: primero se hicieron el documento de arquitectura y el backlog, y después sprints de una semana, cada uno con su *Milestone* en GitHub.
- Calendario de la Construcción: Sprint 1 (15/10 – 21/10), Sprint 2 (22/10 – 28/10), Sprint 3 (29/10 – 04/11), Sprint 4 (05/11 – 11/11), Sprint 5 (12/11 – 18/11), Sprint 5.5 (19/11 – 21/11), Sprint 6 (19/11 – 25/11), Sprint 6.5 (24/11 – 25/11) y Sprint 7 (26/11 – 30/11). Los sprints 5.5 y 6.5 son de un solo issue, con correcciones que salieron al probar la app.
- Desde el Sprint 3, por recomendación del docente, cada sprint se centra en funciones completas de punta a punta, en lugar de avanzar cada capa por separado.

## 2. Sistema de carpetas

La documentación y el código se ordenan por disciplina de RUP, no por el sprint en que se hicieron. Cada carpeta tiene su `README.md`:

- **01\_bus/**: modelado del negocio (justificación, oportunidad de negocio y perfiles de los interesados).
- **02\_req/**: requisitos. Documento de Visión, especificación de casos de uso (CU-01 a CU-09) e historias de usuario (HU-01 a HU-14), en Word (`editablesWORD/`) y PDF (`pdf/`), y la matriz de requisitos (`pdf/matriz_de_requisitos_churoviaje.md`).
- **03\_des/**: diseño. Diagramas de casos de uso (`DiagCU/`), diagramas de actividad (`01_Diagramas_de_comportamiento/`), la arquitectura dividida por tema (`02_Arquitectura/`: MVC, modelo de dominio, reglas por caso de uso, contratos, base de datos, seguridad y decisiones y parámetros) y el diseño de pantallas (`DisenoUI/`).
- **04\_imp/**: implementación. App Flutter (`app_flutter`), backend Java (`backend_java`), núcleo C++ (`motor_cpp`), servicio de WhatsApp/LLM (`servicio_whatsapp`), simulador (`simulador`) y datos compartidos (`datos`).
- **05\_pro/**: prototipos, investigaciones y pruebas de apoyo (por ejemplo, la decisión de conexión a WhatsApp, el cálculo de velocidad y la prueba de la API de ANH).
- **06\_proyect/**: gestión del proyecto. `TRAZABILIDAD.md`, `CONTRIBUTING.md`, los backlogs, la matriz de sprints con la *Definition of Done* y la guía del servidor (`servidor/`).

## 3. Arquitectura en pocas palabras

- **App Flutter** con Provider y el mapa de Mapbox (D-08): vista y controladores de pantalla. No aplica reglas de negocio; el backend las vuelve a revisar todas.
- **Backend Java** con Spring Boot: única puerta de entrada. Aplica las reglas en el modelo de dominio y accede a PostgreSQL con PostGIS por JDBC, con sentencias preparadas y migraciones de Flyway.
- **Núcleo C++**: calcula rutas con A* sobre la red de calles de Tarija (extracto de OpenStreetMap) y detecta congestión con la telemetría. Habla con el backend por pipe, un JSON por línea, a través de un wrapper en Java.
- **Servicio de WhatsApp/LLM** en Node.js: lee los grupos con Baileys y analiza los mensajes por lotes cada 5 minutos con Gemini. La API pública de ANH es una fuente complementaria de combustible, que consulta el backend (D-28).
- **Simulador** en Python: usuarios fantasma para las pruebas de carga, siempre marcados como simulados.
- **Servidor**: la instancia `churoviaje-server` de Oracle Cloud, con el dominio `churoviaje.orelqu.com`. Los secretos se quedan en el servidor.

## 4. Identificadores y trazabilidad

Un identificador nunca se reutiliza ni cambia de número. La cadena de trazabilidad es:
**Requisito (RF-XX / RNF-XX) → Caso de Uso (CU-XX) → Historia de Usuario (HU-XX) → Tarea (T-XXX) → Caso de Prueba (CP-XX)**.

- **RF-XX / RNF-XX**: requisito funcional o no funcional de la matriz de requisitos (antes se usaba RQ-XX).
- **CU-XX**: caso de uso (CU-01 a CU-09).
- **HU-XX**: historia de usuario (HU-01 a HU-14).
- **T-XXX**: tarea con número propio, con tres dígitos (por ejemplo T-124). Los issues técnicos que no son de una HU llevan su T-XXX en el título.
- **CP-XX**: caso de prueba. Va en el issue con su evidencia esperada, y su prueba automatizada en el `test/` o `tests/` de cada parte.
- **RN-XX.Y, D-XX y P-XX**: reglas de negocio, decisiones y parámetros de la arquitectura. Los criterios de aceptación citan de dónde salen.
- **Matriz de trazabilidad**: `06_proyect/TRAZABILIDAD.md`, una sola tabla con las columnas RF / RNF | CU | HU | Reglas | Issue | Rama | Casos de prueba | Estado. Se actualiza en el mismo PR de cada issue.

## 5. Ramas

- **`main`**: siempre lista para demos. Nadie hace *push* directo.
- **`develop`**: el trabajo terminado e integrado de cada sprint. Nadie hace *push* directo.
- **`feature/HUxx-CUxx-nombre-corto`**: una rama por issue de historia de usuario (por ejemplo `feature/HU05-CU03-flutter-como-llegar`).
- **`feature/Txxx-nombre-corto`**: una rama por tarea que no es de una HU (por ejemplo `feature/T124-correcciones-interfaz`).
- Siempre se crean desde `develop` y vuelven con un *Pull Request*. `feature/` va en minúscula, y los nombres van sin tildes, sin ñ y sin espacios.

## 6. Issues, tablero y commits

- **Tablero Kanban:** `Backlog → Por hacer → En progreso → En revisión (PR) → Hecho`. Cada tarjeta es un issue.
- **Títulos:** `[HU-XX][CU-XX][Tecnología] Título` para las historias y `[T-XXX] Título` para las tareas. El cuerpo trae el caso de uso, la historia o la descripción, los criterios de aceptación, las tareas, los casos de prueba con su evidencia esperada y el nombre de la rama. Cada issue tiene su *Milestone* (el sprint).
- **Labels:** `caso-uso:CU-XX`, `tipo:historia-usuario`, `tipo:tarea`, `tipo:bug`, `tipo:prueba`, `estado:bloqueado`, `fase:elaboracion` / `fase:construccion` y la tecnología (`tec:flutter`, `tec:java`, `tec:cpp`…).
- **Commits:** *Conventional Commits* en español (`feat(app): …`, `fix(backend): …`, `docs: …`), con `Refs #N` para vincular el issue y `Closes #N` solo en el commit o PR que lo cierra.
- **Pull Requests:** cada PR referencia su issue y cumple la *Definition of Done* (`06_proyect/Backlog_Construccion/09_Matriz_sprints_DoD.md`): criterios de aceptación marcados, cada CP con su evidencia en el PR (salida de la CI para C++ y Java, video o captura para Flutter, salida del comando para el servidor) y la CI en verde. Marcelo revisa y mergea los PR.

## 7. Equipo

| Integrante | GitHub | Área |
| --- | --- | --- |
| Marcelo Quiroga (líder) | MarcelQuiroF | Núcleo C++ y seguridad del servidor |
| Jorge Cabrera | jorgecabreraz | Backend Java |
| Franz Caceres | CarlosCaceresAuca | WhatsApp/LLM; ayuda con los modos sin conexión de Flutter |
| Alex Quispe | NuzEstrada | App Flutter |
| Antonio Cuellar | antoniocuellarca19-byte | Simulador; apoya a Jorge en Java |
