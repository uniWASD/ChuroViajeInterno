# HU-08 / CU-06 — Investigación y elección del LLM para análisis de mensajes de WhatsApp
## 1. Resumen
Para CU-06 se selecciona Google Gemini 3.1 Flash-Lite (gemini-3.1-flash-lite) como LLM de producción del prototipo, la arquitectura definitiva no envía todos los mensajes al modelo, primero aplica un preclasificador determinista local y solo deriva los casos ambiguos o contextuales, el prototipo actual trabaja exclusivamente con Chat1.txt y Chat2.txt, no se conecta a WhatsApp, Twilio, bots ni APIs de mensajeríam, por defecto toma los últimos 25 mensajes de cada chat es decir, un máximo de 50 mensajes por ejecución.
La versión final agrega todos los casos que requieren LLM en una sola llamada lógica por ejecución, cada caso conserva su identificador de ventana de 5 minutos y el prompt prohíbe usar contexto de otra ventana, si el proceso se programa cada 5 minutos, el diseño queda limitado a 288 llamadas lógicas por día como máximo, sin contar reintentos HTTP por fallos transitorios, aqui la validación controlada alcanzó 40/40 casos correctos con 72,5 % de cobertura local, 100 % de precisión local sobre los casos resueltos por reglas, 11 casos derivados al LLM, 100 % de precisión semántica del LLM en el benchmark final y 0 alucinaciones de entidad, la optimización final a una sola llamada lógica por ejecución fue validada con pruebas automatizadas específicas, no se repitió el benchmark live completo después de esa optimización porque el Tier Gratis de Gemini agotó la cuota durante las pruebas.

## 2. Alcance y restricciones
- Entrada actual: archivos `.txt` exportados de WhatsApp.
- Archivos esperados: Chats/Chat1.txt y Chats/Chat2.txt.
- Límite operativo por defecto: 25 mensajes recientes por chat.
- No existe conexión a chats en vivo.
- No se implementa ningún LLM local en producción.
- El catálogo de surtidores y alias se conserva en el código.
- El análisis es server-side; no debe ejecutarse en frontend.
- La API key se toma de variables de entorno y nunca debe almacenarse en el repositorio.

## 3. T-034 — Capacidad necesaria y costo
3.1 Datos observados
En las exportaciones usadas durante las pruebas se detectaron:

| Archivo         | Mensajes disponibles |
| --------------- | -------------------: |
| Chat1.txt       |                3.917 |
| Chat2.txt       |                  198 |
| Total histórico |                4.115 |

El análisis operativo no procesa los 4.115 mensajes, por defecto usa los 25 últimos de cada archivo, máximo 50.

3.2 Estrategia de lote
- Intervalo lógico: 5 minutos.
- Máximo teórico de ejecuciones por día: 24 × 60 / 5 = 288.
- Preclasificación: local, sin costo de API.
- LLM: como máximo 1 llamada lógica agregada por ejecución cuando existe al menos un caso derivado.
- Reintentos HTTP 429/503 se contabilizan aparte como intentos de transporte, no como nuevos lotes de negocio.

3.3 Escenarios de dimensionamiento
Las siguientes cifras son estimaciones de capacidad, no facturas, se usan 30 días/mes y tarifas estándar por millón de tokens. El número de llamadas/día se mantiene por debajo del máximo de 288.

| Escenario | Mensajes/día | Llamadas LLM/día estimadas | Entrada/call | Salida/call | Gemini 3.1 Flash-Lite/mes | GPT-5.6 Luna/mes | Claude Haiku 4.5/mes |
|---|---:|---:|---:|---:|---:|---:|---:|
| Bajo | 300 | 60 | 1.800 tok | 500 tok | USD 2,16 | USD 1,73 | USD 7,74 |
| Medio | 1.500 | 180 | 3.000 tok | 900 tok | USD 11,34 | USD 9,07 | USD 40,50 |
| Alto | 5.000 | 288 | 4.500 tok | 1.500 tok | USD 29,16 | USD 23,33 | USD 103,68 |

En Gemini Tier Gratuito el costo financiero puede ser cero mientras el proyecto permanezca dentro de sus cuotas, durante esta investigación el proyecto devolvió explícitamente un límite de 500 solicitudes del Tier gratuito para gemini-3.1-flash-lite, Google advierte que los límites activos dependen del proyecto/tier y deben consultarse en AI Studio

## 4. T-035 — Comparación de proveedores API
4.1 Google — Gemini 3.1 Flash-Lite
- Modelo: gemini-3.1-flash-lite.
- Estado: estable/GA desde mayo de 2026.
- Contexto: 1.048.576 tokens de entrada, hasta 65.536 de salida.
- Precio estándar: USD 0,25/M input y USD 1,50/M output.
- Structured Outputs: **sí**.
- Orientación del proveedor: tareas ligeras, extracción, clasificación y alto volumen.
- Tier Gratuito: disponible, sujeto a cuotas del proyecto.
- Latencia observada en smoke test de 5 casos: 4,7–7,6 s según la corrida.
- Benchmark controlado: 40/40 en la configuración híbrida validada.
- Riesgo observado: 429 por cuota y 503 por alta demanda, el código implementa backoff, circuit breaker, checkpoint y --resume.

4.2 OpenAI — GPT-5.6 Luna
- Modelo: gpt-5.6-luna.
- Precio estándar: USD 0,20/M input y USD 1,20/M output.
- Contexto: 1,05 M tokens.
- Structured Outputs: **sí**.
- Free API: no soportado para este modelo.
- Tier 1 publicado: 500 RPM y 500.000 TPM.
- Posicionamiento: cargas de trabajo de alto volumen sensibles al costo.
- Latencia en este proyecto: no se midió con credenciales reales, no se inventan valores.

4.3 Anthropic — Claude Haiku 4.5
- Modelo: claude-haiku-4-5-20251001.
- Precio estándar: USD 1/M input y USD 5/M output.
- Structured Outputs: **sí**, disponibles en Claude API.
- Contexto: hasta 200K en la tarifa estándar publicada para Haiku 4.5.
- Free API: no se documenta una cuota API gratuita general equivalente al Free Tier de Gemini.
- Posicionamiento: modelo Haiku rápido para aplicaciones interactivas y procesamiento de alto volumen.
- Latencia en este proyecto: no se midió con credenciales reales; no se inventan valores.

4.4 Conclusión de comparación
GPT-5.6 Luna es ligeramente más barato por token en tarifa pagada, y Claude Haiku 4.5 es claramente más caro para este volumen. Gemini 3.1 Flash-Lite queda seleccionado porque combina costo bajo, Structured Outputs, experiencia empírica positiva con español informal de Tarija y Tier Gratuito para desarrollo, para producción con mensajes reales de WhatsApp, la decisión debe considerar también la política de datos, por ese motivo se recomienda Gemini con billing activo, no el Tier Gratuito, cuando se procese información personal real.

## 5. Política de datos de los proveedores
5.1.- Google Gemini
En servicios gratuitos, Google indica que puede usar prompts y respuestas para proporcionar, mejorar y desarrollar productos y tecnologías de machine learning; también advierte que revisores humanos pueden leer, anotar y procesar entradas y salidas. Google indica expresamente que no se debe enviar información sensible, confidencial o personal a los servicios gratuitos.
Con un proyecto asociado a Cloud Billing, Gemini API se considera servicio pagado: Google indica que no usa prompts ni respuestas para mejorar sus productos y conserva registros durante un período limitado para seguridad, cumplimiento y detección de abuso.
Implicación para CU-06: el Free Tier es aceptable para mensajes sintéticos/desidentificados de desarrollo. Para conversaciones reales se recomienda activar billing y mantener la pseudonimización implementada.

5.2.- OpenAI API
OpenAI indica que los datos enviados a su API no se usan para entrenar o mejorar sus modelos salvo opt-in explícito. Por defecto los logs de monitoreo de abuso pueden conservar contenido hasta 30 días; clientes elegibles pueden solicitar controles adicionales de retención.

5.3.- Anthropic API
Anthropic indica que por defecto no usa inputs/outputs de sus productos comerciales, incluida la API, para entrenar sus modelos. Para usuarios de Anthropic API, las entradas y salidas se eliminan automáticamente del backend dentro de 30 días, salvo excepciones documentadas o acuerdos específicos.

## 6. T-036 — Opción autoalojada investigada y descartada
Se revisó Qwen3 4B con Ollama como referencia autoalojada pero la distribución qwen3:4b de Ollama tiene 4,02B parámetros y un artefacto Q4_K_M de aproximadamente 2,5 GB; Qwen3 declara soporte multilingüe amplio, ademas no se selecciona para CU-06 por estas razones:
1. El alcance actual solicita descartar LLM locales.
2. No se entregaron especificaciones verificables del VPS para medir RAM disponible, CPU/GPU y throughput real.
3. El tamaño del archivo del modelo no representa el consumo total de memoria en inferencia.
4. La API administrada evita operación de Ollama, actualizaciones, observabilidad y tuning del runtime.
5. Gemini ya alcanzó la precisión requerida en el benchmark controlado.
La opción local queda documentada, pero no implementada.

## 7. T-037 — Set de prueba
Se creó 03_des/HU08_CU06_test_set.json con 40 mensajes y su resultado esperado, el set cubre:
- disponibilidad `HAY`;
- falta de combustible `NO_HAY`;
- preguntas;
- mensajes ambiguos;
- mensajes contradictorios;
- mensajes no relacionados;
- errores ortográficos y alias locales;
- retractaciones y secuencias temporales.
El contrato semántico final distingue contradicción real de secuencia temporal, ejemplo: “llegó diesel pero se acabó al toque” tiene estado final NO_HAY, “hay especial pero también dicen que se acabó” queda CONTRADICTORIO

## 8. T-038 — Prompt base y JSON de salida
8.1 Principios del prompt
El prompt exige:
- no inventar surtidores, combustible, fecha, fila ni estado;
- tratar preguntas como PREGUNTA, no como confirmación;
- usar AMBIGUO cuando falta evidencia;
- separar HAY, NO_HAY y CONTRADICTORIO;
- respetar el catálogo canónico y aliases;
- resolver referencias solo dentro de la misma ventana lógica de 5 minutos;
- no cruzar contexto entre ventanas diferentes aunque todos los casos viajen en una sola solicitud agregada;
- conservar la fecha recibida;
- limitar la evidencia a una paráfrasis breve;
- devolver exclusivamente JSON compatible con schema.

8.2 Catálogo canónico
El código incluye los surtidores y alias del clasificador existente, entre otros: Las Vegas, Don Daniel, Tacuarandi, Campesino, YPFB Morros Blancos, Agrupa, El Portillo, Moto Mendez, Ex Terminal, Sointa, Panamericano, San Jorge, San Geronimo, El Molle, Surtidor Tarija, San Martin, Pimentel, Villanueva, YPFB Parada Chaco, Domingo Savio y Coliseo Universitario.

Combustibles:
- `Gasolina Especial`: especial, clarita, blanquita, blanca, limpia y variantes.
- `Etanol`.
- `Gasolina Plus`: plus, amarilla.
- `Diesel`: diesel, diésel, diessel.

8.3 Estructura de salida
La salida raíz contiene:

```json
{
  "meta": {},
  "eventos": [],
  "mensajes_descartados": [],
  "batches": []
}
```
Evento:
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
Mensaje descartado:
```json
{
  "mensaje_id": "Chat1:000124",
  "motivo": "PREGUNTA",
  "fecha": "2026-09-25T12:31:00-04:00",
  "detalle": "Consulta disponibilidad; no confirma un estado.",
  "origen": "llm"
}
```
Si la API no puede clasificar un caso por cuota/falla, se usa PENDIENTE_API, nunca se convierte una falla de infraestructura en un hecho sobre combustible

## 9. T-039 — Pruebas y resultados
9.1 Benchmark controlado
Resultado empírico final antes de la optimización de batching:

| Métrica | Resultado |
|---|---:|
| Casos | 40 |
| Cobertura local | 72,5 % |
| Precisión local sobre casos resueltos | 100 % |
| Casos derivados a Gemini | 11 |
| Disponibilidad Gemini | 100 % |
| Precisión semántica Gemini | 100 % |
| Precisión global | **100 %** |
| Alucinaciones de entidad | **0** |
| Llamadas exitosas | 11 |
| Intentos HTTP | 17 |
| Reintentos | 6 |
| Costo estimado | USD 0,005316 |
| P95 de latencia | 48,55 s |

El P95 alto estuvo dominado por respuestas 503/reintentos, no por CPU local.

9.2 Prueba con chats reales exportados
Con FIX8 se procesaron 100 mensajes recientes, con timestamps correctos MDY/DMY:
- 100 mensajes contabilizados.
- 12 mensajes únicos con evento.
- 88 descartados únicos.
- 0 solapamientos evento/descarte.
- 21 eventos totales, porque algunos mensajes listaban varios surtidores.
- 24 llamadas API.
- 3 reintentos.
- 0 errores API.
- costo estimado: USD 0,01540575.
- tiempo: 4 min 18 s.
Posteriormente se redujo el límite a 25 mensajes por chat. Las pruebas posteriores expusieron la cuota 429 del Free Tier, lo que motivó checkpoint, --resume, circuit breaker y la optimización final de una sola llamada lógica agregada por ejecución

9.3 Arquitectura final y evidencia pendiente
La versión final envía en una sola solicitud todos los casos derivados, preservando el identificador de ventana para impedir cruces de contexto, esto reduce el máximo lógico a 1 llamada por ejecución y 288/día si se programa cada 5 minutos.
Esta modificación final fue validada con 7/7 pruebas automatizadas específicas: una sola llamada para varias ventanas, preservación del identificador de ventana, fail-closed ante 429, modo rules sin API, límite de 25 mensajes por chat, set de 40 casos y metadatos de proveedores. No se ejecutó nuevamente contra Gemini live porque la cuota Free Tier se encontraba agotada al cierre de la sesión.

## 10. T-040 — Dónde corre el análisis y cómo llega al backend Java
Se define backend Java como punto de orquestación de CU-06. El código Python funciona como worker server-side de análisis y no forma parte del frontend.
Flujo propuesto:
1. Backend Java obtiene o exporta el lote de mensajes.
2. Java invoca el worker Python mediante `ProcessBuilder` o, en una evolución posterior, mediante un microservicio interno HTTP.
3. El worker aplica preclasificación local.
4. Si existen casos derivados, hace una sola llamada lógica a Gemini.
5. El worker escribe/retorna el JSON estructurado.
6. Java valida el `status` (`COMPLETADO`, `PARCIAL`, `PARCIAL_CUOTA`) y persiste los eventos válidos.
7. Los casos `PENDIENTE_API` se reintentan posteriormente; nunca se convierten en eventos.
Para el prototipo con archivos exportados, la salida se guarda como JSON y el resumen se imprime en stdout. El contrato JSON permite migrar el worker a Java más adelante sin cambiar el formato consumido por el backend.

## 11. Intervalo de análisis
Se propone cada 5 minutos porque coincide con el requisito del Issue y da un máximo de 288 ejecuciones/día, la versión de archivos exportados no ejecuta un scheduler ni un bot, el intervalo es la unidad lógica de agrupación y la propuesta de operación futura, en producción debe persistirse el último mensaje procesado para no reanalizar los mismos 25 mensajes en cada ciclo. El límite de 25 por chat es una protección del prototipo, no un sustituto del cursor de mensajes nuevos.

## 12. T-041 — Decisión final
Modelo seleccionado: Google Gemini 3.1 Flash-Lite
Justificación:
- Structured Outputs compatible.
- Diseñado para clasificación/extracción y alto volumen.
- Precio bajo.
- Benchmark controlado 40/40 con 0 alucinaciones de entidad.
- Buen entendimiento de español informal, faltas ortográficas y alias del dominio en las pruebas realizadas.
- Tier Gratuito útil para desarrollo.
- La arquitectura híbrida reduce considerablemente los mensajes enviados al proveedor.
Condición de producción: usar un proyecto con billing activo cuando se procesen mensajes reales con información personal, el Tier Gratuito de Gemini no es apropiado para contenido personal/confidencial según las condiciones vigentes de Google y además mostró límites 429 durante la validación intensiva

## 13. Archivos finales del entregable

```text
PruebaAnalisis/
├── hu08_cu06_analizador_llm.py
└── 03_des/
    ├── HU08_CU06_investigacion_LLM.md
    ├── HU08_CU06_test_set.json
    └── HU08_CU06_benchmark.json
```

`Chats/` puede conservarse localmente para pruebas pero no debería subirse al repositorio si contiene conversaciones reales, los archivos `salida_*.json`, `*.checkpoint.jsonl`, `*.partial.json`, copias `BACKUP`, `ANTES_*` y `__pycache__` son artefactos temporales y no forman parte de la entrega.

## 14. Fuentes consultadas
1. Google — Gemini 3.1 Flash-Lite: https://ai.google.dev/gemini-api/docs/models/gemini-3.1-flash-lite
2. Google — Gemini API rate limits: https://ai.google.dev/gemini-api/docs/rate-limits
3. Google — Gemini API Additional Terms: https://ai.google.dev/gemini-api/terms
4. Google — Gemini API billing: https://ai.google.dev/gemini-api/docs/billing
5. OpenAI — GPT-5.6 Luna: https://developers.openai.com/api/docs/models/gpt-5.6-luna
6. OpenAI — API data controls: https://developers.openai.com/api/docs/guides/your-data
7. Anthropic — Claude Haiku 4.5 announcement/pricing: https://www.anthropic.com/news/claude-haiku-4-5
8. Anthropic — Structured Outputs: https://platform.claude.com/docs/en/build-with-claude/structured-outputs
9. Anthropic — API training policy: https://privacy.claude.com/en/articles/7996868-is-my-data-used-for-model-training
10. Anthropic — API retention: https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data
11. Ollama — Qwen3 4B: https://ollama.com/library/qwen3:4b

## 15. Matriz de cumplimiento del Issue

| Ítem | Evidencia |
|---|---|
| Documento `.md` de investigación | Este archivo |
| Capacidad, tokens, llamadas y costo | Sección 3 |
| 3 proveedores API + 1 opción local | Secciones 4 y 6 |
| Política de datos | Sección 5 |
| Español informal de Tarija | Secciones 7 y 9 |
| Prompt y JSON estructurado | Sección 8 + código |
| Ubicación del proceso | Sección 10 |
| Intervalo de actualización | Sección 11 |
| Recomendación final | Sección 12 |
| T-034 | Sección 3 |
| T-035 | Secciones 4 y 5 |
| T-036 | Sección 6 |
| T-037 | `03_des/HU08_CU06_test_set.json` |
| T-038 | Sección 8 + `hu08_cu06_analizador_llm.py` |
| T-039 | Sección 9 + `03_des/HU08_CU06_benchmark.json` |
| T-040 | Sección 10 |
| T-041 | Sección 12 |
