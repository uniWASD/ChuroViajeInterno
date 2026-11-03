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
│   │   └── navigation_controller.dart
│   ├── services/
│   │   ├── api/
│   │   │   └── api_client.dart
│   │   └── ubicacion/
│   │       └── servicio_ubicacion.dart
│   ├── models/
│   │   └── coordenada.dart
│   ├── storage/
│   │   └── .gitkeep
│   └── views/
│       ├── main/
│       │   └── main_screen.dart
│       ├── map/
│       │   ├── map_screen.dart
│       │   ├── mapa_base.dart
│       │   └── mapa_mapbox.dart
│       ├── events/
│       │   └── events_screen.dart
│       └── stations/
│           └── stations_screen.dart
└── test/
    ├── dobles/
    │   └── dobles_mapa.dart
    ├── widget_test.dart
    ├── api_client_test.dart
    ├── map_controller_test.dart
    ├── map_screen_test.dart
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

La prueba de widget **CP-20** simula el backend apagado y comprueba que se muestra el error de red sin cerrar la pantalla principal. **CP-29** (el mapa no carga) y **CP-30** (en segundo plano se deja de pedir la ubicación) se prueban con el mapa y la ubicación falsos; en un teléfono hay que comprobar además que el ícono de ubicación del sistema se apaga al minimizar la app.

Para analizar el código:

```bash
flutter analyze
```

## Estado actual

La app tiene la estructura inicial, la navegación principal, la gestión de estado con Provider, la configuración por entornos, un cliente HTTP centralizado y el mapa base de Mapbox con la ubicación del conductor (T-093).

Las funcionalidades de rutas, eventos y surtidores se incorporarán en etapas posteriores.

El botón «Probar conexión» es temporal y solo aparece fuera de producción, encima del mapa. Se retirará con la vista Cómo llegar, que es la primera que llama al backend y donde pasa a probarse CP-20.