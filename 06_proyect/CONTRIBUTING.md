# Guía de trabajo en equipo — ChuroViaje

Este documento explica el repositorio: metodología, estructura de carpetas, identificadores, ramas, commits, Issues y tablero.

---

## 1. Metodología

- El proyecto sigue **RUP** (Racional Unified Process) en sus fases generales (Inicio, Elaboración, Construcción, Transición).
- Dentro de la fase de Construcción, trabajamos con una metodología ágil (Scrum): el trabajo se organiza en sprints, cada uno con su propio Milestone en GitHub.

## 2. Estructura de carpetas del repositorio

```
01_bus/       Modelado del negocio
02_req/       Requisitos (Documento de Visión, Casos de Uso)
03_des/       Diseño
04_imp/       Implementación
05_pro/       Pruebas
06_proyect/   Gestión de proyecto (incluye TRAZABILIDAD.md)
```

Cada quien coloca sus artefactos en la carpeta que corresponda a su naturaleza, no a en qué sprint se generaron.

## 3. Identificadores: la regla más importante

Todo en el proyecto se puede rastrear con esta cadena:

**Documento de Visión (RQ-XX) → Caso de Uso (CU-XX) → Historia de Usuario (HU-XX) → Tarea (T-XX) → Caso de Prueba (CP-XX)**

| Artefacto | Prefijo | Ejemplo | Dónde vive |
|---|---|---|---|
| Requisito de Visión | RQ-XX | RQ-05 | Documento de Visión |
| Caso de uso | CU-XX | CU-04 | Documento de Casos de Uso (CU-01 a CU-08) |
| Historia de usuario | HU-XX | HU-12 | Issue de GitHub |
| Tarea de desarrollo | T-XX | T-034 | Checklist dentro del Issue de la HU |
| Caso de prueba | CP-XX | CP-09 | `05_pro/` o carpeta `tests/` del repo |

**Un identificador nunca se reutiliza ni cambia de número.** Antes de crear uno nuevo, revisa `06_proyect/TRAZABILIDAD.md` para ver el último número usado en su categoría.

Lista de casos de uso vigentes:

| Código | Nombre |
|---|---|
| CU-01 | Reportar Evento Vial |
| CU-02 | Visualizar Eventos Viales |
| CU-03 | Calcular Ruta Óptima |
| CU-04 | Recalcular Ruta |
| CU-05 | Consultar Disponibilidad de Combustible |
| CU-06 | Monitorear Estado de Surtidores vía WhatsApp |
| CU-07 | Ejecutar Simulador de Tráfico |
| CU-08 | Iniciar Sesión |

## 4. Ramas (branches)

- `main` → siempre lista para demo. Nadie hace push directo aquí.
- `develop` → trabajo terminado e integrado de cada sprint. Nadie hace push directo aquí.
- `feature/HUxx-CUxx-nombre-corto` → una rama por historia de usuario, siempre creada desde `develop`.

Ejemplo real: `feature/HU12-CU04-recalculo-congestion`

Como crear una rama feature nueva:
`git checkout develop`
`git pull`
`git checkout -b feature/HU08-CU03-calculo-ruta-optima`

Reglas:
1. Nunca se trabaja directo sobre `main` o `develop`.
2. Toda rama `feature/*` vuelve a `develop` mediante **Pull Request**, nunca con merge directo.
3. Nombres sin tildes ni espacios. Si el caso de uso no aplica (tareas de infraestructura), se omite: `feature/HU20-config-ci`.

## 5. Labels del repositorio

| Label | Uso |
|---|---|
| `caso-uso:CU-01` … `caso-uso:CU-08` | Identifica a qué caso de uso pertenece el Issue |
| `tipo:historia-usuario` | El Issue es una historia de usuario |
| `tipo:tarea` | El Issue es una tarea puntual (no ligada a una HU completa) |
| `tipo:bug` | Defecto encontrado (en desarrollo o en pruebas) |
| `tipo:prueba` | Relacionado a un caso de prueba |
| `estado:bloqueado` | El Issue no puede avanzar por una dependencia externa |
| `fase:elaboracion` / `fase:construccion` | Fase RUP a la que pertenece |

## 6. Milestones

- Fase de Elaboración → un Milestone por iteración (ej. *"Elaboración - Iteración 2: Prototipo de ruteo"*).
- Fase de Construcción → un Milestone por sprint (ej. *"Sprint 3"*).

Todo Issue debe tener un Milestone asignado antes de entrar al tablero.

## 7. Cómo crear un Issue (No es necesario que leas esto)

Formato de título:
```
[HU-12][CU-04] Recalcular ruta ante congestión detectada
```

Y esta plantilla en el cuerpo:
```markdown
**Caso de uso:** CU-04 — Recalcular Ruta
**Historia de usuario:** Como <rol>, quiero <acción>, para <beneficio>.

**Criterios de aceptación**
- [ ] ...
- [ ] ...

**Tareas**
- [ ] T-XXX ...
- [ ] T-XXX ...

**Relacionado:** Vision RQ-XX · CU-XX
```

Etiqueta el Issue con `caso-uso:CU-XX` y `tipo:historia-usuario`, y asígnalo al Milestone del sprint actual.

## 8. Tablero (Project — Kanban)

Columnas: `Backlog → Por hacer → En progreso → En revisión (PR) → Hecho`

- Cada tarjeta corresponde a un Issue (no se crean tarjetas sueltas sin Issue).
- Mueve la tarjeta a "En revisión" en cuanto abras el Pull Request, no antes.
- Solo se mueve a "Hecho" cuando el PR fue aprobado y mergeado a `develop`.

## 9. Commits

Formato: **Conventional Commits** + identificadores + referencia al Issue (El "id" del issue).

```
feat(ruteo): recalcular ruta al detectar congestión (HU-12, CU-04)

Refs #48
```

En el commit (o PR) que termina la historia completa:
```
feat(ruteo): completar recalculo de ruta ante congestión (HU-12, CU-04)

Closes #48
```

- `Refs #48` → deja el vínculo visible, no cierra el Issue. Úsalo en commits intermedios.
- `Closes #48` / `Fixes #48` → cierra el Issue automáticamente al mergear el PR. Úsalo solo en el commit/PR final.
- Tipos válidos: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`.

## 10. Pull Requests

- Todo PR debe referenciar su Issue (`Closes #N` en la descripción).
- Marcelo debe revisar y aprobar antes de mergear.
- El merge a `develop` se hace solo si los checks (si los hay) pasan y los criterios de aceptación del Issue están cumplidos.

## 11. Casos de prueba

- Cada caso de prueba tiene su propio ID `CP-XX` y declara qué HU/CU valida.
- Se guardan en `05_pro/` (o `tests/` si son automatizados), con nombre de archivo `CP-XX_descripcion-corta.md`.
- Si un caso de prueba falla: abre un Issue con `tipo:bug` + `caso-uso:CU-XX` correspondiente, y referencia el CP en el cuerpo ("Detectado en CP-09").

## 12. Matriz de trazabilidad

Archivo: `06_proyect/TRAZABILIDAD.md`. Se actualiza cada vez que se cierra una historia de usuario, agregando o completando su fila:

| RQ (Visión) | CU | HU | Issue | Rama | Casos de prueba | Estado |
|---|---|---|---|---|---|---|
| RQ-05 | CU-04 | HU-12 | #48 | feature/HU12-CU04-recalculo-congestion | CP-09, CP-10 | En progreso |

---

### Resumen

1. Lee el Documento de Visión y los Casos de Uso (CU-01 a CU-08) antes de tomar una tarea.
2. Busca en el tablero una tarjeta en "Por hacer" o pide que te asignen una.
3. Crea tu rama desde `develop` con el patrón `feature/HUxx-CUxx-nombre-corto`.
4. Commitea con el formato de la sección 9.
5. Abre el PR contra `develop`, referenciando el Issue.
6. Cuando te aprueben el PR y se mergee, actualiza `TRAZABILIDAD.md` si corresponde.
