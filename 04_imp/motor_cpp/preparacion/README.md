# T-111 · Red vial vehicular de Tarija (issue #93)

Prepara un archivo JSON con **nodos y tramos dirigidos** a partir de OpenStreetMap, recortados por `04_imp/datos/mancha_urbana_tarija.geojson`. Cumple RN-03.5 (solo vehículos), RN-03.6 (sentidos) y RN-03.7 (longitudes de recorrido). **No implementa la carga en C++** (T-096). La carga, el formato y la aprobación del contrato son responsabilidades distintas.

## Dependencias y datos

Python 3.10+ y `requirements.txt`: `shapely` para operaciones espaciales y `osmium` (pyosmium) para `.osm.pbf`. El GeoJSON de cobertura procede de T-089 y **debe existir en el repositorio completo**; no se sustituye por un contorno ficticio. Descarga del extracto de Bolivia: [Geofabrik](https://download.geofabrik.de/south-america/bolivia.html). Si recorta previamente un PBF con osmium, use `--strategy complete_ways`; el script realiza el recorte geométrico final.

El extracto `.osm.pbf` y el JSON generado deben guardarse **fuera del repositorio**. Además, `.gitignore` en `04_imp/motor_cpp` evita incluir accidentalmente archivos generados bajo esa carpeta; los datos reales deben permanecer fuera del repositorio.

## Ejecución desde la raíz del repositorio

**Linux / Ubuntu (bash):**

```bash
mkdir -p "$HOME/ChuroViajeDatos"
python3 -m venv "$HOME/ChuroViajeDatos/venv-t111"
source "$HOME/ChuroViajeDatos/venv-t111/bin/activate"
python -m pip install -r 04_imp/motor_cpp/preparacion/requirements.txt
python -m unittest discover -s 04_imp/motor_cpp/preparacion/tests -v

mkdir -p "$HOME/ChuroViajeDatos"
curl -fL https://download.geofabrik.de/south-america/bolivia-latest.osm.pbf \
  -o "$HOME/ChuroViajeDatos/bolivia-latest.osm.pbf"
python 04_imp/motor_cpp/preparacion/preparar_red.py \
  --osm "$HOME/ChuroViajeDatos/bolivia-latest.osm.pbf" \
  --cobertura 04_imp/datos/mancha_urbana_tarija.geojson \
  --salida "$HOME/ChuroViajeDatos/red_vial_tarija.json"
python 04_imp/motor_cpp/preparacion/validar_red.py \
  --red "$HOME/ChuroViajeDatos/red_vial_tarija.json" \
  --cobertura 04_imp/datos/mancha_urbana_tarija.geojson
python 04_imp/motor_cpp/preparacion/verificar_casos_reales.py \
  --osm "$HOME/ChuroViajeDatos/bolivia-latest.osm.pbf" \
  --red "$HOME/ChuroViajeDatos/red_vial_tarija.json" \
  --cobertura 04_imp/datos/mancha_urbana_tarija.geojson
python 04_imp/motor_cpp/preparacion/listar_sentidos_unicos.py \
  --red "$HOME/ChuroViajeDatos/red_vial_tarija.json" \
  --cobertura 04_imp/datos/mancha_urbana_tarija.geojson \
  --salida-csv "$HOME/ChuroViajeDatos/revision_sentidos_centro.csv"
```

**Windows (PowerShell):**

```powershell
$venv = Join-Path $env:TEMP 'churoviaje-t111-venv'
py -3 -m venv $venv
$python = Join-Path $venv 'Scripts\python.exe'
& $python -m pip install -r .\04_imp\motor_cpp\preparacion\requirements.txt
& $python -m unittest discover -s .\04_imp\motor_cpp\preparacion\tests -v

$datos = Join-Path $env:USERPROFILE 'ChuroViajeDatos'
New-Item -ItemType Directory -Path $datos -Force | Out-Null
$osm = Join-Path $datos 'bolivia-latest.osm.pbf'
$red = Join-Path $datos 'red_vial_tarija.json'
Invoke-WebRequest 'https://download.geofabrik.de/south-america/bolivia-latest.osm.pbf' -OutFile $osm
& $python .\04_imp\motor_cpp\preparacion\preparar_red.py --osm $osm --cobertura .\04_imp\datos\mancha_urbana_tarija.geojson --salida $red
& $python .\04_imp\motor_cpp\preparacion\validar_red.py --red $red --cobertura .\04_imp\datos\mancha_urbana_tarija.geojson
& $python .\04_imp\motor_cpp\preparacion\verificar_casos_reales.py --osm $osm --red $red --cobertura .\04_imp\datos\mancha_urbana_tarija.geojson
& $python .\04_imp\motor_cpp\preparacion\listar_sentidos_unicos.py --red $red --cobertura .\04_imp\datos\mancha_urbana_tarija.geojson --salida-csv (Join-Path $datos 'revision_sentidos_centro.csv')
```

Si el extracto no trae fecha, agregue a la preparación `--fecha-extracto AAAA-MM-DD` **con la fecha real del extracto**, no la fecha de ejecución. El script valida un archivo temporal antes de reemplazar la salida: ante un error, conserva el archivo anterior.

## Clasificación `highway` → `tipo_via`

| Tipo exportado | Etiquetas OSM | Banda de velocidad de referencia del diseño (no exportada) |
| --- | --- | --- |
| `via_rapida` | `motorway`, `motorway_link`, `trunk`, `trunk_link` | 50–60 km/h |
| `avenida_principal` | `primary`, `primary_link`, `secondary`, `secondary_link`, `tertiary`, `tertiary_link` | 35–45 km/h |
| `zona_residencial` | `unclassified`, `residential`, `living_street`, `service`, `road` | 15–25 km/h |

La correspondencia exacta entre `highway` y tipo es **una decisión de T-111**; las bandas proceden del modelo y la investigación de velocidades. El núcleo asigna las velocidades y el nivel de tráfico, no este script. Se excluyen vías peatonales, ciclistas, áreas, accesos restringidos, vías con restricciones/sentidos condicionales y sentidos no interpretables. La prioridad de acceso es `motorcar` → `motor_vehicle` → `vehicle` → `access`. El programa presenta las causas de exclusión y los kilómetros dirigidos por tipo para facilitar el control.

## Contrato JSON v1

- Archivo UTF-8 con `version=1`, `fuente="OpenStreetMap"`, `fecha_extracto`, `nodos` y `tramos`.
- Nodo: `id`, `lat`, `lon` (WGS84). Tramo: `id`, `desde`, `hasta`, `opuesto`, `nombre`, `tipo_via`, `longitud_m`, `geometria`.
- Cada par de `geometria` se guarda en orden **`[latitud, longitud]`**; **solo el GeoJSON de cobertura** usa su orden habitual `[longitud, latitud]`.
- Una vía bidireccional produce dos tramos, con `opuesto` apuntando recíprocamente a la otra dirección. Un tramo unidireccional lleva `"opuesto": null`.
- La longitud se calcula sobre el recorrido completo con Haversine y se redondea hacia arriba al milímetro; los extremos de la geometría coinciden con sus nodos.
- Los IDs de tramo solo son estables **dentro de un extracto**; un nuevo extracto puede cambiar los fragmentos.

La especificación ampliada se propone para `03_des/02_Arquitectura/05_contratos.md`, sección **«Archivo de la red vial»**, mediante D-33 en `08_decisiones_parametros.md`. Hasta que se incorporen estos cambios y Marcelo apruebe el contrato, la conformidad documental se considera **pendiente**. Este paquete está limitado a `04_imp/motor_cpp` y **no modifica** los documentos de arquitectura en `03_des`: la incorporación y aprobación de D-33 deben tramitarse por separado.

El archivo de ejemplo `ejemplos/red_vial_ejemplo.json` es **sintético** y se valida en las pruebas automáticas. No contiene vías reales de Tarija.

## Validaciones y límites

`validar_red.py` verifica IDs y referencias, cobertura, geometrías, longitud, tipos, fecha y reciprocidad de `opuesto`; además **informa**, sin rechazar, tamaños de las componentes fuertemente y débilmente conexas. `verificar_casos_reales.py` verifica CP-45 (Pasaje Baldiviezo) y CP-46 (General Trigo) **contra el mismo extracto OSM usado para generar el archivo**. La hoja CSV lista calles de sentido único dentro de 1500 m de la Plaza Luis de Fuentes y deja la verificación presencial como `PENDIENTE`.

Las pruebas sin datos reales no certifican señalización vial, rendimiento de carga del núcleo C++ ni ejecución en Ubuntu ARM. Consulte `VERIFICACION.md` para la evidencia y las limitaciones observadas.

**Datos**: © colaboradores de OpenStreetMap, licencia ODbL; [atribución y condiciones](https://www.openstreetmap.org/copyright).
