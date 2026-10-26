# Campos sin evidencia: T-089 — Tarija ciudad

Fecha de revisión: **24 de octubre de 2026**. Este documento forma parte del entregable del issue #41. **Vacío no significa cero, falso, estación inexistente ni ubicación (-, 0/0).** Los nombres del prototipo son referencias de trabajo, no confirmaciones de ANH.

## Datos que NO estaban en los seis ZIP

El ZIP T-115 contiene el código de un cliente Python, su README y un informe de ejecución de **31 estaciones del departamento de Tarija y 57 filas estación–combustible**; **no contiene** `datos_tarija.json`, `estaciones_tarija.csv`, el GeoJSON de puntos ni las respuestas originales de esa ejecución. No fue posible consultar en vivo el host oficial desde este entorno. Por tanto, **no se conoce con respaldo reproducible para cada estación** el `estacion_id` de ANH, nombre exacto, longitud, latitud, dirección/zona, saldo reportado ni hora de actualización.

En la implementación T-089 no se guardan saldos, estados reportados, indicadores de venta, filas o fechas de carga de la API; son datos transitorios de otro flujo, con semántica diferente a los de WhatsApp/Conductores. La API tampoco da alias coloquiales comprobados ni fecha de recepción física de combustible.

## Catálogo legado de 21 surtidores por conciliar

Se conservaron estos 21 **nombres del prototipo únicamente como guía de conciliación**, con las columnas `aliases`, `longitud`, `latitud` y `fuente_ubicacion` totalmente vacías. Los campos de revisión siguen en `NO` y `decision=PENDIENTE`; no tienen `estacion_id_anh` ni prueba de correspondencia a un nombre ANH oficial.

| N.º | Nombre provisional del prototipo | Alias | Longitud | Latitud | Fuente | Franz | Equipo |
|---:|---|---|---|---|---|---|---|
| 1 | Las Vegas | — | — | — | — | NO | NO |
| 2 | Don Daniel | — | — | — | — | NO | NO |
| 3 | Tacuarandi | — | — | — | — | NO | NO |
| 4 | Campesino | — | — | — | — | NO | NO |
| 5 | YPFB Morros Blancos | — | — | — | — | NO | NO |
| 6 | Agrupa | — | — | — | — | NO | NO |
| 7 | El Portillo | — | — | — | — | NO | NO |
| 8 | Moto Mendez | — | — | — | — | NO | NO |
| 9 | Ex Terminal | — | — | — | — | NO | NO |
| 10 | Sointa | — | — | — | — | NO | NO |
| 11 | Panamericano | — | — | — | — | NO | NO |
| 12 | San Jorge | — | — | — | — | NO | NO |
| 13 | San Geronimo | — | — | — | — | NO | NO |
| 14 | El Molle | — | — | — | — | NO | NO |
| 15 | Surtidor Tarija | — | — | — | — | NO | NO |
| 16 | San Martin | — | — | — | — | NO | NO |
| 17 | Pimentel | — | — | — | — | NO | NO |
| 18 | Villanueva | — | — | — | — | NO | NO |
| 19 | YPFB Parada Chaco | — | — | — | — | NO | NO |
| 20 | Domingo Savio | — | — | — | — | NO | NO |
| 21 | Coliseo Universitario | — | — | — | — | NO | NO |

No se copiaron coordenadas guía de ninguno de los ocho puntos aproximados, ni los alias anteriores, que no constan en ANH. Las 21 filas requieren conciliación con el `estacion_id` y el nombre exacto de una extracción real. Si un nombre no se encuentra en ANH, **queda sin emparejar y sin insertarse**, salvo nueva fuente permitida y decisión de alcance aprobada.

## Seis estaciones adicionales fuera del alcance de T-089

Las seis propuestas **no están autorizadas** para V2, aunque posteriormente aparezcan en ANH. Se mantienen como pendientes de otro issue, también sin sus coordenadas guía y sin alias.

| N.º | Nombre propuesto | Alias | Longitud | Latitud | Fuente | Franz | Equipo |
|---:|---|---|---|---|---|---|---|
| 1 | Copacabana | — | — | — | — | NO | NO |
| 2 | El Chorro | — | — | — | — | NO | NO |
| 3 | La Terminal | — | — | — | — | NO | NO |
| 4 | Las Américas | — | — | — | — | NO | NO |
| 5 | San Jorge II | — | — | — | — | NO | NO |
| 6 | YPFB Panamericana | — | — | — | — | NO | NO |

## Otros campos pendientes

| Recurso | Campo/valor pendiente | Situación actual y razón |
|---|---|---|
| `datos/mancha_urbana_tarija.geojson` | Contorno urbano verificado por cartografía; aprobación de Marcelo | Contorno aproximado conservado solo para pruebas de desarrollo; `estado_validacion=PROVISIONAL`, `revision_equipo=PENDIENTE`. No se sustituyó por un polígono supuesto. |
| `integraciones/ANH_T115/salida_tarija/<FECHA>/surtidores_anh_revision_PENDIENTE.csv` | Estación ANH `id`, `nombre`, `latitud`, `longitud`, `nombre_prototipo`, `fuente_ubicacion` | **No generado**: falta JSON ANH y cobertura aprobada. Cuando se genere, `aliases` estará vacío; `nombre_prototipo` quedará vacío ante ausencia de coincidencia exacta. |
| `backend_java/src/main/resources/db/migration/V2__surtidores_revisados.sql` | `INSERT` de estaciones y claves normalizadas oficiales | **No creado** para impedir publicar datos falsos. Requiere JSON con TLS y SHA, cobertura aprobada, conciliación y aprobación humana completa. |
| `tipo_evento.color` para los cinco eventos | RGB hexadecimal aprobado por diseño/UI | **V1.1 deja `NULL` en los cinco**, incluidas las referencias rojo/azul que eran ejemplos y no valores HEX acreditados; P-06 pendiente. V1 histórica no ha sido editada. |
| `surtidor_alias` | Alias conversacionales usados por grupos | Ninguno suministrado por API ANH: **no se insertan**. Solo se crea la clave técnica normalizada del nombre oficial ANH tras validación. |
| `estado_combustible`, `confirmacion_recarga` | Saldos, filas, fechas de carga, disponibilidad y fuente de conductor/WhatsApp | **Sin registros inventados** en las migraciones T-089. ANH reporta saldo observado; no equivale al origen WhatsApp/Conductores previsto por el contrato. |
| `06_proyect/TRAZABILIDAD.md` | Fila T-089 | El ZIP de gestión ya está incluido como referencia, pero no contiene fila T-089; debe actualizarse en repositorio principal solo después del merge. |
| Error global, Spring Data, Maven y paquete definitivo | Contrato T-088 | T-088 no fue aportado: requieren rebase y revisión con Jorge. |

**Advertencia Flyway:** la **V1 original conserva exactamente su SHA-256**; los ajustes de colores y normalización de alias se efectúan en una versión posterior, `V1_1__colores_pendientes_y_alias_normalizados.sql`. No modificar migraciones ya aplicadas. La futura V2 solo se generará tras consulta ANH real y dobles aprobaciones.

## Ubicación de la API y de las posibles evidencias futuras

El **cliente ANH íntegro y original** está incluido en `integraciones/ANH_T115/`; se ejecuta desde la raíz mediante `EJECUTAR_API_Y_REVISION.bat` o `python herramientas/ejecutar_extraccion_anh.py`. Una consulta exitosa genera la carpeta `integraciones/ANH_T115/salida_tarija/<FECHA>/` con el JSON y las trazas crudas. **Ninguno de esos archivos de datos existe todavía en la entrega**, porque el servidor no fue accesible desde este entorno. El resumen antiguo de 31 estaciones departamentales y 57 registros no sustituye el snapshot.

El filtro no está limitado solo por palabra «Tarija» en los nombres: **requiere prueba geográfica dentro de un polígono urbano aprobado**. Si un surtidor aparece en ANH pero sin coordenadas verificables, queda en la auditoría como no localizable y no se introduce en V2. Si aparece fuera de la ciudad, se excluye. Las fechas, saldos y estados observados en ANH **no se traducen** a informes de WhatsApp (RN-06.7), cuya procedencia es distinta.
