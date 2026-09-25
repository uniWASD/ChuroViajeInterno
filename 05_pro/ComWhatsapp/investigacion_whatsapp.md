# Investigación sobre integración con WhatsApp

## Caso de Uso CU-06

**Monitorear Estado de Surtidores vía WhatsApp**

| Versión | Fecha | Descripción |
| ------- | ----- | ----------- |
| 1.0 | 22/09/2026 | Investigación inicial de alternativas de conexión a WhatsApp. |
| 1.1 | 23/09/2026 | Corrección de la fuente de datos: la información se obtiene de grupos de WhatsApp existentes donde la comunidad avisa si hay combustible, no de reportes enviados por distribuidores. Se actualizan el análisis, la comparación y la recomendación final. |

## 1. Objetivo

Investigar y comparar las alternativas disponibles para conectar el sistema **ChuroViaje** con WhatsApp, con el propósito de determinar un método viable para obtener información sobre el estado de los surtidores de combustible de Tarija.

De acuerdo con el Documento de Visión (v0.2) y el caso de uso CU-06, la información **no será enviada por distribuidores ni por los conductores directamente a ChuroViaje**. El sistema debe **monitorear grupos de WhatsApp ya existentes** en los que las personas avisan si hay o no gasolina, diésel o GNV en un surtidor, y a partir de esos mensajes actualizar automáticamente el estado de cada surtidor.

El flujo esperado es el siguiente:

**Miembros de los grupos → Grupos de WhatsApp existentes → Cuenta de monitoreo de ChuroViaje → Intérprete de mensajes → Backend Java → Base de datos → App ChuroViaje**

### 1.1 Características de los mensajes a procesar

A diferencia de un reporte con formato fijo, los mensajes de estos grupos son **escritos libremente por cualquier miembro**, con lenguaje informal, abreviaturas y sin estructura. Por ejemplo:

```text
En la San Luis hay gasolina, fila de 2 cuadras
Ya se acabó el diésel en la de la avenida
Alguien sabe si hay gasolina en el surtidor del centro?
Hay GNV pero la fila está larguísima
```

Esto implica que:

* Los mensajes pueden ser afirmaciones, preguntas o conversación sin relación con combustible.
* El mismo surtidor puede ser mencionado con distintos nombres o referencias.
* Pueden existir mensajes contradictorios sobre un mismo surtidor en poco tiempo.
* El remitente es un miembro cualquiera del grupo, no un responsable del surtidor.

Por lo tanto, la solución elegida debe poder **leer mensajes de grupos normales que ya existen**, y el sistema necesitará un intérprete que descarte los mensajes ambiguos (CU-06, flujo alternativo 3a).

---

## 2. WhatsApp Business Platform (API oficial de Meta)

### 2.1 ¿Qué es?

**WhatsApp Business Platform** es la plataforma oficial proporcionada por Meta para que empresas y sistemas informáticos se comuniquen con usuarios de WhatsApp mediante programación. Dentro de ella se encuentra **WhatsApp Cloud API**, que permite conectar una aplicación propia con WhatsApp.

Mediante esta API un sistema puede, entre otras cosas:

* Enviar y recibir mensajes de texto, imágenes, documentos, audios y ubicaciones.
* Detectar el número que envió un mensaje y la fecha y hora.
* Automatizar respuestas.
* Integrar WhatsApp con un backend y una base de datos.

**Limitación clave para ChuroViaje:** Cloud API está diseñada para conversaciones entre **un número de empresa y sus clientes**. El sistema solo recibe los mensajes que se envían **a su propio número**; no puede leer conversaciones de grupos normales en los que participan otras personas.

### 2.2 Requisitos

Para utilizar WhatsApp Cloud API se necesitan principalmente:

* Una cuenta de Meta y acceso a Meta for Developers.
* Crear una aplicación en Meta Developers y agregarle el producto WhatsApp.
* Una cuenta de WhatsApp Business (WABA).
* Un número telefónico verificado mediante SMS o llamada.
* Los identificadores `Phone Number ID` y `WhatsApp Business Account ID`, y un `Access Token`.
* Un Webhook configurado en un servidor o backend que procese los mensajes recibidos.

Meta proporciona números y herramientas de prueba para experimentar con la API sin registrar de inmediato un número definitivo.

### 2.3 Registro de un número

Para usar un número real con Cloud API es necesario registrarlo y verificarlo en la plataforma de Meta. Una vez registrado de forma tradicional, el número pasa a ser utilizado por WhatsApp Business Platform y deja de funcionar como una cuenta común de la aplicación de WhatsApp, aunque Meta contempla un proceso de **desregistro** para devolverlo a la aplicación regular.

Por esto, la estrategia indicada por el docente es correcta: el **número personal** no se modifica y el **número nuevo** se usa exclusivamente para las pruebas. El teléfono o el chip no quedan inutilizados; lo que cambia es la forma en que WhatsApp usa ese número.

### 2.4 Costos

El cobro de WhatsApp Business Platform depende del país del destinatario, el tipo de mensaje y la categoría de la plantilla (marketing, autenticación, utilidad o servicio). Meta anunció cambios de precios con vigencia desde el **1 de octubre de 2026**, por lo que los costos exactos deben verificarse en la tabla de precios vigente de Meta.

Como se explica en la sección 3, esta alternativa no resulta aplicable a la fuente de datos de ChuroViaje, por lo que sus costos no forman parte del presupuesto del proyecto.

### 2.5 Consumo de mensajes mediante Webhooks

Cloud API no descarga mensajes mediante consultas periódicas: Meta envía automáticamente cada mensaje recibido a una dirección del servidor (Webhook). Un mensaje entrante llega con una estructura similar a:

```json
{
    "from": "591XXXXXXXX",
    "timestamp": "XXXXXXXX",
    "type": "text",
    "text": {
        "body": "Hay gasolina en la San Luis"
    }
}
```

Este mecanismo es cómodo, pero solo aplica a mensajes enviados directamente al número de la empresa, no a mensajes de grupos existentes.

---

## 3. Lectura de mensajes de grupos

Este es el aspecto decisivo para el proyecto, porque la fuente de información de ChuroViaje son **grupos normales de WhatsApp que ya existen**.

Meta dispone de una funcionalidad oficial denominada **WhatsApp Groups API**, que permite, bajo determinadas condiciones, crear grupos mediante la API, enviar y recibir mensajes en ellos, generar enlaces de invitación y recibir eventos mediante Webhooks.

Sin embargo, presenta restricciones importantes:

* El acceso está limitado principalmente a empresas con una **Official Business Account (OBA)**.
* Solo aplica a grupos **creados y administrados mediante la propia API**, con un número reducido de participantes.
* **No permite** agregar un número de Cloud API a un grupo normal que ya existe y leer automáticamente sus mensajes.

**Conclusión de esta sección:** la API oficial de Meta no ofrece una forma de monitorear los grupos existentes en los que la comunidad de Tarija comparte la disponibilidad de combustible. Cambiar el modelo para que los distribuidores envíen reportes a un número oficial tampoco es una opción, porque el Documento de Visión establece que el estado de los surtidores se obtendrá del monitoreo de grupos **sin necesidad de solicitar reportes directos**, y deja fuera de alcance cualquier integración que dependa de la colaboración de las gasolineras.

---

## 4. Alternativas no oficiales

Existen librerías desarrolladas por terceros que se conectan a WhatsApp de forma similar a WhatsApp Web. Una cuenta vinculada mediante estas librerías se comporta como un dispositivo más del usuario, por lo que **puede leer los mensajes de todos los grupos de los que el número es miembro**, que es justamente lo que requiere ChuroViaje.

Las más conocidas son `whatsapp-web.js` y `Baileys`. Ninguna forma parte de la API oficial de Meta.

### 4.1 whatsapp-web.js

`whatsapp-web.js` es una biblioteca de código abierto para Node.js que controla una sesión de WhatsApp Web mediante un navegador automatizado (Puppeteer).

El proceso general consiste en:

1. Instalar Node.js y `whatsapp-web.js`.
2. Ejecutar el programa, que genera un código QR.
3. Escanear el QR desde el WhatsApp del número dedicado para vincular la sesión.
4. Escuchar los mensajes recibidos.

Ejemplo conceptual, filtrando solo los mensajes de grupos:

```javascript
const { Client } = require('whatsapp-web.js');

const client = new Client();

client.on('qr', qr => {
    console.log('Escanear código QR');
});

client.on('ready', () => {
    console.log('WhatsApp conectado');
});

client.on('message', async message => {
    const chat = await message.getChat();
    if (chat.isGroup) {
        console.log(`[${chat.name}] ${message.body}`);
    }
});

client.initialize();
```

Permite obtener el mensaje, el grupo de origen, la fecha y el tipo de mensaje, además de funciones como unirse a grupos mediante invitación y obtener información de grupos.

Su principal desventaja, además de no ser oficial, es que necesita mantener un navegador completo en ejecución, lo que consume bastante memoria en el servidor. El propio proyecto indica que no está afiliado ni respaldado por WhatsApp y que no puede garantizar que una cuenta no sea bloqueada.

### 4.2 Baileys

**Baileys** es una biblioteca de código abierto en TypeScript/JavaScript que se comunica con WhatsApp directamente mediante **WebSockets**, sin necesidad de un navegador.

Permite recibir mensajes, trabajar con grupos, consultar chats y participantes, y recibir eventos de conexión. Al igual que `whatsapp-web.js`, requiere vincular una cuenta (QR o código de vinculación).

```text
Celular (número dedicado)
   |
   | Vinculación
   v
 Baileys (Node.js)
   |
   v
Intérprete de mensajes
   |
   v
Backend Java
```

Su ventaja principal es que es **más ligera**: no ejecuta Chrome, lo que la hace más adecuada para el VPS básico previsto en el Documento de Visión. Sus desarrolladores indican que debe usarse bajo responsabilidad del usuario y no para spam ni automatizaciones abusivas.

---

## 5. Riesgos de las alternativas no oficiales y cómo mitigarlos

Dado que la alternativa no oficial es la única que se ajusta a la fuente de datos del proyecto, es necesario reconocer sus riesgos y definir medidas para reducirlos.

| Riesgo | Descripción | Mitigación propuesta |
| ------ | ----------- | -------------------- |
| Bloqueo del número | WhatsApp puede detectar clientes no oficiales y cerrar la sesión, restringir o suspender el número. | Usar un **número nuevo y exclusivo**; operar en modo **solo lectura** (nunca enviar mensajes automáticos); no unirse a una cantidad excesiva de grupos. |
| Términos de servicio | Estas librerías no forman parte de las APIs oficiales de Meta. | Limitar el uso al alcance académico del proyecto y documentar el riesgo; no usar para envío masivo ni spam. |
| Cambios internos de WhatsApp | Cambios en protocolos o autenticación pueden dejar la librería sin funcionar temporalmente. | Mantener la librería actualizada y aislar la conexión en un servicio propio, para poder reemplazarla sin afectar al backend. |
| Estabilidad de la sesión | Sesiones cerradas o necesidad de volver a vincular el QR. | Persistir la sesión, reintentar la conexión periódicamente y conservar el último estado conocido de cada surtidor con su fecha (CU-06, flujo 1a). |
| Pérdida de la fuente en la defensa | Si el número es bloqueado cerca de la defensa, el módulo se queda sin datos. | Tener un **grupo de prueba controlado por el equipo** para la demostración y un número de respaldo. |

---

## 6. Privacidad de los datos de los grupos

Al monitorear grupos se procesan mensajes escritos por personas que no son usuarios de ChuroViaje. En cumplimiento de la restricción legal y los requisitos de privacidad del Documento de Visión:

* Solo se monitorearán grupos cuyo **administrador haya autorizado** la presencia de la cuenta de ChuroViaje.
* El sistema **no almacenará** números de teléfono, nombres ni el texto completo de los mensajes; solo guardará el dato interpretado: surtidor, tipo de combustible, estado, fila aproximada (si se menciona) y fecha y hora.
* Las credenciales y la sesión de la cuenta de monitoreo deben estar protegidas y accesibles únicamente para el servicio autorizado (requisito de seguridad de CU-06).

---

## 7. Comparación

| Característica                                   | API oficial de Meta                  | whatsapp-web.js        | Baileys          |
| ------------------------------------------------ | ------------------------------------ | ---------------------- | ---------------- |
| Es oficial                                       | Sí                                   | No                     | No               |
| Requiere Meta Developers                         | Sí                                   | No                     | No               |
| Requiere vinculación mediante QR                 | No                                   | Sí                     | Sí / código      |
| Recepción de mensajes enviados al propio número  | Sí                                   | Sí                     | Sí               |
| Lectura de grupos normales existentes            | **No**                               | **Sí**                 | **Sí**           |
| Groups API oficial                               | Sí, solo grupos propios y con OBA    | No aplica              | No aplica        |
| Requiere navegador                               | No                                   | Sí (Puppeteer)         | No               |
| Consumo de recursos en el servidor               | Bajo                                 | Alto                   | Bajo             |
| Riesgo de bloqueo                                | Bajo                                 | Mayor                  | Mayor            |
| Dependencia de cambios en WhatsApp Web           | No                                   | Sí                     | Sí               |
| Compatible con la fuente de datos de ChuroViaje  | **No**                               | **Sí**                 | **Sí**           |

---

## 8. Recomendación final

Como la fuente de información de ChuroViaje son **grupos de WhatsApp existentes** donde la comunidad avisa si hay combustible, la API oficial de Meta **no es viable** para este caso de uso: solo recibe mensajes enviados al propio número y su Groups API no permite leer grupos normales ya existentes.

Se recomienda utilizar **Baileys** como librería de conexión, ejecutada como un **servicio independiente en Node.js**, por las siguientes razones:

* Permite leer los mensajes de los grupos de los que el número dedicado es miembro.
* No requiere un navegador, por lo que consume menos recursos en el VPS básico del proyecto.
* Al estar aislado como servicio propio, puede reemplazarse (por ejemplo, por `whatsapp-web.js`) sin modificar el backend Java.

`whatsapp-web.js` queda como alternativa si el equipo encuentra dificultades con Baileys, aceptando su mayor consumo de memoria.

La arquitectura recomendada es:

```text
Grupos de WhatsApp existentes
(la comunidad avisa si hay combustible)
            |
            v
Número dedicado de ChuroViaje (miembro de los grupos, solo lectura)
            |
            v
Servicio de monitoreo (Node.js + Baileys)
            |
            v
Intérprete de mensajes (palabras clave / reglas)
   |                         |
   | mensaje interpretable   | mensaje ambiguo o sin surtidor
   v                         v
Backend Java              Se descarta
   |
   v
Base de datos
   |
   v
App ChuroViaje (módulo de combustible, CU-05)
```

El intérprete convertiría un mensaje como:

```text
En la San Luis hay gasolina, fila de 2 cuadras
```

en un dato estructurado como:

```text
surtidor    = San Luis
combustible = Gasolina
estado      = Disponible
fila        = 2 cuadras
fecha_hora  = fecha y hora del mensaje
```

Mientras que un mensaje como `Alguien sabe si hay gasolina en el surtidor del centro?` se descartaría por ser una pregunta y no un aviso.

Se propone trabajar en dos etapas:

### Etapa 1 — Prueba de conexión

Realizar una prueba controlada utilizando un **número nuevo** y un **grupo de prueba creado por el equipo**:

```text
Grupo de prueba del equipo
       |
       v
Número dedicado
       |
       v
Baileys
       |
       v
Leer mensajes del grupo
       |
       v
Mostrar en consola: grupo, fecha y texto
```

El objetivo es comprobar que técnicamente es posible detectar los mensajes provenientes de un grupo, y que la sesión se mantiene y se recupera después de una desconexión.

### Etapa 2 — Monitoreo de grupos reales e integración

1. Identificar los grupos de Tarija donde se comparte la disponibilidad de combustible y solicitar autorización a sus administradores.
2. Recolectar mensajes de ejemplo para diseñar las reglas del intérprete.
3. Construir un catálogo de surtidores con sus nombres y las formas en que la gente los menciona.
4. Implementar el intérprete y el envío del dato interpretado al backend Java, de modo que el estado se refleje en el módulo de combustible en menos de 1 minuto (CU-06).

Si en el futuro los surtidores otorgan acceso a una API propia, esa API pasaría a ser la fuente primaria y el monitoreo de WhatsApp quedaría como respaldo, tal como establece CU-06.

---

## 9. Conclusión

La investigación permitió determinar que existen diferentes alternativas para consumir información de WhatsApp, pero solo algunas se ajustan a la fuente de datos definida para ChuroViaje.

La alternativa oficial, **WhatsApp Cloud API**, es estable y de bajo riesgo, pero únicamente recibe mensajes enviados al número de la empresa. Su **Groups API** está restringida a grupos propios y a cuentas empresariales oficiales, por lo que no permite monitorear los grupos existentes donde la comunidad de Tarija avisa si hay combustible.

Las librerías no oficiales **Baileys** y `whatsapp-web.js` sí permiten leer los mensajes de esos grupos, a cambio de riesgos de bloqueo, dependencia de cambios internos de WhatsApp y posibles problemas de estabilidad. Estos riesgos se reducen usando un número dedicado en modo solo lectura, aislando la conexión en un servicio propio, conservando el último estado conocido de cada surtidor y contando con un grupo de prueba para la demostración.

Por lo tanto, se recomienda implementar el monitoreo de CU-06 mediante **Baileys**, con un **número telefónico nuevo y exclusivo para el proyecto**, almacenando únicamente el estado interpretado de cada surtidor y no los datos personales de los miembros de los grupos.

---

## 10. Fuentes consultadas

1. **ChuroViaje — Documento de Visión v0.2 (11/09/2026)**
   * Alcance del módulo de disponibilidad de combustible y fuente de datos vía grupos de WhatsApp.
2. **ChuroViaje — Especificación de Caso de Uso CU-06**
   * Flujo de monitoreo, extensiones y requisitos especiales.
3. **Meta — WhatsApp Business Platform**
   * Documentación oficial de WhatsApp Business Platform.
4. **Meta — WhatsApp Cloud API**
   * Documentación oficial para integración, registro de números, envío y recepción de mensajes.
5. **Meta — WhatsApp Developer Hub**
   * Información sobre números de prueba, Webhooks y herramientas para desarrolladores.
6. **Meta — WhatsApp Groups API**
   * Documentación sobre grupos empresariales, requisitos y limitaciones.
7. **Meta — WhatsApp Business Platform Pricing**
   * Información sobre categorías y modelo de cobro de mensajes.
8. **whatsapp-web.js**
   * Repositorio oficial del proyecto de código abierto en GitHub.
9. **WhiskeySockets/Baileys**
   * Repositorio oficial del proyecto Baileys en GitHub.
10. **Twilio — WhatsApp Business Platform Pricing Updates**
    * Información sobre los cambios de precios de Meta anunciados para 2026.

---

**Fecha de actualización de la investigación:** 23 de septiembre de 2026.
