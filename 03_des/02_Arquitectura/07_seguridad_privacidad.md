# Seguridad y privacidad

[Índice de la arquitectura](README.md)

La seguridad se apoya en tres ideas: nadie entra sin identificarse, el backend desconfía de todo lo que recibe y el sistema guarda lo mínimo sobre las personas.

## Quién entra y cómo se identifica

| Quién | Cómo se identifica | Qué puede hacer |
| --- | --- | --- |
| Conductor | Cuenta de Google, y después el JWT del sistema | Todas las operaciones de la app |
| Usuario fantasma | Credenciales de prueba, solo en un ambiente que las habilite | Lo mismo que un conductor; todo lo suyo queda marcado como simulado |
| Servicio de WhatsApp/LLM | Credencial de servicio propia | Leer el catálogo de surtidores y entregar resultados de análisis. Nada más |
| Motor core | No recibe llamadas de fuera del VPS | Responder al backend |

El sistema no guarda contraseñas ni credenciales de Google. El JWT dura 1 hora y la sesión se renueva hasta 30 días.

## Comunicación

- Todo el tráfico entre la app y el backend viaja cifrado por HTTPS.
- El backend habla con el motor core y con la base de datos dentro del VPS. Esos puertos no se abren a internet.
- Las llamadas a Google y al proveedor del LLM salen por HTTPS.

## Defensa contra el abuso

| Riesgo | Cómo se frena | Regla |
| --- | --- | --- |
| Inundar el mapa de reportes | Límite de 5 reportes cada 10 minutos y rechazo de duplicados | [RN-01.8](04_reglas/CU-01_ReportarEventoVial.md), [RN-01.9](04_reglas/CU-01_ReportarEventoVial.md) |
| Reportes falsos | Tres votos «falso» retiran el evento | [RN-02.13](04_reglas/CU-02_VisualizarEventosViales.md) |
| Votar varias veces o votar lo propio | Un voto por conductor y evento; el reportante no vota | [RN-02.11](04_reglas/CU-02_VisualizarEventosViales.md), [RN-02.12](04_reglas/CU-02_VisualizarEventosViales.md) |
| Confirmar combustible sin estar en el surtidor | El backend comprueba la cercanía y no acepta repeticiones | [RN-05.13](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md), [RN-05.14](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md) |
| Datos inventados por el LLM | Cada aviso se valida contra el catálogo antes de tocar un estado | [RN-06.15](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) |
| Alguien que se hace pasar por el servicio de monitoreo | Solo se aceptan resultados con la credencial de servicio | [RN-06.14](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) |
| Usar credenciales de prueba en producción | Solo funcionan si el ambiente las habilita | [RN-07.5](04_reglas/CU-07_EjecutarSimuladorTrafico.md) |

## Privacidad

| Dato | Cómo se protege | Fuente |
| --- | --- | --- |
| Ubicación del conductor | Se obtiene solo con la app abierta en pantalla. El permiso se puede revocar y el resto de la app sigue funcionando | RNF-13, [RN-04.1](04_reglas/CU-04_RecalcularRuta.md), [RN-04.3](04_reglas/CU-04_RecalcularRuta.md) |
| Telemetría | No se guarda después de analizarse y llega al motor core sin datos del conductor | [RN-04.12](04_reglas/CU-04_RecalcularRuta.md) |
| Rutas | No se guardan en el servidor | [D-05](08_decisiones_parametros.md), Visión 6.3 |
| Quién reportó, votó o confirmó | Nunca sale en una respuesta | RNF-10, [RN-02.5](04_reglas/CU-02_VisualizarEventosViales.md), [RN-05.7](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md) |
| Remitente de un mensaje de WhatsApp | Su número y su nombre no se guardan en ningún momento | RNF-11, [RN-06.4](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) |
| Texto de un mensaje de WhatsApp | Se borra al analizarse, o a los 30 minutos | [RN-06.11](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md), [RN-06.12](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) |
| Lo que recibe el proveedor del LLM | Solo el texto y la fecha. Se elige un proveedor que no entrene sus modelos con los datos enviados por API | [RN-06.6](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md), CU-06 sección 7 |

La política de privacidad de la app debe decir qué datos se recogen y que el texto y la fecha de los mensajes de los grupos se envían al proveedor del LLM, como pide la Visión 6.3.

## Servidor

- Se entra al VPS por SSH solo con llave, sin contraseña.
- El firewall deja abierto únicamente el puerto HTTPS del backend, además del SSH.
- El sistema operativo y las dependencias se mantienen actualizados.
- Los secretos no van al repositorio: la clave que firma los JWT, la credencial del LLM, la credencial de servicio y la sesión del número de WhatsApp se guardan en el servidor con acceso restringido.
