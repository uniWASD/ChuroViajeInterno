# Backlog de Construcción — Servicio de WhatsApp/LLM

[Índice del backlog](README.md)

Responsable: Franz. Código en `04_imp/servicio_whatsapp`.

El servicio lee los grupos de WhatsApp autorizados con Baileys, guarda los mensajes pendientes sin datos del remitente, los analiza por lotes con un LLM cada 5 minutos y entrega el resultado al backend. No decide el estado de un surtidor: eso lo valida el backend ([02_mvc.md](../../03_des/02_Arquitectura/02_mvc.md), regla de reparto 6). La base de este trabajo es la decisión [decision_conexion_whatsapp.md](../../05_pro/ComWhatsapp/decision_conexion_whatsapp.md) (v1.4) y las reglas de [CU-06](../../03_des/02_Arquitectura/04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md).

| Sprint | Issues |
| --- | --- |
| S1 (15/10–21/10) | Estructura del servicio |
| S2 (22/10–28/10) | HU-08 Leer los grupos autorizados |
| S3 (29/10–04/11) | HU-08 Analizar los mensajes por lotes y enviar el resultado |
| S5 (12/11–18/11) | Prueba con mensajes reales |

En el sprint 4 Franz trabaja en los modos sin conexión de la app (HU-02 y HU-04, en [01_Flutter.md](01_Flutter.md)).

---

## Sprint 1

### [T-091][WhatsApp/LLM] Estructura del servicio

**Tipo:** Tarea de infraestructura (base del servicio de CU-06)
**Tecnología:** Node.js · **Sprint:** Sprint 1 (15/10–21/10) · **Responsable sugerido:** Franz
**Depende de:** nada
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:whatsapp-llm`

**Descripción:** Crear el proyecto del servicio con su configuración, su almacenamiento propio y sus pruebas, para que la lectura de grupos y el análisis se construyan encima por separado.

**Criterios de aceptación**
- [ ] El proyecto corre con la versión LTS de Node.js en `04_imp/servicio_whatsapp`, en Windows y en Linux ARM.
- [ ] La configuración (lista de grupos, dirección del backend, credencial de servicio, credencial del LLM) se lee de variables de entorno o de un archivo fuera del repositorio.
- [ ] Los mensajes pendientes se guardan en un archivo SQLite propio del servicio (P-22), con identificador, grupo, fecha y hora, texto y estado (pendiente, procesado o descartado). No hay ninguna columna para el número ni el nombre del remitente (RN-06.4).
- [ ] Hay un cliente que llama a `GET /monitoreo/surtidores` y `POST /monitoreo/resultados` con la credencial de servicio.
- [ ] Los registros nunca incluyen el texto de un mensaje ni datos de un remitente.
- [ ] Al arrancar, el servicio descarta los pendientes de más de 30 minutos y borra su texto, por si estuvo detenido (RN-06.11, RN-06.12).
- [ ] Las pruebas corren con un solo comando, y el README explica cómo configurar y ejecutar.

**Tareas**
- [ ] Crear el proyecto (JavaScript o TypeScript; anotar la elección) y su librería de pruebas.
- [ ] Implementar la carga de configuración.
- [ ] Implementar el almacenamiento de `MensajePendiente` en SQLite.
- [ ] Implementar el cliente del backend.
- [ ] Escribir el README.

**Casos de prueba**
- [ ] CP-26 Caso de prueba: un mensaje guardado y leído de nuevo conserva grupo, fecha y texto, y la tabla no tiene datos del remitente.

**Relacionado:** RNF-11 · CU-06 · RN-06.4 · P-22 · D-13

Lo subes en una nueva rama llamada "feature/T91-whatsapp-estructura-servicio" (No olvides que el source de la rama es la de develop)

---

## Sprint 2

### [HU-08][CU-06][WhatsApp/LLM] Leer los grupos autorizados

**Caso de uso:** CU-06 — Monitorear Estado de Surtidores vía WhatsApp
**Tecnología:** Node.js · **Sprint:** Sprint 2 (22/10–28/10) · **Responsable sugerido:** Franz
**Depende de:** [T-091][WhatsApp/LLM] Estructura del servicio #43
**Issue anterior:** #6 (investigación de la conexión a WhatsApp)
**Etiquetas:** `caso-uso:CU-06` · `tipo:historia-usuario` · `fase:construccion` · `tec:whatsapp-llm`

**Historia de usuario:** Como sistema, quiero extraer los mensajes de los grupos de WhatsApp monitoreados e interpretarlos por lotes, para mantener actualizado el estado de los surtidores sin depender de reportes manuales.

Este issue cubre la extracción; el análisis va en su propio issue.

**Criterios de aceptación**
- [ ] El servicio se conecta con Baileys usando el número dedicado del proyecto y guarda la sesión en un archivo con acceso restringido.
- [ ] Solo lee los grupos de la lista configurada, y en esa lista solo entran grupos cuyo administrador autorizó la cuenta (RN-06.1). Cada autorización queda registrada (grupo, fecha y cómo se pidió) en un archivo fuera del repositorio.
- [ ] Nunca envía mensajes a los grupos; no hay ninguna función de envío en el código (RN-06.2).
- [ ] Guarda solo los mensajes de texto; imágenes, audios, stickers, reacciones y avisos del sistema se ignoran (RN-06.3).
- [ ] Cada mensaje se guarda como pendiente con su identificador, grupo, fecha y hora y texto, sin número ni nombre del remitente (RN-06.4).
- [ ] Si se pierde la conexión, el servicio reintenta solo cada cierto tiempo y lo deja en el registro (RN-06.13).
- [ ] Un mensaje que llega dos veces (por una reconexión) se guarda una sola vez.

**Tareas**
- [ ] Conseguir el número dedicado y pedir la autorización de los administradores de los grupos.
- [ ] Implementar la conexión con Baileys y la persistencia de la sesión.
- [ ] Implementar el filtro de grupos y de tipos de mensaje.
- [ ] Implementar la reconexión.
- [ ] Probar en un grupo de prueba controlado por el equipo antes de entrar a los grupos reales.

**Casos de prueba**
- [ ] CP-48 Caso de prueba: en el grupo de prueba se envían un texto, una imagen y un audio; solo el texto queda pendiente, sin datos del remitente.
- [ ] CP-49 Caso de prueba: un mensaje en un grupo que no está en la lista no se guarda.
- [ ] CP-50 Caso de prueba: al cortar la red del servicio 2 minutos, se reconecta solo al volver.

**Relacionado:** RF-08 · RNF-11 · CU-06 · RN-06.1 a RN-06.4, RN-06.13 · [decision_conexion_whatsapp.md](../../05_pro/ComWhatsapp/decision_conexion_whatsapp.md)

Lo subes en una nueva rama llamada "feature/HU08-CU06-whatsapp-leer-grupos" (No olvides que el source de la rama es la de develop)

---

## Sprint 3

### [HU-08][CU-06][WhatsApp/LLM] Analizar los mensajes por lotes y enviar el resultado

**Caso de uso:** CU-06 — Monitorear Estado de Surtidores vía WhatsApp
**Tecnología:** Node.js · **Sprint:** Sprint 3 (29/10–04/11) · **Responsable sugerido:** Franz
**Depende de:** [HU-08][CU-06][WhatsApp/LLM] Leer los grupos autorizados #51 · [HU-08][CU-06][Java] Recibir el análisis de WhatsApp #57
**Issue anterior:** #10 (investigación del LLM)
**Etiquetas:** `caso-uso:CU-06` · `tipo:historia-usuario` · `fase:construccion` · `tec:whatsapp-llm`

**Historia de usuario:** Como sistema, quiero extraer los mensajes de los grupos de WhatsApp monitoreados e interpretarlos por lotes, para mantener actualizado el estado de los surtidores sin depender de reportes manuales.

Este issue cubre el análisis por lotes y la entrega al backend.

**Criterios de aceptación**
- [ ] Cada 5 minutos, si hay mensajes pendientes, se envían al LLM en un solo lote ordenado por fecha; si no hay, no se llama al LLM (RN-06.5).
- [ ] Una revisión cada minuto descarta los mensajes con más de 30 minutos sin analizar y borra su texto, sin esperar al ciclo de 5 minutos (RN-06.11, RN-06.12).
- [ ] Al LLM solo viajan el texto y la fecha de cada mensaje, con los números de teléfono del texto enmascarados, junto con el catálogo de surtidores, alias, combustibles y valores de fila que entrega el backend (RN-06.6, D-17, P-15).
- [ ] Se le pide al LLM una salida estructurada; de cada mensaje útil sale un aviso con surtidor, tipo de combustible, estado, fila si se menciona y fecha del mensaje (RN-06.7).
- [ ] El tipo de combustible es siempre uno del catálogo o Desconocido (RN-06.8).
- [ ] Preguntas, mensajes ambiguos o contradictorios y mensajes sin un surtidor reconocible se descartan (RN-06.9).
- [ ] Si el LLM falla, los mensajes siguen pendientes para el siguiente ciclo (RN-06.10).
- [ ] El resultado se envía a `POST /monitoreo/resultados` apenas termina el análisis (RN-06.18).
- [ ] Analizado el lote, el texto de sus mensajes se borra y quedan marcados como procesados (RN-06.12). Si el backend no responde, se guarda el resultado sin texto y se reenvía en el siguiente ciclo (RN-06.19).
- [ ] Se usa la capa gratuita de la API de Gemini. El README anota que sus términos permiten a Google usar lo enviado para mejorar sus productos, con el enlace a los términos y la fecha en que se revisaron (D-17).

**Tareas**
- [ ] Llevar al servicio el prompt y el formato de salida de la investigación del issue #10 (Gemini 3.1 Flash-Lite).
- [ ] Implementar el enmascarado de números de teléfono antes del envío.
- [ ] Implementar el ciclo de 5 minutos, el descarte por vigencia y el armado del lote.
- [ ] Implementar la llamada al LLM con salida estructurada y la validación de su formato antes de enviarla.
- [ ] Implementar el envío al backend, el borrado del texto y el reenvío de resultados pendientes.
- [ ] Escribir pruebas con el LLM simulado: lote normal, LLM caído, backend caído.

**Casos de prueba**
- [ ] CP-74 Caso de prueba: «ya hay gasolina en Las Vegas, fila corta» produce un aviso de Las Vegas, Gasolina, disponible, fila corta.
- [ ] CP-75 Caso de prueba: «¿alguien sabe si hay diésel en El Portillo?» se descarta.
- [ ] CP-76 Caso de prueba: con el LLM caído, los mensajes siguen pendientes y se analizan en el ciclo siguiente.
- [ ] CP-77 Caso de prueba: un mensaje válido en el grupo de prueba queda en la tabla `estado_combustible` del backend en menos de 6 minutos (RNF-04).
- [ ] CP-78 Caso de prueba: un mensaje con «llamen al 71234567» llega al LLM con el número enmascarado.

**Relacionado:** RF-08 · RF-09 · RNF-04 · RNF-11 · CU-06 · RN-06.5 a RN-06.12, RN-06.18, RN-06.19 · D-17 · P-15 · [decision_conexion_whatsapp.md](../../05_pro/ComWhatsapp/decision_conexion_whatsapp.md)

Lo subes en una nueva rama llamada "feature/HU08-CU06-llm-analisis-lotes" (No olvides que el source de la rama es la de develop)

---

## Sprint 5

### [T-101][WhatsApp/LLM] Prueba con mensajes reales

**Tipo:** Tarea de pruebas (cubre CU-06)
**Tecnología:** Node.js · **Sprint:** Sprint 5 (12/11–18/11) · **Responsable sugerido:** Franz
**Depende de:** [HU-08][CU-06][WhatsApp/LLM] Analizar los mensajes por lotes y enviar el resultado #59 · [T-100][Servidor] Desplegar el servicio de WhatsApp/LLM #68 · [HU-07][CU-05][Java] Consultar surtidores cercanos #69 · [HU-07][CU-05][Flutter] Consultar surtidores cercanos #71
**Etiquetas:** `tipo:prueba` · `fase:construccion` · `tec:whatsapp-llm`

**Descripción:** Medir qué tan bien interpreta el LLM los mensajes reales de los grupos de Tarija y ajustar el prompt antes del cierre, como pide la sección «Revisión futura» de la decisión de WhatsApp.

**Criterios de aceptación**
- [ ] Hay una muestra de al menos 100 mensajes, con la respuesta correcta anotada a mano por el equipo, tomada solo de los chats que ya están en `05_pro/PruebaClasificacion/Chats` y de mensajes escritos por el equipo en el grupo de prueba. No se guardan textos nuevos de los grupos monitoreados, porque su texto se borra al analizarse (RN-06.12).
- [ ] Antes de medir, el equipo fija la precisión mínima aceptable y la anota en el issue.
- [ ] Se mide cuántos avisos acierta el LLM en surtidor, combustible y estado, y cuántos mensajes inútiles descarta bien.
- [ ] Los resultados, el prompt final y el consumo por lote quedan escritos en `05_pro/PruebaAnalisisLLM`.
- [ ] En el servidor, el tiempo desde que llega un mensaje hasta que se ve en la app es menor a 6 minutos (RNF-04).

**Tareas**
- [ ] Armar la muestra con los chats existentes y el grupo de prueba, y anotar las respuestas correctas, sin nombres ni números de los remitentes.
- [ ] Escribir el script que compara las respuestas del LLM con las anotadas.
- [ ] Ajustar el prompt y el catálogo de alias según los errores.
- [ ] Medir el tiempo de punta a punta en el servidor.

**Casos de prueba**
- [ ] CP-127 Caso de prueba: la precisión sobre la muestra anotada alcanza el mínimo que fijó el equipo.

**Relacionado:** RF-08 · RF-09 · RNF-04 · RNF-11 · CU-06 · RN-06.7 a RN-06.9, RN-06.12

Lo subes en una nueva rama llamada "feature/T101-llm-prueba-mensajes-reales" (No olvides que el source de la rama es la de develop)
