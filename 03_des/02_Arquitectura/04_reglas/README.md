# Reglas de negocio por caso de uso

[Índice de la arquitectura](../README.md)

Cada regla tiene un código, el objeto de dominio que la hace cumplir y la fuente de donde sale. Los controladores no deciden nada: reciben la petición, llaman al modelo y devuelven lo que el modelo responde.

El código se lee así: [RN-01.4](CU-01_ReportarEventoVial.md) es la cuarta regla del caso de uso CU-01. En la columna Fuente, «c.» abrevia criterio de aceptación y «ext.», extensión del caso de uso.

Algunas reglas son de funcionamiento de la app: cada cuánto consulta, qué hace sin red o cuándo obtiene la ubicación. Su responsable es un controlador de la app, porque no deciden qué es válido; eso lo decide siempre el modelo.

Otras son reglas del ciclo de una fuente automática: cada cuánto consulta a ANH, cuánto espera, cuántas veces reintenta y qué deja en el registro (CU-09). Su responsable es la tarea periódica del backend, un servicio del modelo, y tampoco deciden qué es válido: eso lo hacen `ReporteANH` y `EstadoCombustible`.

Cada archivo trae, además de las reglas, el recorrido del proceso. Cada proceso se describe como el camino de una petición: qué hace la vista, qué hace cada controlador y en qué punto el modelo aplica cada regla.

| Caso de uso | Historias de usuario | Reglas |
| --- | --- | --- |
| [CU-01 — Reportar evento vial](CU-01_ReportarEventoVial.md) | HU-01, HU-02 | 17 |
| [CU-02 — Visualizar eventos viales](CU-02_VisualizarEventosViales.md) | HU-03, HU-04, HU-11 | 16 |
| [CU-03 — Calcular ruta óptima](CU-03_CalcularRutaOptima.md) | HU-05 | 18 |
| [CU-04 — Recalcular ruta](CU-04_RecalcularRuta.md) | HU-06, HU-13 | 21 |
| [CU-05 — Consultar disponibilidad de combustible](CU-05_ConsultarDisponibilidadCombustible.md) | HU-07, HU-12 | 17 |
| [CU-06 — Monitorear estado de surtidores vía WhatsApp](CU-06_MonitorearSurtidoresWhatsApp.md) | HU-08 | 19 |
| [CU-07 — Ejecutar simulador de tráfico](CU-07_EjecutarSimuladorTrafico.md) | HU-09 | 13 |
| [CU-08 — Iniciar sesión](CU-08_IniciarSesion.md) | HU-10 | 14 |
| [CU-09 — Consultar estado de combustible desde ANH](CU-09_ConsultarEstadoANH.md) | HU-14 | 19 |
