# T-111 — Preparación de la red vial de Tarija

Este directorio resuelve la preparación del grafo vehicular del issue #93. Lee un extracto de OpenStreetMap, recorta exactamente con `04_imp/datos/mancha_urbana_tarija.geojson`, separa los recorridos dirigidos y exporta nodos y tramos. No modifica el núcleo C++ ni implementa su carga: ese trabajo corresponde a T-096.

## Requisitos

- Python 3.10 o posterior.
- `shapely` para recortar las líneas por el polígono, incluidos bordes y huecos.
- `osmium` (pyosmium) para leer archivos `.osm.pbf` de OpenStreetMap.
- El polígono `04_imp/datos/mancha_urbana_tarija.geojson` ya existente en `develop` (dependencia de T-089). Este ZIP de T-111 no duplica ni sobrescribe ese archivo.
- Un extracto **real** de OpenStreetMap que incluya las coordenadas de los nodos de sus vías. No se incluye un extracto ficticio ni se inventan calles de Tarija.

Para descargar el extracto de Bolivia puede utilizarse [Geofabrik](https://download.geofabrik.de/south-america/bolivia.html), que publica `bolivia-latest.osm.pbf`. El extracto se procesa directamente; el script descarta las vías fuera de la caja del polígono antes de construir la red. Si se prefiere recortar primero con Osmium, hay que conservar los nodos de las vías completas (`osmium extract --strategy complete_ways`). La intersección final con el polígono siempre la realiza el script.

## Tabla `highway` → tipo de vía

| Tipo exportado | Etiquetas OpenStreetMap admitidas | Velocidad esperada de referencia del diseño |
| --- | --- | --- |
| `via_rapida` | `motorway`, `motorway_link`, `trunk`, `trunk_link` | 50–60 km/h |
| `avenida_principal` | `primary`, `primary_link`, `secondary`, `secondary_link`, `tertiary`, `tertiary_link` | 35–45 km/h |
| `zona_residencial` | `unclassified`, `residential`, `living_street`, `service`, `road` | 15–25 km/h |

Las bandas de velocidad se contrastaron directamente con `05_pro/CalculoVelocidad/CU-04_CalculoVelocidad.md`, apartado 1.1: vías rápidas, 50–60 km/h; avenidas principales, 35–45 km/h; zonas residenciales, 15–25 km/h. Coinciden con `03_des/02_Arquitectura/03_modelo_dominio.md`. El archivo `05_pro/CalculoVelocidad/Analisis_Trafico.java` calcula distancia, velocidad, azimut y retraso, pero **no contiene una correspondencia entre etiquetas `highway` y tipos de vía**. Por tanto, la tabla anterior es una propuesta de clasificación de T-111 basada en la jerarquía OSM, no una equivalencia definida por el prototipo. No se asignan velocidades individuales a los tramos ni se implementa el cálculo del tráfico en esta tarea.

Se excluyen vías peatonales (`pedestrian`, `footway`, `steps`), ciclovías (`cycleway`), senderos (`path`, `track`), vías exclusivas de autobús, áreas, vías en construcción, accesos privados o no permitidos para automóviles, accesos limitados a destinos/clientes/reparto y pasillos de estacionamiento y entradas privadas. Las restricciones de automóvil tienen precedencia sobre las restricciones genéricas (`motorcar` → `motor_vehicle` → `vehicle` → `access`). Se excluyen conservadoramente las vías con restricciones o sentidos condicionales y las etiquetas de sentido desconocidas, porque no pueden modelarse fielmente como uno o dos tramos permanentes. Esas vías requieren revisión de campo antes de decidir una incorporación.

## Sentido y formación de tramos

- `oneway=yes`, `1`, `true`: un solo tramo en el orden de los nodos OSM.
- `oneway=-1`, `reverse`: un solo tramo invertido y con geometría invertida.
- `oneway=no`, `0`, `false`: dos tramos, uno por cada dirección.
- Sin `oneway`: dos tramos. `junction=roundabout`, `motorway` y `motorway_link` implican sentido único salvo que exista una etiqueta explícita contraria.
- Las etiquetas `oneway:motorcar`, `oneway:motor_vehicle` y `oneway:vehicle` prevalecen sobre `oneway` en ese orden.
- Cada vía se divide por nodos OSM compartidos con otras vías elegibles, nodos repetidos de la misma vía, extremos de la vía y puntos donde la cobertura corta la geometría. Un cruce gráfico sin nodo OSM compartido **no** se convierte artificialmente en una intersección.
- Un tramo conserva el orden de todos los vértices de su recorrido. La longitud en metros suma distancias geodésicas aproximadas entre vértices consecutivos (Haversine, radio 6 371 008,8 m); se redondea hacia arriba al milímetro, de manera que no es menor que el recorrido ni la distancia directa entre extremos.

El recorte genera nodos `corte:...` cuando la calle entra o sale del polígono. Esos extremos **no** conectan calles distintas solo porque ambas toquen el mismo borde; esta decisión evita giros artificiales y rutas que salgan de la zona de cobertura. Para la geometría se usa WGS84, pares `[longitud, latitud]`.

## Formato JSON para revisión con Marcelo

**Contrato de archivo propuesto, versión 1, pendiente de aprobación por Marcelo.** T-111 entrega el formato definido y documentado; no afirma que ya haya sido acordado fuera del repositorio.

```json
{
  "version": 1,
  "fuente": "OpenStreetMap",
  "fecha_extracto": "2026-10-20",
  "nodos": [
    {"id": "osm:100", "lon": -64.72, "lat": -21.53},
    {"id": "osm:101", "lon": -64.721, "lat": -21.531}
  ],
  "tramos": [
    {
      "id": "osm:500:1:ida",
      "desde": "osm:100",
      "hasta": "osm:101",
      "nombre": "Ejemplo de formato",
      "tipo_via": "zona_residencial",
      "longitud_m": 151.867,
      "geometria": [[-64.72, -21.53], [-64.721, -21.531]]
    }
  ]
}
```

El bloque anterior **solo ilustra el esquema**; los identificadores, nombres y distancias del ejemplo no representan datos reales ni un archivo de salida. Al ejecutar el script, se generan los valores reales y su longitud calculada. El `id` del nodo es `osm:<id>` si procede de OSM y `corte:...` si lo introduce el recorte. El `id` del tramo es `osm:<id_de_via>:<número_de_fragmento>:ida|vuelta`. Los campos `desde` y `hasta` son referencias a IDs de `nodos`; la secuencia `geometria` va orientada de `desde` a `hasta`. La fecha es ISO 8601 `AAAA-MM-DD`, obtenida de los metadatos del extracto o declarada con `--fecha-extracto`. Nunca se sustituye por la fecha de ejecución.

## Ejecución en PowerShell

El ZIP contiene rutas con prefijo `04_imp/`. **Si ya tiene abierta la carpeta `04_imp`, descomprima el ZIP en su carpeta superior**, para obtener `04_imp/motor_cpp/preparacion` y evitar una carpeta duplicada `04_imp/04_imp`.

Ejecute estos comandos **desde la raíz del repositorio ChuroViajeInterno**, con el paquete de T-111 ya descomprimido y los archivos previos de `04_imp` intactos:

```powershell
$venv = Join-Path $env:TEMP 'churoviaje-t111-venv'
py -3 -m venv $venv
$python = Join-Path $venv 'Scripts\python.exe'
& $python -m pip install -r .\04_imp\motor_cpp\preparacion\requirements.txt
& $python -m unittest discover -s .\04_imp\motor_cpp\preparacion\tests -v
```

Descargue el archivo OSM original a una carpeta **externa** al repositorio:

```powershell
$datos = Join-Path $env:USERPROFILE 'ChuroViajeDatos'
New-Item -ItemType Directory -Force -Path $datos | Out-Null
Invoke-WebRequest -Uri 'https://download.geofabrik.de/south-america/bolivia-latest.osm.pbf' -OutFile (Join-Path $datos 'bolivia-latest.osm.pbf')
$osm = Join-Path $datos 'bolivia-latest.osm.pbf'
$red = Join-Path $datos 'red_vial_tarija.json'
& $python .\04_imp\motor_cpp\preparacion\preparar_red.py --osm $osm --cobertura .\04_imp\datos\mancha_urbana_tarija.geojson --salida $red
& $python .\04_imp\motor_cpp\preparacion\validar_red.py --red $red --cobertura .\04_imp\datos\mancha_urbana_tarija.geojson
```

Si el extracto no contiene fecha en la cabecera, repita el comando de preparación agregando `--fecha-extracto AAAA-MM-DD`, con la fecha real proporcionada por el distribuidor. Para una actualización, descargue el extracto más reciente y vuelva a ejecutar el mismo comando. La salida se valida y reemplaza de forma atómica solo si la preparación terminó correctamente, contiene tramos y supera las comprobaciones de integridad.

## Pruebas de aceptación

- **RN-03.5 / CP-45:** `test_cp45_no_existen_tramos_peatonales` verifica el filtro con una vía OSM sintética. `verificar_casos_reales.py` busca **Pasaje Baldiviezo**, entre las calles Campero y Juan Misael Saracho, en el extracto original. Exige que el `highway` de cada vía identificada sea peatonal y que ninguno de sus IDs OSM aparezca en los tramos dirigidos de salida.
- **RN-03.6 / CP-46:** las pruebas unitarias cubren sentido único, doble, invertido, rotondas y prioridad de etiquetas. En el extracto real, `verificar_casos_reales.py` comprueba **Calle General Trigo** en el centro y verifica que cada fragmento de vía vehicular con dirección única tenga exactamente un tramo orientado en el sentido de OSM, sin su opuesto. Una calle OSM puede dividirse en varios fragmentos por intersecciones; por ello no se exige un único tramo para toda la calle.
- **RN-03.7:** el test de línea curva y el validador comprueban que la distancia de recorrido nunca sea inferior a la línea recta y que coincida con la geometría guardada.
- **Topología y datos:** pruebas de intersecciones, huecos, recortes, nombres, clases, coordenadas, errores, reproducibilidad y fecha del extracto.

Las referencias consultadas para identificar los casos son el portal turístico municipal de Tarija para [Pasaje Baldiviezo](https://tarijaturismo.com/pasaje-baldiviezo/), y las etiquetas OSM publicadas para [Pasaje Baldiviezo (`footway`)](https://bolivia-streets.openalfa.com/streets/pasaje-baldiviezo-provincia-cercado-tarija) y [Calle General Trigo (`oneway=yes`)](https://callejero-bolivia.openalfa.com/calles/calle-general-trigo-provincia-cercado-tarija). Las etiquetas definitivas se leen de **la misma versión del PBF** con la que se preparó el JSON; no se dan por válidas únicamente por las páginas consultadas. El centro se delimita mediante un radio de 1.500 metros alrededor de la Plaza Luis de Fuentes y Vargas, localizada aproximadamente en `[-64.73425, -21.53390]` según [OpenStreetMap](https://mapcarta.com/es/W163307682).

**Comprobación real CP-45 y CP-46:** después de generar la red y ejecutar las pruebas unitarias, ejecute desde la raíz del repositorio:

```powershell
$python = Join-Path $env:TEMP 'churoviaje-t111-venv\Scripts\python.exe'
$datos = Join-Path $env:USERPROFILE 'ChuroViajeDatos'
$osm = Join-Path $datos 'bolivia-latest.osm.pbf'
$red = Join-Path $datos 'red_vial_tarija.json'
$cobertura = '.\04_imp\datos\mancha_urbana_tarija.geojson'
& $python .\04_imp\motor_cpp\preparacion\verificar_casos_reales.py --osm $osm --red $red --cobertura $cobertura
if ($LASTEXITCODE -ne 0) { throw 'No se han acreditado CP-45 y CP-46.' }
```

El verificador comprueba también la integridad y el recorte del JSON, la fecha del extracto cuando figura en la cabecera PBF y los IDs de las vías frente a los tramos exportados. **Termina con error** si no encuentra alguna vía de referencia, si han cambiado sus etiquetas, si se ha mezclado un PBF de otra fecha o si la salida contiene una dirección prohibida. No modifica los archivos. Un resultado correcto presenta los IDs reales OSM de cada caso y el número de fragmentos dirigidos válidos.

Las pruebas unitarias utilizan geometrías de prueba; ejecutar `verificar_casos_reales.py` contra el PBF y JSON originales es el paso que valida los casos geográficos. Esa verificación automática no certifica una señalización vial física distinta de lo cartografiado en OSM. La consulta presencial a conductores y la aprobación personal del contrato JSON quedan fuera de esta verificación por decisión del responsable de la tarea.

## Evidencia de ejecución y contraste vial externo

### Resultados reproducidos en la máquina de integración

En PowerShell, con el polígono de `04_imp/datos` y el extracto real de Bolivia con fecha OSM **2026-10-28**, se registraron los siguientes resultados. Estas cifras corresponden a ese extracto específico y pueden cambiar al descargar una versión posterior de OpenStreetMap:

| Comprobación | Resultado observado |
| --- | --- |
| Pruebas automatizadas (`unittest`) | **53/53 correctas** (`OK`; sin omisiones) |
| Generación de red de Tarija | **8.908 nodos** y **23.193 tramos dirigidos** |
| `validar_red.py` con GeoJSON original | **Validación correcta** |
| CP-45, Pasaje Baldiviezo | Vía OSM **163684398**; **0 tramos vehiculares** |
| CP-46, Calle General Trigo | Vías OSM **130844400, 203162484, 397865056, 1414364147**; **22 tramos dirigidos, 0 contrarios** |

Los 22 tramos de CP-46 provienen de cuatro vías OSM que se fragmentan en intersecciones y límites de cobertura. **No equivalen a 22 calles distintas**: cada fragmento permitido se representa con un tramo dirigido y sin el opuesto. Los tests automáticos generan también casos sintéticos para comprobar el comportamiento ante datos inválidos. La prueba con PBF y JSON reales se ejecuta por separado con `verificar_casos_reales.py`.

### Revisión documental de sentidos únicos del centro de Tarija

La clasificación de **General Trigo** como vía de sentido único en sectores del centro cuenta con respaldo *independiente de OpenStreetMap*:

1. **Copas Terán, Paola Cecilia (2024), _Análisis de los efectos de congestionamiento de vehículos livianos en la Ciudad de Tarija_, Universidad Autónoma Juan Misael Saracho.** En el capítulo III, figuras **3.6** (página impresa 41, General Trigo entre 15 de Abril y La Madrid) y **3.7** (página impresa 42, General Trigo entre Virginio Lema y 15 de Abril), el trabajo registra accesos «en un solo sentido». Fuente consultable: <https://biblioteca.uajms.edu.bo/biblioteca/opac_css/doc_num.php?explnum_id=41555>.
2. **UAJMS, estudio de geometría e intersecciones viales de Tarija.** La **tabla 3.2**, página impresa **92**, clasifica la calle General Trigo como **«Un sentido»** en su intersección con Cochabamba y la calle Cochabamba como «Dos sentidos». Fuente: <https://biblioteca.uajms.edu.bo/biblioteca/opac_css/doc_num.php?explnum_id=40834>.
3. El **Gobierno Autónomo Municipal de Tarija**, en su nota del **28/08/2024**, documenta medidas **temporales** de peatonalización en otras calles del centro. No se utilizaron esos cierres puntuales para modificar permanentemente el grafo: <https://noticias.tarija.bo/index.php/2024/08/28/alcaldia-de-tarija-avanza-con-el-plan-de-peatonalizacion/>.

**Alcance de la corroboración:** estos estudios sustentan la condición unidireccional de los sectores mencionados en el momento del levantamiento; no acreditan por sí mismos el sentido cardinal vigente de cada cuadra en octubre de 2026. La dirección concreta aplicada a los tramos se toma de las etiquetas `oneway` del PBF fechado 2026-10-28 y se contrasta automáticamente con el JSON generado. Una revisión presencial de señalización aportaría una validación adicional, pero no se afirma haberla realizado.

### Integración de la tarea con el repositorio

El ZIP de entrega contiene exclusivamente los **siete archivos nuevos** de `04_imp/motor_cpp/preparacion/`. El polígono requerido ya forma parte de `develop`, como dependencia de T-089; el JSON generado y el PBF se mantienen fuera del repositorio. No se incluyen artefactos de prueba, archivos ZIP anteriores, binarios, `__pycache__` ni cambios en C++ o Java.

El contrato JSON está documentado para su aprobación: el **acuerdo con Marcelo todavía requiere confirmación**, por lo que no se declara concluida esa gestión. La creación de `feature/T111-red-vial-preparacion` a partir de `develop` y su publicación corresponden al procedimiento de entrega y no forman parte de este ZIP.

## Alcance y atribución

Se entregan únicamente preparación, validación, dependencias, pruebas y esta documentación. No se modifican backend, C++, motor de cálculo ni esquemas de base de datos. La carga del JSON en C++ forma parte de T-096.

Los datos provienen de © colaboradores de OpenStreetMap, bajo ODbL. Fuente: <https://www.openstreetmap.org/copyright>. Al distribuir el archivo derivado deberán respetarse las obligaciones de atribución y licencia aplicables.
