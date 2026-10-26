# ChuroViaje — T-089 (#41): catálogos, cobertura y surtidores ANH

**Estado: entrega revisable y reproducible, NO lista para producción ni para cerrar el issue.**

Este paquete contiene el trabajo de `04_imp` y **el cliente Python original ANH T-115 (v2.2.0)**, incorporado íntegramente en `integraciones/ANH_T115/` junto con sus dependencias, instructivo y archivo `.bat`. Incluye además las reglas solicitadas de arquitectura y gestión en `referencias_t089/`, y únicamente el diccionario `SURTIDORES` (21 nombres con expresiones regulares) extraído como referencia no verificada en `datos_pendientes/patrones_prototipo_surtidores_SIN_VALIDAR.json`. **No se incluyen chats de WhatsApp ni se los utiliza como fuente para la base de datos**.

**Distinción indispensable:** la API ANH consulta el **departamento de Tarija**; el MVP de ChuroViaje trabaja dentro de la **mancha urbana de la ciudad de Tarija**. La ubicación de cada estación solo se propone para revisión urbana si su coordenada proviene de ANH y está dentro del GeoJSON **cartográficamente aprobado**. El GeoJSON que vino en T-089 es un **borrador, no está aprobado**. No se dibujó ni certificó artificialmente un límite.

La API **no está incluida como base de datos descargada**. Se incluye su *cliente de consulta*, que recupera datos mediante HTTPS cuando el servidor es accesible. Ni el JSON ni las coordenadas reales originales están presentes en los ZIP; la consulta directa ensayada en este entorno falló por DNS. El informe anterior que refiere «31 estaciones / 57 combinaciones» describe una ejecución de otro equipo, **no es el snapshot crudo necesario para crear V2**. Las filas de prueba de `herramientas/tests/` son sintéticas, temporales y nunca se entregan como estaciones reales.

## Contenido del paquete

| Ubicación | Finalidad |
| --- | --- |
| `integraciones/ANH_T115/anh_tarija.py` y `requirements.txt` | Cliente ANH T-115 sin modificar; consulta Tarija con verificación HTTPS; gasolina y diésel. |
| `integraciones/ANH_T115/ejecutar_windows.bat` y `README.md` | Ejecución original e instrucciones de la API. |
| `herramientas/ejecutar_extraccion_anh.py` | Orquesta el cliente, contrasta las respuestas JSON y sus trazas, y prepara revisión solo con cobertura aprobada. |
| `herramientas/validacion_anh_t089.py` | Valida identidad, coordenadas, trazabilidad de URLs/TLS declarada, completitud y geometría. |
| `herramientas/preparar_revision_anh.py` | Prepara CSV sin autoconceder aprobaciones. |
| `herramientas/generar_migracion_surtidores.py` | Generador V2 con bloqueos: JSON ANH, huella SHA-256, ubicación, 21 conciliaciones y aprobaciones. |
| `backend_java/` | Código T-089, configuración Spring, migraciones y tests de integración para unir a T-088. |
| `backend_java/.../V1__postgis_y_catalogos.sql` | **Original intacto**, para conservar checksum de Flyway. |
| `backend_java/.../V1_1__colores_pendientes_y_alias_normalizados.sql` | Limpia solo colores provisionales originales, permite NULL y añade restricción de normalización. |
| `datos_pendientes/` | 21 nombres de prototipo, seis propuestas fuera de alcance y la relación de campos vacíos. |
| `referencias_t089/` | Reglas y decisiones que se pidieron, contratos/MVC, revisión de Franz y gestión Git. |
| `CORRECCIONES_Y_PRUEBAS_T089.md` | Matriz de cumplimiento, evidencia y riesgos restantes. |
| `MAPA_CUMPLIMIENTO_ISSUE_41.md` | Criterio de aceptación → artefacto → prueba → estado, sin prometer cierres falsos. |
| `evidencias/` | Resultados reproducibles de la suite y prueba de red fallida. |

## Paso 1. Consultar la API de Tarija con el cliente incluido

Windows (PowerShell), desde la raíz de este paquete `04_imp`:

```powershell
python -m pip install -r .\integraciones\ANH_T115\requirements.txt
python .\herramientas\ejecutar_extraccion_anh.py
```

También se puede usar `EJECUTAR_API_Y_REVISION.bat` tras instalar Python 3.10 o posterior. En GNU/Linux/macOS:

```bash
python3 -m pip install -r integraciones/ANH_T115/requirements.txt
python3 herramientas/ejecutar_extraccion_anh.py
```

El proceso invoca **el cliente T-115 original** con `--usar-certifi --productos 0 1 --max-detalles 20` y no admite TLS inseguro. Guarda resultados nuevos en `integraciones/ANH_T115/salida_tarija/<fecha>/`. Deben conservarse `datos_tarija.json`, `ejecucion.json`, `respuestas/peticiones.json` y `respuestas/respuesta_*_sin_fecha_venta.json`; el envoltorio comprueba lista de estaciones y de productos contra esas respuestas. El JSON o los metadatos, por sí solos, **no constituyen una firma digital expedida por ANH**: se conserva toda la evidencia de la consulta.

Al no estar aprobado el GeoJSON actual, el script **solo guarda `evaluacion_T089.json`** (departamento, incidencias y bloqueo) y finaliza con código 3. No crea CSV urbano ni V2. Si el servidor ANH/DNS falla, devuelve código 2 y no inventa estaciones. No almacena «última recarga» ni convierte el saldo instantáneo de ANH en un estado de origen WhatsApp/Conductores.

## Paso 2. Validar la cobertura cartográfica urbana

Partir de cartografía comprobada y dibujar/revisar realmente el contorno urbano de Tarija, incluyendo huecos y multipolígonos donde corresponda. Solo **Marcelo, tras revisar**, puede cambiar en `datos/mancha_urbana_tarija.geojson`:

- Propiedad raíz `estado_validacion`: `APROBADO`.
- Cada `features[*].properties.revision_equipo`: `APROBADO`.

No se debe declarar aprobado un borrador ni suplantar revisiones. Java y Python comprueban ambas marcas antes del uso productivo o la generación de SQL. En `dev` se puede probar el borrador; en `prod` se requiere cobertura aprobada.

## Paso 3. Reanalizar el snapshot sin repetir la consulta ANH

Cuando **sí** haya un GeoJSON aprobado, ejecutar:

```bash
python herramientas/ejecutar_extraccion_anh.py \
  --snapshot "integraciones/ANH_T115/salida_tarija/FECHA/datos_tarija.json" \
  --geojson datos/mancha_urbana_tarija.geojson
```

Esto generará `surtidores_anh_revision_PENDIENTE.csv`, `revision_urbana_PENDIENTE.json` y una evaluación nueva **sin sobrescribir informes anteriores**. El filtrado usa **coordenadas de ANH** y excluye la zona departamental exterior al polígono; no asigna nombre de prototipo salvo coincidencia exacta, no añade los seis extras, y deja vacíos los apodos sin respaldo.

Franz debe conciliar las 21 filas originales con los IDs ANH, verificar cada ubicación y decidir `INCLUIR` o `EXCLUIR` (con motivo de exclusión). Marcelo debe aprobar cada resolución. La aprobación de ninguno de los dos puede completarla un script. La ausencia de coincidencia exacta exige revisión, no emparejamiento por proximidad o imaginación.

## Paso 4. Generar V2 únicamente con datos aprobados

Una vez actualizado el CSV de revisión y el acta `datos_pendientes/surtidores_revision.csv` con las 21 decisiones, ejecutar:

```bash
python herramientas/generar_migracion_surtidores.py \
  --csv "integraciones/ANH_T115/salida_tarija/FECHA/surtidores_anh_revision_PENDIENTE.csv" \
  --api-json "integraciones/ANH_T115/salida_tarija/FECHA/datos_tarija.json" \
  --acta-prototipo datos_pendientes/surtidores_revision.csv \
  --geojson datos/mancha_urbana_tarija.geojson \
  --salida backend_java/src/main/resources/db/migration/V2__surtidores_revisados.sql
```

El generador **rechaza V2 vacía**, nombres/coordenadas que difieren de ANH, ID ambiguo, ubicación exterior, alias coloquiales sin respaldo, snapshot cambiado, inclusiones sin correspondencia y aprobaciones sin resolver. Nunca sobrescribe V2. Los alias que puede generar son **únicamente las claves normalizadas del nombre oficial ANH**; los alias comunitarios de RN-06 deben conciliarse con el equipo por un flujo autorizado independiente. **La entrega no contiene V2** porque no hay insumos reales y autorizaciones suficientes.

## Paso 5. Integrar T-088 y ejecutar PostgreSQL/PostGIS

T-088 (#40, Jorge) requiere aportar la estructura Spring Boot, el build y el manejador global de errores. Estos archivos no estaban en los ZIP de T-089; no se inventó un `pom.xml` ni se afirmó integración Spring. Mantener el consejo de `05_contratos.md`: 422 para reportes fuera de cobertura (RN-01.6) y `PUNTO_INVALIDO` como resultado del cálculo de ruta por fuera de cobertura (RN-03.3), pendiente de acordar la serialización REST con Marcelo; se validan también las paradas.

Con Maven, JDK 21, Docker/PostGIS y T-088 ya integrados, configurar `DB_PASSWORD` y luego:

```bash
docker compose -f backend_java/compose.yaml up -d
# desde backend_java, una vez integrado el build de T-088:
mvn test
```

Una base vacía deberá tener V1 (original), V1.1 (ajustes) y V2 (estaciones reales aprobadas). **CP-23 no puede aprobarse todavía**; la suite de Testcontainers no se ejecutó en este entorno. Si V1 ya fue aplicada, su checksum queda intacto gracias a V1.1; comprobar antes si existen migraciones posteriores para evitar orden fuera de secuencia. Prohibido editar migraciones aplicadas o usar `repair` para ocultar desajustes.

Pruebas autónomas disponibles sin servidor:

```bash
python -m unittest discover -s herramientas/tests -p 'test*.py' -v
bash herramientas/probar_dominio_java.sh
```

El estado de ramas, commits, PR a `develop`, `Refs #41`, `Closes #41` y `TRAZABILIDAD.md` sigue condicionado por la aprobación de Marcelo y el merge efectivo. El archivo original de trazabilidad se conservó sin modificar; **no se falsificó una fila T-089 inexistente en el ZIP recibido**.

Para ver exactamente qué queda vacío, revisar `datos_pendientes/CAMPOS_SIN_EVIDENCIA.md` y `CORRECCIONES_Y_PRUEBAS_T089.md`.
