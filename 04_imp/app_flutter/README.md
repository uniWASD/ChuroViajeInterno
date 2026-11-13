# ChuroViaje — Aplicación Flutter

Aplicación móvil del proyecto ChuroViaje, desarrollada con Flutter y Dart. Esta aplicación constituye la estructura base para incorporar las funcionalidades de mapas, eventos y surtidores.

## Requisitos

* Flutter SDK **3.47.2** (Dart 3.13.2).
* Android Studio o un dispositivo Android para ejecutar la aplicación.
* Un emulador Android o dispositivo físico para probar la conexión con el backend.
* Un token público de Mapbox (empieza con `pk.`) para ver el mapa base. No va en el repositorio.

## Estructura del proyecto

```text
.
├── lib/
│   ├── app_providers.dart
│   ├── main.dart
│   ├── config/
│   │   └── app_config.dart
│   ├── controllers/
│   │   ├── map_controller.dart
│   │   ├── navigation_controller.dart
│   │   └── ruta_controller.dart
│   ├── services/
│   │   ├── api/
│   │   │   └── api_client.dart
│   │   ├── rutas/
│   │   │   └── servicio_rutas.dart
│   │   └── ubicacion/
│   │       └── servicio_ubicacion.dart
│   ├── models/
│   │   ├── coordenada.dart
│   │   └── ruta.dart
│   ├── storage/
│   │   └── .gitkeep
│   └── views/
│       ├── main/
│       │   └── main_screen.dart
│       ├── map/
│       │   ├── map_screen.dart
│       │   ├── mapa_base.dart
│       │   └── mapa_mapbox.dart
│       ├── ruta/
│       │   └── como_llegar_screen.dart
│       ├── events/
│       │   └── events_screen.dart
│       └── stations/
│           └── stations_screen.dart
└── test/
    ├── dobles/
    │   └── dobles_mapa.dart
    ├── widget_test.dart
    ├── api_client_test.dart
    ├── como_llegar_screen_test.dart
    ├── map_screen_test.dart
    ├── ruta_controller_test.dart
    ├── ruta_test.dart
    └── ubicacion_test.dart
```

### Responsabilidades

* **Views:** muestran la interfaz y reciben las interacciones del usuario. No realizan peticiones HTTP directamente.
* **Controllers:** gestionan el estado y convierten interacciones en peticiones. No aplican reglas de negocio, el backend vuelve a validar todas las reglas y los controladores no se llaman entre sí (ver `03_des/02_Arquitectura/02_mvc.md`, reglas 2 y 4).
* **Services:** contienen la comunicación con servicios externos. `ApiClient` centraliza las peticiones HTTP.
* **Config:** define el entorno y la dirección base del backend.
* **Tests:** verifican el comportamiento de la interfaz y del cliente HTTP.

## Gestión de estado

Se utiliza **Provider**, con `ChangeNotifier` para notificar cambios de estado a la interfaz.

El `NavigationController` administra la pestaña seleccionada en la navegación principal.

## Navegación principal

La aplicación dispone de una barra de navegación inferior con tres pantallas:

* Mapa.
* Eventos.
* Surtidores.

Las pantallas son estructuras iniciales en español, preparadas para incorporar sus funcionalidades posteriormente.

## Configuración de entornos

La dirección del backend se configura mediante argumentos `--dart-define`, sin modificar el código fuente.

Variables disponibles:

| Variable       | Función                                             |
| -------------- | --------------------------------------------------- |
| `APP_ENV`      | Selecciona `development`, `testing` o `production`. |
| `API_URL_DEV`  | Dirección del backend de desarrollo.                |
| `API_URL_TEST` | Dirección del backend de pruebas.                   |
| `API_URL_PROD` | Dirección del backend de producción.                |
| `MAPBOX_ACCESS_TOKEN` | Token público de Mapbox para el mapa base. Sin él, la vista Mapa muestra el error de mapa no disponible. |
| `RUTA_DE_EJEMPLO` | `false` por defecto: Cómo llegar pide la ruta a `POST /rutas`. Con `true` muestra una ruta de ejemplo, para probar sin backend. En producción siempre llama al backend. |
| `GOOGLE_SERVER_CLIENT_ID` | Client ID **web** del proyecto de Google Cloud. El token de identidad de Google sale para ese Client ID, que es el mismo `GOOGLE_CLIENT_ID` que valida el backend (HU-10). |

### Desarrollo en Android

```bash
flutter run --dart-define=APP_ENV=development --dart-define=API_URL_DEV=http://10.0.2.2:8080
```

`10.0.2.2` permite acceder desde el emulador Android al equipo anfitrión; solo sirve en ese emulador. En el simulador de iOS se usa `localhost`. En un dispositivo físico, normalmente se necesita la dirección IP local del equipo que ejecuta el backend.

### Entorno de pruebas

```bash
flutter run --dart-define=APP_ENV=testing --dart-define=API_URL_TEST=http://10.0.2.2:8080
```

### Producción

Se debe proporcionar la dirección oficial del backend antes de ejecutar la aplicación en producción:

```bash
flutter run --dart-define=APP_ENV=production --dart-define=API_URL_PROD=https://TU-DOMINIO-API
```

Reemplazar `https://TU-DOMINIO-API` por la dirección oficial correspondiente.

## Mapa base y ubicación

La vista Mapa usa el SDK de Mapbox solo para dibujar el mapa (D-08): las rutas las calcula el motor del backend. El token se pasa al ejecutar o compilar:

```bash
flutter run --dart-define=MAPBOX_ACCESS_TOKEN=pk.xxxxx --dart-define=API_URL_DEV=http://10.0.2.2:8080
```

* Las vistas no usan Mapbox directamente: piden un `ConstructorMapa` (en `mapa_base.dart`) y la app les da el de Mapbox (`mapa_mapbox.dart`). Las pruebas les dan un mapa falso, porque el mapa nativo no corre en `flutter test`.
* El mapa abre en la plaza Luis de Fuentes. Tocar un punto lo marca y muestra sus coordenadas; lo usarán el ajuste manual del reporte y la elección de destino.
* La ubicación se pide con `geolocator` al abrir la vista Mapa. Solo se sigue con la app en pantalla: al pasar a segundo plano se cancela y al volver se retoma (RNF-13). No se pide permiso de ubicación en segundo plano.
* Sin permiso o con el GPS apagado, el mapa sigue funcionando y un aviso ofrece dar el permiso o abrir los ajustes (RN-04.3).
* Si el estilo del mapa no carga (sin conexión, token inválido), la vista muestra «No se pudo cargar el mapa. Revisa tu conexión e inténtalo de nuevo.» con un botón para reintentar (RN-03.18). Los mosaicos que fallan sueltos no cuentan como error.
* El logo y la atribución de Mapbox y OpenStreetMap quedan visibles (RNF-19).

## Cómo llegar

El botón «Cómo llegar» de la vista Mapa abre la vista del mismo nombre (CU-03). Si había un punto elegido en el mapa, pasa a ser el destino.

* El origen es la posición actual; con «Marcar» se puede tocar otro punto. Si no hay ubicación ni origen marcado, la vista lo pide antes de llamar al backend.
* El destino y las paradas se eligen tocando el mapa (no hay buscador, P-19 y D-24). Las paradas van en el orden en que se agregan y son 10 como máximo (RN-03.2).
* La app manda los puntos a `POST /rutas` y dibuja lo que vuelve, con su tiempo y su distancia (RN-03.13). Las reglas las aplica el backend.
* «No se encontró una ruta válida» y «Origen o destino inválido» se muestran como respuesta, no como error. Si el backend no responde o responde 503, se muestra el fallo con «Reintentar» (RN-03.15).
* «Iniciar» se ve deshabilitado hasta que exista la vista Navegación activa.

Desde HU-10 la app inicia sesión y pide la ruta al backend. Con `--dart-define=RUTA_DE_EJEMPLO=true` (fuera de producción) la vista usa `assets/datos/ruta_ejemplo_post_rutas.json`: la respuesta del primer viaje de referencia de Tarija (T-120, de la plaza Luis de Fuentes a la ex terminal), copiada de `04_imp/datos`. Es siempre la misma ruta, elija el conductor los puntos que elija, y la vista lo avisa.

## Cliente HTTP

`ApiClient` centraliza las peticiones al backend y proporciona:

* Encabezados para solicitudes JSON.
* Inclusión del token JWT mediante `Authorization: Bearer <token>` cuando existe un token configurado.
* Interpretación de errores del backend con el formato `{ "codigo": "...", "mensaje": "..." }`.
* Conversión de errores de conexión en excepciones `ApiException` con mensajes en español.

Los controladores deben capturar estas excepciones y comunicar el error a la interfaz para que pueda mostrarse al usuario.

## Ejecutar la aplicación

Desde la carpeta `04_imp/app_flutter`:

```bash
flutter pub get
flutter run
```

## Ejecutar las pruebas

Para ejecutar todas las pruebas automatizadas:

```bash
flutter test
```

La prueba de widget **CP-20** simula el backend apagado y comprueba que, al calcular una ruta, se muestra el error de red sin cerrar la pantalla. **CP-105** (la ruta aparece con tiempo y distancia) y **CP-106** (un destino fuera de Tarija muestra «Origen o destino inválido») usan respuestas falsas del backend. **CP-29** (el mapa no carga) y **CP-30** (en segundo plano se deja de pedir la ubicación) se prueban con el mapa y la ubicación falsos; en un teléfono hay que comprobar además que el ícono de ubicación del sistema se apaga al minimizar la app.

Para analizar el código:

```bash
flutter analyze
```

## Estado actual

La app tiene la estructura inicial, la navegación principal, la gestión de estado con Provider, la configuración por entornos, un cliente HTTP centralizado, el mapa base de Mapbox con la ubicación del conductor (T-093) y la vista Cómo llegar (HU-05).

La navegación activa, los eventos y los surtidores se incorporarán en etapas posteriores.

## Inicio de sesión (HU-10)

- Sin sesión, la app muestra solo la vista Inicio de sesión. El botón «Iniciar sesión con Google» abre el selector de cuentas (`google_sign_in`) y envía el token de identidad a `POST /sesion/google`.
- El JWT y el token de renovación se guardan con `flutter_secure_storage` (Keystore en Android, Keychain en iOS). Con un token de renovación vigente, la app entra directo al mapa aunque no haya red.
- `ApiClient` pide el JWT a `SesionController` antes de cada petición: si le quedan menos de 2 minutos, lo renueva con `POST /sesion/renovar`. Si el backend responde 401, renueva y repite la petición una vez; si no puede renovar, la app vuelve a Inicio de sesión.
- Al iniciar sesión se guardan los catálogos (`GET /catalogos`); las demás pantallas toman de ahí los tipos, colores, vigencias y tiempos.
- «Cerrar sesión», en el menú de la cuenta del Mapa principal, llama a `POST /sesion/cerrar` y borra todo lo guardado.

Para que el selector de cuentas funcione en Android hay que registrar en Google Cloud un Client ID de Android con el nombre del paquete y la huella SHA-1 de la llave de firma, además del Client ID web que se pasa en `GOOGLE_SERVER_CLIENT_ID`.

## Eventos viales (HU-01, HU-02 y HU-03)

- **Reportar (HU-01):** «Reportar evento» en el mapa abre la vista con los tipos reportables del catálogo y «Hace 5 minutos» marcado: con el tipo y «Confirmar» son tres toques. La app usa la ubicación del GPS o, si no hay, el punto que el conductor marca en el mapa. Envía la hora de confirmación y los minutos; nunca calcula el momento del evento.
- **Sin conexión (HU-02):** si no hay red, el reporte se guarda en una base SQLite del teléfono (`sqflite`) y se reenvía solo cuando vuelve la red (`connectivity_plus`) o cada 30 segundos con la app abierta. Antes de reenviarlo, la app comprueba su vigencia con el catálogo guardado. Los que fallan varias veces se muestran como pendientes en el mapa.
- **Ver (HU-03):** con el mapa abierto, la app consulta `GET /eventos` cada 5 segundos, pidiendo solo los cambios con la marca de la consulta anterior. Cada evento se dibuja con el color de su tipo; al tocarlo se ven su tipo y hace cuánto ocurrió. Si el backend no responde, el mapa conserva lo último y avisa «No se pudo actualizar eventos».

Las pruebas de la cola usan `sqflite_common_ffi`, que necesita SQLite instalado en la máquina donde se corren (en Windows viene con el paquete; en Linux, `libsqlite3`).
