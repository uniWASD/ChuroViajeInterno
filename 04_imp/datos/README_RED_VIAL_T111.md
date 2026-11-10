# Datos reproducibles de la red vial — T-111

La cobertura geográfica y el grafo de circulación son artefactos **distintos**:

- `mancha_urbana_tarija.geojson`: polígono operativo oficial de T-089, coordenadas GeoJSON `[longitud, latitud]`, EPSG:4326. No representa un límite municipal ni catastral.
- `red_vial_tarija.json`: grafo **dirigido** obtenido del extracto OpenStreetMap 2026-11-03, formato JSON **v1** (geometrías por tramo en orden `[latitud, longitud]`). Contiene 8.908 nodos y 23.193 tramos. **No está en el repositorio** (D-33): se genera con el script de abajo y se copia aparte al servidor.

SHA-256 esperado de `red_vial_tarija.json` generado en Windows (6.934.403 bytes):

```text
4011ad148a9a7f885f18df88097ba43414487a84e01416458868322842fb83d0
```

`preparar_red.py` termina el archivo con un salto de línea, y en Windows Python lo escribe como CRLF. Si la red se genera en Linux, o si pasa por Git, termina en LF: mide 6.934.402 bytes y su SHA-256 es `e6e8856bdfb66207913bcd4dc7981185b00e197871ead043e9e71e52093f4c64`, el que figura en T-120 (#137). Es la misma red.

El PBF original (aprox. 166 MiB), los CSV de revisión, los entornos virtuales y el JSON de la red (6,93 MB) **no** se incluyen en Git, como pide D-33: cada extracto nuevo de OSM produciría un diff muy grande.

El script reproducible está en `../motor_cpp/preparacion/preparar_red.py`. Su validador está en `../motor_cpp/preparacion/validar_red.py`. El informe de esta ejecución está en `../motor_cpp/preparacion/VERIFICACION_2026-11-03.md`.

Para cargar la red en el motor C++ del `develop` integrado, usar:

```text
motor_nucleo --red /ruta/a/red_vial_tarija.json
```

Al enviar `{"id":1,"operacion":"consultar_estado"}` por `stdin`, el proceso debe responder por `stdout` con `red_cargada:true`, `fecha_extracto:"2026-11-03"`, `nodos:8908` y `tramos:23193`. El mensaje de carga solo debe aparecer en `stderr`.

La copia en `/home/deploy/churoviaje-staging/red_vial_tarija.json` es de **preparación**: transferir allí no modifica automáticamente la configuración de producción ni inicia el motor.

**Origen y licencia de los datos viales:** © colaboradores de OpenStreetMap, ODbL 1.0. Se debe mantener la atribución al distribuir la base de datos derivada. Referencia: https://www.openstreetmap.org/copyright
