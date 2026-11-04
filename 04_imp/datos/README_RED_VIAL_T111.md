# Datos reproducibles de la red vial — T-111

La cobertura geográfica y el grafo de circulación son artefactos **distintos**:

- `mancha_urbana_tarija.geojson`: polígono operativo oficial de T-089, coordenadas GeoJSON `[longitud, latitud]`, EPSG:4326. No representa un límite municipal ni catastral.
- `red_vial_tarija.json`: grafo **dirigido** obtenido del extracto OpenStreetMap 2026-11-03, formato JSON **v1** (geometrías por tramo en orden `[latitud, longitud]`). Contiene 8.908 nodos y 23.193 tramos.

SHA-256 esperado de `red_vial_tarija.json`:

```text
4011ad148a9a7f885f18df88097ba43414487a84e01416458868322842fb83d0
```

El PBF original (aprox. 166 MiB), los CSV de revisión y los entornos virtuales **no** se incluyen en Git. El JSON de 6,93 MB sí se versiona en esta rama **como instantánea para T-111, por petición expresa**, pese al criterio anterior de D-33 de guardarlo externamente. Esta excepción debe revisarse con el equipo para futuros extractos: los cambios diarios de OSM pueden producir diffs voluminosos.

El script reproducible está en `../motor_cpp/preparacion/preparar_red.py`. Su validador está en `../motor_cpp/preparacion/validar_red.py`. El informe de esta ejecución está en `../motor_cpp/preparacion/VERIFICACION_2026-11-03.md`.

Para cargar la red en el motor C++ del `develop` integrado, usar:

```text
motor_nucleo --red /ruta/a/red_vial_tarija.json
```

Al enviar `{"id":1,"operacion":"consultar_estado"}` por `stdin`, el proceso debe responder por `stdout` con `red_cargada:true`, `fecha_extracto:"2026-11-03"`, `nodos:8908` y `tramos:23193`. El mensaje de carga solo debe aparecer en `stderr`.

La copia en `/home/deploy/churoviaje-staging/red_vial_tarija.json` es de **preparación**: transferir allí no modifica automáticamente la configuración de producción ni inicia el motor.

**Origen y licencia de los datos viales:** © colaboradores de OpenStreetMap, ODbL 1.0. Se debe mantener la atribución al distribuir la base de datos derivada. Referencia: https://www.openstreetmap.org/copyright
