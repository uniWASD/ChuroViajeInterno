# T-111 — Verificación de la red vehicular de Tarija (extracto 2026-11-03)

## Alcance y procedencia

Esta entrega utiliza `04_imp/datos/mancha_urbana_tarija.geojson` (cobertura operativa aprobada en T-089) y el extracto de Bolivia de OpenStreetMap fechado el **2026-11-03**. El procesamiento fue ejecutado en Windows el 2026-11-04 con los scripts de `04_imp/motor_cpp/preparacion`.

Las cifras de ejecución y la transferencia al servidor se sustentan en **los registros de PowerShell suministrados por el ejecutor**. No se interpretan como una prueba de carga del proceso C++ ni como validación presencial de la señalización vial.

## Identificación del artefacto

| Propiedad | Valor |
| --- | --- |
| Archivo | `red_vial_tarija.json`, fuera de Git (D-33) |
| Formato | JSON v1, coordenadas `[lat, lon]` |
| Fuente | © colaboradores de OpenStreetMap (ODbL) |
| Fecha de extracto | `2026-11-03` |
| Nodos | **8.908** |
| Tramos dirigidos | **23.193** |
| Bytes | **6.934.403** |
| SHA-256 | `4011ad148a9a7f885f18df88097ba43414487a84e01416458868322842fb83d0` (generado en Windows, con CRLF al final; con LF mide 6.934.402 bytes y es `e6e8856bdfb66207913bcd4dc7981185b00e197871ead043e9e71e52093f4c64`, la misma red) |
| Tramos sin nombre | 10.814 |
| Componentes fuertes | 23, la mayor contiene 8.813 nodos |
| Componentes débiles | 20, la mayor contiene 8.813 nodos |

El tamaño y la huella corresponden a la red generada y validada, no al JSON sintético de `preparacion/ejemplos`.

## Verificaciones observadas

- **73 pruebas aprobadas y 52 subpruebas correctas**, sin fallos en la salida presentada.
- Validación Python de integridad, topología, geometría, longitud y reciprocidad de tramos: **correcta**.
- **CP-45:** Pasaje Baldiviezo (OSM 163684398), cero tramos vehiculares.
- **CP-46:** calle General Trigo (OSM 130844400, 203162484, 397865056, 1414364147), 22 tramos dirigidos y cero contrarios.
- Nueva ejecución forzada con el mismo PBF: resultado de **8.908 nodos y 23.193 tramos**, SHA-256 idéntico.
- Archivo transferido por SCP a `/home/deploy/churoviaje-staging/red_vial_tarija.json`. Comparación SHA-256 local/remoto: **idéntica**.

## Distribución de los tramos dirigidos

| Categoría | Tramos | Longitud dirigida |
| --- | ---: | ---: |
| `via_rapida` | 499 | 48,739 km |
| `avenida_principal` | 3.855 | 315,408 km |
| `zona_residencial` | 18.839 | 1.715,992 km |

## Integración con el núcleo C++

El `develop` público consultado el 2026-11-04 contiene `--red` en `04_imp/motor_cpp/src/main.cpp` y `cargar_red_para_arranque()` en la implementación de `RedVial`. El contrato del núcleo devuelve `red_cargada`, `fecha_extracto`, `nodos` y `tramos` mediante `consultar_estado`. **No sobrescribir estas implementaciones con los archivos anteriores del ZIP de T-111.**

Se comprobó el 10/11/2026 en Linux con el binario de `develop`: `motor_nucleo --red red_vial_tarija.json` respondió a `consultar_estado` con `red_cargada:true`, `fecha_extracto:"2026-11-03"`, 8.908 nodos y 23.193 tramos, y cargó la red en 208 ms.

## Pendientes y límites

1. El CSV `revision_sentidos_centro.csv` incluye **171 grupos de calles de sentido único**. Su señalización real continúa pendiente de verificación presencial.
2. Existen **10.814 tramos** con el nombre `Sin nombre`, relevantes para instrucciones de navegación basadas en nombres de calles.
3. El generador conservador descarta varias etiquetas condicionales. No contempla actualmente todas las variantes, por ejemplo `vehicle:conditional` y `oneway:vehicle:conditional`; debe tratarse como mejora futura antes de regeneraciones con requisitos de circulación por horario.
4. La carga real y la latencia del proceso C++ en ARM, así como la integración con el wrapper Java, necesitan prueba por separado.
5. La red no se versiona (D-33). El archivo del servidor se administra por separado y su ubicación se configura con `RED_VIAL_PATH`.

## Reproducción de las comprobaciones

Desde la raíz del repositorio, con las dependencias Python de `preparacion/requirements.txt` instaladas:

```powershell
py -3 -m pytest -q .\04_imp\motor_cpp\preparacion\tests
py -3 .\04_imp\motor_cpp\preparacion\validar_red.py --red .\04_imp\datos\red_vial_tarija.json --cobertura .\04_imp\datos\mancha_urbana_tarija.geojson
Get-FileHash .\04_imp\datos\red_vial_tarija.json -Algorithm SHA256
```

Para CP-45/CP-46, ejecutar `verificar_casos_reales.py` utilizando el **mismo** PBF fechado 2026-11-03 que generó el JSON.

**Atribución:** © colaboradores de OpenStreetMap, licencia Open Database License (ODbL).
