# Informe integral de preentrega — T-089 / Issue #41 · Tarija

**Fecha:** 26 de octubre de 2026
**Objeto:** base de datos PostGIS, catálogos, cobertura urbana y conciliación de surtidores.
**Estado:** implementación T-089 técnicamente verificada **en el ordenador del usuario**, propuesta de carga preparada, **sin certificación cartográfica ni doble aprobación**. No declarar PR cerrado ni datos de producción.

## 1. Alcance y procedencia

Se reconstruyó un paquete **completo de código T-089** a partir del archivo adjunto `T089_ChuroViaje_04_imp.rar` (85 archivos útiles sin caches). Se preservan los módulos Java, migraciones V1/V1.1, scripts Python ANH, pruebas, referencias, 21 nombres del prototipo y documentos originales en Markdown. Se agregaron `pom.xml`, aplicación T-089 aislada, pruebas de PostGIS/JDBC/MVC/arranque y el método de consulta de alias confirmados por la conversación.

**Límite importante:** el RAR adjunto es una versión antigua. Los 11 archivos originales de T-088 agregados después por el usuario en G:, el snapshot JSON crudo ANH y el archivo completo de log de 16 pruebas **no están en el RAR proporcionado**. No se inventaron. El paquete tiene todos los códigos de T-089 disponibles, pero **no pretende ser una imagen bit a bit de G:**. Ejecutar `herramientas/COMPROBAR_DIFERENCIAS_CON_G.ps1` para ver la diferencia sin modificar archivos.

## 2. Cumplimiento técnico frente al Issue #41

| Requisito | Entrega | Verificación / límite |
|---|---|---|
| Flyway + extensión PostGIS | `backend_java/src/main/resources/db/migration/V1__postgis_y_catalogos.sql` | V1 preservada; Postgres 16.15 confirmado por el usuario |
| Tabla `tipo_evento` con D-04/D-10 | V1 original y V1.1 | 4 reportables 60 min; congestión 30 min no reportable; colores P-06 aún pendientes |
| Tabla `tipo_combustible` | V1 | GASOLINA, DIESEL, GASOLINA_PREMIUM, DIESEL_ULS, DESCONOCIDO |
| Tablas `surtidor`, `surtidor_alias`, relaciones e índice GIST | V1 y V1.1 | Existen en base persistente; aún 0 filas reales cargadas; CHECK y FK configurados |
| Cobertura GeoJSON | `datos/mancha_urbana_tarija.geojson`, `ConfiguracionCobertura`, `LectorCoberturaGeoJson` | dev permite PROVISIONAL; prod rechaza sin aprobación |
| Objeto ubicación RN-01.6 / RN-03.3 | Dominio Java y validador de ruta | Reporte fuera 422; ruta punto inválido como resultado, incluyendo paradas |
| CP-22 | Tests GeoJSON/cobertura existentes | Lógica de inclusión/borde/hueco probada; delimitación urbana **no aprobada** |
| CP-23 V1/V1.1 | `MigracionesPostgisV1Test` | Confirmado por Maven/Testcontainers en equipo del usuario |
| CP-23 V1 + V1.1 + V2 | Tests originales preservados y perfil `-Pcp23-final` | **PENDIENTE**, V2 autorizada inexistente |
| Integración T-088 | Contrato y revisión de ramas documentadas | Bloqueo externo: `evento_vial` sin migración oficial en ramas conocidas |

## 3. Evidencia técnica informada por la ejecución local (no reejecutada aquí)

- `16` pruebas JUnit aprobadas conjuntamente (`BUILD SUCCESS`, 0 errores, 0 fallos): salida real del usuario del 26/10/2026 a las 18:26–18:27, con `T089_VALIDACION_16_PRUEBAS.txt` en su G:.
- `53` pruebas Python y `45` comprobaciones aisladas Java en ciclos anteriores.
- Arranque de Spring Boot **aislado T-089** con PostgreSQL/PostGIS, Flyway V1/V1.1, cobertura provisional y catálogos; el arranque de la aplicación T-088 completa falló porque no existe `eventos_viales`.
- La base local `churoviaje_dev`, puerto 5433, confirmó las migraciones 1 y 1.1 y que `surtidor` tiene 0 registros antes de V2.
- Estos resultados se citan como **reportados y visibles en conversación**, no como nueva prueba realizada dentro de este ZIP.

## 4. Fuente de datos ANH y alcance geográfico

- API declarada: `https://abastecimiento.prod.anh.gob.bo/api/estaciones/?departamento=6&producto=0`.
- Snapshot del usuario de 24/10/2026, SHA-256 esperado: `0214fb4252301b8acaea4edb7783b165a8f6c411dbbe35fa53ba09a2a6a9c320`.
- Auditoría previa del usuario: **31** estaciones del departamento; **12 dentro** y **19 fuera** del polígono de referencia.
- GeoJSON operativo original: SHA-256 `da13387f7d14850caba4231d2c2dbdeee3a5c1048bb0c2bed79bad1f7c759082`.
- Referencia cartográfica Leyes 110/118: SHA-256 `b1089ac8d22a2e914f6886691cb696669f165b0263164bff6e2a6fe02cf4e36a`.
- **Los dos GeoJSON están PROVISIONAL / PENDIENTE** y no equivalen a límite urbano oficial aprobado; la decisión geográfica es solo una **propuesta**.

## 5. Selección técnica de los 21 nombres del prototipo

| Propuesta | Nombre histórico | ID ANH | Razón |
|---|---|---:|---|
| INCLUIR | Agrupa | 2390 | Coincidencia textual y dentro del perímetro provisional |
| INCLUIR | Don Daniel | 2804 | Coincidencia textual y dentro del perímetro provisional |
| EXCLUIR | El Molle | 2977 | Fuera del perímetro de referencia |
| INCLUIR | El Portillo | 2472 | Coincidencia textual y dentro del perímetro provisional |
| EXCLUIR | Moto Mendez | 2627 | Fuera del perímetro de referencia |
| INCLUIR | Panamericano | 2388 | Coincidencia textual y dentro del perímetro provisional |
| INCLUIR | San Jorge | 2688 | Coincidencia textual y dentro del perímetro provisional |
| EXCLUIR | San Martin | 2979 | Fuera del perímetro de referencia |
| INCLUIR | Sointa | 2364 | Coincidencia textual y dentro del perímetro provisional |
| INCLUIR | Surtidor Tarija | 2386 | Coincidencia textual y dentro del perímetro provisional |
| INCLUIR | Tacuarandi | 2339 | Coincidencia textual y dentro del perímetro provisional |

Para los siguientes **diez** nombres se propone `EXCLUIR` **solo de la primera carga**, porque el snapshot disponible no permite identificarlos unívocamente: Campesino, Coliseo Universitario, Domingo Savio, Ex Terminal, Las Vegas, Pimentel, San Geronimo, Villanueva, YPFB Morros Blancos, YPFB Parada Chaco. **No afirmar que no existen.**

**Resumen:** 8 propuestas INCLUIR + 13 propuestas EXCLUIR = 21; 3 exteriores + 10 sin identidad inequívoca. Las 4 estaciones ANH interiores sin equivalencia nominal — IDs **2341**, **2642**, **2911**, **2974** — se documentan sin añadirlas al alcance aprobado.

Las coordenadas de 11 correspondencias en `propuestas/T089_ANH_11_CANDIDATOS_DESDE_SALIDA_USUARIO.csv` fueron transcritas de la salida que el usuario mostró; **antes de enviar a migración deben volver a compararse contra el JSON crudo ANH del usuario**. El paquete no afirma contener 31 filas crudas si no recibió ese archivo.

### Matriz completa de las 21 decisiones técnicas (sin firmas)

| Nombre del prototipo | ID ANH sugerido | Coordenadas ANH presentadas por el usuario (lat, lon) | Decisión propuesta | Justificación | Revisión |
|---|---:|---|---|---|---|
| Las Vegas | — | No disponible: identidad sin conciliar | EXCLUIR | Sin identidad ANH univoca; excluir de primera carga | NO / NO |
| Don Daniel | 2804 | -21.5133312, -64.7382423 | INCLUIR | Identidad ANH dentro de referencia; cobertura final pendiente | NO / NO |
| Tacuarandi | 2339 | -21.5461962, -64.7016073 | INCLUIR | Identidad ANH dentro de referencia; cobertura final pendiente | NO / NO |
| Campesino | — | No disponible: identidad sin conciliar | EXCLUIR | Sin identidad ANH univoca; excluir de primera carga | NO / NO |
| YPFB Morros Blancos | — | No disponible: identidad sin conciliar | EXCLUIR | Sin identidad ANH univoca; excluir de primera carga | NO / NO |
| Agrupa | 2390 | -21.5111041, -64.7487867 | INCLUIR | Identidad ANH dentro de referencia; cobertura final pendiente | NO / NO |
| El Portillo | 2472 | -21.5666524, -64.6700224 | INCLUIR | Identidad ANH dentro de referencia; cobertura final pendiente | NO / NO |
| Moto Mendez | 2627 | -21.4720998, -64.7576881 | EXCLUIR | Fuera del perimetro de referencia | NO / NO |
| Ex Terminal | — | No disponible: identidad sin conciliar | EXCLUIR | Sin identidad ANH univoca; excluir de primera carga | NO / NO |
| Sointa | 2364 | -21.5420577, -64.7075786 | INCLUIR | Identidad ANH dentro de referencia; cobertura final pendiente | NO / NO |
| Panamericano | 2388 | -21.5441213, -64.711626 | INCLUIR | Identidad ANH dentro de referencia; cobertura final pendiente | NO / NO |
| San Jorge | 2688 | -21.5538205, -64.6905788 | INCLUIR | Identidad ANH dentro de referencia; cobertura final pendiente | NO / NO |
| San Geronimo | — | No disponible: identidad sin conciliar | EXCLUIR | Sin identidad ANH univoca; excluir de primera carga | NO / NO |
| El Molle | 2977 | -21.4530516, -64.7533344 | EXCLUIR | Fuera del perimetro de referencia | NO / NO |
| Surtidor Tarija | 2386 | -21.53627, -64.74234 | INCLUIR | Identidad ANH dentro de referencia; cobertura final pendiente | NO / NO |
| San Martin | 2979 | -22.0198784, -63.6772487 | EXCLUIR | Fuera del perimetro de referencia | NO / NO |
| Pimentel | — | No disponible: identidad sin conciliar | EXCLUIR | Sin identidad ANH univoca; excluir de primera carga | NO / NO |
| Villanueva | — | No disponible: identidad sin conciliar | EXCLUIR | Sin identidad ANH univoca; excluir de primera carga | NO / NO |
| YPFB Parada Chaco | — | No disponible: identidad sin conciliar | EXCLUIR | Sin identidad ANH univoca; excluir de primera carga | NO / NO |
| Domingo Savio | — | No disponible: identidad sin conciliar | EXCLUIR | Sin identidad ANH univoca; excluir de primera carga | NO / NO |
| Coliseo Universitario | — | No disponible: identidad sin conciliar | EXCLUIR | Sin identidad ANH univoca; excluir de primera carga | NO / NO |

**Adicionales ANH no incorporados a los 21:** 2341 La Terminal, 2642 Avenida Circunvalación, 2911 Las Américas, 2974 Presidente Arce. El perímetro usado es preliminar; no se afirman aprobaciones.

## 6. Preparación de V2 y restricciones

- SQL propuesta en `propuestas/SQL_PROPUESTA_8_NO_EJECUTAR.sql.txt` **NO SE EJECUTA NI ESTÁ EN `db/migration`**; contiene 8 estaciones y solo alias derivados del nombre oficial, sin sobrenombres WhatsApp.
- Generador oficial original preservado: `herramientas/generar_migracion_surtidores.py`: valida cobertura `APROBADO`, las 21 decisiones finales, `revision_franz=SI`, `aprobado_equipo=SI`, hashes, IDs y coordenadas ANH y alias ambiguos.
- En cuanto los responsables aprueben la cobertura y acta, generar `backend_java/src/main/resources/db/migration/V2__surtidores_revisados.sql`; luego `mvn -Pcp23-final test` en base vacía. Nunca reescribir V1 aplicada.
- El perfil `dev` sirve para comprobaciones locales provisionales y no equivale a prod.

## 7. Integración con T-088, riesgos y responsables

- La rama `T88-java-correcciones-revision` declara `@Table(name="evento_vial")`, mientras que el código anterior declara `eventos_viales`. Ninguna de las ramas revisadas por el usuario contenía migración de esas tablas. Se deja como dependencia HU-01/T-088.
- `CatalogosJdbc.buscarPorNombreOAlias` es comodidad: busca alias normalizados. V2 derivará el nombre oficial como alias normalizado, pero el diseño de otros alias exige revisión.
- Manejador T-089 se marca con `@Order(HIGHEST_PRECEDENCE)` para `422` de reporte; coordinación con manejador T-088 pendiente.
- Los colores P-06 de eventos no se inventan; su `NULL` continúa pendiente de diseño.
- En rutas se devuelve `PUNTO_INVALIDO` para RN-03.3, sin simular un rechazo 422; la representación HTTP final pertenece al contrato de rutas.

## 8. Resultado de entrega

**Se puede presentar**: código fuente T-089, pruebas, configuración de Docker, Java y Python, inventario ANH reconciliado como propuesta, polígono provisional con trazabilidad, SQL propuesta aislada y documentación Markdown. **No se puede declarar**: aprobación definitiva de 21 estaciones, cobertura oficial, V2 aplicada, CP-23 completo ni backend HU-01 integrado.

**GitHub:** no se ejecutan `commit`, `push`, `merge`, `rebase` ni `fetch`. El paquete no es un PR.
