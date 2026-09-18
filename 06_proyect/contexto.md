# Contexto del Proyecto — ChuroViaje

Este documento sirve como base de conocimiento sobre la metodología, estructura, identificadores y flujo de trabajo del proyecto ChuroViaje.

## 1. Metodología

- El proyecto se desarrolla bajo la **Metodología RUP** (Rational Unified Process) abarcando sus fases generales: Inicio, Elaboración, Construcción y Transición.
- Dentro de la fase de Construcción, se implementa una **metodología ágil (Scrum)**.
- El trabajo se organiza en iteraciones o sprints, los cuales están representados mediante *Milestones* en GitHub.

## 2. Sistema de Carpetas

La documentación y el código se organizan según su naturaleza (disciplinas RUP), no por el sprint en el que fueron creados. Cada carpeta cuenta con su propio `README.md` explicativo:

- **01\_bus/**: Modelado del negocio (Justificación, oportunidad de negocio, perfiles de stakeholders).
- **02\_req/**: Requisitos (Documento de Visión, Especificaciones de Casos de Uso del CU-01 al CU-08).
- **03\_des/**: Diseño (Arquitectura cliente-servidor, diagramas de comportamiento UML, modelos de BD).
- **04\_imp/**: Implementación (Código fuente de producción en Flutter, Java y C++).
- **05\_pro/**: Pruebas (Casos de prueba CP-XX, simuladores de tráfico, pruebas de algoritmos de ruteo y telemetría).
- **06\_proyect/**: Gestión de proyecto (incluye el archivo `TRAZABILIDAD.md` y configuraciones de entorno).

## 3. Identificadores y Trazabilidad

Existe una regla estricta de trazabilidad en la que un identificador nunca se reutiliza ni cambia de número. La cadena de trazabilidad es:
**Documento de Visión (RQ-XX) → Caso de Uso (CU-XX) → Historia de Usuario (HU-XX) → Tarea (T-XX) → Caso de Prueba (CP-XX)**.

- **RQ-XX**: Requisito de Visión.
- **CU-XX**: Caso de uso (Actualmente definidos del CU-01 al CU-08).
- **HU-XX**: Historia de usuario (Issue principal en GitHub).
- **T-XX**: Tarea de desarrollo (Checklist dentro del Issue).
- **CP-XX**: Caso de prueba (Archivos ubicados en `05_pro/` o `tests/`).
- **Matriz de Trazabilidad**: Se mantiene en el archivo `06_proyect/TRAZABILIDAD.md` y debe actualizarse cada vez que se cierra una historia de usuario.

## 4. Estrategia de Ramas (Branches)

- **`main`**: Siempre lista para demos. Nadie hace *push* directo aquí.
- **`develop`**: Trabajo terminado e integrado de cada sprint. Nadie hace *push* directo aquí.
- **`feature/HUxx-CUxx-nombre-corto`**: Una rama por historia de usuario. Siempre se crea partiendo desde `develop` y debe regresar a esta mediante un *Pull Request* (PR). No usar tildes ni espacios (Ej. `feature/HU12-CU04-recalculo-congestion`).

## 5. Flujo de Trabajo en GitHub (Issues, Tablero y Commits)

- **Tablero Kanban:** Se maneja con las columnas: `Backlog → Por hacer → En progreso → En revisión (PR) → Hecho`. Cada tarjeta debe corresponder obligatoriamente a un Issue.
- **Creación de Issues:** Deben tener un título estructurado como `[HU-XX][CU-XX] Título descriptivo` y una plantilla en el cuerpo que defina el Caso de Uso, la Historia, Criterios de Aceptación y las Tareas (T-XX). Todos deben tener asignado un *Milestone*.
- **Labels:** Los issues se categorizan usando etiquetas como `caso-uso:CU-XX`, `tipo:historia-usuario`, `tipo:tarea`, `tipo:bug`, `tipo:prueba`, `estado:bloqueado`, y fases de RUP (`fase:elaboracion` / `fase:construccion`).
- **Mensajes de Commit:** Se usa el formato *Conventional Commits* incluyendo los identificadores y la referencia al Issue. Ejemplo: `feat(ruteo): recalcular ruta al detectar congestión (HU-12, CU-04) Refs #48`.
- **Conexión con Issues:** En el cuerpo del commit se debe usar `Refs #ID` para vincularlo sin cerrar el Issue, y usar `Closes #ID` o `Fixes #ID` únicamente en el commit/PR final para cerrarlo automáticamente al hacer el merge.
- **Pull Requests (PR):** Todo PR debe referenciar su Issue, cumplir los criterios de aceptación y ser revisado y aprobado por Marcelo (o el encargado designado) antes de mergearse a `develop`.
