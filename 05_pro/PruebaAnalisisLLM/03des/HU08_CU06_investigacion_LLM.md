# HU-08 / CU-06 — Investigación y elección del LLM para análisis de mensajes de WhatsApp

**Fecha de actualización:** 27/09/2026  
**Estado:** arquitectura final corregida y validada en el prototipo  
**Implementación de referencia:** `hu08_cu06_analizador_llm.py`

## 1. Resumen

Para CU-06 se selecciona **Google Gemini 3.1 Flash-Lite (`gemini-3.1-flash-lite`)** como modelo del prototipo. La arquitectura final es híbrida, pero el papel de las reglas locales quedó deliberadamente limitado: **el preclasificador no decide `HAY`, `NO_HAY` ni `CONTRADICTORIO`; solo descarta ruido evidente, saludos aislados, multimedia/elementos omitidos y mensajes sin contenido textual útil**. Todo mensaje textual restante se deriva al LLM para clasificación semántica.

Después de la respuesta del LLM existe una **segunda capa determinista de validación**. Esta capa no se limita a comprobar que un nombre pertenezca al catálogo: también verifica que surtidor, combustible, estado y fila estén respaldados por el mensaje objetivo o por el contexto autorizado de la **misma ventana lógica de 5 minutos**. Si el LLM devuelve un dato canónico pero no sustentado, el evento no se acepta y el mensaje pasa a `AMBIGUO` con `origen="validador"`.

La ejecución operativa toma por defecto los **últimos 25 mensajes de cada chat**, máximo 50 mensajes por ejecución. Los casos que requieren LLM se agregan en **una sola llamada lógica por ejecución**. El prototipo trabaja exclusivamente con exportaciones `.txt`; no se conecta directamente a WhatsApp, Twilio, bots ni APIs de mensajería.

La validación final disponible incluye: **8/8 pruebas deterministas del validador PASS**, un **smoke test LIVE de Gemini de 5/5 casos correctos**, y una prueba LIVE sobre **50 mensajes reales exportados** con 15 descartes locales, 35 mensajes derivados al LLM, una sola llamada API, 3 eventos finales aceptados, 47 descartados, 0 pendientes API y 0 errores API.

## 2. Alcance y restricciones

- Entrada actual: archivos `.txt` exportados de WhatsApp.
- Archivos esperados: `Chats/Chat1.txt` y `Chats/Chat2.txt`.
- Límite por defecto: 25 mensajes recientes por chat, máximo 50 por ejecución.
- No existe conexión a chats en vivo.
- No se implementa un LLM local en producción.
- El análisis es server-side; no debe ejecutarse en frontend.
- La API key se toma de variables de entorno y no debe almacenarse en el repositorio.
- Los autores se pseudonimizan antes de enviarse al proveedor y se sanitizan URL, correo y teléfono en el texto enviado.
- El intervalo de 5 minutos es una **unidad lógica de contexto** y una propuesta de periodicidad futura; el prototipo no implementa scheduler.
- En producción se debe persistir un cursor/último mensaje procesado; tomar los últimos 25 por chat es una protección del prototipo, no sustituye ese cursor.

## 3. T-034 — Capacidad, llamadas, tokens y costo

### 3.1 Datos observados

| Archivo | Mensajes disponibles en las exportaciones de prueba |
|---|---:|
| `Chat1.txt` | 3.917 |
| `Chat2.txt` | 198 |
| **Total histórico** | **4.115** |

El flujo operativo no procesa los 4.115 mensajes en cada ejecución. Por defecto utiliza los 25 últimos de cada archivo, máximo 50.

### 3.2 Estrategia de llamadas

- Intervalo lógico: **5 minutos**.
- Máximo teórico si se ejecutara continuamente: **288 ejecuciones/día**.
- Preclasificador: local y sin costo API; solo descarta ruido evidente.
- LLM: **máximo una llamada lógica agregada por ejecución** cuando existe al menos un mensaje derivado.
- Reintentos HTTP por 408/409/429/5xx se registran como intentos de transporte, no como nuevas llamadas lógicas de negocio.
- Gemini se invoca con Structured Output y `temperature=0` para reducir variación; esto no convierte al modelo en matemáticamente determinista, por lo que la seguridad final depende del validador local.

### 3.3 Evidencia empírica de costo y latencia

**Smoke test final (5 mensajes sintéticos):**

- 5/5 correctos.
- 1.424 tokens de entrada.
- 553 tokens de salida.
- latencia observada: 4.890,3 ms.
- costo estimado a tarifa estándar: **USD 0,0011855**.

**Prueba real final (50 mensajes exportados):**

- 15 descartados por filtro local.
- 35 derivados a Gemini.
- 24 ventanas con al menos un objetivo LLM.
- 1 llamada lógica / 1 llamada API exitosa.
- 0 reintentos.
- costo estimado: **USD 0,00609275**.

Estas cifras son mediciones de pruebas concretas y no deben extrapolarse linealmente sin considerar longitud real de mensajes, cantidad de contexto y tasa de descarte.

### 3.4 Escenarios del estimador incluido en el código

El subcomando `estimate` conserva una hipótesis configurable de planificación: `routed_rate=0.25`, 1.600 tokens fijos de entrada por llamada, 35 tokens de entrada por mensaje derivado y 55 de salida por mensaje derivado. **No es un resultado empírico de la arquitectura final**; es una herramienta de dimensionamiento.

| Escenario | Mensajes/día | Hipótesis derivados al LLM | Llamadas/día estimadas | Tokens totales/call estimados | Gemini/mes | GPT-5.6 Luna/mes | Claude Haiku 4.5/mes |
|---|---:|---:|---:|---:|---:|---:|---:|
| Bajo | 300 | 25% | 67 | 1700.7 | USD 1.0093 | USD 0.8075 | USD 3.9135 |
| Medio | 1500 | 25% | 210 | 1760.7 | USD 3.5466 | USD 2.8373 | USD 13.5675 |
| Alto | 5000 | 25% | 285 | 1994.7 | USD 6.8419 | USD 5.4735 | USD 25.3050 |

Las tarifas usadas por el código para esa estimación son USD 0,25/M input y USD 1,50/M output para Gemini 3.1 Flash-Lite; USD 0,20/M input y USD 1,20/M output para GPT-5.6 Luna; y USD 1/M input y USD 5/M output para Claude Haiku 4.5.

## 4. T-035 — Comparación de proveedores API y estado real de las pruebas

### 4.1 Comparación documental

| Proveedor/modelo | Contexto publicado | Salida máx. publicada | Structured Outputs | Tarifa usada en este proyecto | ¿Prueba LIVE en este proyecto? |
|---|---:|---:|---|---|---|
| Google Gemini 3.1 Flash-Lite | 1.048.576 tokens | 65.536 tokens | Sí | USD 0,25/M input; USD 1,50/M output | **Sí** |
| OpenAI GPT-5.6 Luna | 1.050.000 tokens | 128.000 tokens | Sí, mediante Responses API/JSON Schema | USD 0,20/M input; USD 1,20/M output | **No** |
| Anthropic Claude Haiku 4.5 | 200.000 tokens | 64.000 tokens | Sí | USD 1/M input; USD 5/M output | **No** |
| Qwen3 4B + Ollama | depende del runtime/hardware | depende del runtime | No aplica al mismo contrato administrado | autoalojado | **No** |

### 4.2 Google — Gemini 3.1 Flash-Lite

- Modelo usado: `gemini-3.1-flash-lite`.
- Modelo estable; actualización publicada por Google: mayo de 2026.
- Optimizado para tareas ligeras de alta frecuencia, extracción simple y alto volumen.
- Soporta Structured Outputs.
- Existe **Free Tier** para este modelo, sujeto a cuotas activas del proyecto.
- Fue la única alternativa API que se pudo probar de extremo a extremo en este proyecto sin contratar créditos de otro proveedor.
- Evidencia final: smoke 5/5 y prueba real de 50 mensajes con una llamada API exitosa.
- Durante iteraciones anteriores se observaron respuestas 429/503; por ello el worker conserva backoff, trazas, checkpoint y `--resume`.

### 4.3 OpenAI — GPT-5.6 Luna: investigado, no probado LIVE

GPT-5.6 Luna se dejó implementado como proveedor alternativo en el worker, pero **no se ejecutó un benchmark LIVE con credenciales reales en este proyecto**. La razón es económica/operativa, no técnica: no se dispuso de una organización OpenAI API con créditos disponibles para estas pruebas.

La documentación actual de OpenAI describe la API para cuentas nuevas mediante facturación prepagada. Si una cuenta recibe créditos gratuitos, estos se consumen primero, pero **no se asumió ni se contó con una prueba gratuita general garantizada**. Para no inventar resultados, el documento no asigna precisión ni latencia empírica a GPT-5.6 Luna.

### 4.4 Anthropic — Claude Haiku 4.5: investigado, no probado LIVE

Claude Haiku 4.5 también quedó implementado como proveedor alternativo y soporta Structured Outputs, pero **no se ejecutó un benchmark LIVE con credenciales reales**. Anthropic documenta el uso normal de su API mediante créditos de uso prepagados que deben comprarse antes de consumir la API. Existen programas especiales que pueden otorgar créditos gratuitos a determinados investigadores, pero no constituyen un Free Tier general equivalente al usado con Gemini para este proyecto.

Por esa razón no se inventan valores de precisión, latencia ni costo empírico de Claude en este trabajo.

### 4.5 Qué se pudo y qué no se pudo comparar empíricamente

- **Gemini 3.1 Flash-Lite:** sí se midió precisión, costo, uso y latencia con llamadas reales.
- **GPT-5.6 Luna:** comparación documental de precio, contexto y capacidad; sin benchmark LIVE por falta de créditos/credencial facturable disponible para el proyecto.
- **Claude Haiku 4.5:** comparación documental; sin benchmark LIVE porque el acceso normal a la API requiere créditos prepagados y no se dispuso de ellos.
- **Qwen3 4B/Ollama:** no se ejecutó porque la opción local quedó fuera del alcance y no se contaba con especificaciones verificables del VPS/hardware objetivo para una medición comparable.

Por tanto, la selección de Gemini se apoya en **evidencia empírica propia para Gemini** y en comparación documental oficial para las otras alternativas; no se presenta como si los tres proveedores hubieran sido benchmarkeados bajo las mismas condiciones.

## 5. Política de datos de los proveedores

### 5.1 Google Gemini

Google distingue entre servicios gratuitos y pagados. En el nivel gratuito, el contenido puede utilizarse para mejorar productos; para servicios pagados, Google declara que no usa prompts ni respuestas para mejorar sus productos. En servicios pagados existen registros limitados para seguridad/abuso según las condiciones aplicables.

**Implicación para CU-06:** el Free Tier se utilizó para pruebas sintéticas y del prototipo. Para procesamiento de conversaciones reales con datos personales se recomienda habilitar billing y mantener la pseudonimización/sanitización ya implementadas.

### 5.2 OpenAI API

OpenAI documenta que los datos enviados a su API no se usan para entrenar o mejorar sus modelos salvo opt-in. Los registros de monitoreo de abuso pueden conservar contenido hasta 30 días por defecto, con controles adicionales disponibles para clientes elegibles.

### 5.3 Anthropic API

Anthropic documenta que por defecto no usa inputs/outputs de sus productos comerciales, incluida la API, para entrenar sus modelos. Para usuarios de la API, la retención estándar elimina automáticamente entradas y salidas del backend dentro de 30 días, salvo excepciones documentadas o acuerdos específicos.

## 6. T-036 — Opción autoalojada investigada y descartada

Se revisó Qwen3 4B con Ollama como referencia autoalojada. No se implementó ni se probó en este entregable porque:

1. El alcance actual descarta un LLM local como solución de producción.
2. No se entregaron especificaciones verificables del VPS/hardware objetivo para medir RAM, CPU/GPU y throughput real.
3. El tamaño del archivo cuantizado no representa por sí solo el consumo total de memoria en inferencia.
4. Una API administrada evita operar Ollama, actualizaciones y tuning del runtime.
5. Gemini ya dispone de evidencia empírica suficiente para el prototipo.

La opción local queda documentada como alternativa investigada, no como alternativa benchmarkeada.

## 7. T-037 — Set de prueba

`03_des/HU08_CU06_test_set.json` contiene 40 mensajes sintéticos/controlados y su resultado esperado. Cubre:

- `HAY`;
- `NO_HAY`;
- `PREGUNTA`;
- `AMBIGUO`;
- `CONTRADICTORIO`;
- `NO_RELEVANTE`;
- errores ortográficos y alias locales;
- retractaciones y secuencias temporales.

El contrato semántico distingue contradicción real de secuencia temporal: “llegó diesel pero se acabó al toque” debe terminar como `NO_HAY`, mientras que “hay especial pero también dicen que ya se acabó” queda `CONTRADICTORIO` cuando no existe resolución fiable.

Con la arquitectura final, el preclasificador del set controlado maneja localmente solo 1/40 casos (`<Multimedia omitido>`) y deriva 39/40 al LLM. Esto es deliberado: las reglas ya no intentan decidir disponibilidad.

## 8. T-038 — Prompt completo, JSON Schema y catálogo con alias

Esta sección reemplaza la versión anterior, que solo resumía los principios del prompt y listaba nombres canónicos sin desarrollar sus alias.

### 8.1 Contrato de tres capas

1. **Preclasificador local:** solo descarta ruido evidente; nunca crea eventos `HAY`/`NO_HAY`.
2. **LLM:** interpreta semánticamente mensajes textuales y devuelve eventos o descartes mediante Structured Output.
3. **Validador determinista:** comprueba catálogo, evidencia y contexto autorizado; si un evento no está sustentado, se convierte en `AMBIGUO` con `origen="validador"`.

### 8.2 Texto completo del prompt de clasificación

El siguiente bloque reproduce el contenido funcional del `SYSTEM_PROMPT`. Para legibilidad documental, los alias del catálogo se muestran como expresiones humanas equivalentes; los patrones regex exactos usados por el código se detallan en 8.3. La fuente de verdad ejecutable sigue siendo `SYSTEM_PROMPT` en `hu08_cu06_analizador_llm.py`.

```text
Eres un clasificador de mensajes de grupos de WhatsApp de Tarija, Bolivia, para monitorear surtidores de combustible.
Tu tarea es EXTRAER HECHOS, no conversar. Debes entender español informal, abreviaturas, errores ortográficos y regionalismos.

REGLAS CRITICAS:
1. No inventes surtidores, combustibles, fechas, filas ni estados.
2. Analiza únicamente mensajes con analizar=true. Los de analizar=false son contexto y NUNCA deben aparecer como evento/descartado.
3. Una pregunta o solicitud explícita sobre combustible/surtidor ("dnd hay", "avisen", "alguien sabe") NO confirma disponibilidad: descártala como PREGUNTA.
3a. Si una frase interrogativa depende de un antecedente ausente (por ejemplo "ahí sigue?") y no identifica qué combustible/surtidor consulta, usa AMBIGUO; no inventes el antecedente.
4. Si un mensaje afirma claramente disponibilidad: estado=HAY. Si afirma agotado/no disponible: estado=NO_HAY.
4a. Expresiones vagas como "está normal", "ahí sigue", "por ahí", "dicen", "creo" o similares NO bastan por sí solas para declarar HAY/NO_HAY; si falta evidencia explícita, usa AMBIGUO.
5. Si el mismo mensaje contiene afirmaciones positiva y negativa sobre el mismo objetivo, decide así:
   - CONTRADICTORIO cuando coexisten versiones incompatibles sin resolución fiable (por ejemplo "hay, pero otro dice que no" o "dicen que ya se acabó").
   - NO_HAY cuando el propio mensaje establece una secuencia temporal o retractación inequívoca cuyo estado final es falta de combustible (por ejemplo "llegó pero se acabó al toque", "está vendiendo... mentira, ya no hay", "al final quedó sin combustible").
   - HAY solo si la secuencia inequívoca termina confirmando disponibilidad actual.
   No elijas una cláusula solo por aparecer al final: debe existir una señal temporal o retractación explícita.
6. Si la frase no permite afirmar un estado con suficiente seguridad: descártala como AMBIGUO.
7. Si no tiene relación con combustible/surtidores: NO_RELEVANTE.
8. Si no se indica tipo de combustible, usa NO_ESPECIFICADO. Si no se puede identificar el surtidor, NO_IDENTIFICADO.
9. Cada entrada incluye una ventana lógica. Puedes resolver pronombres/referencias usando SOLO mensajes de la misma ventana lógica. Nunca relaciones mensajes de ventanas distintas. Si no existe un antecedente explícito suficiente, no lo inventes. Si infieres por contexto, confianza <= 85.
10. Conserva la fecha de entrada exactamente. No completes datos faltantes.
11. Evidencia: máximo 160 caracteres y solo una paráfrasis breve del texto que sustenta la clasificación.
12. Responde exclusivamente con el JSON que cumple el esquema solicitado.

CATALOGO CANONICO DE SURTIDORES Y ALIAS:
- Las Vegas: las vegas, la vegas, vegas, mas vegas.
- Don Daniel: don daniel, daniel.
- Tacuarandi: tacuarandi, tacurandi.
- Campesino: campesino.
- YPFB Morros Blancos: ypfb ... morros, morros blancos, ypfb cuando no aparece campesino/parada/chaco.
- Agrupa: agrupa.
- El Portillo: portillo.
- Moto Mendez: moto méndez, moto mendez, moto mendes.
- Ex Terminal: ex terminal, exterminal, la terminal, laterminal.
- Sointa: sointa, sointar.
- Panamericano: panamericano.
- San Jorge: san jorge, san gorje.
- San Geronimo: san geronimo, san gerónimo.
- El Molle: el molle, molle.
- Surtidor Tarija: surtidor tarija, el tarija.
- San Martin: san martin, san martín.
- Pimentel: pimentel.
- Villanueva: villanueva, villa nueva.
- YPFB Parada Chaco: parada chaco.
- Domingo Savio: domingo savio.
- Coliseo Universitario: coliseo universitario, coliseo univ.

COMBUSTIBLES Y ALIAS PRINCIPALES:
- Gasolina Especial: especial, clarita, calarita, clara, blanquita, blanca, blankita, limpia, chura, linda, wena.
- Etanol: etanol, etanos.
- Gasolina Plus: plus, amarilla.
- Diesel: diesel, diésel, diessel.

FILA:
- SIN_FILA: sin fila/no hay fila.
- CORTA: poca/poquita/fila corta.
- MEDIA: fila media/mediana.
- LARGA: mucha fila/fila larga/filón/cola larga.
- DESCONOCIDA: si no existe evidencia explícita.
```

### 8.3 Catálogo canónico de surtidores, alias y patrones exactos

| Surtidor canónico | Alias/variantes reconocidas | Patrones almacenados en `SURTIDORES` |
|---|---|---|
| Las Vegas | `las vegas`, `la vegas`, `vegas`, `mas vegas` | `\blas\s*vegas\b`<br>`\bla\s*vegas\b`<br>`\bvegas\b`<br>`\bmas\s*vegas\b` |
| Don Daniel | `don daniel`, `daniel` | `\bdon\s*daniel\b`<br>`\bdaniel\b` |
| Tacuarandi | `tacuarandi`, `tacurandi` | `\btacuarandi\b`<br>`\btacurandi\b` |
| Campesino | `campesino` | `\bcampesino\b` |
| YPFB Morros Blancos | `ypfb ... morros`, `morros blancos`, `ypfb cuando no aparece campesino/parada/chaco` | `\bypfb\b.*\bmorros\b`<br>`\bmorros\s*blancos\b`<br>`\bypfb\b(?!.*\b(?:campesino\|parada\|chaco)\b)` |
| Agrupa | `agrupa` | `\bagrupa\b` |
| El Portillo | `portillo` | `\bportillo\b` |
| Moto Mendez | `moto méndez`, `moto mendez`, `moto mendes` | `\bmoto\s*m[eé]ndez\b`<br>`\bmoto\s*mende[sz]\b` |
| Ex Terminal | `ex terminal`, `exterminal`, `la terminal`, `laterminal` | `\bex\s*terminal\b`<br>`\bexterminal\b`<br>`\bla\s*terminal\b`<br>`\blaterminal\b` |
| Sointa | `sointa`, `sointar` | `\bsointa\b`<br>`\bsointar\b` |
| Panamericano | `panamericano` | `\bpanamericano\b` |
| San Jorge | `san jorge`, `san gorje` | `\bsan\s*jorge\b`<br>`\bsan\s*gorje\b` |
| San Geronimo | `san geronimo`, `san gerónimo` | `\bsan\s*ger[oó]nimo\b` |
| El Molle | `el molle`, `molle` | `\bel\s*molle\b`<br>`\bmolle\b` |
| Surtidor Tarija | `surtidor tarija`, `el tarija` | `\bsurtidor\s*tarija\b`<br>`\bel\s*tarija\b` |
| San Martin | `san martin`, `san martín` | `\bsan\s*mart[ií]n\b` |
| Pimentel | `pimentel` | `\bpimentel\b` |
| Villanueva | `villanueva`, `villa nueva` | `\bvillanueva\b`<br>`\bvilla\s*nueva\b` |
| YPFB Parada Chaco | `parada chaco` | `\bparada\s*chaco\b` |
| Domingo Savio | `domingo savio` | `\bdomingo\s*savio\b` |
| Coliseo Universitario | `coliseo universitario`, `coliseo univ` | `\bcoliseo\s*universitario\b`<br>`\bcoliseo\s*univ\b` |

### 8.4 Catálogo de combustibles y alias

| Combustible canónico | Alias/variantes | Patrones almacenados en `COMBUSTIBLES` |
|---|---|---|
| Gasolina Especial | especial, clarita, calarita, clara, blanquita, blanca, blankita, limpia, buena + gasolina/gaso, chura, linda, wena | `\bespecial\b`<br>`\bclarita\b`<br>`\bcalarita\b`<br>`\bclara\b`<br>`\bblanquita\b`<br>`\bblanca\b`<br>`\bblankita\b`<br>`\blimpia\b`<br>`\bbuena\b(?=.*\bgasolina\b\|\bgaso\b)`<br>`\bchura\b`<br>`\blinda\b`<br>`\bwena\b` |
| Etanol | etanol, etanos | `\betanol\b`<br>`\betanos\b` |
| Gasolina Plus | plus, amarilla | `\bplus\b`<br>`\bamarilla\b` |
| Diesel | diesel, diésel, diessel | `\bdi[eé]ss?el\b`<br>`\bdiessel\b` |

### 8.5 JSON Schema exacto solicitado al LLM

El LLM no devuelve `meta` ni `batches`. Su Structured Output está restringido al siguiente schema, que corresponde a `OUTPUT_SCHEMA` en el código:

```json
{
  "type": "object",
  "properties": {
    "eventos": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "mensaje_id": {
            "type": "string"
          },
          "surtidor": {
            "type": "string",
            "enum": [
              "Las Vegas",
              "Don Daniel",
              "Tacuarandi",
              "Campesino",
              "YPFB Morros Blancos",
              "Agrupa",
              "El Portillo",
              "Moto Mendez",
              "Ex Terminal",
              "Sointa",
              "Panamericano",
              "San Jorge",
              "San Geronimo",
              "El Molle",
              "Surtidor Tarija",
              "San Martin",
              "Pimentel",
              "Villanueva",
              "YPFB Parada Chaco",
              "Domingo Savio",
              "Coliseo Universitario",
              "NO_IDENTIFICADO"
            ]
          },
          "combustible": {
            "type": "string",
            "enum": [
              "Gasolina Especial",
              "Etanol",
              "Gasolina Plus",
              "Diesel",
              "NO_ESPECIFICADO"
            ]
          },
          "estado": {
            "type": "string",
            "enum": [
              "HAY",
              "NO_HAY",
              "CONTRADICTORIO",
              "DESCONOCIDO"
            ]
          },
          "fila": {
            "type": "string",
            "enum": [
              "SIN_FILA",
              "CORTA",
              "MEDIA",
              "LARGA",
              "DESCONOCIDA"
            ]
          },
          "fecha": {
            "type": "string"
          },
          "confianza": {
            "type": "integer",
            "minimum": 0,
            "maximum": 100
          },
          "evidencia": {
            "type": "string"
          }
        },
        "required": [
          "mensaje_id",
          "surtidor",
          "combustible",
          "estado",
          "fila",
          "fecha",
          "confianza",
          "evidencia"
        ],
        "additionalProperties": false
      }
    },
    "mensajes_descartados": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "mensaje_id": {
            "type": "string"
          },
          "motivo": {
            "type": "string",
            "enum": [
              "PREGUNTA",
              "AMBIGUO",
              "NO_RELEVANTE"
            ]
          },
          "fecha": {
            "type": "string"
          },
          "detalle": {
            "type": "string"
          }
        },
        "required": [
          "mensaje_id",
          "motivo",
          "fecha",
          "detalle"
        ],
        "additionalProperties": false
      }
    }
  },
  "required": [
    "eventos",
    "mensajes_descartados"
  ],
  "additionalProperties": false
}
```

### 8.6 JSON final del worker

Después de validar la salida del LLM, el worker agrega metadatos operativos y estadísticas. La raíz final que consume el backend tiene esta forma:

```json
{
  "meta": {},
  "eventos": [],
  "mensajes_descartados": [],
  "batches": []
}
```

Ejemplo de evento final aceptado:

```json
{
  "mensaje_id": "Chat1:000123",
  "surtidor": "Tacuarandi",
  "combustible": "Gasolina Especial",
  "estado": "HAY",
  "fila": "CORTA",
  "fecha": "2026-09-25T12:30:00-04:00",
  "confianza": 95,
  "evidencia": "Confirma especial con poca fila.",
  "origen": "llm"
}
```

Ejemplo de descarte por el LLM:

```json
{
  "mensaje_id": "Chat1:000124",
  "motivo": "PREGUNTA",
  "fecha": "2026-09-25T12:31:00-04:00",
  "detalle": "Consulta disponibilidad; no confirma un estado.",
  "origen": "llm"
}
```

Ejemplo de evento rechazado por la segunda capa determinista:

```json
{
  "mensaje_id": "Chat2:000194",
  "motivo": "AMBIGUO",
  "fecha": "2026-09-19T17:24:00-04:00",
  "detalle": "Surtidor sin respaldo en el mensaje o contexto autorizado",
  "origen": "validador"
}
```

Si la API falla o alcanza cuota/rate limit, el worker utiliza `PENDIENTE_API`; una falla de infraestructura nunca se transforma en un hecho sobre combustible.

## 9. Validación determinista posterior al LLM

El validador final no confía ciegamente en una entidad solo porque exista en el catálogo.

Para cada evento propuesto por el LLM verifica:

- que `mensaje_id` corresponda realmente a un objetivo de la ejecución;
- que `surtidor` sea canónico o `NO_IDENTIFICADO`;
- que el surtidor esté mencionado directamente o pueda resolverse de forma no ambigua desde contexto anterior **autorizado de la misma ventana**;
- que `combustible` sea canónico o `NO_ESPECIFICADO` y esté igualmente respaldado;
- que `estado` sea uno de `HAY`, `NO_HAY`, `CONTRADICTORIO` o `DESCONOCIDO`;
- que `HAY`/`NO_HAY`/`CONTRADICTORIO` tenga evidencia textual suficiente;
- que expresiones vagas como “había”, “creo”, “dicen”, “supuestamente”, “tal vez”, “imagino” o “debe haber” no se conviertan automáticamente en hechos;
- que una pregunta no se convierta en evento;
- que una fila distinta de `DESCONOCIDA` esté respaldada directamente por el texto objetivo;
- que la confianza quede en 0–100 y se limite a máximo 85 cuando el evento depende de contexto;
- que la fecha final sea siempre la fecha original guardada por el parser, no una fecha inventada por el LLM.

Si alguna comprobación de grounding falla, el evento se rechaza y el mensaje pasa a `AMBIGUO` con `origen="validador"`.

### 9.1 Self-test del validador

El comando `selftest-validator` terminó **8/8 PASS**. Las pruebas incluidas son:

1. `preclasificador_no_decide_disponibilidad`;
2. `rechaza_surtidor_canonico_no_respaldado`;
3. `rechaza_combustible_canonico_no_respaldado`;
4. `rechaza_contexto_fuera_de_ventana`;
5. `rechaza_estado_no_hay_sin_evidencia`;
6. `acepta_contexto_valido_misma_ventana`;
7. `restaura_fecha_original`;
8. `rechaza_fila_no_respaldada`.

## 10. T-039 — Pruebas y resultados finales

### 10.1 Resultados que ya no se usan como evidencia final

La versión anterior del documento reportaba **72,5 % de cobertura local, 11 casos LLM y 40/40 en una arquitectura híbrida anterior**. Esas cifras fueron eliminadas como evidencia final porque pertenecían a una versión donde el preclasificador todavía resolvía semánticamente más casos. Ya no describen el comportamiento del código actual.

Asimismo, la prueba histórica de 100 mensajes/FIX8 queda como antecedente de desarrollo, pero no se utiliza para caracterizar la versión final.

### 10.2 Preclasificador actual sobre T-037

| Métrica | Resultado |
|---|---:|
| Casos | 40 |
| Manejados localmente | 1 |
| Derivados al LLM | 39 |
| Cobertura local | 2,5 % |
| Precisión local sobre lo manejado | 100 % |

El único caso resuelto localmente en ese set es el ruido explícito `<Multimedia omitido>`.

### 10.3 Smoke test LIVE final de Gemini

| Métrica | Resultado |
|---|---:|
| Casos | 5 |
| Aciertos | 5 |
| Accuracy | 1,0 |
| Input tokens | 1.424 |
| Output tokens | 553 |
| Latencia | 4.890,3 ms |
| Costo estimado | USD 0,0011855 |

Casos incluidos: `HAY`, `NO_HAY`, `PREGUNTA`, `CONTRADICTORIO` y `NO_RELEVANTE`.

### 10.4 Prueba LIVE final con 50 mensajes reales exportados

| Métrica | Resultado |
|---|---:|
| Mensajes procesados | 50 |
| Máximo por chat | 25 |
| Ventanas lógicas | 33 |
| Descartados localmente | 15 |
| Derivados al LLM | 35 |
| Ventanas con LLM | 24 |
| Llamadas lógicas LLM | 1 |
| API calls | 1 |
| API attempts | 1 |
| API retries | 0 |
| Eventos finales aceptados | 3 |
| Mensajes descartados finales | 47 |
| Pendientes API | 0 |
| Errores API | 0 |
| Costo estimado | USD 0,00609275 |

Los tres eventos aceptados fueron respaldados por evidencia disponible en el mensaje/contexto permitido. Cuatro eventos propuestos por el LLM fueron rechazados por el validador determinista:

| Mensaje | Decisión final | Motivo del validador |
|---|---|---|
| `Chat1:003895` | `AMBIGUO` | Surtidor sin respaldo en el mensaje o contexto autorizado |
| `Chat2:000190` | `AMBIGUO` | Estado sin evidencia suficiente en el mensaje o contexto autorizado |
| `Chat2:000194` | `AMBIGUO` | Surtidor sin respaldo en el mensaje o contexto autorizado |
| `Chat2:000197` | `AMBIGUO` | Estado sin evidencia suficiente en el mensaje o contexto autorizado |

Esta prueba demuestra la función de la tercera capa: un valor puede existir en el catálogo y aun así ser rechazado si no está sustentado por la evidencia permitida.

### 10.5 Alcance de la evidencia

No se afirma un benchmark final `40/40` LIVE de la arquitectura corregida porque ese benchmark completo **no se volvió a ejecutar después del refuerzo del validador**. La evidencia final disponible es el self-test 8/8, el smoke LIVE 5/5 y la prueba real LIVE de 50 mensajes descrita arriba.

Tampoco se atribuyen resultados empíricos a OpenAI o Anthropic, porque no fueron ejecutados con credenciales reales en este proyecto.

## 11. T-040 — Dónde corre el análisis y cómo llega al backend Java

El backend Java se define como punto de orquestación de CU-06. El código Python funciona como worker server-side y no forma parte del frontend.

Flujo propuesto:

1. Java obtiene/exporta el lote de mensajes.
2. Invoca el worker Python mediante `ProcessBuilder` o, posteriormente, un microservicio HTTP interno.
3. El worker parsea y pseudonimiza.
4. El preclasificador descarta únicamente ruido evidente.
5. Los mensajes textuales restantes se agrupan por ventana lógica y se envían en una sola llamada lógica al LLM.
6. El LLM devuelve JSON estructurado.
7. El validador determinista comprueba evidencia, catálogo, contexto, fecha y fila.
8. El worker retorna/escribe el JSON final.
9. Java valida `status` (`COMPLETADO`, `PARCIAL`, `PARCIAL_CUOTA`) y persiste solo eventos aceptados.
10. Los casos `PENDIENTE_API` se reintentan posteriormente y nunca se convierten en eventos.

## 12. Intervalo de análisis

Se propone una ejecución cada 5 minutos porque coincide con la unidad lógica del caso de uso y limita el máximo teórico a 288 ejecuciones por día. En el prototipo con archivos exportados, el intervalo se usa para aislar contexto; no existe un scheduler incorporado.

La versión final puede transportar varias ventanas en una sola llamada API, pero el validador conserva para cada objetivo su **contexto autorizado por ventana**. Por ello, una entidad real tomada de otra ventana no pasa la validación aunque el LLM la haya devuelto.

## 13. T-041 — Decisión final

**Modelo seleccionado para el prototipo: Google Gemini 3.1 Flash-Lite.**

Justificación:

- Structured Outputs compatible.
- Costo bajo para clasificación/extracción.
- Free Tier disponible para desarrollo y pruebas.
- Evidencia empírica propia: smoke 5/5 y prueba real de 50 mensajes.
- Manejo correcto de español informal y alias en los casos probados.
- La arquitectura final ya no delega la seguridad de los hechos exclusivamente al LLM: existe validación determinista posterior.
- Una sola llamada lógica agregada por ejecución reduce overhead y consumo de requests.

**Condición de producción:** cuando se procesen conversaciones reales con información personal, utilizar un proyecto con billing activo y mantener la pseudonimización/sanitización. El Free Tier se conserva como entorno de desarrollo/pruebas, no como recomendación para datos personales reales.

## 14. Archivos finales del entregable

```text
PruebaAnalisisLLM/
├── hu08_cu06_analizador_llm.py
└── 03_des/
    ├── HU08_CU06_investigacion_LLM.md
    ├── HU08_CU06_test_set.json
    └── HU08_CU06_benchmark.json
```

`Chats/` puede conservarse localmente para pruebas, pero no debería subirse al repositorio si contiene conversaciones reales. `salida_*.json`, `estabilidad_*.json`, `*.checkpoint.jsonl`, `*.partial.json`, copias `BACKUP`, `ANTES_*` y `__pycache__/` son artefactos de prueba/operación y no forman parte del entregable principal.

**Importante sobre `HU08_CU06_benchmark.json`:** si se conserva el archivo antiguo con métricas de la arquitectura anterior, debe actualizarse o identificarse explícitamente como histórico. No debe presentarse junto con este documento como si sus valores `72,5 % / 11 casos LLM` correspondieran al código final.

## 15. Fuentes consultadas y verificadas

Verificación documental actualizada al **27/09/2026**.

1. Google — Gemini 3.1 Flash-Lite: https://ai.google.dev/gemini-api/docs/models/gemini-3.1-flash-lite
2. Google — Gemini API pricing: https://ai.google.dev/gemini-api/docs/pricing
3. Google — Gemini API rate limits: https://ai.google.dev/gemini-api/docs/rate-limits
4. Google — Zero data retention / paid-services data use: https://ai.google.dev/gemini-api/docs/zdr
5. Google — Data logging and sharing: https://ai.google.dev/gemini-api/docs/logs-policy
6. OpenAI — GPT-5.6 Luna: https://developers.openai.com/api/docs/models/gpt-5.6-luna
7. OpenAI — Structured Outputs: https://developers.openai.com/api/docs/guides/structured-outputs
8. OpenAI — Data controls: https://developers.openai.com/api/docs/guides/your-data
9. OpenAI — Prepaid API billing: https://help.openai.com/en/articles/8264644-setting-up-and-managing-prepaid-api-billing
10. Anthropic — Models overview / Claude Haiku 4.5: https://platform.claude.com/docs/en/about-claude/models
11. Anthropic — Pricing: https://platform.claude.com/docs/en/about-claude/pricing
12. Anthropic — Structured Outputs: https://platform.claude.com/docs/en/build-with-claude/structured-outputs
13. Anthropic — API prepaid usage credits: https://support.anthropic.com/en/articles/8977456-how-do-i-pay-for-my-api-usage
14. Anthropic — Commercial/API training policy: https://privacy.claude.com/en/articles/7996868-is-my-data-used-for-model-training
15. Anthropic — API retention: https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data
16. Ollama — Qwen3 4B: https://ollama.com/library/qwen3:4b

## 16. Matriz de cumplimiento del Issue

| Ítem | Evidencia final |
|---|---|
| Documento `.md` de investigación | Este archivo |
| Capacidad, tokens, llamadas y costo | Sección 3 |
| 3 proveedores API + 1 opción local | Secciones 4 y 6 |
| Identificación explícita de alternativas no probadas | Sección 4.3–4.5 |
| Política de datos | Sección 5 |
| Español informal de Tarija | Secciones 7 y 10 |
| Prompt completo | Sección 8.2 |
| Catálogo con alias | Secciones 8.3 y 8.4 |
| JSON Schema completo | Sección 8.5 |
| Validador determinista post-LLM | Sección 9 |
| Ubicación del proceso | Sección 11 |
| Intervalo de actualización | Sección 12 |
| Recomendación final | Sección 13 |
| T-034 | Sección 3 |
| T-035 | Secciones 4 y 5 |
| T-036 | Sección 6 |
| T-037 | Sección 7 + `03_des/HU08_CU06_test_set.json` |
| T-038 | Sección 8 + `hu08_cu06_analizador_llm.py` |
| T-039 | Sección 10 |
| T-040 | Sección 11 |
| T-041 | Sección 13 |
