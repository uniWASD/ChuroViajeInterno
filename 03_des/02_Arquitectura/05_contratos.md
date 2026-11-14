# Contratos entre componentes

[Índice de la arquitectura](README.md)

Hay cuatro contratos: el de la app con el backend, el del backend con el motor core, el del servicio de WhatsApp/LLM con el backend y el del backend con la API de ANH. Las rutas son una propuesta de nombres; lo que fija cada contrato es qué dato viaja y quién decide.

## Convenciones comunes

- Todo viaja en JSON, con el tipo `application/json`. Cada respuesta sale de un DTO de solo lectura, nunca de un objeto de dominio ([D-43](08_decisiones_parametros.md)).
- Las fechas van en formato ISO 8601 con zona horaria, por ejemplo `2026-10-06T15:02:00-04:00`.
- Una ubicación son dos números: latitud y longitud en grados decimales.
- La app y el simulador envían el JWT en la cabecera `Authorization` de cada petición, salvo al iniciar sesión.
- Un rechazo lleva un código y un mensaje en español listo para mostrar al conductor, con la forma `{"codigo":"SESION_VENCIDA","mensaje":"Tu sesión venció. Inicia sesión de nuevo."}`. El código es estable y sirve a la app para decidir qué hacer; el mensaje es el que se muestra.
- En la API REST los nombres de los campos van en `camelCase` (`tokenGoogle`, `expiraEn`). En los mensajes por pipe con el núcleo van en `snake_case` (`red_cargada`, `fecha_extracto`), porque es lo que ya habla el núcleo.
- Ninguna respuesta incluye identificadores de otros conductores.
- Una consulta responde 200 con el DTO o la lista de DTO. Una petición que crea un recurso, como un reporte de evento, responde 201 con el DTO de lo creado. Un rechazo usa el código de la tabla «Respuestas de rechazo», con el cuerpo de arriba ([D-43](08_decisiones_parametros.md)).

## App y simulador con el backend

| Operación | Método y ruta | Qué envía | Qué recibe | Proceso |
| --- | --- | --- | --- | --- |
| Iniciar sesión | `POST /sesion/google` | Token de identidad de Google | JWT, su expiración y el token de renovación | CU-08 |
| Renovar sesión | `POST /sesion/renovar` | Token de renovación | JWT nuevo | CU-08 |
| Cerrar sesión | `POST /sesion/cerrar` | Token de renovación | Nada; el token de renovación deja de valer | CU-08 |
| Sesión de prueba | `POST /sesion/prueba` | Credenciales de prueba e identificador de la prueba de carga | JWT de un conductor simulado | CU-07 |
| Leer catálogos | `GET /catalogos` | Nada | Tipos de evento con color, vigencia y si son reportables; tipos de combustible; tiempos del reporte | CU-01, CU-02 |
| Reportar evento | `POST /eventos` | Tipo, ubicación, hora de confirmación y minutos elegidos | Evento aceptado, o motivo del rechazo | CU-01 |
| Consultar eventos | `GET /eventos` | Marca de la última consulta, opcional | Eventos vigentes o solo los cambios, y la marca nueva | CU-02 |
| Votar un evento | `POST /eventos/{id}/votos` | Real o falso | Estado del evento tras el voto | CU-02 |
| Calcular ruta | `POST /rutas` | Origen, destino y paradas | Trazado, distancia y tiempo, con el identificador y el nombre de la calle de cada tramo; o «sin ruta» o «punto inválido» | CU-03 |
| Revisar ruta activa | `POST /rutas/revision` | Posición actual, tramos que faltan, destino, paradas que faltan y eventos ya avisados | Sin cambios; sin alternativa; o ruta alternativa. Las dos últimas traen el evento que las provocó. Si la red vial cambió, «ruta desactualizada» | CU-04 |
| Enviar telemetría | `POST /telemetria` | Grupo de lecturas: posición, precisión y momento | Recibido | CU-04 |
| Consultar surtidores | `GET /surtidores` | Ubicación actual | Surtidores por cercanía, con estado por tipo, fila, origen (WhatsApp, Conductores o ANH) y fecha. Si el origen es ANH, también los litros, el estado de ANH y el momento en que el backend lo leyó | CU-05 |
| Confirmar recarga | `POST /surtidores/{id}/confirmaciones` | Tipo de combustible y ubicación actual | Confirmación aceptada, o motivo del rechazo | CU-05 |

La app nunca envía el momento en que ocurrió un evento ya calculado: manda la hora de confirmación y los minutos elegidos, y el `EventoVial` hace la resta ([RN-01.4](04_reglas/CU-01_ReportarEventoVial.md)).

### Sesión (`/sesion/*`)

Son las únicas rutas de la app que no piden JWT. Todas reciben y devuelven JSON; el sistema nunca ve la contraseña de Google ([D-34](08_decisiones_parametros.md)).

| Ruta | Cuerpo de la petición | Respuesta |
| --- | --- | --- |
| `POST /sesion/google` | `{"tokenGoogle":"<token de identidad de Google>"}` | 200 con `{"jwt":"…","expiraEn":"2026-10-06T16:02:00Z","tokenRenovacion":"…"}` |
| `POST /sesion/renovar` | `{"tokenRenovacion":"…"}` | 200 con `{"jwt":"…","expiraEn":"…"}`. No devuelve un token de renovación nuevo: el que tiene la app sigue valiendo hasta que vence |
| `POST /sesion/cerrar` | `{"tokenRenovacion":"…"}` | 204 sin cuerpo. Es idempotente: cerrar una sesión que ya no existe también responde 204 |

- `expiraEn` es la expiración del JWT, en ISO 8601 y en UTC. El JWT dura 1 hora y el token de renovación, 30 días desde que se inició la sesión ([P-05](08_decisiones_parametros.md)).
- El primer ingreso con una cuenta de Google crea al conductor; los siguientes usan el mismo ([RN-08.4](04_reglas/CU-08_IniciarSesion.md)).
- Códigos de rechazo:

| Código | Estado | Cuándo |
| --- | --- | --- |
| `TOKEN_GOOGLE_INVALIDO` | 401 | Google no reconoce el token, venció o no es para esta aplicación |
| `GOOGLE_NO_DISPONIBLE` | 503 | El backend no pudo comunicarse con Google. Es un fallo transitorio: la app puede reintentar |
| `SESION_VENCIDA` | 401 | El token de renovación no existe, venció o ya se cerró. La app debe volver a pedir el inicio de sesión con Google |
| `NO_AUTENTICADO` | 401 | Una ruta que pide JWT llegó sin él, con uno vencido o con uno mal firmado |
| `ACCESO_DENEGADO` | 403 | El JWT es válido pero no alcanza para esa ruta; por ejemplo, un conductor en `/monitoreo/*` |
| `PETICION_INVALIDA` | 400 | Falta el campo del cuerpo o no cumple el formato |

### Catálogos (`GET /catalogos`)

La app no lleva listas propias: todo lo que muestra en los selectores sale de esta respuesta ([D-06](08_decisiones_parametros.md)). Los tipos de evento y de combustible salen de la base de datos; los tiempos del reporte y los valores de la fila son fijos del backend. El orden de cada lista es estable y es el que debe mostrar la app. Los valores del ejemplo son los del catálogo inicial (migraciones de la base de datos), recortado a dos elementos por lista.

```json
{
  "tiposEvento": [
    {"codigo": "ACCIDENTE", "nombre": "Accidente", "color": "#E53935", "vigenciaMinutos": 60, "reportable": true},
    {"codigo": "CONGESTION", "nombre": "Congestión", "color": "#FDD835", "vigenciaMinutos": 30, "reportable": false}
  ],
  "tiposCombustible": [
    {"codigo": "GASOLINA", "nombre": "Gasolina", "seleccionable": true},
    {"codigo": "DESCONOCIDO", "nombre": "Desconocido", "seleccionable": false}
  ],
  "tiemposReporte": [
    {"minutos": 0, "nombre": "Ahora mismo", "porDefecto": false},
    {"minutos": 5, "nombre": "Hace 5 minutos", "porDefecto": true}
  ],
  "valoresFila": [
    {"codigo": "SIN_FILA", "nombre": "Sin fila"},
    {"codigo": "CORTA", "nombre": "Corta"}
  ]
}
```

- `reportable` es falso para la congestión: la crea el sistema con la telemetría y el conductor no puede reportarla ([RN-01.3](04_reglas/CU-01_ReportarEventoVial.md)).
- `seleccionable` es falso para «Desconocido»: lo usa el sistema cuando un mensaje no dice el combustible y el conductor no puede elegirlo ([RN-05.9](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md)).
- `tiemposReporte` ofrece 0, 5, 10 y 30 minutos; el marcado con `porDefecto` es el que se usa si el conductor no elige ([D-09](08_decisiones_parametros.md)).
- `valoresFila` son los mismos de [P-15](08_decisiones_parametros.md). La app los trata como una lista cerrada.
- La ruta pide JWT, como el resto de la API de la app.

### Rutas (`POST /rutas`)

```json
{"origen":{"latitud":-21.5355,"longitud":-64.7296},"destino":{"latitud":-21.5300,"longitud":-64.7260},"paradas":[]}
```

Si hay ruta, responde 200 con:

```json
{
  "estado": "ENCONTRADA",
  "distanciaM": 755,
  "tiempoS": 136,
  "trazado": [{"latitud": -21.5355, "longitud": -64.7296}, {"latitud": -21.5348, "longitud": -64.728}, {"latitud": -21.53, "longitud": -64.726}],
  "tramos": [{"id": "osm:5001:1:ida", "calle": "Calle Comercio"}, {"id": "osm:5002:1:ida", "calle": "Av. Las Américas"}]
}
```

- `paradas` es opcional y va en el orden que eligió el conductor, con 10 como máximo ([RN-03.2](04_reglas/CU-03_CalcularRutaOptima.md)).
- `tramos` va en el orden de la ruta. La app guarda sus `id` y los devuelve al revisar la ruta ([D-12](08_decisiones_parametros.md)); `calle` es lo que muestra la navegación.
- Sin camino, responde 200 con `{"estado":"SIN_RUTA","mensaje":"No se encontró una ruta válida"}` ([RN-03.12](04_reglas/CU-03_CalcularRutaOptima.md)). Con un punto fuera de la mancha urbana o lejos de cualquier calle, 200 con `{"estado":"PUNTO_INVALIDO","mensaje":"Origen o destino inválido"}` ([RN-03.3](04_reglas/CU-03_CalcularRutaOptima.md), [RN-03.4](04_reglas/CU-03_CalcularRutaOptima.md)). Ninguna de las dos trae trazado.
- Sin origen o sin destino, o con más de 10 paradas, responde 400 con `PETICION_INVALIDA`. Si el motor no responde a tiempo, se está reiniciando o está apagado, responde 503 con `MOTOR_NO_DISPONIBLE` y la app puede reintentar ([RN-03.15](04_reglas/CU-03_CalcularRutaOptima.md)).
- La ruta pide JWT y el servidor no la guarda ([RN-03.1](04_reglas/CU-03_CalcularRutaOptima.md), [D-05](08_decisiones_parametros.md)).

### Revisión de la ruta activa (`POST /rutas/revision`)

Mientras la ruta está Activa, la app manda lo que le falta ([D-12](08_decisiones_parametros.md)):

```json
{
  "posicion": {"latitud": -21.5355, "longitud": -64.7296},
  "tramos": ["osm:5001:1:ida", "osm:5002:1:ida", "osm:5003:1:ida"],
  "destino": {"latitud": -21.5290, "longitud": -64.7220},
  "paradas": [],
  "eventosAvisados": [150]
}
```

Responde siempre 200, con uno de cuatro estados:

```json
{"estado": "SIN_CAMBIOS"}
{"estado": "SIN_ALTERNATIVA", "eventoId": 154}
{"estado": "ALTERNATIVA", "eventoId": 153, "costo": 1700, "costoActual": 3244, "distanciaM": 1700, "tiempoS": 306,
 "trazado": [{"latitud": -21.5355, "longitud": -64.7296}, {"latitud": -21.533, "longitud": -64.731}],
 "tramos": [{"id": "osm:6001:1:ida", "calle": "Av. Circunvalación"}]}
{"estado": "RUTA_DESACTUALIZADA"}
```

- `tramos` son los `id` de los tramos que faltan, en el orden en que llegaron en la ruta. `paradas` (las que faltan) y `eventosAvisados` (los `id` de los eventos que ya provocaron un aviso en esta ruta) son opcionales ([RN-04.19](04_reglas/CU-04_RecalcularRuta.md)).
- `SIN_ALTERNATIVA` y `ALTERNATIVA` traen el `eventoId` que las provocó, el mismo `id` de `GET /eventos`; la app lo agrega a sus avisados para no volver a avisar ([RN-04.16](04_reglas/CU-04_RecalcularRuta.md), [RN-04.19](04_reglas/CU-04_RecalcularRuta.md)).
- `ALTERNATIVA` trae los mismos campos que una ruta de `POST /rutas`, más `costo` y `costoActual`: el de la alternativa y el de seguir, en metros ponderados por el tráfico. Si un bloqueo corta lo que falta, no viene `costoActual`. La alternativa parte de la posición actual y pasa por las paradas que faltan ([RN-04.15](04_reglas/CU-04_RecalcularRuta.md)).
- `RUTA_DESACTUALIZADA` quiere decir que algún tramo ya no existe porque se recargó la red vial: la app pide la ruta de nuevo con `POST /rutas`, desde su posición y con el mismo destino y las paradas que faltan.
- Sin `posicion`, `tramos` o `destino` responde 400 con `PETICION_INVALIDA`. Si el motor no responde a tiempo, se está reiniciando o está apagado, 503 con `MOTOR_NO_DISPONIBLE`: la app sigue con su ruta y vuelve a intentar en el siguiente ciclo. Pide JWT y el servidor no guarda nada ([D-05](08_decisiones_parametros.md)).

### Reporte de evento (`POST /eventos`)

```json
{"tipo":"CONTROL_POLICIAL","ubicacion":{"latitud":-21.5355,"longitud":-64.7296},"horaConfirmacion":"2026-11-01T08:00:00-04:00","minutosElegidos":5}
```

Si lo acepta, responde 201 con el evento creado:

```json
{"id":153,"tipo":"CONTROL_POLICIAL","ubicacion":{"latitud":-21.5355,"longitud":-64.7296},"momentoOcurrencia":"2026-11-01T07:55:00-04:00","estado":"ACTIVO","confirmado":false,"origen":"CONDUCTOR"}
```

- `minutosElegidos` es opcional y vale 0, 5, 10 o 30; sin él, el evento ocurrió hace 5 minutos. El backend resta los minutos a la hora de confirmación y nunca deja el evento en el futuro ([RN-01.4](04_reglas/CU-01_ReportarEventoVial.md)).
- Esta respuesta y cualquier consulta posterior nunca dicen quién reportó el evento ([RN-01.12](04_reglas/CU-01_ReportarEventoVial.md)). Los eventos de congestión traen además `nivel`.
- Rechazos: 400 `PETICION_INVALIDA` si falta el tipo, la ubicación o la hora, o si los minutos no son una de las opciones; 422 `TIPO_NO_REPORTABLE` para la congestión o un tipo que no existe, `UBICACION_FUERA_COBERTURA` fuera de Tarija y `EVENTO_VENCIDO` si ya pasó la vigencia de su tipo; 409 `REPORTE_DUPLICADO` (mismo conductor y tipo, a menos de 100 m y dentro de 10 minutos); 429 `LIMITE_DE_REPORTES` (más de 5 en 10 minutos) ([RN-01.2](04_reglas/CU-01_ReportarEventoVial.md) a [RN-01.9](04_reglas/CU-01_ReportarEventoVial.md)).

### Consulta de eventos (`GET /eventos`)

La primera consulta va sin parámetros y trae todos los eventos vigentes. Las siguientes mandan la marca que recibió la anterior, `GET /eventos?desde=2026-11-01T11:59:58Z`, y traen solo los cambios:

```json
{"marca":"2026-11-01T12:00:03Z","completa":false,"eventos":[{"id":153,"tipo":"CONTROL_POLICIAL","ubicacion":{"latitud":-21.5355,"longitud":-64.7296},"momentoOcurrencia":"2026-11-01T07:55:00-04:00","estado":"CONFIRMADO","confirmado":true,"origen":"CONDUCTOR"}],"quitados":[150]}
```

- `completa` es verdadero cuando la respuesta trae todos los vigentes (la consulta sin marca); entonces `quitados` va vacío.
- Con marca, `eventos` trae los nuevos y los que cambiaron (por ejemplo, los que se confirmaron) y `quitados` los identificadores de los que dejaron de mostrarse por vencidos o retirados. La app reemplaza los que ya tenía con el mismo `id` y borra los quitados ([RN-02.6](04_reglas/CU-02_VisualizarEventosViales.md)).
- La vigencia se calcula en cada consulta: un evento que venció hace segundos ya llega entre los quitados, aunque la tarea periódica todavía no lo haya marcado ([RN-02.4](04_reglas/CU-02_VisualizarEventosViales.md)).
- La marca queda 2 segundos antes de la consulta, para no perder un evento que se estaba guardando en ese momento; por eso algún cambio puede llegar dos veces. Una marca que no es una fecha responde 400 `PETICION_INVALIDA`.

### Telemetría (`POST /telemetria`)

Mientras navega, la app manda sus lecturas en grupos ([D-07](08_decisiones_parametros.md)):

```json
{"lecturas":[
  {"ubicacion":{"latitud":-21.5400,"longitud":-64.7380},"precisionM":8,"momento":"2026-11-14T10:00:00-04:00"},
  {"ubicacion":{"latitud":-21.5400,"longitud":-64.73794},"precisionM":6.5,"momento":"2026-11-14T10:00:03-04:00"}]}
```

Responde 202 sin esperar al motor:

```json
{"estado":"RECIBIDO","lecturas":2}
```

- Un grupo trae de 1 a 100 lecturas, cada una con `ubicacion`, `precisionM` (en metros, no negativa) y `momento`. Si falta algo, 400 `PETICION_INVALIDA`. Pide JWT de un conductor que sigue existiendo; si no, 401 ([RN-04.4](04_reglas/CU-04_RecalcularRuta.md)).
- El backend no guarda las lecturas ([RN-04.12](04_reglas/CU-04_RecalcularRuta.md)). Se las pasa al motor con `analizar_telemetria` en un hilo aparte, con un identificador de recorrido en lugar del conductor: un HMAC del conductor con una clave que vive solo en memoria y cambia cada día, así el motor reconoce el mismo recorrido aunque se renueve el JWT, pero no sabe de quién es. Si el motor no está, ese grupo se pierde y la app no se entera: la telemetría no se repite.
- Cuando el motor informa una congestión, el backend crea un evento Congestión con origen Telemetría, el nivel y el tramo, sin reportante ([RN-04.10](04_reglas/CU-04_RecalcularRuta.md)). Si ese tramo ya tiene una congestión vigente, no crea otra: mueve su momento y su nivel ([RN-04.11](04_reglas/CU-04_RecalcularRuta.md)). En los dos casos se lo avisa al motor con `actualizar_trafico` y la congestión aparece en `GET /eventos` como cualquier evento.

### Votos (`POST /eventos/{id}/votos`)

```json
{"valor":"real"}
```

Responde 201 con el estado del evento después del voto, sin datos de quién votó:

```json
{"eventoId":153,"estado":"CONFIRMADO","confirmado":true}
```

- `valor` es `real` o `falso`, sin importar mayúsculas. Con 3 votos `falso`, un evento Activo pasa a Retirado; con 3 `real`, pasa a Confirmado y ya no se retira por votos ([RN-02.13](04_reglas/CU-02_VisualizarEventosViales.md), [RN-02.14](04_reglas/CU-02_VisualizarEventosViales.md)).
- Rechazos: 400 `PETICION_INVALIDA` si el voto falta o no es `real` ni `falso`; 404 `EVENTO_NO_ENCONTRADO`; 409 `VOTO_REPETIDO` si el conductor ya votó ese evento; 422 `VOTO_NO_PERMITIDO` para la congestión que detecta el sistema ([D-20](08_decisiones_parametros.md)), `EVENTO_NO_VIGENTE` si ya está retirado o vencido y `VOTO_PROPIO` si lo reportó el mismo conductor ([RN-02.10](04_reglas/CU-02_VisualizarEventosViales.md) a [RN-02.12](04_reglas/CU-02_VisualizarEventosViales.md)).

### Surtidores (`GET /surtidores`)

La app manda su ubicación actual: `GET /surtidores?latitud=-21.5355&longitud=-64.7296`. Responde los surtidores del catálogo, del más cercano al más lejano ([RN-05.2](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md)):

```json
{"surtidores":[
  {"id":7,"nombre":"Ex Terminal","ubicacion":{"latitud":-21.54194,"longitud":-64.72413},"distanciaM":913,
   "informacionReciente":true,
   "combustibles":[
     {"tipo":"DIESEL","nombre":"Diesel","disponible":true,"origen":"ANH","fecha":"2026-11-14T08:00:00-04:00",
      "litros":1250.5,"estadoAnh":"ALTO","consultadoEn":"2026-11-14T16:30:00-04:00"},
     {"tipo":"GASOLINA","nombre":"Gasolina","disponible":true,"origen":"CONDUCTORES","fecha":"2026-11-14T10:30:00-04:00"}]},
  {"id":2,"nombre":"Don Daniel","ubicacion":{"latitud":-21.5133312,"longitud":-64.7382423},"distanciaM":2623,
   "informacionReciente":false,"combustibles":[]}]}
```

- La distancia se mide en PostGIS, en metros. Hay un dato por tipo de combustible: el vigente más reciente de WhatsApp, Conductores o ANH ([RN-05.3](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md)). Con la misma fecha gana ANH, después WhatsApp y al final Conductores, porque una confirmación se ve solo si es más reciente que las otras dos fuentes ([RN-05.15](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md)).
- Un dato vale 6 horas: desde su `fecha` en WhatsApp y Conductores, y desde `consultadoEn` en ANH ([RN-05.5](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md), [RN-09.17](04_reglas/CU-09_ConsultarEstadoANH.md)). Un surtidor sin datos vigentes trae `informacionReciente` falso y la lista vacía: la app muestra «sin información reciente».
- `fila` sale solo si se conoce. `litros`, `estadoAnh` y `consultadoEn` salen solo en los datos de ANH ([RN-05.4](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md)). Un dato de WhatsApp sin tipo sale como `DESCONOCIDO` ([RN-05.6](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md)). Nunca sale quién aportó el dato ([RN-05.7](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md)).
- Pide JWT de conductor. Sin `latitud` o `longitud`, o con valores fuera de rango, responde 400 `PETICION_INVALIDA`.

## Backend con el motor core

El backend nunca habla directo con el programa en C++. Llama a métodos del wrapper `MotorDeRutas`, que es Java y vive dentro del backend, y el wrapper traduce cada llamada a un mensaje para el núcleo C++ ([D-15](08_decisiones_parametros.md)). Los nombres de métodos y mensajes son una propuesta, igual que las rutas de la API.

### Operaciones

| Operación | Método del wrapper | Mensaje al núcleo | Qué envía | Qué recibe | Reglas |
| --- | --- | --- | --- | --- | --- |
| Calcular ruta | `calcularRuta` | `calcular_ruta` | Origen, destino y paradas | Ruta con tramos, distancia y tiempo; «sin ruta» o «punto inválido» | [RN-03.4](04_reglas/CU-03_CalcularRutaOptima.md) a [RN-03.14](04_reglas/CU-03_CalcularRutaOptima.md) |
| Revisar ruta | `revisarRuta` | `revisar_ruta` | Posición, tramos que faltan, destino, paradas que faltan y eventos ya avisados | Sin cambios; sin alternativa; o ruta alternativa con su costo. Las dos últimas traen el evento que las provocó. Si los tramos ya no existen, «ruta desactualizada» | [RN-04.14](04_reglas/CU-04_RecalcularRuta.md) a [RN-04.16](04_reglas/CU-04_RecalcularRuta.md) |
| Actualizar tráfico | `actualizarTrafico` | `actualizar_trafico` | Evento que nace, cambia su vencimiento, se retira o vence: identificador, tipo, nivel si es congestión, ubicación y vencimiento | Tramos afectados; los dos sentidos de la calle salvo en la congestión ([D-18](08_decisiones_parametros.md)) | [RN-03.9](04_reglas/CU-03_CalcularRutaOptima.md), [D-14](08_decisiones_parametros.md) |
| Analizar telemetría | `analizarTelemetria` | `analizar_telemetria` | Lecturas con un identificador temporal de recorrido y la marca de simulado, sin datos del conductor | Congestiones detectadas: tramo, punto, nivel y si salen de recorridos simulados | [RN-04.5](04_reglas/CU-04_RecalcularRuta.md) a [RN-04.10](04_reglas/CU-04_RecalcularRuta.md) |
| Consultar estado | `consultarEstado` | `consultar_estado` | Nada | Si la red vial está cargada, la fecha del extracto y cuántos nodos y tramos tiene | [D-08](08_decisiones_parametros.md) |

### Cómo viajan los mensajes por el pipe

- Al iniciar, el backend arranca el núcleo C++ como un proceso hijo. No hay puertos ni red de por medio.
- Cada petición es una línea de JSON que el wrapper escribe en la entrada estándar del núcleo. Cada respuesta es una línea de JSON que el núcleo escribe en su salida estándar.
- Toda petición lleva un `id` y una `operacion`; la respuesta repite el mismo `id`.
- El núcleo atiende una petición a la vez. Si llegan varias juntas, el wrapper las pone en fila.
- Lo que el núcleo escribe en su salida de errores va al registro del backend y nunca se mezcla con las respuestas.

Una petición de ruta y su respuesta se ven así. En el pipe, cada una ocupa una sola línea:

```json
{"id":17,"operacion":"calcular_ruta","origen":[-21.5355,-64.7296],"destino":[-21.5300,-64.7260],"paradas":[]}
{"id":17,"ok":true,"estado":"ENCONTRADA","distancia_m":755,"tiempo_s":136,"trazado":[[-21.5355,-64.7296],[-21.5348,-64.7280],[-21.5300,-64.7260]],"tramos":[{"id":"osm:5001:1:ida","calle":"Calle Comercio"},{"id":"osm:5002:1:ida","calle":"Av. Las Américas"}]}
```

El campo `estado` puede ser `ENCONTRADA`, `SIN_RUTA` o `PUNTO_INVALIDO`; los dos últimos son respuestas correctas, no fallos, y no traen más campos. Una ruta encontrada trae la distancia en metros, el tiempo estimado en segundos, el trazado como una lista de puntos `[latitud, longitud]` y, en orden, cada tramo con su `id` y el nombre de su `calle`; esos `id` son los que la app devuelve al revisar la ruta ([D-12](08_decisiones_parametros.md)). `paradas` puede faltar o venir vacía. `"ok":false` queda para un mensaje mal formado (`PETICION_INVALIDA`), para un núcleo que todavía no tiene la red vial (`RED_NO_CARGADA`) o para un fallo interno del núcleo, y entonces la respuesta trae un campo `error` con un código y una descripción.

Un aviso de tráfico y su respuesta:

```json
{"id":18,"operacion":"actualizar_trafico","evento":"153","accion":"nace","tipo":"ACCIDENTE","ubicacion":[-21.5352,-64.7288],"vence":"2026-10-06T16:02:00-04:00"}
{"id":18,"ok":true,"tramos":["osm:5001:1:ida","osm:5001:1:vuelta"]}
```

- `accion` es `nace`, `cambia_vencimiento`, `retira` o `vence`. Las dos primeras guardan el evento; un aviso nuevo del mismo `evento` reemplaza al anterior, así que reenviar los eventos vigentes al arrancar no los duplica. Las dos últimas lo quitan y solo necesitan `evento`.
- `tipo` es el código del catálogo: `ACCIDENTE`, `CONTROL_POLICIAL`, `BLOQUEO`, `OTRO` o `CONGESTION`. Una congestión lleva además `nivel` (`CONGESTIONADO` o `MUY_CONGESTIONADO`) y `tramo`, el id que informó `analizar_telemetria`, para que afecte solo ese sentido ([D-18](08_decisiones_parametros.md)); sin `tramo`, se toma el tramo más cercano a la ubicación, en un solo sentido.
- `vence` va en ISO 8601 con zona horaria. Un evento cuyo vencimiento ya pasó deja de contar aunque el aviso `vence` no haya llegado.
- La respuesta trae los tramos que el evento afecta, o los que afectaba si se quitó. Un evento a más de 100 m de una calle, o que ya venció, no afecta ninguno ([RN-03.9](04_reglas/CU-03_CalcularRutaOptima.md)).

### Arranque, demoras y caídas

- **Arranque.** Cada vez que el wrapper arranca el núcleo, espera a que cargue la red vial (mensaje `consultar_estado`) y le envía todos los eventos vigentes con `actualizar_trafico`. Recién entonces atiende rutas. Así el núcleo nunca empieza con el tráfico en blanco y nunca necesita llamar al backend.
- **Configuración.** El backend arranca el núcleo solo si `MOTOR_HABILITADO=true`; apagado, las operaciones responden 503. El ejecutable y el archivo de la red vial vienen de `MOTOR_PATH` y `RED_VIAL_PATH`, y el wrapper le pasa la ruta de la red al núcleo con `--red <ruta>` ([D-35](08_decisiones_parametros.md)).
- **Primera respuesta.** El núcleo carga la red antes de leer su entrada, así que la respuesta a la primera consulta de estado puede tardar mucho más que 3 segundos. Hasta que la red está cargada, el wrapper espera con su propio plazo ([P-33](08_decisiones_parametros.md)) y no con el de una petición. Si ese plazo vence, detiene el núcleo y lo arranca de nuevo.
- **Respuestas del núcleo.** El wrapper devuelve la respuesta tal como llega, con su `ok`: un `"ok":false` es una respuesta válida del núcleo y quien llama la interpreta. Solo se convierte en error del backend lo que no depende del núcleo: demora, caída o arranque en curso. Eso responde 503 con el código `MOTOR_NO_DISPONIBLE`.
- **Demora.** Si el núcleo no responde una petición a tiempo para cumplir los 3 segundos de [RN-03.15](04_reglas/CU-03_CalcularRutaOptima.md), contados desde que se le escribió, el wrapper lo detiene, lo vuelve a arrancar y el backend responde 503. Una petición que vence mientras espera en la fila responde 503 sin reiniciar el núcleo.
- **Caída.** Si el núcleo termina por un error, el wrapper lo arranca de nuevo y le vuelve a enviar los eventos vigentes. Mientras tanto, las rutas responden 503 de inmediato, sin esperar en la fila.

## Archivo de la red vial

Un script de Python (`04_imp/motor_cpp/preparacion`, T-111) arma este archivo a partir de un extracto de OpenStreetMap, y el núcleo C++ lo carga completo al arrancar (T-096, [D-08](08_decisiones_parametros.md), [D-33](08_decisiones_parametros.md)). Es lo único que une a las dos piezas, así que cada una puede avanzar por su lado mientras respete lo que sigue. La versión actual del formato es la 1.

### Forma general

- Un solo archivo JSON en UTF-8 llamado `red_vial_tarija.json`.
- No va al repositorio: se genera con el script y en el servidor vive fuera de él. El núcleo recibe la ruta con el argumento `--red <ruta>`; el wrapper `MotorDeRutas` se la pasa al arrancarlo. Sin ese argumento, el núcleo arranca sin red y `consultar_estado` responde `"red_cargada":false`.
- Los datos son de los colaboradores de OpenStreetMap (ODbL), y el archivo lo dice en `fuente`.

Un ejemplo pequeño, con valores inventados: una calle de doble sentido (dos tramos) y una avenida de un solo sentido (un tramo), unidas en el nodo `osm:1002`:

```json
{
  "version": 1,
  "fuente": "OpenStreetMap",
  "fecha_extracto": "2026-10-28",
  "nodos": [
    {"id": "osm:1001", "lat": -21.5339, "lon": -64.7342},
    {"id": "osm:1002", "lat": -21.5339, "lon": -64.7334},
    {"id": "osm:1003", "lat": -21.5345, "lon": -64.7338}
  ],
  "tramos": [
    {"id": "osm:5001:1:ida", "desde": "osm:1001", "hasta": "osm:1002", "opuesto": "osm:5001:1:vuelta",
     "nombre": "Calle de ejemplo", "tipo_via": "zona_residencial", "longitud_m": 93.944,
     "geometria": [[-21.5339, -64.7342], [-21.5341, -64.7338], [-21.5339, -64.7334]]},
    {"id": "osm:5001:1:vuelta", "desde": "osm:1002", "hasta": "osm:1001", "opuesto": "osm:5001:1:ida",
     "nombre": "Calle de ejemplo", "tipo_via": "zona_residencial", "longitud_m": 93.944,
     "geometria": [[-21.5339, -64.7334], [-21.5341, -64.7338], [-21.5339, -64.7342]]},
    {"id": "osm:5002:1:ida", "desde": "osm:1002", "hasta": "osm:1003", "opuesto": null,
     "nombre": "Avenida de ejemplo", "tipo_via": "avenida_principal", "longitud_m": 78.505,
     "geometria": [[-21.5339, -64.7334], [-21.5342, -64.7336], [-21.5345, -64.7338]]}
  ]
}
```

### Campos

| Dónde | Campo | Qué es |
| --- | --- | --- |
| Archivo | `version` | Número entero. Hoy, 1 |
| Archivo | `fuente` | Siempre `OpenStreetMap` |
| Archivo | `fecha_extracto` | Fecha del extracto de OpenStreetMap, como `AAAA-MM-DD`. Es la que informa `consultar_estado`; nunca es la fecha en que se corrió el script |
| Archivo | `nodos`, `tramos` | Las dos listas del archivo |
| Nodo | `id` | Texto. `osm:<número>` si viene de OpenStreetMap, `corte:...` si lo creó el recorte |
| Nodo | `lat`, `lon` | Grados decimales (WGS84) |
| Tramo | `id` | Texto, con la forma `osm:<vía>:<fragmento>:ida` o `...:vuelta` |
| Tramo | `desde`, `hasta` | `id` de dos nodos de la lista. El tramo solo se recorre de `desde` a `hasta` |
| Tramo | `opuesto` | `id` del tramo que recorre la misma calle en sentido contrario, o `null` si la calle es de un solo sentido |
| Tramo | `nombre` | Nombre de la calle. Si OpenStreetMap no trae nombre ni referencia, `Sin nombre` |
| Tramo | `tipo_via` | `via_rapida`, `avenida_principal` o `zona_residencial`. La tabla que los relaciona con las etiquetas `highway` está en el README del script |
| Tramo | `longitud_m` | Metros, con tres decimales |
| Tramo | `geometria` | Los puntos del trazado de `desde` a `hasta`, cada uno como `[latitud, longitud]`, con los dos extremos iguales a los nodos |

### Reglas del archivo

- **Sentido.** Una calle de doble sentido son dos tramos y una de un solo sentido es uno ([RN-03.6](04_reglas/CU-03_CalcularRutaOptima.md)).
- **Los dos sentidos de una calle.** Cada tramo de una calle de doble sentido apunta al otro con `opuesto`, y el otro apunta de vuelta. Con eso el núcleo sabe a cuáles afecta un evento ([D-18](08_decisiones_parametros.md)) y a cuál asignar una lectura ([RN-04.8](04_reglas/CU-04_RecalcularRuta.md)). `ida` y `vuelta` en el `id` solo indican el sentido respecto a cómo dibuja la calle OpenStreetMap: el núcleo no debe leer el `id` para esto.
- **Longitud.** Es el recorrido real, sumando los segmentos de `geometria`, y nunca es menor que la línea recta entre los extremos ([RN-03.7](04_reglas/CU-03_CalcularRutaOptima.md)).
- **Qué no trae el archivo.** Ni la velocidad esperada de cada tipo de vía (la pone el núcleo, [RN-03.14](04_reglas/CU-03_CalcularRutaOptima.md)) ni el nivel de tráfico (lo calcula el núcleo con los eventos vigentes, [RN-03.9](04_reglas/CU-03_CalcularRutaOptima.md)).
- **Nodos.** Son intersecciones, finales de calle y puntos de corte donde una calle cruza el borde de la mancha urbana (`corte:...`). Dos calles que solo coinciden en el borde no se conectan entre sí.
- **Orden de las coordenadas.** Todo par de coordenadas va como `[latitud, longitud]`, igual que los mensajes por pipe de este documento. Los nodos llevan `lat` y `lon` con nombre, así que no hay duda. El GeoJSON de la mancha urbana usa el orden estándar `[longitud, latitud]`, pero el núcleo no lo lee.
- **Identificadores.** Un `id` de tramo solo vale dentro de su archivo. Con un extracto nuevo el número de fragmento puede cambiar, y un `id` viejo podría apuntar a otro fragmento. Para saber si una ruta activa quedó vieja conviene comparar también `fecha_extracto` (se decide en el issue de `revisar_ruta`).

### Qué hace el núcleo al cargar

- Lee `version`. Si no es 1, no carga la red, lo anota en su salida de errores y `consultar_estado` sigue respondiendo `"red_cargada":false`. Lo mismo si el archivo falta o no se puede leer.
- Ignora los campos que no conoce. Agregar un campo nuevo no obliga a subir la versión; cambiar o quitar uno existente sí.
- Con la red cargada, `consultar_estado` informa `fecha_extracto`, `nodos` y `tramos`. Sin red: `null`, `0` y `0`.

### Cómo se genera y se comprueba

`preparar_red.py` escribe el archivo y, antes de reemplazar el anterior, lo revisa con `validar_red.py`: que los nodos y tramos no se repitan, que cada tramo apunte a nodos que existen, que cada `opuesto` sea recíproco (extremos cruzados, geometría inversa, misma longitud, nombre y tipo de vía), que todo quede dentro de la mancha urbana y que las longitudes coincidan con la geometría. Si algo falla, el archivo anterior se queda como estaba. Para cambiar el formato hay que subir `version` y actualizar en el mismo cambio esta sección, el script y el validador.

## Servicio de WhatsApp/LLM con el backend

### Credencial de servicio

- El servicio envía en cada petición `Authorization: Bearer <credencial>`, la misma cabecera que usa la app.
- En el backend la variable se llama `SERVICE_CREDENTIAL`; en el servicio, `SERVICE_TOKEN`. Los dos nombres guardan el mismo valor.
- La credencial es un valor fijo y aleatorio. Vive como variable de entorno en el backend y en el servicio, con el mismo valor, y nunca en el repositorio ([D-13](08_decisiones_parametros.md)).
- El backend la distingue así: si coincide con la configurada, es el servicio; si no coincide pero es un JWT de conductor válido, responde 403; si no es ninguna de las dos, responde 401. Como la credencial es fija, no vence: se cambia a mano en los dos lados.
- El servicio llama al backend en `http://127.0.0.1:8080`, dentro de la máquina (el puerto por defecto de Spring Boot). Ese puerto no se abre a internet: Nginx es lo único que sale ([D-03](08_decisiones_parametros.md)).

### Operaciones

| Operación | Método y ruta | Qué envía | Qué recibe | Reglas |
| --- | --- | --- | --- | --- |
| Leer catálogo de surtidores | `GET /monitoreo/surtidores` | Credencial de servicio | Nombres y alias de los surtidores, tipos de combustible y valores de la fila | [RN-06.7](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md), [RN-06.8](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) |
| Entregar resultado | `POST /monitoreo/resultados` | Credencial de servicio y la lista de avisos del lote (hasta 1000) | Cuántos avisos se aceptaron y cuáles se descartaron, con su posición en el lote y el motivo | [RN-06.14](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) a [RN-06.17](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) |

El servicio toma del backend el catálogo que entrega al LLM. Así hay una sola lista de surtidores y de combustibles en todo el sistema.

### Catálogo (`GET /monitoreo/surtidores`)

La lista de combustibles trae los cuatro del catálogo. «Desconocido» no va en la lista: es un valor fijo de [RN-06.8](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md). Los valores de `filas` son los de [P-15](08_decisiones_parametros.md). Los alias de este ejemplo son inventados. Los alias reales llegan como los guarda el catálogo: en minúsculas, sin tildes ni signos.

```json
{
  "surtidores": [
    {"nombre": "Las Vegas", "alias": ["Vegas", "Las Vegas Express"]},
    {"nombre": "El Portillo", "alias": []}
  ],
  "combustibles": ["Gasolina", "Diesel", "Gasolina Premium", "Diesel ULS"],
  "filas": ["SIN_FILA", "CORTA", "MEDIA", "LARGA"]
}
```

### Resultado (`POST /monitoreo/resultados`)

Un resultado tiene esta forma:

```json
{
  "lote": "2026-10-06T15:05:00-04:00",
  "avisos": [
    {
      "surtidor": "Las Vegas",
      "combustible": "Gasolina",
      "estado": "DISPONIBLE",
      "fila": "CORTA",
      "fecha": "2026-10-06T15:02:00-04:00"
    },
    {
      "surtidor": "El Portillo",
      "combustible": "Desconocido",
      "estado": "AGOTADO",
      "fecha": "2026-10-06T15:03:30-04:00"
    }
  ]
}
```

- `SIN_FILA` significa que el mensaje dice que no hay fila. Si el campo `fila` no está, el mensaje no la menciona. Un valor de `fila` desconocido se acepta, pero el aviso se guarda sin fila.
- Los mensajes descartados por el LLM no viajan al backend.
- Un lote tiene como máximo 1000 avisos; uno más grande, o un cuerpo sin la lista `avisos`, responde 400 `PETICION_INVALIDA` y no guarda nada.
- El surtidor se busca por nombre o alias, y el combustible por nombre o código, sin importar mayúsculas, tildes ni signos; «Desconocido» es válido. El estado también se acepta en minúsculas.
- La fecha debe traer la zona horaria y no puede estar más de 5 minutos en el futuro, por la diferencia entre relojes; si no, `FECHA_INVALIDA`. Si falta el surtidor, el combustible, el estado o la fecha, o no son texto, o el aviso no es un objeto, `FORMATO_INVALIDO`.

La respuesta es siempre 200 con un cuerpo. `indice` es la posición del aviso en el lote, empezando en 0. Ejemplo para el lote de arriba si «El Portillo» no estuviera en el catálogo:

```json
{
  "aceptados": 1,
  "descartados": [
    {"indice": 1, "motivo": "SURTIDOR_DESCONOCIDO"}
  ]
}
```

- Un aviso válido pero más viejo que el dato actual cuenta como aceptado: no cambia nada ([RN-06.17](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md)). «Descartado» queda solo para los avisos inválidos.
- Los motivos de descarte son una lista fija ([RN-06.15](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md)): `SURTIDOR_DESCONOCIDO`, `COMBUSTIBLE_INVALIDO`, `ESTADO_INVALIDO`, `FECHA_INVALIDA` y `FORMATO_INVALIDO`.
- Los errores usan el cuerpo `{codigo, mensaje}`, como el resto de la API.

### Qué hace el servicio con cada respuesta

Se aplica [RN-06.19](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md). El reenvío es seguro porque [RN-06.17](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) evita que un aviso repetido cambie algo.

| Respuesta | Qué hace el servicio |
| --- | --- |
| 200 | Terminó, aunque haya descartados. Lo descartado no se reenvía |
| 5xx, sin conexión o tiempo agotado | Guarda el resultado (sin el texto de los mensajes) y reenvía el mismo lote en el ciclo siguiente |
| 400 o 422 | No reenvía, porque se atascaría para siempre. Lo registra y lo suelta |
| 401 o 403 | Es un error de configuración. Conserva el resultado y reintenta mientras se corrige |

## Backend con la API de ANH

El backend consulta a ANH Abastecimiento, el servicio público de la Agencia Nacional de Hidrocarburos. Es el único componente que lo hace ([D-29](08_decisiones_parametros.md)): la app, el simulador y el servicio de WhatsApp/LLM nunca llaman a ANH. Aquí el backend es el cliente, así que el contrato dice qué pide, qué lee, cuándo acepta una respuesta y qué hace cuando algo sale mal. Lo que ANH responde no es una promesa: ANH no publica condiciones de uso, cuotas ni disponibilidad garantizada, y su respuesta puede cambiar sin aviso. Por eso el backend valida todo lo que recibe.

- La base es `https://abastecimiento.prod.anh.gob.bo/api/`.
- Todas las llamadas son `GET`, sin cuerpo, sin credencial y sin ningún dato de personas. Piden JSON y se identifican con el nombre del programa.
- Es HTTPS con TLS 1.2 como mínimo. Se verifican el certificado, su vigencia y el nombre del servidor, y esa verificación no se desactiva nunca ([RN-09.3](04_reglas/CU-09_ConsultarEstadoANH.md)). Una redirección fuera del servidor de ANH, o a HTTP, se detiene.
- El prototipo de `05_pro/PruebaApiANH` ya probó estas rutas con la consulta del departamento de Tarija: 31 estaciones y 57 registros de estación y combustible.

### Rutas que se usan

| Ruta | Para qué | Cuándo |
| --- | --- | --- |
| `GET /departamentos/?producto=0` | Identificar a Tarija en el catálogo de departamentos, buscando la única entrada con el nombre «Tarija». No se supone ningún identificador | Al empezar cada ciclo |
| `GET /departamentos/?producto=1` | Lo mismo, si el primer catálogo no permite resolver a Tarija por un error que no sea de TLS | Solo como alternativa |
| `GET /estaciones/?departamento={id}&producto=0` | Estaciones de Tarija con su saldo de gasolina | Cada ciclo |
| `GET /estaciones/?departamento={id}&producto=1` | Estaciones de Tarija con su saldo de diésel | Cada ciclo |

`{id}` es el identificador que devolvió el catálogo. Un ciclo sin errores hace tres peticiones: un catálogo y dos listados. El prototipo también conoce `GET /estaciones/{id}`, `GET /estaciones/{id}/estado`, `GET /Disclaimer` y `GET /precios/1`, pero el backend no las usa: el detalle de una estación solo sirve para completar el nombre o la ubicación, y el vínculo con el catálogo ya los tiene. Los productos 2 y 3 (Gasolina Premium y Diesel ULS) no se piden, porque no quedaron acreditados en la consulta de prueba.

### Qué se lee de cada estación

| Campo de ANH | Para qué se usa |
| --- | --- |
| `id` | Identificar la estación y buscar el surtidor vinculado (`anh_id`). Debe ser un número entero |
| `departamento_id` | Comprobar que la estación es del departamento pedido, cuando viene |
| `lat`, `lng` | Comprobar que la estación cae dentro de la mancha urbana y, para vincularla, compararla con el catálogo. No se guardan |
| `producto_id`, `producto_nombre` | Comprobar que el registro es del combustible que se pidió. El tipo del dato sale del código que se pidió: 0, Gasolina; 1, Diesel |
| `saldo_litros` | Los litros. Si no es un número válido o es negativo, el dato es «sin saldo» |
| `saldo_estado` | El estado de ANH: alto, medio o bajo. Se guarda como llega |
| `updated_at` | La fecha del dato |

Algunas respuestas traen los datos del combustible dentro de una lista `productos`; de ahí se toma solo el combustible pedido. Cualquier otro campo se ignora. En particular, la fecha de última venta y el indicador de venta se descartan apenas llegan, y no se lee ni se guarda la dirección ([RN-09.18](04_reglas/CU-09_ConsultarEstadoANH.md)). ANH no informa cuándo llegó combustible a una estación, y el sistema no inventa ese dato.

### Cuándo se acepta una respuesta

- Es HTTP 200, de contenido JSON y de hasta 10 MiB.
- Es un objeto con `decCodigo` igual a 1 y la lista de estaciones en `oResultado`. Un `decCodigo` distinto de 1 es un error de ANH, nunca una lista vacía ([RN-09.5](04_reglas/CU-09_ConsultarEstadoANH.md)).
- Se descarta la respuesta completa si trae estaciones de otro departamento o de otro combustible que el pedido, una estación repetida, un identificador que no es un número entero o señales de paginación, porque el listado estaría incompleto ([RN-09.6](04_reglas/CU-09_ConsultarEstadoANH.md)).
- Un saldo inválido solo anula el dato de esa estación ([RN-09.7](04_reglas/CU-09_ConsultarEstadoANH.md)).
- Una estación que no aparece en la respuesta no significa que tenga cero litros: conserva su último dato.

### Espera, reintentos y `Retry-After`

Los valores de partida son los del cliente de prueba ([P-32](08_decisiones_parametros.md)): 12 segundos de espera por petición, 1 reintento y 1 segundo de pausa entre peticiones. Son decisiones del programa, no cuotas publicadas por ANH.

- Se reintenta solo cuando falta conexión, se agota el tiempo o ANH responde 429, 502, 503 o 504. Entre un intento y otro se espera, como mínimo, lo que pida `Retry-After`, que puede venir en segundos o como fecha.
- Si `Retry-After` pide más de 30 segundos, no se reintenta y el backend no vuelve a consultar hasta pasado ese plazo.
- Cada combustible se pide por separado. Si uno falla, el otro se actualiza igual ([RN-09.16](04_reglas/CU-09_ConsultarEstadoANH.md)).

### Qué hace el backend con cada respuesta

Se aplican [RN-09.4](04_reglas/CU-09_ConsultarEstadoANH.md), [RN-09.5](04_reglas/CU-09_ConsultarEstadoANH.md), [RN-09.6](04_reglas/CU-09_ConsultarEstadoANH.md) y [RN-09.15](04_reglas/CU-09_ConsultarEstadoANH.md). Con cualquier error, cada surtidor conserva su último dato de ANH con su fecha, y el registro del backend anota el motivo ([RN-09.19](04_reglas/CU-09_ConsultarEstadoANH.md)).

| Respuesta | Qué hace el backend |
| --- | --- |
| 200 y respuesta válida | Actualiza los surtidores vinculados, según [RN-09.8](04_reglas/CU-09_ConsultarEstadoANH.md) a [RN-09.14](04_reglas/CU-09_ConsultarEstadoANH.md) |
| 200 pero no es JSON, `decCodigo` distinto de 1, forma inesperada, paginación o más de 10 MiB | Descarta la respuesta completa y no cambia ningún estado. No reintenta, porque repetirla no la arregla |
| 429, 502, 503 o 504 | Reintenta como dice la sección anterior. Si sigue fallando, termina el ciclo con lo que tiene |
| Otro error HTTP (400, 401, 403, 404, 500...) | No reintenta y lo registra |
| Sin conexión, DNS o tiempo agotado | Reintenta y, si sigue fallando, termina el ciclo con lo que tiene |
| Error de certificado o de TLS | No reintenta, no desactiva la verificación y lo registra como un fallo del servidor o de la conexión |
| Redirección fuera de ANH o a HTTP | Se detiene y lo registra |

El backend no publica ningún endpoint nuevo para ANH: lo que se guarda se ve en `GET /surtidores`.

## Respuestas de rechazo

| Código HTTP | Cuándo se usa | Ejemplo |
| --- | --- | --- |
| 400 | La petición no tiene el formato esperado | Falta el tipo de evento |
| 401 | No hay sesión o está vencida | [RN-08.11](04_reglas/CU-08_IniciarSesion.md) |
| 403 | La credencial no tiene permiso para esa operación | Un conductor llama a una ruta de monitoreo |
| 404 | El evento o el surtidor no existe | Votar un evento ya borrado |
| 409 | Choca con algo que ya existe | Reporte duplicado ([RN-01.8](04_reglas/CU-01_ReportarEventoVial.md)), segundo voto ([RN-02.11](04_reglas/CU-02_VisualizarEventosViales.md)), confirmación repetida ([RN-05.14](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md)) |
| 422 | El formato es correcto pero rompe una regla | Ubicación fuera de Tarija ([RN-01.6](04_reglas/CU-01_ReportarEventoVial.md)), evento ya vencido ([RN-01.7](04_reglas/CU-01_ReportarEventoVial.md)) |
| 429 | Se superó un límite de frecuencia | Más de 5 reportes en 10 minutos ([RN-01.9](04_reglas/CU-01_ReportarEventoVial.md)) |
| 503 | El motor core no responde a tiempo o se está reiniciando | [RN-03.15](04_reglas/CU-03_CalcularRutaOptima.md) |

Que no exista una ruta no es un rechazo: la respuesta es correcta y dice «No se encontró una ruta válida» ([RN-03.12](04_reglas/CU-03_CalcularRutaOptima.md)).
