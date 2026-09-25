# Decisión de arquitectura: Conexión a WhatsApp para el monitoreo de surtidores

| Campo | Valor |
| ----- | ----- |
| Proyecto | ChuroViaje |
| Caso de uso | CU-06 — Monitorear Estado de Surtidores vía WhatsApp |
| Historia de usuario | HU-08 — Monitorear estado de surtidores vía WhatsApp |
| Tareas relacionadas | T-024, T-025, T-026, T-027 |
| Documento base | `investigacion_whatsapp.md` (v1.1) |
| Estado | Aceptada |
| Versión | 1.2 |
| Fecha | 25/09/2026 |

| Versión | Fecha | Descripción |
| ------- | ----- | ----------- |
| 1.0 | 23/09/2026 | Decisión inicial: Baileys con intérprete basado en palabras clave y reglas. |
| 1.1 | 23/09/2026 | La interpretación pasa a un LLM que analiza los mensajes por lotes cada 5 minutos. La extracción y el análisis se separan en dos etapas; se actualizan riesgos, privacidad e implicaciones. |
| 1.2 | 25/09/2026 | Se registra que CU-06, HU-08 y el Documento de Visión (v0.3) ya fueron actualizados según esta decisión; el requisito de tiempo pasa a menos de 6 minutos. |

---

## 1. Contexto

El módulo de disponibilidad de combustible (CU-05) necesita conocer el estado de los surtidores de Tarija. Según el Documento de Visión v0.2, si los surtidores no otorgan acceso a una API propia, el dato se obtendrá **monitoreando grupos de WhatsApp existentes donde la comunidad avisa si hay o no combustible**, sin solicitar reportes directos a los usuarios ni depender de la colaboración de las gasolineras.

Esto impone dos condiciones a la solución:

1. Debe poder **leer mensajes de grupos normales de WhatsApp que ya existen** y de los que el equipo no es administrador.
2. Debe poder interpretar **mensajes informales**, escritos libremente por cualquier miembro del grupo, con lenguaje coloquial, abreviaturas y distintas formas de referirse a un mismo surtidor.

---

## 2. Decisión

La solución se divide en dos etapas independientes:

### 2.1 Extracción de mensajes

Se utilizará la librería no oficial **Baileys**, ejecutada como un **servicio independiente en Node.js**, conectada a un **número telefónico nuevo y exclusivo del proyecto** que será miembro de los grupos monitoreados en **modo solo lectura**.

El servicio leerá los mensajes de texto de los grupos configurados y los guardará en un **almacenamiento temporal** como mensajes pendientes de análisis, sin el número ni el nombre del remitente.

### 2.2 Análisis con LLM por lotes

Un **proceso de análisis** revisará cada **5 minutos** si existen mensajes pendientes. Si no hay mensajes nuevos, no hace nada. Si los hay, envía el lote a un **LLM**, que identifica en cada mensaje el surtidor, el tipo de combustible, el estado y la fila aproximada, y descarta los mensajes ambiguos, las preguntas y los que no mencionan un surtidor identificable. El resultado estructurado se envía al **backend Java** y los mensajes del lote se marcan como procesados.

```text
Grupos de WhatsApp existentes
            |
            v
Número dedicado de ChuroViaje (solo lectura)
            |
            v
Servicio de extracción (Node.js + Baileys)
            |
            v
Almacenamiento temporal de mensajes pendientes
            |
            |  cada 5 minutos, solo si hay mensajes
            v
Proceso de análisis ──> LLM ──> mensajes ambiguos: se descartan
            |
            v
Backend Java ──> Base de datos ──> App (CU-05)
```

---

## 3. Alternativas consideradas

### 3.1 Conexión a WhatsApp

| Alternativa | Resultado | Motivo |
| ----------- | --------- | ------ |
| WhatsApp Cloud API (oficial de Meta) | Descartada | Solo recibe mensajes enviados al número de la empresa; no puede leer grupos existentes. |
| WhatsApp Groups API (oficial de Meta) | Descartada | Limitada a grupos creados por la propia API y a cuentas con Official Business Account (OBA). |
| Distribuidores reportando a un número oficial | Descartada | Contradice el alcance del Documento de Visión: no se solicitan reportes directos ni se depende de las gasolineras. |
| whatsapp-web.js | Alternativa de respaldo | Lee grupos existentes, pero requiere un navegador (Puppeteer) y consume más memoria en el VPS básico. |
| **Baileys** | **Elegida** | Lee grupos existentes, no requiere navegador y consume pocos recursos. |

### 3.2 Interpretación de mensajes

| Alternativa | Resultado | Motivo |
| ----------- | --------- | ------ |
| Intérprete por palabras clave y reglas | Descartada | Frágil ante el lenguaje informal, las abreviaturas y las múltiples formas de nombrar un surtidor; requiere mantener reglas manualmente. |
| LLM mensaje por mensaje | Descartada | Genera una llamada por cada mensaje, con mayor costo y más consumo de límites de uso. |
| **LLM por lotes cada 5 minutos** | **Elegida** | Entiende lenguaje natural, recibe el contexto de varios mensajes juntos (útil ante mensajes contradictorios) y reduce la cantidad de llamadas; si no hubo mensajes, no hay llamada. |

---

## 4. Consecuencias

### Positivas

* Cumple el alcance del Documento de Visión: el dato proviene de los grupos, sin reportes directos.
* Bajo consumo de recursos en la extracción, compatible con el VPS básico del proyecto.
* Extracción y análisis quedan desacoplados: se pueden desarrollar, probar y reemplazar por separado (por ejemplo, cambiar Baileys por whatsapp-web.js o cambiar de LLM).
* El LLM tolera mejor el lenguaje informal que un conjunto de reglas fijas.

### Negativas y riesgos aceptados

| Riesgo | Mitigación |
| ------ | ---------- |
| Bloqueo del número por usar un cliente no oficial | Número nuevo y exclusivo; modo solo lectura (nunca enviar mensajes automáticos); no unirse a una cantidad excesiva de grupos. |
| Incumplimiento de los términos de servicio de WhatsApp | Uso limitado al alcance académico del proyecto; riesgo documentado. |
| La librería deja de funcionar por cambios internos de WhatsApp | Mantenerla actualizada; servicio aislado y reemplazable. |
| Sesión cerrada o desconexión | Persistir la sesión, reintentar periódicamente y conservar el último estado conocido de cada surtidor con su fecha (CU-06, flujo 1a). |
| Pérdida de la fuente cerca de la defensa | Grupo de prueba controlado por el equipo y número de respaldo. |
| El LLM interpreta mal un mensaje o inventa datos | Pedir una respuesta en formato estructurado, validarla en el backend contra el catálogo de surtidores y descartar lo que no coincida (CU-06, flujo 4a); proporcionar al LLM la lista de surtidores con sus alias. |
| Mensajes contradictorios sobre un mismo surtidor | Enviar el lote ordenado por fecha para que el LLM considere el mensaje más reciente. |
| El servicio del LLM no está disponible o falla | Los mensajes permanecen pendientes y se reintentan en el siguiente ciclo; se descartan si superan un tiempo máximo de vigencia (CU-06, flujo 3c). |
| Costo del LLM | Análisis por lotes y solo cuando hay mensajes nuevos. |

### Implicaciones de arquitectura

* Se agrega un **cuarto componente** a la arquitectura: el servicio de extracción en Node.js, además de Flutter, backend Java y motor C++. El proceso de análisis puede formar parte de este servicio o del backend; se definirá en su propio issue.
* Se agrega una **dependencia externa**: el proveedor del LLM.
* Deben definirse dos contratos: el formato de los mensajes pendientes en el almacenamiento temporal y el formato del resultado que el análisis envía al backend Java.
* **Tiempo de actualización (resuelto en v1.2):** con lotes cada 5 minutos, un mensaje puede tardar hasta unos 5 minutos más el tiempo de análisis. CU-06 (requisito de rendimiento) y HU-08 (criterio 7) se actualizaron a **menos de 6 minutos**.
* **Documentación actualizada (v1.2):** CU-06, HU-08 y el Documento de Visión v0.3 (perspectiva del producto, suposiciones y dependencias, costos, restricciones, requisitos de sistema y glosario) ya reflejan esta decisión.

---

## 5. Privacidad

* Solo se monitorearán grupos cuyo **administrador haya autorizado** la presencia de la cuenta de ChuroViaje.
* **No se almacenan** números de teléfono ni nombres de los remitentes en ningún momento.
* El **texto de los mensajes** se guarda solo de forma temporal, hasta que el LLM lo procesa o hasta que supera un tiempo máximo; después se elimina. De forma permanente solo se guarda el dato interpretado del surtidor.
* Los mensajes se envían a un **servicio externo (el proveedor del LLM)**. Se envía únicamente el texto y la fecha, sin datos del remitente, y debe elegirse un proveedor que no use los datos enviados por API para entrenar sus modelos. Esto debe mencionarse en la política de privacidad del sistema.
* La sesión del número dedicado y la credencial del LLM solo son accesibles para los componentes autorizados.

---

## 6. Revisión futura

Esta decisión se revisará si:

* Los surtidores otorgan acceso a una API propia: esa API pasaría a ser la fuente primaria y el monitoreo de WhatsApp quedaría como respaldo, como establece CU-06.
* El número dedicado es bloqueado de forma recurrente, lo que obligaría a reevaluar el enfoque de extracción.
* La precisión del LLM o su costo no resultan aceptables en las pruebas con mensajes reales.
