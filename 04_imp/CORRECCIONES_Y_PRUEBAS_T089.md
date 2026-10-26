# Informe técnico final — T-089 / Issue #41

**Fecha de revisión:** 24/10/2026
**Rama objetivo:** `feature/T89-java-base-de-datos` (entrega local; no se ha hecho push ni PR)
**Dictamen:** **correcciones locales y pruebas unitarias satisfactorias; cierre integral BLOQUEADO** por falta de extracción ANH en vivo, cobertura cartográfica aprobada, revisión humana de surtidores y base T-088/Spring Boot/PostGIS para integración. No se ha simulado la conclusión de ninguna de estas tareas.

## 1. Material inspeccionado y jerarquía

Se utilizaron **seis archivos ZIP**: (1) T-089 `04_imp` original; (2) T-115 `05_pro_PruebaApiANH` con el cliente ANH; (3) T-084 requisitos; (4) T-084 diseño y arquitectura; (5) `HU08-CU06-AnalisisLLM/05_pro_PruebaClasificacion`; (6) `main/06_proyect`. Además, la revisión de Franz `correcciones_franz_T-089.md`, las dos capturas del issue y el anterior paquete corregido para contrastar regresiones. La revisión de Franz, arquitectura, decisiones formalizadas y restricciones del issue prevalecen sobre los datos guía del prototipo.

Los contenidos originales relevantes se conservan en `referencias_t089/`. Se extrajo **únicamente el diccionario `SURTIDORES`** del clasificador original a `datos_pendientes/patrones_prototipo_surtidores_SIN_VALIDAR.json`, manteniendo sus 21 entradas para conciliación. Este diccionario **no es un registro oficial de ANH** y no se usa para insertar estaciones ni alias conversacionales. Los chats, el modelo y sus ejemplos no se incluyeron ni se trataron como evidencia de ubicación.

El **cliente T-115 original** (`integraciones/ANH_T115/anh_tarija.py`) se incorporó **sin cambios**, con `requirements.txt`, `ejecutar_windows.bat`, README e informe técnico. El archivo Python es idéntico en SHA-256 a la fuente adjunta: `7bba307fdb4eafced99ac871f07d1aaa79500b3b46e2f92880315f900557d0a4`. Esto incluye el *cliente*, no una réplica local del servidor ANH ni una extracción descargada previamente.

## 2. Fuente única de registros oficiales ANH y filtro de Tarija

**Regla aplicada:** la extracción ANH consulta el **departamento de Tarija**, pero la base T-089 solo puede recibir estaciones verificadas dentro de la **mancha urbana de la ciudad de Tarija**. No confundir departamento, ciudad y municipio. El límite urbano suministrado no es cartografía validada.

1. `herramientas/ejecutar_extraccion_anh.py` llama al cliente original con dos productos (`0` gasolina y `1` diésel) y certificados TLS verificados (`--usar-certifi`); no tolera extracciones parciales. Resguarda JSON, archivos de respuestas y trazas de consulta.
2. `herramientas/validacion_anh_t089.py` comprueba departamento, productos y conteos, id de estación, identidad de estación, HTTPS/origen, hora de observación y procedencia de cada posición. Si el detalle `/estaciones/{id}` completa la coordenada, se registra **esa URL de detalle** como fuente de ubicación.
3. Se reconcilian los dos productos: un dato de coordenada presente frente al otro ausente puede conservarse si es acreditado por ANH; **coordenadas o nombres contradictorios excluyen** el registro. Ubicación omitida ≠ `(0,0)` ni ubicación probable.
4. `herramientas/preparar_revision_anh.py` recorta solo con GeoJSON **aprobado** y deja constancia de puntos no localizables, externos, ambiguos, sin nombre o propuestos fuera de alcance. Las candidatas generan CSV con `revision_franz=NO`, `aprobado_equipo=NO`, `decision=PENDIENTE` y `aliases` vacío.
5. `herramientas/generar_migracion_surtidores.py` exige el snapshot ANH original, su SHA-256 idéntico al del CSV, coordenadas y nombres iguales a la fuente, el acta de **las 21 entradas** resueltas (inclusión o exclusión motivada) y las dos aprobaciones reales. No acepta las seis propuestas adicionales ni genera V2 vacía. Solo añade **clave de búsqueda normalizada del nombre oficial**, derivada de ese mismo nombre, nunca apodos imaginados.

**Estado de los datos**: T-115 contiene código y un informe sobre una consulta anterior en otro equipo (**31 estaciones departamentales, 57 filas estación–combustible**); **no adjunta** `datos_tarija.json`, CSV completo de estaciones ni las respuestas crudas. Se intentó ejecutar el cliente contra el host oficial desde este entorno: error de resolución DNS (`[Errno -3] Temporary failure in name resolution`), código de salida 2. Evidencia: `evidencias/intento_api_directa_t089.log`. Esto **no prueba** que la API carezca de surtidores y no autoriza sustituirlos con datos del prototipo. **No se creó V2.**

Se añadió `EJECUTAR_API_Y_REVISION.bat` e instrucciones Windows/Linux en `readme.md`. Cuando la API sea accesible, el paquete permite consultar y documentar las estaciones sin modificar el cliente original; cuando se apruebe el límite urbano, permite continuar la conciliación sin autoconceder aprobaciones.

## 3. Correcciones de Franz y decisiones técnicas

| Hallazgo | Acción concreta | Situación comprobada |
|---|---|---|
| **1.1 V2** | Generador con validaciones de snapshot, 21 decisiones, doble aprobación y salida atómica. | **Bloqueado por evidencias**; no se entregó `V2__surtidores_revisados.sql` inventada. CP-23 requiere V2 real y sigue sin satisfacerse. |
| **1.2 Ubicaciones** | Se retiraron las 8 coordenadas guía sin verificar y los demás valores ausentes de los 21 nombres preliminares; procedencia ANH exacta al importar. | **21/21 pendientes** de correspondencia oficial, revisión de Franz y aprobación de Marcelo. |
| **1.3 Seis adicionales** | CSV separado sin coordenadas guía; bloqueo explícito de los seis nombres en V2. | **6/6 fuera de alcance**. «La Terminal»/«Ex Terminal» y «YPFB Panamericana»/«Tacuarandi» no se deduplican sin pruebas. |
| **1.4 GeoJSON** | Estado `APROBADO` raíz **y** `revision_equipo=APROBADO` de cada geometría obligatorios en Java y Python. | Borrador original **PROVISIONAL**; no sustituido por límite supuesto. `prod` impide aceptarlo. |
| **1.5 T-088** | Se deja la integración con Spring Boot y manejador global para rebase con Jorge; se agregan suites autónomas. | **No compilado como aplicación Spring ni probado con PostGIS**: build T-088 y herramientas de integración no suministrados. |
| **2.1 Punto en ruta** | Dominio devuelve resultado `PUNTO_INVALIDO` y valida origen, destino **y cada parada**. | Lógica aislada comprobada; codificación HTTP/JSON final requiere coordinación con Marcelo y T-088. En reportes se conserva 422. |
| **2.2 Manejador global** | Se mantiene el manejador T-089 acotado como puente sin inventar un global inexistente en el ZIP. | **Pendiente** eliminar duplicidad/armonizar con el manejador de T-088 al integrarlo. |
| **2.3 MVC y repositorios** | Se movió normalización a servicio de dominio (`ServicioBusquedaSurtidores`); repositorio recibe clave normalizada. | Capas aisladas verificadas; elección JDBC/JPA y nombres finales requieren integración T-088. |
| **2.4 Repositorio** | Test `CatalogosJdbcIntegrationTest` para catálogo, distancia y clave de nombre; se conserva JDBC ya existente. | **Incluido, no ejecutado**, requiere V2 real, Maven y Docker/PostGIS. |
| **2.5 Colores P-06** | Se elimina el uso efectivo de HEX no aprobados mediante nueva migración V1.1; tipo_evento.color puede ser `NULL`. | **Cinco colores pendientes**; V1 original conserva sus bytes, y V1.1 neutraliza solo valores iniciales conocidos para no borrar colores futuros aprobados. |
| **2.6 Latitud/longitud** | `Coordenada(latitud,longitud)` y firmas uniformes; la inversión a `[lon,lat]` solo en GeoJSON/PostGIS. | Verificado en 45 comprobaciones Java y tests Python de geometría. |
| **2.7 Ignorar archivos** | `.gitignore` contempla `.env`, `target/`, salidas ANH y temporales. | Revisado estáticamente. |
| **2.8 Git/PR** | Instrucciones precisas `Refs #41`, `Closes #41`, PR hacia `develop`, aprobación y trazabilidad postmerge. | **Sin commits nuevos, push, PR ni merge** realizados desde este entorno. |
| **3.1–3.12 Menores** | Fixtures de tests, `ClaveAliasTest`/`TipoCombustibleTest`, GeoJSON de pruebas, parámetros, README, comentarios de reglas, restricciones de alias, comprobaciones cartográficas y autocontrol de scripts. | Ajustes locales entregados. La localización definitiva de «Plaza Luis de Fuentes», test de UI para orden por presentación y despliegue físico siguen fuera de esta validación. |

**Integridad Flyway:** `V1__postgis_y_catalogos.sql` se devolvió **byte por byte** al original T-089, SHA-256 `06fd693039a5ca0bbc2c69e2a1d1ba2392398bdbc5f707b8a159363cbc30ce3b`. Los cambios a colores pendientes y al `CHECK` de alias están en **`V1_1__colores_pendientes_y_alias_normalizados.sql`**: versión nueva, ordenada después de V1 y antes de una futura V2. Si una versión de Flyway ya corrió en cualquier ambiente, no editarla ni ocultar checksum con `repair`; cotejar antes la historia de migraciones del entorno. Se corrigió también una aserción de un test PostgreSQL: con V1.1 el alias tiene **dos** restricciones CHECK, no una.

## 4. Correspondencia con los apartados específicos exigidos

| Fuente de arquitectura | Resultado en T-089 |
|---|---|
| `06_base_de_datos.md`: `tipo_evento`, `tipo_combustible`, `surtidor`, `surtidor_alias`, PK, FK, `ON DELETE CASCADE`, GIST y garantías propias | V1 original mantiene estructura, restricciones, extensiones e índices; V1.1 agrega chequeo de alias. Test de migración en base vacía dispuesto, todavía sin ejecutar. |
| `03_modelo_dominio.md`: valores iniciales, `Ubicación` | Eventos y combustibles del catálogo; `Ubicacion`, `Coordenada` y cobertura con reglas de geometría; no generar surtidores desde el diccionario no oficial. |
| `08_decisiones_parametros.md`, **D-02** | PostgreSQL + PostGIS mediante V1 y consultas de distancia geográfica, conservando Spring Boot para T-088. |
| **D-04 / RN-02.3** | `CONGESTION`, no reportable, vigencia 30 minutos; demás eventos vigencia 60 minutos. |
| **D-10** | `ACCIDENTE`, `CONTROL_POLICIAL`, `BLOQUEO`, `OTRO` reportables. |
| **RN-01.6** | Reportes fuera de mancha urbana rechazados con excepción de dominio/HTTP 422. |
| **RN-03.3** | Origen, destino y todas las paradas dentro de cobertura o resultado `PUNTO_INVALIDO`; salida REST final pendiente T-088. |
| **RN-06.7** | Catálogo puede identificar surtidor por ID/nombre oficial, pero **no se generan** avisos de WhatsApp sin mensajes originales ni estados/fila/fecha correspondientes. |
| **RN-06.8** | Cinco valores del catálogo: cuatro tipos precisos y `DESCONOCIDO`. El cliente ANH no sustituye al clasificador de mensajes. |
| `Clasificador_Surtidores.py`, diccionario `SURTIDORES` | 21 claves y patrones guardados como referencia aparte, sin convertir expresiones o alias en inventario oficial. |
| `CONTRIBUTING.md` §4, §9, §10 | Rama feature → PR `develop`, Conventional Commits + `Refs #41`, `Closes #41` solo al cierre, revisión de Marcelo obligatoria. |
| `TRAZABILIDAD.md`, fila T-089 | El ZIP suministrado **no contiene fila T-089**; copia intacta. No se añade estado «Hecho» antes de merge. |

La cobertura y la base son piezas de infraestructura del issue, no implementaciones completas de los contratos CU-06/telemetría/ruteo. Donde la integración depende de otra historia no se simula cumplimiento.

## 5. Pruebas ejecutadas y resultados reproducibles

**Entorno:** Python 3.13.5 y OpenJDK 21.0.12.1. En este contenedor no se dispone de Maven, Gradle, Docker, PostgreSQL/PostGIS ni fuentes de T-088.

| Ejecución | Ciclo 1 | Ciclo 2 | Ciclo 3 | Observaciones |
|---|---:|---:|---:|---|
| `python -m unittest discover -s herramientas/tests -p 'test*.py' -v` | **53/53** | **53/53** | **53/53** | 159 ejecuciones de casos; HTTP ANH simulado **solo dentro de tests**, datos inventados no exportados como fuente real. |
| `bash herramientas/probar_dominio_java.sh` | **45/45** | **45/45** | **45/45** | 135 verificaciones de dominio Java; compilación independiente con `javac`, **no** Spring/JUnit integral. |
| Consistencia Python/GeoJSON | OK | OK | OK | Incluida en las 53 pruebas: cuadrícula de **961 puntos por corrida** contrastada con Shapely, más límites y agujeros. |
| Verificación estática de 5 YAML y sintaxis Python | OK | — | — | No valida bootstrap Spring. |
| Hashes de V1 y cliente ANH | OK | — | — | Coinciden con los ZIP originales. |
| Consulta **real** ANH por HTTPS | **Error DNS** | — | — | Cliente no devuelve inventario; `evidencias/intento_api_directa_t089.log`. |
| CP-22 contra **polígono real aprobado** | **Pendiente** | — | — | Límite cartográfico sigue provisional. Pruebas de lógica geométrica no sustituyen cartografía real. |
| CP-23 sobre PostGIS vacío con **V1 + V1.1 + V2** | **Pendiente** | — | — | V2 real y sistema T-088 no están disponibles. |
| Tests `CatalogosJdbcIntegrationTest` / Spring / Flyway | **No ejecutados** | — | — | Requieren dependencias de build, Docker y PostGIS. |

Evidencias textuales conservadas en `evidencias/pruebas_python_corrida_{1,2,3}.log`, `evidencias/pruebas_java_corrida_{1,2,3}.log`, `evidencias/verificacion_final.log` y `evidencias/intento_api_directa_t089.log`. No afirmar “todos los tests del proyecto pasan”: hay pruebas de integración explícitamente pendientes.

## 6. Campos que permanecen vacíos y pasos de cierre

Ver el **inventario nominativo completo** en `datos_pendientes/CAMPOS_SIN_EVIDENCIA.md` (21 entradas del prototipo y seis adicionales). No fueron completados sin fuente: alias coloquiales; latitud/longitud y URL de evidencia por entrada; ID y nombre ANH conciliados; aprobaciones; cinco colores HEX P-06; informes WhatsApp (estado, fila, fecha); cobertura urbana aprobada; y V2.

**Secuencia imprescindible**:

1. Ejecutar el cliente ANH **incluido** donde el DNS y la red permitan TLS certificado, conservar `datos_tarija.json`, JSON crudos, trazas y hash SHA-256. Si falla, registrar error y no publicar estaciones.
2. Levantar/dibujar límite urbano real con cartografía verificada y obtener aprobación explícita de Marcelo. Cambiar las dos marcas GeoJSON **solo** tras revisión efectiva.
3. Reprocesar el snapshot vía `ejecutar_extraccion_anh.py --snapshot` y revisar auditoría, incompletos, duplicados y estaciones exteriores. No promover las seis adicionales ni usar alias del prototipo como confirmados.
4. Conciliar 21 nombres con IDs oficiales; registrar `INCLUIR` o `EXCLUIR` motivado, revisión de Franz y aprobación de Marcelo; validar ambas actas antes de generar V2.
5. Rebasar sobre T-088 (#40), unificar controlador de errores, build, dependencias y repositorio con Jorge, acordar el contrato de `PUNTO_INVALIDO` con Marcelo. Revisar orden efectivo de migraciones.
6. Ejecutar CP-22 con cartografía aprobada, CP-23 sobre **dos bases vacías PostgreSQL/PostGIS** y test JDBC con surtidores reales, sin insertos ficticios; adjuntar logs al PR.
7. Commits Conventional Commits con `Refs #41`, PR a `develop` con `Closes #41`, revisión y aprobación, merge; **solo entonces** completar la trazabilidad T-089.

**Resultado:** entrega técnica revisable con cliente ANH completo y pruebas locales repetidas. **No** se atribuyen registros sin consultar, aprobaciones no concedidas, integraciones no ejecutadas ni satisfacción absoluta de #41.
