# Matriz de cobertura de criterios T-089 — Issue #41

**Situación al 24/10/2026:** algunos criterios implementados en código, pero **el issue sigue abierto para validación**. Las expresiones «incluido», «probado» y «bloqueado» tienen significados distintos; esta matriz no declara aprobación inexistente.

## Aceptación del issue

| Criterio indicado | Evidencia en la entrega | Prueba y estado |
|---|---|---|
| Flyway habilita extensión PostGIS; tablas `tipo_evento`, `tipo_combustible`, `surtidor` y `surtidor_alias` según `06_base_de_datos.md` | `backend_java/src/main/resources/db/migration/V1__postgis_y_catalogos.sql` **original intacta**. | Comprobación SQL estática correcta. **Test real CP-23/PostGIS aún no ejecutado**. |
| `tipo_evento` incluye accidente, control policial, bloqueo, otro reportables con 60 minutos y congestión no reportable con 30 minutos | V1 original + `V1_1__colores_pendientes_y_alias_normalizados.sql` para neutralizar colores HEX no aprobados (P-06). | Aserciones JUnit preparadas, revisión SQL. **Integración pendiente**. |
| `tipo_combustible` contiene gasolina, diésel, gasolina premium, diésel ULS y desconocido | V1 original y `TipoCombustible.java`. | Java aislado OK y JUnit dispuesto. |
| Catálogo surtidores de Tarija ciudad con ubicación y alias verificados | Cliente ANH original y scripts de extracción, validación geográfica y generación V2 con aprobación. | **Bloqueado**: sin snapshot en vivo, sin cartografía aprobada, sin acta de 21 entradas aprobada. Sin `INSERT`s inventados. |
| GeoJSON mancha urbana cargada al arrancar backend | `datos/mancha_urbana_tarija.geojson`, `ConfiguracionCobertura.java`, `LectorCoberturaGeoJson.java`. | Cobertura lógica/GeoJSON provisional para `dev` OK; **cartografía definitiva y CP-22 real pendientes**. `prod` rechaza estado provisional. |
| Objeto `Ubicacion` valida pertenencia a la zona urbana | `Ubicacion.java`, `Coordenada.java`, `CoberturaManchaUrbana.java`, `ValidadorPuntosRuta.java`. | 45 comprobaciones de dominio Java ×3; geometría de referencia 961 puntos ×3 en Python. **Contrato HTTP final pendiente de T-088**. |
| Base vacía queda igual que producción solo corriendo migraciones | Test `MigracionesPostgisTest.java` (dos bases) y test V1+V1.1 separado. | **Pendiente** hasta V2 con estaciones autorizadas + PostgreSQL/PostGIS/Docker/Maven; no se alegó éxito. |
| PK/FK, cascada y toda referencia a surtidor/tipo es válida | V1 con claves y `ON DELETE CASCADE`; V1.1 CHECK alias. | SQL inspeccionado; pruebas con PostgreSQL pendientes. |
| CP-22 punto en plaza dentro de cobertura; punto en San Lorenzo fuera | Tests de geometría con límites/huecos y fixture provisional de Tarija. | Lógica OK con borrador; **resultado final depende del límite urbano realmente aprobado**. No cambiar rótulo «plaza» por una aproximación sin comprobar. |
| CP-23 migración de base vacía completa | Tests Testcontainers y migraciones versionadas V1/V1.1/V2 preparada pero no materializada. | **Bloqueado**; se exige inventario ANH real. |

## Documentos/fragmentos que el usuario fijó como obligatorios

| Referencia | Norma/alcance exacto | Implementación o limitación |
|---|---|---|
| `03_des/02_Arquitectura/06_base_de_datos.md` | Tablas principales; «Lo que la base de datos asegura por su cuenta» | Claves, checks, índice GIST, integridad referencial de V1, y control adicional de alias en V1.1. Se deja fuera cualquier carga ficticia. |
| `03_des/02_Arquitectura/03_modelo_dominio.md` | «Catálogos y valores iniciales», objeto `Ubicación` | Valores de combustible y eventos en V1; objetos de dominio y geometría; latitud primero salvo GeoJSON. |
| `08_decisiones_parametros.md` D-02 | Spring Boot + PostgreSQL/PostGIS | Diseño SQL/PostGIS y Java Spring en T-089; pendiente ensamblaje con T-088. |
| D-04 | Congestión solo desde telemetría | `CONGESTION` no reportable, 30 minutos, sin crear mensajes/avisos inventados. |
| D-10 | Cuatro tipos reportables por conductor | Códigos y vigencias en V1, preservados. |
| `CU-01_ReportarEventoVial.md` RN-01.6 | Reporte solo dentro de la mancha urbana | Excepción de dominio para rechazo 422; controlador final depende de T-088. |
| `CU-02_VisualizarEventosViales.md` RN-02.3 | Vigencia desde hora de ocurrencia: 30/60 min | Catálogo preparado; el cálculo de vigencia de eventos depende del módulo que los gestione. |
| `CU-03_CalcularRutaOptima.md` RN-03.3 | Origen, destino y paradas dentro de mancha urbana | Comprobación de los tres tipos de puntos; resultado normal `PUNTO_INVALIDO`, cuya representación de API debe definirse con Marcelo. |
| `CU-06_MonitorearSurtidoresWhatsApp.md` RN-06.7 | Aviso con surtidor, combustible, estado, fila y fecha del mensaje | Catálogo para identificar surtidor; **no** se inventan estados de mensaje desde saldos ANH. La producción de avisos pertenece al flujo WhatsApp/LLM. |
| RN-06.8 | Combustible: catálogo de cuatro o DESCONOCIDO | Valores correctos en V1; ningún dato ANH se fuerza a ser «mensaje». |
| `05_pro/PruebaClasificacion/Clasificador_Surtidores.py` | Solo `SURTIDORES` | Diccionario/patrones recuperados exclusivamente para conciliación; no se incorporan alias ni coordenadas como datos reales. |
| `06_proyect/CONTRIBUTING.md` §4 | Feature branch desde develop; PR | Instrucciones en README; ningún push realizado. |
| CONTRIBUTING §9–10 | Conventional Commits, Refs/Closes, revisión PR | Tareas descritas para cuando los bloqueos se resuelvan. |
| `06_proyect/TRAZABILIDAD.md` | Fila T-089 | Copia adjunta no presenta esa fila; el cambio se hace en repositorio de integración tras PR/merge, no se falsifica «Hecho». |

## Pruebas ejecutadas

- Tres ciclos: **53/53 tests Python** y **45/45 comprobaciones Java** en cada ciclo. Incluyen pruebas del **cliente ANH original** con transporte sustituido *solo dentro de tests*, validación de origen TLS, conciliación gasolina/diésel, exclusión por contradicciones, geofiltro y bloqueos por aprobación.
- Revisión de sintaxis Python, cinco YAML, SHA-256 del cliente ANH y de V1 sin modificación, separación de 21 nombres históricos y seis propuestas, rechazo de GeoJSON no aprobado, inexistencia de V2 ficticia.
- Conexión real ANH: **no se pudo resolver DNS** en este entorno. No disponible test completo de Spring Boot/JUnit/PostGIS ni CP-22 final/CP-23. Véase `evidencias/`.

**Interpretación:** la entrega permite completar el flujo de datos de T-089 de forma reproducible desde ANH cuando existan red y aprobaciones. **No** equivale todavía a «todos los criterios cumplidos» ni a un inventario urbano validado.
