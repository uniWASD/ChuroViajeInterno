# ChuroViaje — Aplicación Flutter

Aplicación móvil del proyecto ChuroViaje, desarrollada con Flutter y Dart. Esta aplicación constituye la estructura base para incorporar las funcionalidades de mapas, eventos y surtidores.

## Requisitos

* Flutter SDK **3.47.2** (Dart 3.13.2).
* Android Studio o un dispositivo Android para ejecutar la aplicación.
* Un emulador Android o dispositivo físico para probar la conexión con el backend.

## Estructura del proyecto

```text
lib/
├── config/
│   └── app_config.dart
├── controllers/
│   ├── map_controller.dart
│   └── navigation_controller.dart
├── services/
│   └── api/
│       └── api_client.dart
├── models/
│   └── .gitkeep
├── storage/
│   └── .gitkeep
├── views/
│   ├── main/
│   │   └── main_screen.dart
│   ├── map/
│   │   └── map_screen.dart
│   ├── events/
│   │   └── events_screen.dart
│   └── stations/
│       └── stations_screen.dart
└── main.dart

test/
├── widget_test.dart
├── api_client_test.dart
└── map_controller_test.dart
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

La prueba de widget **CP-20** simula el backend apagado y comprueba que se muestra el error de red sin cerrar la pantalla principal.

Para analizar el código:

```bash
flutter analyze
```

## Estado actual

Esta entrega proporciona la estructura inicial de la aplicación, la navegación principal, la gestión de estado con Provider, la configuración por entornos y un cliente HTTP centralizado.

Las funcionalidades de mapas, eventos y surtidores se incorporarán en etapas posteriores.

El botón «Probar conexión» es temporal y solo aparece fuera de producción; se retirará en T-093 al incorporar el mapa base.