# Núcleo C++ del motor core

Programa que mantiene la red vial y calcula rutas ([D-15](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). No tiene servidor web ni abre puertos: el backend lo arranca como proceso hijo a través del wrapper Java `MotorDeRutas` y le habla por pipe. El contrato completo está en [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md#backend-con-el-motor-core).

**Estado (T-090, issue #42):** estructura y protocolo. `consultar_estado` responde de verdad; las otras cuatro operaciones responden «no implementada» hasta que lleguen sus issues. La red vial todavía no se carga (issue T-111), así que `consultar_estado` informa `"red_cargada":false`.

## Protocolo

- **Entrada estándar:** una petición por línea, en JSON. Lleva `id` (entero o texto) y `operacion`.
- **Salida estándar:** una respuesta por línea, en JSON, con el mismo `id`. No sale nada más por aquí.
- **Salida de errores:** registros para el log del backend. Nunca repiten el contenido de una petición.
- **Cierre:** al cerrarse la entrada estándar, el núcleo termina solo con código 0.
- Las líneas en blanco se ignoran (no generan respuesta) y se acepta `\r\n`.

Operaciones del contrato: `consultar_estado`, `calcular_ruta`, `revisar_ruta`, `actualizar_trafico`, `analizar_telemetria`.

```json
{"id":1,"operacion":"consultar_estado"}
{"id":1,"ok":true,"red_cargada":false,"fecha_extracto":null}
```

### Respuestas con `"ok":false`

Traen `error` con un `codigo` y una `descripcion` en español. El núcleo sigue funcionando después de cualquiera de ellas.

| Código | Cuándo | `id` de la respuesta |
| --- | --- | --- |
| `JSON_INVALIDO` | La línea no es JSON | `null` |
| `PETICION_INVALIDA` | Es JSON, pero no es un objeto, o falta `id` u `operacion`, o tienen un tipo incorrecto | el de la petición si se pudo leer; si no, `null` |
| `OPERACION_DESCONOCIDA` | La `operacion` no existe en el contrato | el de la petición |
| `NO_IMPLEMENTADA` | La operación es del contrato pero aún no se programó | el de la petición |
| `ERROR_INTERNO` | Fallo inesperado del núcleo | el de la petición si se conocía |

Que no haya ruta (`SIN_RUTA`, `PUNTO_INVALIDO`) **no** es un error: irá como `"ok":true` cuando exista `calcular_ruta`.

## Requisitos

- CMake 3.16 o superior y un compilador de C++17 (g++, clang o Visual Studio / MSVC).
- Python 3 (opcional): solo para el script de mensajes y la prueba de extremo a extremo.
- Conexión a internet la primera vez que se configura: CMake descarga nlohmann/json 3.11.3 y GoogleTest 1.14.0 (con su huella SHA-256). Si ya están instaladas en el sistema (`find_package`), usa esas.

## Compilar

Desde esta carpeta (`04_imp/motor_cpp`):

```
cmake -S . -B build
cmake --build build
```

- **Linux x86 y ARM** (por ejemplo la instancia `churoviaje-server`, Ampere A1): el mismo comando. No hay opciones específicas de procesador.
- **Windows:** el mismo comando con Visual Studio (el ejecutable queda en `build\Release\motor_nucleo.exe` si se agrega `--config Release` al segundo comando) o con MinGW/Ninja (`build\motor_nucleo.exe`).
- Para compilar sin pruebas (por ejemplo en el servidor): `cmake -S . -B build -DBUILD_TESTING=OFF`.

## Probar

Un solo comando compila lo que falte y corre todas las pruebas (GoogleTest y la prueba por pipe):

```
cmake --build build --target pruebas
```

También sirve `ctest --test-dir build --output-on-failure` (en Visual Studio agrega `-C Release`).

Las pruebas cubren, entre otras, **CP-24** (`consultar_estado` responde en una sola línea con el mismo `id`) y **CP-25** (una línea que no es JSON responde `"ok":false` y la siguiente petición válida se atiende normal).

## Mandar un mensaje a mano

Linux, macOS o Git Bash:

```
echo '{"id":1,"operacion":"consultar_estado"}' | ./build/motor_nucleo
```

PowerShell:

```
'{"id":1,"operacion":"consultar_estado"}' | .\build\Release\motor_nucleo.exe
```

Para una conversación completa, el script arranca el núcleo por pipe como lo hará el wrapper, manda mensajes de ejemplo (uno malformado, uno desconocido, uno no implementado) e imprime cada respuesta:

```
python scripts/enviar_mensajes.py --nucleo build/motor_nucleo
python scripts/enviar_mensajes.py --nucleo build/motor_nucleo -m '{"id":1,"operacion":"consultar_estado"}'
```

Para escribir mensajes a mano uno por uno, ejecuta `./build/motor_nucleo` y escribe en la terminal; `Ctrl+D` (Linux) o `Ctrl+Z` y Enter (Windows) cierra la entrada y el núcleo termina.

## Estructura

| Archivo | Contenido |
| --- | --- |
| `src/nucleo.hpp/.cpp` | `Nucleo::atender`: una línea de JSON entra, una respuesta sale; validación y despacho por `operacion` |
| `src/bucle.hpp/.cpp` | Bucle de lectura y escritura; separa respuestas (salida estándar) de registros (salida de errores) |
| `src/main.cpp` | Punto de entrada que arranca el wrapper |
| `tests/` | Pruebas de GoogleTest |
| `scripts/enviar_mensajes.py` | Mensajes de prueba por pipe |

Las operaciones nuevas se agregan en `Nucleo::atender` (`src/nucleo.cpp`): quitar el nombre de `kOperacionesPendientes` y añadir su rama junto a `consultar_estado`.
