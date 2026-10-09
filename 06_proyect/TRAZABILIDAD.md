## Matriz de trazabilidad

<<<<<<< Updated upstream
| RF / RNF | CU | HU | Issue | Rama | Casos de prueba | Estado |
|---|---|---|---|---|---|---|
| RF-01 · RNF-01 · RNF-06 · RNF-08 · RNF-09 | CU-01 | HU-01 | — | — | — | Pendiente |
| RF-02 | CU-01 | HU-02 | — | — | — | Pendiente |
| RF-03 · RNF-01 · RNF-10 | CU-02 | HU-03 | — | — | — | Pendiente |
| RF-04 | CU-02 | HU-04 | — | — | — | Pendiente |
| RF-05 · RNF-02 · RNF-10 | CU-03 | HU-05 | #2 | feature/HU01-CU03-calcular-ruta-optima | CP-01, CP-02 | Completado |
| RF-05 · RNF-02 · RNF-10 | CU-03 | HU-05 | #15 | feature/HU05-CU03-grafo-irregular | CP-14, CP-15, CP-16, CP-17, CP-18, CP-19 | Completado |
| RF-05 · RNF-02 · RNF-10 | CU-03 | HU-05 | #29 | feature/HU05-CU03-errores-pruebas-ruteo | CP-09, CP-10, CP-11, CP-12, CP-13 | Completado |
| RF-06 | CU-04 · CU-02 | HU-06 | #3 | Feature/HU02-CU04-CalculoVelocidadGPS | CP-03, CP-04, CP-05 | Completado |
| RF-07 · RNF-03 · RNF-07 | CU-05 | HU-07 | — | — | — | Pendiente |
| RF-08 · RF-09 · RNF-04 · RNF-11 | CU-06 | HU-08 | #5 | feature/HU03-CU06-clasificar-mensajes-whatsapp | CP-06, CP-07, CP-08 | Completado |
| RF-08 · RF-09 · RNF-04 · RNF-11 | CU-06 | HU-08 | #6 | feature/HU08-CU06-ComWhatsApp | — | Completado |
| RF-08 · RF-09 · RNF-04 · RNF-11 | CU-06 | HU-08 | #10 | feature/HU08-CU06-AnalisisLLM | — | En progreso |
| RF-10 · RNF-12 | CU-07 | HU-09 | — | — | — | Pendiente |
| RF-11 · RNF-05 · RNF-08 | CU-08 | HU-10 | — | — | — | Pendiente |
=======
| RF / RNF | CU | HU | Reglas | Issue | Rama | Casos de prueba | Estado |
|---|---|---|---|---|---|---|---|
| RF-01 · RNF-01 · RNF-06 · RNF-08 · RNF-09 | CU-01 | HU-01 | [RN-01.1 a RN-01.12, RN-01.16, RN-01.17](../03_des/02_Arquitectura/04_reglas/CU-01_ReportarEventoVial.md) | — | — | — | Pendiente |
| RF-02 | CU-01 | HU-02 | [RN-01.13 a RN-01.15](../03_des/02_Arquitectura/04_reglas/CU-01_ReportarEventoVial.md) | — | — | — | Pendiente |
| RF-03 · RNF-01 · RNF-10 | CU-02 | HU-03 | [RN-02.1 a RN-02.8](../03_des/02_Arquitectura/04_reglas/CU-02_VisualizarEventosViales.md) | — | — | — | Pendiente |
| RF-04 | CU-02 | HU-04 | [RN-02.9](../03_des/02_Arquitectura/04_reglas/CU-02_VisualizarEventosViales.md) | — | — | — | Pendiente |
| RF-05 · RNF-02 · RNF-10 | CU-03 | HU-05 | [RN-03.1 a RN-03.18](../03_des/02_Arquitectura/04_reglas/CU-03_CalcularRutaOptima.md) | #2 | feature/HU01-CU03-calcular-ruta-optima | CP-01, CP-02 | Completado |
| RF-05 · RNF-02 · RNF-10 | CU-03 | HU-05 | [RN-03.1 a RN-03.18](../03_des/02_Arquitectura/04_reglas/CU-03_CalcularRutaOptima.md) | #15 | feature/HU05-CU03-grafo-irregular | CP-14, CP-15, CP-16, CP-17, CP-18, CP-19 | Completado |
| RF-05 · RNF-02 · RNF-10 | CU-03 | HU-05 | [RN-03.1 a RN-03.18](../03_des/02_Arquitectura/04_reglas/CU-03_CalcularRutaOptima.md) | #29 | feature/HU05-CU03-errores-pruebas-ruteo | CP-09, CP-10, CP-11, CP-12, CP-13 | Completado |
| RF-06 | CU-04 · CU-02 | HU-06 | [RN-04.13 a RN-04.19](../03_des/02_Arquitectura/04_reglas/CU-04_RecalcularRuta.md) | #3 | Feature/HU02-CU04-CalculoVelocidadGPS | CP-03, CP-04, CP-05 | Completado |
| RF-07 · RNF-03 · RNF-07 | CU-05 | HU-07 | [RN-05.1 a RN-05.7](../03_des/02_Arquitectura/04_reglas/CU-05_ConsultarDisponibilidadCombustible.md) | — | — | — | Pendiente |
| RF-08 · RF-09 · RNF-04 · RNF-11 | CU-06 | HU-08 | [RN-06.1 a RN-06.18](../03_des/02_Arquitectura/04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) | #5 | feature/HU03-CU06-clasificar-mensajes-whatsapp | CP-06, CP-07, CP-08 | Completado |
| RF-08 · RF-09 · RNF-04 · RNF-11 | CU-06 | HU-08 | [RN-06.1 a RN-06.18](../03_des/02_Arquitectura/04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) | #6 | feature/HU08-CU06-ComWhatsApp | — | Completado |
| RF-08 · RF-09 · RNF-04 · RNF-11 | CU-06 | HU-08 | [RN-06.1 a RN-06.18](../03_des/02_Arquitectura/04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) | #10 | feature/HU08-CU06-AnalisisLLM | — | En progreso |
| RF-10 · RNF-12 | CU-07 | HU-09 | [RN-07.1 a RN-07.13](../03_des/02_Arquitectura/04_reglas/CU-07_EjecutarSimuladorTrafico.md) | — | — | — | Pendiente |
| RF-11 · RNF-05 · RNF-08 | CU-08 | HU-10 | [RN-08.1 a RN-08.13](../03_des/02_Arquitectura/04_reglas/CU-08_IniciarSesion.md) | — | — | — | Pendiente |
| RF-12 · RNF-09 | CU-02 | HU-11 | [RN-02.10 a RN-02.16](../03_des/02_Arquitectura/04_reglas/CU-02_VisualizarEventosViales.md) | — | — | — | Pendiente |
| RF-13 · RNF-07 · RNF-09 | CU-05 | HU-12 | [RN-05.8 a RN-05.16](../03_des/02_Arquitectura/04_reglas/CU-05_ConsultarDisponibilidadCombustible.md) | — | — | — | Pendiente |
| RF-14 · RNF-13 | CU-04 | HU-13 | [RN-04.1 a RN-04.12, RN-04.20](../03_des/02_Arquitectura/04_reglas/CU-04_RecalcularRuta.md) | — | — | — | Pendiente |
>>>>>>> Stashed changes
