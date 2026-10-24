# CU-09 — Consultar estado de combustible desde ANH

[Índice de reglas](README.md) · [Índice de la arquitectura](../README.md)

Cubre HU-14 y los requisitos RF-15, RNF-04 y RNF-07. Este proceso es automático y no tiene vista ni controlador REST: lo inicia el reloj del backend y todas sus reglas las cumple el modelo, con una tarea periódica que consulta a ANH y los objetos de dominio que validan lo que responde. Junto con CU-06, es una de las dos fuentes automáticas del estado de los surtidores.

## Reglas

| Código | Regla | Objeto responsable | Fuente |
| --- | --- | --- | --- |
| RN-09.1 | El backend consulta a ANH con una tarea periódica propia, cada [P-29](../08_decisiones_parametros.md). Ningún cliente de ChuroViaje (la app, el simulador o el servicio de WhatsApp/LLM) llama a ANH. | Tarea periódica del backend | CU-09 paso 1, [D-29](../08_decisiones_parametros.md), [P-29](../08_decisiones_parametros.md) |
| RN-09.2 | Cada ciclo pide a ANH el listado de estaciones de Tarija de gasolina y el de diésel, uno por combustible. Antes identifica a Tarija en el catálogo de departamentos de ANH por su nombre: si no hay exactamente una entrada, no consulta y no supone ningún identificador. | Tarea periódica del backend | CU-09 pasos 2 y 3, [D-29](../08_decisiones_parametros.md) |
| RN-09.3 | Toda llamada a ANH es un GET por HTTPS al servidor de ANH, sin credenciales y sin datos de personas. La verificación del certificado y del nombre del servidor nunca se desactiva. Una redirección fuera del servidor de ANH, o a HTTP, se detiene. | Tarea periódica del backend | CU-09 sección 7 |
| RN-09.4 | Cada petición espera como máximo el tiempo de [P-32](../08_decisiones_parametros.md) y se reintenta las veces que diga ese parámetro, solo si falta conexión, se agota el tiempo o ANH responde 429, 502, 503 o 504. Se respeta `Retry-After`: si pide esperar más de 30 segundos, no se reintenta y no se vuelve a consultar hasta pasado ese plazo. Entre una petición y otra hay una pausa. | Tarea periódica del backend | CU-09 ext. 2a, [P-32](../08_decisiones_parametros.md) |
| RN-09.5 | Una respuesta solo se acepta si es HTTP 200, de contenido JSON y de hasta 10 MiB, con `decCodigo` igual a 1 y el resultado en la forma esperada. Un `decCodigo` distinto de 1 es un error de ANH, nunca una lista vacía de estaciones. | ReporteANH | CU-09 ext. 4a |
| RN-09.6 | Se descarta la respuesta completa, sin cambiar ningún estado, si trae estaciones de otro departamento o de otro combustible que el pedido, la misma estación repetida, identificadores que no son números enteros o señales de paginación (el listado estaría incompleto). | ReporteANH | CU-09 ext. 4a |
| RN-09.7 | Si de una estación llega un saldo que no es un número válido o es negativo, ese dato se trata como «sin saldo». Los demás datos de la respuesta siguen su curso. | ReporteANH | CU-09 ext. 4b |
| RN-09.8 | Se ignora toda estación cuya ubicación cae fuera de la mancha urbana de Tarija. ANH devuelve todo el departamento, también Bermejo, Yacuiba y otras ciudades. | Ubicación, ReporteANH | CU-09 ext. 5a, [D-30](../08_decisiones_parametros.md) |
| RN-09.9 | Solo se toma la estación que está vinculada a un surtidor del catálogo por su identificador de ANH. Las demás se ignoran y, si caen dentro de la mancha urbana, se anotan en el registro como candidatas a entrar al catálogo. | Surtidor, ReporteANH | CU-09 ext. 5b, [D-30](../08_decisiones_parametros.md) |
| RN-09.10 | Un surtidor tiene a lo sumo un identificador de ANH, y cada identificador de ANH pertenece a un solo surtidor. El equipo fija el vínculo comparando el nombre y la ubicación de la estación de ANH con los del catálogo, dentro de la distancia máxima de [P-31](../08_decisiones_parametros.md), y lo revisa a mano. | Surtidor | [D-30](../08_decisiones_parametros.md), [P-31](../08_decisiones_parametros.md) |
| RN-09.11 | Los litros se traducen así: 0 litros es agotado, más de 0 litros es disponible y sin saldo es sin dato, que no crea ni cambia ningún estado. Un fallo, un valor ausente o una estación que no aparece en la respuesta nunca se toman como cero litros. | ReporteANH | CU-09 paso 6 y ext. 6a, [D-31](../08_decisiones_parametros.md) |
| RN-09.12 | Se guardan los litros y el estado de ANH (alto, medio o bajo) tal como llegan. El estado de ANH no se convierte en agotado ni en disponible: solo los litros deciden la disponibilidad. | EstadoCombustible | CU-09 paso 6, [D-31](../08_decisiones_parametros.md) |
| RN-09.13 | Un dato válido se convierte en un `EstadoCombustible` con origen ANH, el tipo de combustible que se pidió (Gasolina o Diesel), la fecha que informa ANH (`updated_at`) y el momento en que el backend lo leyó. Si `updated_at` no trae zona horaria, se toma la de Tarija; si falta o no se entiende, el dato no se guarda, porque sin fecha no se sabe si es el más reciente. | EstadoCombustible | CU-09 paso 6 y ext. 6a, [D-28](../08_decisiones_parametros.md) |
| RN-09.14 | Si para ese surtidor y tipo ya hay un dato de ANH con una fecha más reciente, el nuevo no lo reemplaza. Si tiene la misma fecha, solo se actualiza el momento de la lectura. La comparación es solo entre datos de ANH. | EstadoCombustible | CU-09 ext. 6b, CU-05 sección 8 |
| RN-09.15 | Si ANH falla (falta de conexión, error HTTP, certificado inválido o respuesta descartada), cada surtidor conserva su último dato de ANH con su fecha. Un fallo nunca se convierte en cero litros ni cambia un estado. | EstadoCombustible | CU-09 ext. 2a y 4a, [D-32](../08_decisiones_parametros.md) |
| RN-09.16 | Si falla la consulta de un combustible pero no la del otro, el que respondió bien se actualiza igual. | Tarea periódica del backend | CU-09 ext. 2b |
| RN-09.17 | Un dato de ANH vence según la vigencia de [P-30](../08_decisiones_parametros.md), contada desde el momento en que el backend lo leyó por última vez y no desde `updated_at`, porque ANH puede mantener la misma fecha mientras el saldo no cambia. Un dato vencido deja de tomarse en cuenta ([RN-05.5](CU-05_ConsultarDisponibilidadCombustible.md)). | EstadoCombustible | [D-32](../08_decisiones_parametros.md), [P-30](../08_decisiones_parametros.md) |
| RN-09.18 | No se guardan la fecha de última venta ni el indicador de venta que traiga ANH, la dirección ni la zona de la estación, las respuestas completas ni nada de las estaciones ignoradas. ANH no informa cuándo llegó combustible a una estación, y el sistema nunca inventa esa fecha. | ReporteANH | CU-09 sección 7, informe técnico de ANH |
| RN-09.19 | Cada ciclo deja en el registro del backend la hora, las rutas consultadas, el estado HTTP de cada una, cuántas estaciones llegaron, cuántas se aceptaron y cuántas se ignoraron, y el motivo de cada falla. No guarda el cuerpo de la respuesta ni datos de personas. | Tarea periódica del backend | CU-09 sección 7 |

## Recorrido por Vista, Controlador y Modelo

Este proceso no tiene vista ni controlador REST. Lo inicia el reloj del backend.

1. **Tarea periódica del backend.** Se cumple el ciclo de consulta (RN-09.1). Pide a ANH el catálogo de departamentos y localiza a Tarija (RN-09.2).
2. **Cliente de ANH (backend).** Hace las peticiones GET por HTTPS, con su tiempo de espera y sus reintentos (RN-09.3, RN-09.4), y entrega cada respuesta al modelo. Si falla un combustible, el otro sigue (RN-09.16).
3. **ANH.** Responde con las estaciones del departamento: ubicación, saldo en litros, estado y fecha de actualización.
4. **Modelo (backend).** El `ReporteANH` valida la respuesta (RN-09.5 a RN-09.7), ignora las estaciones que caen fuera de la mancha urbana o no están vinculadas a un surtidor (RN-09.8 a RN-09.10) y traduce los litros (RN-09.11, RN-09.12).
5. **Modelo (backend).** El `EstadoCombustible` guarda el dato con origen ANH (RN-09.13, RN-09.14). Si algo falló, cada surtidor conserva su dato anterior (RN-09.15), que vence según su vigencia (RN-09.17).
6. **Tarea periódica del backend.** Deja el resumen del ciclo en el registro (RN-09.19). De ANH no se guarda nada más de lo que dice RN-09.18.
7. **Los conductores.** Ven el estado nuevo en su siguiente consulta de surtidores, que es CU-05.
