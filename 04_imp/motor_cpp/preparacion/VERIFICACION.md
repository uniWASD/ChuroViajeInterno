# Verificación técnica — T-111 / issue #93

## Alcance y evidencia disponible

Se verificó la preparación de la red vial de Tarija con un extracto real de
OpenStreetMap y el polígono `04_imp/datos/mancha_urbana_tarija.geojson` del
repositorio. Los siguientes resultados se transcriben de la **salida de
PowerShell facilitada por el ejecutor**; no se incluyen en Git ni el PBF
original, ni el JSON generado, ni los archivos CSV de trabajo.

**Entorno informado:** Windows, Python 3.13.6, Shapely 2.2.0,
pyosmium 4.3.2 y pytest 9.2.0. **Fecha del extracto:** 2026-10-29.

| Comprobación | Resultado observado |
| --- | --- |
| `pytest -v` con polígono oficial | **73 passed, 52 subtests passed**, 0 fallos, 0 omisiones (3,82 s) |
| `unittest` anterior con el paquete inicial, sin polígono | 71 correctas, 2 omitidas; omitidas resueltas al incorporar el GeoJSON |
| Cobertura registrada en la ejecución anterior | 76 % combinado (antes de incorporar el GeoJSON; no recalculada después) |
| Preparación y validación de PBF real | **8.908 nodos y 23.193 tramos dirigidos** |
| Tamaño del archivo JSON | **6.934.351 bytes** |
| Tiempo de preparación y validación | **61,018 s** en el equipo del ejecutor; no mide carga C++ |
| Validación geométrica y topológica | Correcta |
| CP-45: Pasaje Baldiviezo | Vía OSM 163684398; **0 tramos vehiculares** |
| CP-46: Calle General Trigo | Vías OSM 130844400, 203162484, 397865056 y 1414364147; **22 tramos dirigidos y 0 contrarios** |
| Revisión de sentidos únicos | CSV generado con **171 grupos**; verificación presencial pendiente |

### Resumen de red dirigido

| Clase de vía | Tramos dirigidos | Longitud dirigida (km) |
| --- | ---: | ---: |
| `via_rapida` | 499 | 48,739 |
| `avenida_principal` | 3.855 | 315,408 |
| `zona_residencial` | 18.839 | 1.715,992 |

De los 8.908 nodos, 8.813 pertenecen a la mayor componente fuertemente
conexa (98,93 %). La validación informó **23 componentes fuertemente conexas**
y **20 componentes débilmente conexas**; esos valores son informativos y no
constituyen automáticamente errores. Los tramos con nombre ausente son 10.814.

## Pruebas funcionales

La suite incluye filtrado de caminos peatonales y accesos restringidos,
orientación de calles, recorte espacial, cálculo de longitudes, preservación de
tramos bidireccionales mediante `opuesto`, integridad del JSON, tratamiento de
entradas erróneas, generación de CSV, conectividad y CLI completo. El ejemplo
sintético `ejemplos/red_vial_ejemplo.json` se valida sin depender de
`documentacion/` ni de otros archivos fuera de `04_imp/motor_cpp`.

Desde la **raíz del repositorio**, tras instalar `requirements.txt`:

```powershell
py -3 -m pytest -v .\04_imp\motor_cpp\preparacion\tests
```

Las pruebas que dependen de `04_imp/datos/mancha_urbana_tarija.geojson` solo
pueden ejecutarse con el archivo oficial presente. El resto trabaja con datos
sintéticos. Para reproducir la integración con OSM real, siga el README y use
el **mismo PBF** en preparación y CP-45/CP-46.

## Pendientes fuera de estas pruebas

- La lista CSV de sentidos únicos requiere comprobación presencial o una
  validación adicional de señalización vigente: OSM no acredita esa señalización.
- La integración del contrato JSON v1 y de D-33 en `develop` queda sujeta al PR; Marcelo confirmó D-33 el 31/10/2026.
- La carga en menos de 10 segundos, `--red` y el despliegue en Ubuntu ARM
  corresponden a T-096/T-098 y **no se certifican con estas pruebas de Python**.

**Atribución:** © colaboradores de OpenStreetMap (ODbL).
