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
| Sesión de prueba | `POST /sesion/prueba` | Credenciales de prueba | JWT de un conductor simulado | CU-07 |
| Leer catálogos | `GET /catalogos` | Nada | Tipos de evento con color, vigencia y si son reportables; tipos de combustible; tiempos del reporte | CU-01, CU-02 |
| Reportar evento | `POST /eventos` | Tipo, ubicación, hora de confirmación y minutos elegidos | Evento aceptado, o motivo del rechazo | CU-01 |
| Consultar eventos | `GET /eventos` | Marca de la última consulta, opcional | Eventos vigentes o solo los cambios, y la marca nueva | CU-02 |
| Votar un evento | `POST /eventos/{id}/votos` | Real o falso | Estado del evento tras el voto | CU-02 |
| Calcular ruta | `POST /rutas` | Origen, destino y paradas | Trazado, distancia y tiempo; o «sin ruta» | CU-03 |
| Revisar ruta activa | `POST /rutas/revision` | Posición actual y lo que falta de la ruta | Sin cambios, congestión sin alternativa, o ruta alternativa | CU-04 |
| Enviar telemetría | `POST /telemetria` | Grupo de lecturas: posición, precisión y momento | Recibido | CU-04 |
| Consultar surtidores | `GET /surtidores` | Ubicación actual | Surtidores por cercanía, con estado por tipo, fila, origen y fecha | CU-05 |
| Confirmar recarga | `POST /surtidores/{id}/confirmaciones` | Tipo de combustible y ubicación actual | Confirmación aceptada, o motivo del rechazo | CU-05 |

La app nunca envía el momento en que ocurrió un evento ya calculado: manda la hora de confirmación y los minutos elegidos, y el `EventoVial` hace la resta ([RN-01.4](04_reglas/CU-01_ReportarEventoVial.md)).

## Backend con el motor core

Son llamadas HTTP dentro del VPS. Para el resto del modelo quedan ocultas detrás del objeto `MotorDeRutas`.

| Operación | Quién llama | Qué envía | Qué recibe | Reglas |
| --- | --- | --- | --- | --- |
| Calcular ruta | Backend | Origen, destino y paradas | Ruta con tramos, distancia y tiempo; «sin ruta» o «punto inválido» | [RN-03.4](04_reglas/CU-03_CalcularRutaOptima.md) a [RN-03.14](04_reglas/CU-03_CalcularRutaOptima.md) |
| Revisar ruta | Backend | Posición y lo que falta de la ruta | Sin cambios, sin alternativa, o ruta alternativa con su costo | [RN-04.14](04_reglas/CU-04_RecalcularRuta.md) a [RN-04.16](04_reglas/CU-04_RecalcularRuta.md) |
| Actualizar tráfico | Backend | Evento que nace, se retira o vence: identificador, tipo, ubicación y vencimiento | Tramo afectado | [RN-03.9](04_reglas/CU-03_CalcularRutaOptima.md), [D-14](08_decisiones_parametros.md) |
| Analizar telemetría | Backend | Lecturas con un identificador temporal de recorrido, sin datos del conductor | Congestiones detectadas: tramo, punto y nivel | [RN-04.5](04_reglas/CU-04_RecalcularRuta.md) a [RN-04.10](04_reglas/CU-04_RecalcularRuta.md) |
| Consultar estado | Backend | Nada | Si la red vial está cargada y de qué fecha es el extracto | [D-08](08_decisiones_parametros.md) |

Al arrancar, el motor core pide al backend la lista completa de eventos vigentes, para no empezar con el tráfico en blanco.

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
| 503 | El motor core no responde a tiempo | [RN-03.15](04_reglas/CU-03_CalcularRutaOptima.md) |

Que no exista una ruta no es un rechazo: la respuesta es correcta y dice «No se encontró una ruta válida» ([RN-03.12](04_reglas/CU-03_CalcularRutaOptima.md)).
