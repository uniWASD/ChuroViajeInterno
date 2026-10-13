# Contratos entre componentes

[Índice de la arquitectura](README.md)

Hay tres contratos: el de la app con el backend, el del backend con el motor core y el del servicio de WhatsApp/LLM con el backend. Las rutas son una propuesta de nombres; lo que fija cada contrato es qué dato viaja y quién decide.

## Convenciones comunes

- Todo viaja en JSON.
- Las fechas van en formato ISO 8601 con zona horaria, por ejemplo `2026-10-06T15:02:00-04:00`.
- Una ubicación son dos números: latitud y longitud en grados decimales.
- La app y el simulador envían el JWT en la cabecera `Authorization` de cada petición, salvo al iniciar sesión.
- Un rechazo lleva un código y un mensaje en español listo para mostrar al conductor.
- Ninguna respuesta incluye identificadores de otros conductores.

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
| Consultar surtidores | `GET /surtidores` | Ubicación actual | Surtidores por cercanía, con estado por tipo, fila, origen y fecha | CU-05 |
| Confirmar recarga | `POST /surtidores/{id}/confirmaciones` | Tipo de combustible y ubicación actual | Confirmación aceptada, o motivo del rechazo | CU-05 |

La app nunca envía el momento en que ocurrió un evento ya calculado: manda la hora de confirmación y los minutos elegidos, y el `EventoVial` hace la resta ([RN-01.4](04_reglas/CU-01_ReportarEventoVial.md)).

## Backend con el motor core

El backend nunca habla directo con el programa en C++. Llama a métodos del wrapper `MotorDeRutas`, que es Java y vive dentro del backend, y el wrapper traduce cada llamada a un mensaje para el núcleo C++ ([D-15](08_decisiones_parametros.md)). Los nombres de métodos y mensajes son una propuesta, igual que las rutas de la API.

### Operaciones

| Operación | Método del wrapper | Mensaje al núcleo | Qué envía | Qué recibe | Reglas |
| --- | --- | --- | --- | --- | --- |
| Calcular ruta | `calcularRuta` | `calcular_ruta` | Origen, destino y paradas | Ruta con tramos, distancia y tiempo; «sin ruta» o «punto inválido» | [RN-03.4](04_reglas/CU-03_CalcularRutaOptima.md) a [RN-03.14](04_reglas/CU-03_CalcularRutaOptima.md) |
| Revisar ruta | `revisarRuta` | `revisar_ruta` | Posición, tramos que faltan, destino, paradas que faltan y eventos ya avisados | Sin cambios; sin alternativa; o ruta alternativa con su costo. Las dos últimas traen el evento que las provocó. Si los tramos ya no existen, «ruta desactualizada» | [RN-04.14](04_reglas/CU-04_RecalcularRuta.md) a [RN-04.16](04_reglas/CU-04_RecalcularRuta.md) |
| Actualizar tráfico | `actualizarTrafico` | `actualizar_trafico` | Evento que nace, cambia su vencimiento, se retira o vence: identificador, tipo, nivel si es congestión, ubicación y vencimiento | Tramos afectados; los dos sentidos de la calle salvo en la congestión ([D-18](08_decisiones_parametros.md)) | [RN-03.9](04_reglas/CU-03_CalcularRutaOptima.md), [D-14](08_decisiones_parametros.md) |
| Analizar telemetría | `analizarTelemetria` | `analizar_telemetria` | Lecturas con un identificador temporal de recorrido y la marca de simulado, sin datos del conductor | Congestiones detectadas: tramo, punto, nivel y si salen de recorridos simulados | [RN-04.5](04_reglas/CU-04_RecalcularRuta.md) a [RN-04.10](04_reglas/CU-04_RecalcularRuta.md) |
| Consultar estado | `consultarEstado` | `consultar_estado` | Nada | Si la red vial está cargada y de qué fecha es el extracto | [D-08](08_decisiones_parametros.md) |

### Cómo viajan los mensajes por el pipe

- Al iniciar, el backend arranca el núcleo C++ como un proceso hijo. No hay puertos ni red de por medio.
- Cada petición es una línea de JSON que el wrapper escribe en la entrada estándar del núcleo. Cada respuesta es una línea de JSON que el núcleo escribe en su salida estándar.
- Toda petición lleva un `id` y una `operacion`; la respuesta repite el mismo `id`.
- El núcleo atiende una petición a la vez. Si llegan varias juntas, el wrapper las pone en fila.
- Lo que el núcleo escribe en su salida de errores va al registro del backend y nunca se mezcla con las respuestas.

Una petición de ruta y su respuesta se ven así. En el pipe, cada una ocupa una sola línea:

```json
{"id":17,"operacion":"calcular_ruta","origen":[-21.5355,-64.7296],"destino":[-21.5300,-64.7260],"paradas":[]}
{"id":17,"ok":true,"estado":"ENCONTRADA","distancia_m":755,"tiempo_s":150,"trazado":[[-21.5355,-64.7296],[-21.5348,-64.7280],[-21.5300,-64.7260]]}
```

El campo `estado` puede ser `ENCONTRADA`, `SIN_RUTA` o `PUNTO_INVALIDO`; los dos últimos son respuestas correctas, no fallos. `"ok":false` queda para un mensaje mal formado o un fallo interno del núcleo, y entonces la respuesta trae un campo `error` con un código y una descripción.

### Arranque, demoras y caídas

- **Arranque.** Cada vez que el wrapper arranca el núcleo, espera a que cargue la red vial (mensaje `consultar_estado`) y le envía todos los eventos vigentes con `actualizar_trafico`. Recién entonces atiende rutas. Así el núcleo nunca empieza con el tráfico en blanco y nunca necesita llamar al backend.
- **Demora.** Si el núcleo no responde una petición a tiempo para cumplir los 3 segundos de [RN-03.15](04_reglas/CU-03_CalcularRutaOptima.md), contados desde que se le escribió, el wrapper lo detiene, lo vuelve a arrancar y el backend responde 503. Una petición que vence mientras espera en la fila responde 503 sin reiniciar el núcleo.
- **Caída.** Si el núcleo termina por un error, el wrapper lo arranca de nuevo y le vuelve a enviar los eventos vigentes. Mientras tanto, las rutas responden 503.

## Servicio de WhatsApp/LLM con el backend

| Operación | Método y ruta | Qué envía | Qué recibe | Reglas |
| --- | --- | --- | --- | --- |
| Leer catálogo de surtidores | `GET /monitoreo/surtidores` | Credencial de servicio | Nombres y alias de los surtidores, y tipos de combustible | [RN-06.7](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md), [RN-06.8](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) |
| Entregar resultado | `POST /monitoreo/resultados` | Credencial de servicio y la lista de avisos del lote | Cuántos avisos se aceptaron y cuáles se descartaron, con el motivo | [RN-06.14](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) a [RN-06.17](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) |

El servicio toma del backend el catálogo que entrega al LLM. Así hay una sola lista de surtidores y de combustibles en todo el sistema. Un resultado tiene esta forma:

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

El campo `fila` solo aparece cuando el mensaje la menciona. Los mensajes descartados por el LLM no viajan al backend.

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
