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
│       ├── reporte/
│       │   └── hoja_notificar.dart
│       ├── ruta/
│       │   └── hoja_ruta.dart
│       ├── events/
│       │   └── events_screen.dart
│       └── stations/
│           └── stations_screen.dart
└── test/
    ├── dobles/
    │   └── dobles_mapa.dart
    ├── widget_test.dart
    ├── api_client_test.dart
    ├── como_llegar_test.dart
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

Para que la app no se trabe, las vistas escuchan solo lo que usan (`context.select`): la pantalla principal y la
lista de Surtidores no se reconstruyen con cada posición del GPS. `EventosController.eventos` devuelve la misma lista
mientras no llegue ni venza ningún evento, y `EventosController.congestiones` une las congestiones una sola vez por
cada lista; el mapa guarda los marcadores y el tráfico de la ruta mientras sus datos no cambien. En el mapa de
Mapbox, cada capa se dibuja de a una vez (`DibujoEnSerie`), así dos cambios seguidos no dejan marcadores ni líneas
repetidas.

## Navegación principal

La aplicación dispone de una barra de navegación inferior con tres pantallas:

* Mapa.
* Eventos.
* Surtidores.

El mapa queda siempre detrás; Eventos y Surtidores son hojas sobre él. Al cambiar de pestaña no queda nada abierto
de la anterior: se cierran la búsqueda, el detalle de un evento o de un surtidor, el surtidor desplegado en la lista,
«Notificar evento» (salvo que se esté enviando o se haya quedado sin red) y la elección de una parada. El lugar
elegido y su ruta («Cómo llegar») sí se conservan. En Eventos y en Surtidores, tocar el mapa de fondo no elige un
punto; tocar el marcador de un evento desde Surtidores lleva a Eventos, y el de un surtidor desde Eventos, a
Surtidores.

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
| `MAPA_TEXTURA` | `false` por defecto. Con `true`, en Android el mapa se dibuja como textura (Texture Layer Hybrid Composition) en lugar de Hybrid Composition: las hojas de Eventos y Surtidores suelen desplazarse más fluidas sobre el mapa. Es para comparar en el teléfono antes de dejarlo fijo. |
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
* Las imágenes de los marcadores (surtidores, eventos y el pin) se registran una sola vez por dibujo en el estilo del mapa (`churo-…`), y cada marcador solo la nombra. Si registrarla falla, el marcador lleva su propia imagen, como antes. Si el estilo se vuelve a cargar, los marcadores se dibujan de nuevo y las registran otra vez.

## Cómo llegar

Desde #191 (D-49) la ruta se elige en el mismo Mapa principal, como en Google Maps (CU-03). Ya no hay una vista Cómo llegar aparte.

* Al tocar un punto, un lugar conocido del mapa base (por ejemplo «Mercado Negro», con `TapInteraction(StandardPOIs())`) o un resultado de la búsqueda (D-47), se abre la hoja del lugar (`HojaLugar`). `RutaController`, que ahora vive en toda la app, pide la ruta a `POST /rutas` desde la posición del conductor y la hoja muestra su distancia y su tiempo (RN-03.13), con «Notificar», «Ir» y «Cómo llegar». Si el conductor elige otro lugar mientras tanto, la respuesta vieja se descarta.
* El origen es siempre la posición. Sin ubicación, la hoja lo avisa y la ruta se pide cuando llega la primera posición.
* «Cómo llegar» dibuja la ruta en el mapa con la hoja `HojaRuta`: «Iniciar» y «Otra parada». Cada parada se toca en el mapa, van en el orden en que se agregan y son 10 como máximo (RN-03.2); se quitan desde su chip. Cerrar la hoja vuelve a la del lugar, sin paradas.
* La ruta se pinta de amarillo o rojo donde pasa por una congestión de su mismo sentido (`pintarRutaPorTrafico`, en `lib/models/trafico.dart`), también en la Navegación activa.
* «No se encontró una ruta válida» y «Origen o destino inválido» se muestran como respuesta, no como error, y no dejan ir. Si el backend no responde o responde 503, se muestra el fallo con «Reintentar» (RN-03.15).
* «Iniciar» o «Ir» ponen la ruta Activa y abren la vista Navegación activa (RN-03.17), con la cámara sobre el conductor. Al volver no queda nada elegido.
* El gesto de atrás deja de elegir la parada, después cierra la ruta y al final quita el lugar.

Desde HU-10 la app inicia sesión y pide la ruta al backend. Con `--dart-define=RUTA_DE_EJEMPLO=true` (fuera de producción) la vista usa `assets/datos/ruta_ejemplo_post_rutas.json`: la respuesta del primer viaje de referencia de Tarija (T-120, de la plaza Luis de Fuentes a la ex terminal), copiada de `04_imp/datos`. Es siempre la misma ruta, elija el conductor los puntos que elija, y la hoja lo avisa.

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

## Navegación activa (HU-05, #94)

La vista sigue la posición del conductor (la cámara de Mapbox va detrás de él) y muestra la calle que sigue con el sentido del giro, la distancia hasta esa esquina y el tiempo y la distancia que faltan (RN-03.17, D-22). `NavegacionController` lo calcula en el teléfono con el trazado y los nombres de calle que trae la ruta; `lib/models/navegacion.dart` tiene la geometría.

* El conductor se ubica proyectando su posición sobre el trazado. Nunca retrocede: un salto del GPS hacia atrás no cambia lo recorrido.
* La ruta trae los tramos en orden con su calle, pero no dice en qué punto del trazado empieza cada uno. Los tramos seguidos de la misma calle forman una calle; los cambios de calle se ubican en los puntos del trazado donde más gira el rumbo, midiendo 15 m antes y 15 m después de cada punto. Menos de 30° es «sigue derecho». Una calle que cambia de nombre sin girar, junto a una curva más cerrada de otra calle, puede quedar mal ubicada.
* El controlador guarda los tramos que faltan para revisar la ruta (D-12), desde el primer tramo de la calle por la que va el conductor, y las paradas que faltan (se quitan al pasar a menos de 30 m).
* A menos de 30 m del destino, o al tocar «Cancelar» o volver atrás, la ruta pasa a Terminada y se borra de la memoria. Nunca se guarda en el teléfono ni en el servidor (D-05).

## Ruta alternativa (HU-06, #83)

Mientras la ruta está Activa, cada 5 segundos (con la consulta de eventos) el controlador de navegación manda a `POST /rutas/revision` su posición, los tramos y las paradas que faltan, el destino y los eventos ya avisados (RN-04.13, D-12). La llamada es asíncrona: la interfaz no espera la respuesta.

* **Alternativa:** un aviso sobre el resumen, sin tapar la indicación ni el mapa, con «Aceptar» y «Rechazar» y los segundos que le quedan (RN-04.17). Al aceptar, la alternativa pasa a ser la ruta Activa; al rechazar, sigue la original y el evento queda en el mapa (RN-04.18). Sin respuesta en 15 segundos se cierra solo (RN-04.21, P-26). Mientras está a la vista no se revisa.
* **Sin alternativa:** avisa del evento y mantiene la ruta (RN-04.16).
* **Ruta desactualizada:** pide la ruta de nuevo a `POST /rutas` desde la posición, con el mismo destino y las paradas que faltan.
* **Falla** (sin red, 503): sigue con la ruta sin mostrar nada y reintenta en el ciclo siguiente.
* Todo evento que provocó un aviso queda entre los avisados y no vuelve, se acepte, se rechace o se cierre solo (RN-04.19). Como los tramos que faltan se cuentan desde el comienzo de la calle actual, el backend puede avisar de un evento que ya quedó atrás en esa calle: si la app lo conoce por `GET /eventos` y está detrás del conductor sobre el trazado, no lo muestra y lo cuenta como avisado.

## Telemetría (HU-13, #75)

`TelemetriaController` sigue al controlador del mapa y a la sesión: con la app en pantalla, con sesión y con permiso de ubicación, toma una lectura del GPS cada 3 segundos y las envía en grupos cada 15 a `POST /telemetria`, haya o no una ruta activa (P-13, RN-04.2). Cada lectura lleva posición, precisión y momento; la velocidad la calcula el motor (RN-04.1).

* Las lecturas salen de un flujo del GPS sin filtro de distancia (en Android, cada 3 segundos), para que un auto detenido también las mande. Si el GPS no dio una lectura nueva, no se repite la anterior.
* El mapa y la telemetría comparten esa única lectura del GPS (`ServicioUbicacionGeolocator`); al mapa solo le llegan las posiciones cuando el conductor se movió 5 m o más. Antes cada uno pedía la suya, pero `geolocator` devuelve el mismo flujo a la segunda llamada y descarta sus ajustes: la telemetría terminaba con el filtro de 5 m del mapa y un auto detenido dejaba de mandar lecturas.
* Al pasar a segundo plano, al cerrar la sesión o si se pierde el permiso, deja de leer y de enviar, y lo que no se envió se descarta (RN-04.1, RN-04.3, RNF-13). Sin permiso, el resto de la app funciona igual.
* Un grupo que no se pudo enviar se descarta: la telemetría no se reintenta.
* La prueba de batería de 30 minutos comparada con Google Maps (RNF-18) se hace a mano en un teléfono.

## Estado actual

La app tiene la estructura inicial, la navegación principal, la gestión de estado con Provider, la configuración por entornos, un cliente HTTP centralizado, el mapa base de Mapbox con la ubicación del conductor (T-093) y Cómo llegar en el mismo mapa (HU-05, #191).

La navegación activa llegó con HU-05 (#94). Los surtidores se incorporarán en etapas posteriores.

## Inicio de sesión (HU-10)

- Sin sesión, la app muestra solo la vista Inicio de sesión. El botón «Iniciar sesión con Google» abre el selector de cuentas (`google_sign_in`) y envía el token de identidad a `POST /sesion/google`.
- El JWT y el token de renovación se guardan con `flutter_secure_storage` (Keystore en Android, Keychain en iOS). Con un token de renovación vigente, la app entra directo al mapa aunque no haya red.
- `ApiClient` pide el JWT a `SesionController` antes de cada petición: si le quedan menos de 2 minutos, lo renueva con `POST /sesion/renovar`. Si el backend responde 401, renueva y repite la petición una vez; si no puede renovar, la app vuelve a Inicio de sesión.
- Al iniciar sesión se guardan los catálogos (`GET /catalogos`); las demás pantallas toman de ahí los tipos, colores, vigencias y tiempos.
- «Cerrar sesión», en el menú de la cuenta del Mapa principal, llama a `POST /sesion/cerrar` y borra todo lo guardado.

Para que el selector de cuentas funcione en Android hay que registrar en Google Cloud un Client ID de Android con el nombre del paquete y la huella SHA-1 de la llave de firma, además del Client ID web que se pasa en `GOOGLE_SERVER_CLIENT_ID`.

## Eventos viales (HU-01, HU-02 y HU-03)

- **Reportar (HU-01):** desde #191 es la hoja «Notificar evento» sobre el mapa, con los tipos reportables del catálogo y «5 min» marcado, sin imagen ni comentario. La campana la abre en la posición del conductor: con el tipo y «Enviar» son tres toques (RNF-06). «Notificar», en la hoja de un lugar, la abre en ese lugar, y mientras está abierta se puede tocar el mapa para cambiar el punto. El punto puede estar a 1 km o menos del conductor (RN-01.5, `radioReporteMetros` del catálogo): más lejos, la hoja lo avisa y no lo envía; con un punto elegido la app manda también `posicionConductor` para que el backend lo revise. Sin GPS vale el punto que el conductor marca. Al enviarse, la hoja se cierra sola y el mensaje aparece abajo. Envía la hora de confirmación y los minutos; nunca calcula el momento del evento.
- **Sin conexión (HU-02):** si no hay red, el reporte se guarda en una base SQLite del teléfono (`sqflite`) y se reenvía solo cuando vuelve la red (`connectivity_plus`) o cada 30 segundos con la app abierta. Antes de reenviarlo, la app comprueba su vigencia con el catálogo guardado. Los que fallan varias veces se muestran como pendientes en el mapa.
- **Ver (HU-03):** con el mapa abierto, la app consulta `GET /eventos` cada 5 segundos, pidiendo solo los cambios con la marca de la consulta anterior. Cada evento se dibuja con el color de su tipo; al tocarlo se ven su tipo y hace cuánto ocurrió. Si el backend no responde, el mapa conserva lo último y avisa «No se pudo actualizar eventos».
- **Sin conexión (HU-04, #62):** cada consulta de `GET /eventos` que trae algo guarda en el teléfono los eventos que conoce la app, con la hora (los catálogos ya los guarda el inicio de sesión). Al abrir la app, mientras no llega una consulta, el mapa y la lista muestran esos eventos con la marca amarilla «Sin conexión: eventos guardados», que dice a qué hora se guardaron y que pueden estar desactualizados. En cuanto una consulta funciona, que con la consulta de cada 5 segundos es apenas vuelve la red, la marca desaparece y lo guardado se reemplaza por lo vigente (RN-02.9). Un evento guardado que ya venció según el catálogo no se dibuja. Cerrar sesión borra lo guardado. Las pruebas están en `test/eventos_sin_conexion_test.dart` (CP-84, CP-85).
- **Lista y votos (HU-11, #64):** la pestaña Eventos muestra los eventos vigentes con su tipo, hace cuánto ocurrieron y la marca de confirmado, con los mismos datos que el mapa (comparten `EventosController`). «Es real» y «Es falso» son un toque a `POST /eventos/{id}/votos`; después, los botones de ese evento quedan desactivados. Si el backend responde que ya votó, que es su propio evento o que ya no está vigente, se muestra su mensaje y nada cambia; sin red se puede volver a tocar. Las congestiones de la telemetría aparecen sin botones (D-20). Un evento retirado desaparece en la siguiente actualización (o enseguida, si fue el voto de este conductor el que lo retiró).

Las pruebas de la cola usan `sqflite_common_ffi`, que necesita SQLite instalado en la máquina donde se corren (en Windows viene con el paquete; en Linux, `libsqlite3`).

## Surtidores (HU-07, #71)

- **Lista:** la pestaña Surtidores es una hoja sobre el mapa, como la de Eventos (Figma «08 · Surtidores»). Muestra los surtidores de `GET /surtidores` del más cercano al más lejano. Cada fila lleva una barra con el estado general: verde si hay todo, amarilla si hay algunos combustibles, roja si no hay ninguno y gris sin información reciente. También muestra hace cuánto llegó el último dato. Al tocar la fila se despliega el estado por combustible, la distancia con el ícono del auto y el botón «Ir»; la «i» abre el detalle.
- **Detalle:** el nombre, la distancia y, por cada combustible, si hay o no, la fila si se conoce, el origen (WhatsApp, Conductores o ANH, cada uno con su ícono y su color) y hace cuánto llegó el dato. Si el origen es ANH, el saldo en litros va junto al combustible: «Gasolina (19 L)». Desde #191 no se muestran la hora exacta ni el estado de ANH. Un surtidor sin datos vigentes dice «Sin información reciente».
- **Mapa:** los surtidores también son marcadores, con el color de su estado. Al tocar uno se abre su detalle y la cámara va hasta él.
- **Cada 60 segundos:** `CombustibleController` vuelve a pedir la lista mientras el mapa está en pantalla, sea en la pestaña Mapa, Eventos o Surtidores, y la pausa en segundo plano (RN-05.17, P-27). La lista y los marcadores salen de la misma consulta.
- **Sin ubicación:** pide desde el centro de Tarija, para que los marcadores salgan igual, y la hoja explica que necesita la ubicación para ordenar por cercanía, con el botón para concederla. Cuando llega la primera posición, vuelve a pedir la lista.
- **Ir y Cómo llegar:** los dos marcan el surtidor como punto elegido con su nombre (`MapController.elegirLugar`). «Ir», en la lista, abre directamente la Navegación activa; «Cómo llegar», en el detalle, muestra la ruta en el mapa (#191).
- El gesto de atrás cierra primero el detalle del surtidor.

Las pruebas están en `test/surtidores_test.dart` (CP-108 a CP-110).

## Pregunta de recarga (HU-12, #80)

- Con la app abierta, `RecargaController` mira la ubicación que ya sigue el mapa y la revisa cada 10 segundos, porque detenido el GPS casi no informa. Si el conductor pasa 3 minutos a menos de 50 m de un surtidor del catálogo, pregunta «¿Acabas de recargar combustible?» (RN-05.8, D-11, P-09). La distancia se mide en el teléfono, con los surtidores que ya trajo `GET /surtidores`.
- La pregunta es una tarjeta debajo de la barra de búsqueda, como en el diseño «05 · Mapa · ¿Recargaste combustible?». No tapa la lista de surtidores, y en Cómo llegar o en la Navegación activa no aparece: espera a que el conductor vuelva al mapa (RN-05.16).
- Tocar un tipo (Gasolina, Gasolina Premium, Diesel o Diesel ULS) es el «Sí»: un solo toque. Desconocido no aparece (RN-05.9). «No, no recargué» o la X no envían nada, y no vuelve a preguntar mientras el conductor siga junto a ese surtidor (RN-05.10).
- La respuesta va a `POST /surtidores/{id}/confirmaciones` con la ubicación donde se detectó la parada, aunque el conductor ya haya arrancado, y la app muestra el resultado. Después de confirmar, o si el backend responde que es repetida, no vuelve a preguntar por ese surtidor en 60 minutos (RN-05.14, P-14). El silencio vive en memoria: si se cierra la app, el backend sigue rechazando la repetida.
- Una pregunta que nadie responde se cierra sola a los 15 minutos, sin enviar nada.

Las pruebas están en `test/recarga_test.dart` (CP-130 a CP-132), con ubicaciones y un reloj simulados.
