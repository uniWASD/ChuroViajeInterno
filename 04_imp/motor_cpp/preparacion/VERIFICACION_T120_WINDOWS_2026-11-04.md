# T-120 / issue #129 — Verificación en Windows

**Fecha de la ejecución:** 04/11/2026. **Entorno:** PowerShell de Windows, Visual Studio 18 Build Tools/MSVC, CMake, JDK 21, Maven 3.9.11 y Python 3.13.5. **Fuente:** salida de PowerShell y evidencia `rutas_y_100_pares.json` facilitadas por el responsable de pruebas. No se modificó el algoritmo productivo del motor.

## Resultados

| Ensayo | Resultado |
| --- | --- |
| Validación del grafo OSM | 8.908 nodos y 23.193 tramos; fecha del extracto 2026-11-03; SHA-256 `e6e8856bdfb66207913bcd4dc7981185b00e197871ead043e9e71e52093f4c64` (ver la nota de abajo) |
| Auditoría independiente del grafo | 872 comprobaciones, cero errores; 98 de 100 pares aleatorios tienen camino |
| Python `pytest` | 73 pruebas aprobadas, 52 subpruebas aprobadas |
| C++ CMake/MSVC | Compilación correcta de `motor_nucleo.exe` y `pruebas_nucleo.exe` |
| CTest | **106/106 aprobadas**, sin fallos |
| GoogleTest T-120 | Pruebas `RutasReferencia.*`: **4 aprobadas**, incluidos CP-167 y CP-168 |
| Ejecutable C++ real con red OSM por pipe JSON | **6/6 rutas** correctas, una por viaje de referencia |
| Java `RutasReferenciaNucleoRealTest` | **1 prueba aprobada**, sin omisiones, con `MOTOR_PATH` y `RED_VIAL_REAL` |
| Java pruebas complementarias `MotorDeRutasTest`, `MotorDeRutasNucleoRealTest`, `RutaConNucleoRealTest` | **14 pruebas aprobadas**, sin omisiones; Maven `BUILD SUCCESS` |

### Distancias verificadas

| Viaje | Aproximada (m) | Motor / grafo (m) | Desvío |
| --- | ---: | ---: | ---: |
| Plaza Luis de Fuentes → ex terminal | 1.500 | 1.523,802 | 1,587 % |
| Plaza Luis de Fuentes → UAJMS | 1.950 | 1.974,091 | 1,235 % |
| Mercado Bolívar → Caja Nacional de Salud | 1.400 | 1.419,568 | 1,398 % |
| Caja Nacional de Salud → Mercado Bolívar | 1.600 | 1.586,424 | 0,849 % |
| Plaza Luis de Fuentes → Tomatitas | 6.100 | 6.091,800 | 0,134 % |
| San Luis → San Bernardo, con ex terminal | 7.650 | 7.648,959 | 0,014 % |

Todos cumplen la tolerancia de **±15 %** y las calles esperadas en orden. El viaje inverso no utiliza Calle Bolívar en sentido prohibido por el grafo.

## Criterio de inspección física y limitaciones

Marcelo aceptó que la revisión de sentidos y calles con OpenStreetMap reemplace la de un conductor para T-120. No hubo revisión en la calle, así que esto no certifica la señalización real. El cruce del Guadalquivir se toma del itinerario; no se comprobó contra el cauce.

El mock JSON de `POST /rutas` fue comparado estructuralmente con el primer itinerario y con el grafo. **No se realizó una llamada HTTP a un backend en producción ni se ejecutó Flutter**, por no ser necesarios para la validación local acordada. No se realizaron pruebas de 72 horas ni operaciones sobre el servidor.

## SHA-256 de la red

El `e6e8856...` de arriba y el `4011ad1...` de T-111 (#136) son la misma red. `preparar_red.py` termina el archivo con un salto de línea, y en Windows Python lo escribe como CRLF: así sale `4011ad1...` (6.934.403 bytes). Con LF, como queda en Linux o al pasar por Git, sale `e6e8856...` (6.934.402 bytes). El contenido JSON es idéntico.

## D-33: distribución de la red

El archivo real `04_imp/datos/red_vial_tarija.json` **no debe subirse a Git**. Se utiliza localmente para pruebas mediante la variable de entorno `RED_VIAL_REAL`. La presencia de ese archivo se comprueba antes de ejecutar las pruebas dependientes, que se omiten legítimamente sin él.

## Reproducción mínima (PowerShell, en un clon del repositorio)

```powershell
$Repo = (Get-Location).Path
$Red = "$HOME\Downloads\ChuroViaje_T120_AUDITADO_2026-11-04\04_imp\datos\red_vial_tarija.json"
$Build = Join-Path $Repo '04_imp\motor_cpp\build-t120'
cmake -S (Join-Path $Repo '04_imp\motor_cpp') -B $Build
cmake --build $Build --config Release --parallel 1
$env:RED_VIAL_REAL = $Red
ctest --test-dir $Build -C Release --output-on-failure
$env:MOTOR_PATH = Join-Path $Build 'Release\motor_nucleo.exe'
Push-Location (Join-Path $Repo '04_imp\backend_java')
try { mvn '-Dtest=RutasReferenciaNucleoRealTest' test } finally { Pop-Location }
```

El comando usa el grafo local del paquete auditado; no es parte del PR. En CI sin grafo, no debe interpretarse una omisión como aprobación real de CP-167/CP-168.
