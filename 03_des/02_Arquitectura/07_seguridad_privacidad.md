# Seguridad y privacidad

[Índice de la arquitectura](README.md)

La seguridad se apoya en tres ideas: nadie entra sin identificarse, el backend desconfía de todo lo que recibe y el sistema guarda lo mínimo sobre las personas.

## Quién entra y cómo se identifica

| Quién | Cómo se identifica | Qué puede hacer |
| --- | --- | --- |
| Conductor | Cuenta de Google, y después el JWT del sistema | Todas las operaciones de la app |
| Usuario fantasma | Credenciales de prueba, solo en un ambiente que las habilite | Lo mismo que un conductor; todo lo suyo queda marcado como simulado |
| Servicio de WhatsApp/LLM | Credencial de servicio propia | Leer el catálogo de surtidores y entregar resultados de análisis. Nada más |
| ANH | No entra al sistema: el backend la consulta, ella no llama a nadie | Responder las consultas públicas de solo lectura |
| Motor core | No abre puertos: el núcleo C++ solo lee la entrada estándar que le da el backend, que lo arranca como proceso hijo | Responder al wrapper `MotorDeRutas` |

El sistema no guarda contraseñas ni credenciales de Google. El JWT dura 1 hora y la sesión se renueva hasta 30 días.

## Comunicación

- Todo el tráfico entre la app y el backend viaja cifrado por HTTPS.
- El backend habla con el núcleo C++ del motor core por pipe, sin red ([D-15](08_decisiones_parametros.md)).
- La base de datos solo acepta conexiones desde el propio VPS; su puerto no se abre a internet.
- Las llamadas a Google, al proveedor del LLM y a ANH salen por HTTPS.
- Las consultas a ANH las hace solo el backend, con la verificación del certificado y del nombre del servidor siempre activa, y con TLS 1.2 como mínimo. Esa verificación nunca se desactiva, ni siquiera para probar: si el certificado falla, la consulta se detiene y se registra ([RN-09.3](04_reglas/CU-09_ConsultarEstadoANH.md)). Si la Java del servidor no reconoce la entidad que firmó el certificado de ANH, se agrega esa entidad al almacén de confianza del servidor; no se acepta cualquier certificado.
- ANH no pide credencial: no hay clave que guardar para consultarla.

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
| Datos inválidos o alterados que lleguen de ANH | Conexión con certificado verificado, validación de la respuesta completa y de cada saldo, y solo las estaciones vinculadas al catálogo y dentro de la mancha urbana. Un fallo nunca se convierte en cero litros | [RN-09.3](04_reglas/CU-09_ConsultarEstadoANH.md) a [RN-09.9](04_reglas/CU-09_ConsultarEstadoANH.md), [RN-09.11](04_reglas/CU-09_ConsultarEstadoANH.md), [RN-09.15](04_reglas/CU-09_ConsultarEstadoANH.md) |
| Consultar a ANH de más | ANH no publica cuotas ni condiciones de uso, así que consulta una sola tarea del backend, nunca la app; hay una pausa entre peticiones, se respeta `Retry-After` y la frecuencia se fija con pruebas | [RN-09.1](04_reglas/CU-09_ConsultarEstadoANH.md), [RN-09.4](04_reglas/CU-09_ConsultarEstadoANH.md), [P-29](08_decisiones_parametros.md) |

## Privacidad

| Dato | Cómo se protege | Fuente |
| --- | --- | --- |
| Ubicación del conductor | Se obtiene solo con la app abierta en pantalla. El permiso se puede revocar y el resto de la app sigue funcionando | RNF-13, [RN-04.1](04_reglas/CU-04_RecalcularRuta.md), [RN-04.3](04_reglas/CU-04_RecalcularRuta.md) |
| Telemetría | No se guarda después de analizarse y llega al motor core sin datos del conductor | [RN-04.12](04_reglas/CU-04_RecalcularRuta.md) |
| Rutas | No se guardan en el servidor | [D-05](08_decisiones_parametros.md), Visión 6.3 |
| Quién reportó, votó o confirmó | Nunca sale en una respuesta | RNF-10, [RN-02.5](04_reglas/CU-02_VisualizarEventosViales.md), [RN-05.7](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md) |
| Remitente de un mensaje de WhatsApp | Su número y su nombre no se guardan en ningún momento | RNF-11, [RN-06.4](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) |
| Texto de un mensaje de WhatsApp | Se borra al analizarse, o a los 30 minutos | [RN-06.11](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md), [RN-06.12](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) |
| Datos que se leen de ANH | Son datos públicos de estaciones de servicio: nombre, ubicación, saldo y estado. No hay datos de personas y el sistema no le envía ninguno a ANH, solo peticiones GET con parámetros fijos. De cada estación solo se guardan el saldo, el estado y las fechas; no se guardan la fecha de última venta, la dirección ni las respuestas completas | [RN-09.3](04_reglas/CU-09_ConsultarEstadoANH.md), [RN-09.18](04_reglas/CU-09_ConsultarEstadoANH.md) |
| Lo que recibe el proveedor del LLM | Solo el texto y la fecha, con los números de teléfono enmascarados. Se usa la capa gratuita de la API de Gemini, cuyos términos permiten a Google usar lo enviado para mejorar sus productos y que lo lean revisores humanos | [RN-06.6](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md), [D-17](08_decisiones_parametros.md) |

La política de privacidad de la app debe decir qué datos se recogen, que el texto y la fecha de los mensajes de los grupos se envían al proveedor del LLM, como pide la Visión 6.3, y que el sistema consulta a ANH datos públicos de las estaciones sin enviarle datos de ningún usuario. También debe decir que, en la capa gratuita, ese proveedor puede usarlos para mejorar sus productos y que pueden leerlos revisores humanos ([D-17](08_decisiones_parametros.md)).

## Servidor

- Se entra al VPS por SSH solo con llave, sin contraseña.
- El firewall deja abiertos únicamente el puerto HTTPS del backend y el SSH, más el puerto 80, que solo redirige a HTTPS y atiende la validación del certificado de Let's Encrypt. En Oracle Cloud esto se configura en las reglas de red de la VCN y en el firewall de Ubuntu ([D-16](08_decisiones_parametros.md)).
- El sistema operativo y las dependencias se mantienen actualizados.
- Los secretos no van al repositorio: la clave que firma los JWT, la credencial del LLM, la credencial de servicio y la sesión del número de WhatsApp se guardan en el servidor con acceso restringido. La salida HTTPS del servidor hacia ANH se comprueba antes de depender de ella (tarea T-119).
