# CU-05 — Consultar disponibilidad de combustible

[Índice de reglas](README.md) · [Índice de la arquitectura](../README.md)

Cubre HU-07 y HU-12, y los requisitos RF-07, RF-13, RNF-03, RNF-07 y RNF-09.

## Reglas

| Código | Regla | Objeto responsable | Fuente |
| --- | --- | --- | --- |
| RN-05.1 | Solo un conductor con sesión vigente puede consultar los surtidores y confirmar una recarga. | Sesión | CU-05 sección 7, RNF-08 |
| RN-05.2 | La consulta parte de la ubicación actual del conductor y devuelve los surtidores del catálogo ordenados del más cercano al más lejano. | Surtidor | CU-05 pasos 1 y 2, HU-07 c.1 |
| RN-05.3 | Para cada surtidor y tipo de combustible se muestra un solo dato: el más reciente, venga de WhatsApp o de Conductores. | EstadoCombustible | CU-05 sección 8, HU-07 c.2 |
| RN-05.4 | Todo dato se muestra con su disponibilidad (disponible o agotado), su tipo de combustible, su origen y su fecha. La fila aparece solo si se conoce. | EstadoCombustible | CU-05 paso 4, HU-07 c.1 y c.3, RNF-07 |
| RN-05.5 | Un dato con más de 6 horas deja de tomarse en cuenta. Un surtidor sin ningún dato vigente se muestra como «sin información reciente». | EstadoCombustible | CU-05 ext. 3a y sección 8, HU-07 c.4 |
| RN-05.6 | Todo dato lleva un tipo del catálogo. Cuando un mensaje de WhatsApp no lo indica, el dato se guarda y se muestra con el tipo Desconocido. | TipoCombustible | CU-05 sección 8, HU-07 c.5 |
| RN-05.7 | Nunca se muestra quién aportó un dato. | EstadoCombustible | CU-05 sección 7, HU-07 seguridad |
| RN-05.8 | La app pregunta «¿Acabas de recargar combustible?» cuando, estando abierta, detecta que el conductor estuvo detenido unos minutos junto a un surtidor del catálogo. | Controlador de combustible de la app | Decisión [D-11](../08_decisiones_parametros.md), propuesta [P-09](../08_decisiones_parametros.md) |
| RN-05.9 | Si el conductor responde que sí, debe elegir uno de los cuatro tipos: Gasolina, Diesel, Gasolina Premium o Diesel ULS. Desconocido no es una opción. | ConfirmaciónRecarga | CU-05 ext. 4a, HU-12 c.1 |
| RN-05.10 | Si responde que no, o cierra cualquiera de las dos preguntas, no se registra nada. | ConfirmaciónRecarga | CU-05 ext. 4a, HU-12 c.3 |
| RN-05.11 | Una confirmación aceptada marca ese combustible como disponible en ese surtidor, con origen Conductores y con la fecha de la confirmación. | ConfirmaciónRecarga, EstadoCombustible | CU-05 ext. 4a, HU-12 c.2 |
| RN-05.12 | Una confirmación solo puede decir que hay combustible. Nunca marca un surtidor como agotado. | ConfirmaciónRecarga | HU-12 |
| RN-05.13 | El backend acepta la confirmación solo si la ubicación que envía la app está junto a ese surtidor. | ConfirmaciónRecarga, Surtidor | RNF-09, propuesta |
| RN-05.14 | Un conductor no puede repetir la confirmación del mismo surtidor y tipo en poco tiempo. La repetida no se registra, y la app no vuelve a preguntar por ese surtidor en ese lapso. | ConfirmaciónRecarga | CU-05 sección 7, HU-12 c.5, [P-14](../08_decisiones_parametros.md) |
| RN-05.15 | La confirmación se ve en los demás conductores solo si es más reciente que el último dato de WhatsApp para ese surtidor y tipo. | EstadoCombustible | HU-12 c.4 |
| RN-05.16 | La pregunta no interrumpe la consulta de surtidores ni la navegación, y se responde en dos toques como máximo. | Controlador de combustible de la app | HU-12 usabilidad |

## Recorrido por Vista, Controlador y Modelo

La consulta:

1. **Vista Surtidores.** El conductor abre la pestaña Surtidores.
2. **Controlador de combustible (app).** Toma la ubicación actual y pide al backend los surtidores, con el JWT.
3. **Controlador REST de surtidores (backend).** Comprueba el JWT (RN-05.1) y el formato. Llama al servicio del modelo que consulta surtidores.
4. **Modelo (backend).** Ordena los `Surtidor` por cercanía (RN-05.2). Para cada uno, el `EstadoCombustible` elige el dato más reciente por tipo y descarta lo vencido (RN-05.3, RN-05.5, RN-05.6).
5. **Controlador REST de surtidores (backend).** Devuelve la lista sin datos de quien aportó (RN-05.7).
6. **Vista Surtidores.** Muestra cada surtidor con su estado por tipo, la fila si se conoce, el origen y la fecha (RN-05.4).

La confirmación de recarga:

1. **Controlador de combustible (app).** Detecta que el conductor estuvo detenido junto a un surtidor y muestra la pregunta (RN-05.8, RN-05.16).
2. **Vista Pregunta de recarga.** El conductor responde. Si dice que no o la cierra, termina aquí (RN-05.10). Si dice que sí, elige el tipo (RN-05.9).
3. **Controlador de combustible (app).** Envía al backend el surtidor, el tipo y la ubicación, con el JWT.
4. **Controlador REST de surtidores (backend).** Comprueba el JWT y el formato. Llama al servicio del modelo que registra confirmaciones.
5. **Modelo (backend).** La `ConfirmaciónRecarga` comprueba cercanía y repetición (RN-05.13, RN-05.14) y actualiza el `EstadoCombustible` (RN-05.11, RN-05.12).
6. **Los demás conductores.** Ven el dato en su siguiente consulta de surtidores, si es el más reciente (RN-05.15).
