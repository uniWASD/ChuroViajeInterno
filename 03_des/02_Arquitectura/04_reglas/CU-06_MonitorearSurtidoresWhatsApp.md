# CU-06 — Monitorear estado de surtidores vía WhatsApp

[Índice de reglas](README.md) · [Índice de la arquitectura](../README.md)

Cubre HU-08 y los requisitos RF-08, RF-09, RNF-04 y RNF-11. Las reglas RN-06.1 a RN-06.13 y RN-06.18 las cumple el servicio de WhatsApp/LLM; de RN-06.14 a RN-06.17, el modelo del backend.

## Reglas

| Código | Regla | Objeto responsable | Fuente |
| --- | --- | --- | --- |
| RN-06.1 | Solo se leen los grupos de la lista configurada, y solo entran en esa lista los grupos cuyo administrador autorizó a la cuenta del proyecto. | Servicio de WhatsApp/LLM | CU-06 precondición, Visión 5 |
| RN-06.2 | El servicio nunca envía mensajes a los grupos: trabaja en modo solo lectura con el número dedicado. | Servicio de WhatsApp/LLM | CU-06 sección 8, decisión de WhatsApp 2.1 |
| RN-06.3 | Solo se guardan los mensajes de texto. Imágenes, audios, stickers, reacciones y avisos del sistema se ignoran. | MensajePendiente | CU-06 paso 2, issue #9 |
| RN-06.4 | Un mensaje se guarda con su identificador, su grupo, su fecha y hora y su texto. Nunca con el número ni el nombre de quien lo envió. | MensajePendiente | CU-06 paso 2, HU-08 c.1, RNF-11 |
| RN-06.5 | Cada 5 minutos, si hay mensajes pendientes, se envían al LLM en un solo lote ordenado por fecha. Si no hay pendientes, no se llama al LLM. | Servicio de WhatsApp/LLM | CU-06 paso 3 y ext. 3b, HU-08 c.2 y c.3 |
| RN-06.6 | Al proveedor del LLM solo viajan el texto y la fecha de cada mensaje. | MensajePendiente | CU-06 sección 7, Visión 5 |
| RN-06.7 | De cada mensaje útil sale un aviso con cinco datos: surtidor, tipo de combustible, estado (disponible o agotado), fila si se menciona y fecha del mensaje. | ResultadoAnálisis | CU-06 paso 3 y sección 8 |
| RN-06.8 | El tipo de combustible es obligatorio: uno de los cuatro del catálogo, o Desconocido si el mensaje no lo dice. | ResultadoAnálisis | CU-06 sección 8, CU-05 sección 8 |
| RN-06.9 | Una pregunta, un mensaje ambiguo o contradictorio, o uno que no nombra un surtidor reconocible se descarta sin cambiar ningún estado. | ResultadoAnálisis | CU-06 ext. 3a, HU-08 c.4 |
| RN-06.10 | Si el LLM no está disponible o responde con error, los mensajes siguen pendientes y se reintentan en el siguiente ciclo. Los estados de los surtidores no cambian. | MensajePendiente | CU-06 ext. 3c, HU-08 c.6 |
| RN-06.11 | Un mensaje que lleva más de 30 minutos sin analizarse se descarta. | MensajePendiente | CU-06 sección 8, HU-08 c.8 |
| RN-06.12 | En cuanto un mensaje se analiza o se descarta, su texto se borra y queda marcado como procesado. | MensajePendiente | CU-06 paso 5, RNF-11 |
| RN-06.13 | Si se pierde la conexión con WhatsApp, el servicio reintenta periódicamente. Cada surtidor conserva su último estado con su fecha. | Servicio de WhatsApp/LLM, EstadoCombustible | CU-06 ext. 1a, HU-08 c.5 |
| RN-06.14 | El backend acepta resultados únicamente del servicio autorizado. | Sesión | CU-06 sección 7, propuesta [D-13](../08_decisiones_parametros.md) |
| RN-06.15 | El backend valida cada aviso por separado: el surtidor debe estar en el catálogo, por nombre o por alias, y el tipo, el estado y el formato deben ser válidos. El aviso que falla se descarta y los demás del lote siguen su curso. | ResultadoAnálisis, Surtidor | CU-06 ext. 4a, HU-08 c.9 |
| RN-06.16 | Un aviso válido se convierte en un `EstadoCombustible` con origen WhatsApp y con la fecha del mensaje, no la del análisis. | EstadoCombustible | CU-06 paso 4 |
| RN-06.17 | Si para ese surtidor y tipo ya existe un dato más reciente, el aviso no lo reemplaza. Si el lote trae varios avisos del mismo surtidor y tipo, vale el más reciente. | EstadoCombustible | CU-05 sección 8, decisión de WhatsApp 4 |
| RN-06.18 | El servicio envía el resultado al backend apenas termina el análisis, para que el estado se vea en la app en menos de 6 minutos desde que llegó el mensaje. | Servicio de WhatsApp/LLM | CU-06 sección 7, HU-08 c.7, RNF-04 |

## Recorrido por Vista, Controlador y Modelo

Este proceso no tiene vista. Lo inicia la llegada de un mensaje y lo continúa el reloj del servicio.

1. **Servicio de WhatsApp/LLM.** Recibe un mensaje de un grupo de la lista (RN-06.1, RN-06.2). Si es texto, lo guarda como `MensajePendiente` sin datos del remitente (RN-06.3, RN-06.4).
2. **Servicio de WhatsApp/LLM.** Cada 5 minutos revisa los pendientes. Descarta los vencidos (RN-06.11) y, si queda alguno, arma el lote y lo envía al LLM (RN-06.5, RN-06.6).
3. **Proveedor del LLM.** Devuelve el resultado estructurado.
4. **Servicio de WhatsApp/LLM.** Arma el `ResultadoAnálisis` (RN-06.7 a RN-06.9), lo envía al backend (RN-06.18) y borra el texto de los mensajes (RN-06.12). Si el LLM falló, deja los mensajes pendientes (RN-06.10).
5. **Controlador REST de monitoreo (backend).** Comprueba que la petición viene del servicio autorizado (RN-06.14) y el formato general. Llama al servicio del modelo que actualiza surtidores.
6. **Modelo (backend).** Valida cada aviso contra el catálogo de `Surtidor` (RN-06.15) y actualiza el `EstadoCombustible` que corresponda (RN-06.16, RN-06.17).
7. **Controlador REST de monitoreo (backend).** Responde cuántos avisos aceptó y cuántos descartó.
8. **Los conductores.** Ven el estado nuevo en su siguiente consulta de surtidores, que es CU-05.
