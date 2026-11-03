# T-117 — Auditoría de la verificación real en laptop y Oracle Cloud

**Fecha de observación:** 03/11/2026 UTC (02/11/2026, UTC−04:00 en Bolivia al iniciar la ejecución local registrada).
**Fuente:** archivo original `windows-20261102-212856.rar` aportado por el autor y salida de consola; no se realizó ningún nuevo GET a ANH durante esta auditoría.
**SHA-256 del RAR:** `a48f4aaceb8b1336d6fc4e02ed30b52a147677eb630713d4edf24b2005a4bd4e`.

## Resultado de las verificaciones

Las **pruebas automatizadas** declaradas en la evidencia histórica fueron 35/35 en Windows y 35/35 en Oracle; son ejecuciones del mismo conjunto funcional, no 70 casos distintos. Las **432 unidades de calendario virtual** son sintéticas y se mantienen separadas de las **dos rondas reales puntuales** (una por entorno). No consta error HTTP en las seis solicitudes reales ejecutadas.

| Indicador | Laptop (analizador) | Oracle (CSV validado) |
| --- | ---: | ---: |
| Ciclos reales | 1 | 1 |
| Peticiones GET | 3 | 3 |
| Respuestas HTTP 200 | 3/3 | 3/3 |
| Registros estación–combustible | 57 | 57 |
| `updated_at` sin zona explícita | 57/57 | 57/57 |
| Latencia media | 166 ms | 660 ms |
| Latencia máxima | 361 ms | 1.514 ms |
| Latencia p95 (descriptiva; n=3) | 333,8 ms | 1.393 ms |
| Pausa mínima entre peticiones | 1,103 s | 1,099 s |

**Separación entre entornos:** 662.672 segundos entre el último inicio de GET local y el primer inicio de GET en Oracle (más de diez minutos). El intervalo fue suficiente para evitar solapamiento de rondas. La comparación de latencias se limita a tres peticiones por origen: no admite estimar confiabilidad ni percentiles operativos permanentes.

La validación directa del CSV de Oracle confirma **31 registros de gasolina, 26 de diésel y 57 pares estación–combustible**, correspondientes a **31 identificadores de estación distintos**. Los 57 valores `updated_at` están presentes y sin indicador de zona horaria. Para gasolina, el `updated_at` más reciente queda 21,640014 s antes de la consulta si se interpreta como UTC−04:00 y 14.421,640014 s si se interpreta como UTC; para diésel, 240,059362 s frente a 14.640,059362 s. Esta ronda favorece `America/La_Paz` para RN-09.13, pero **no es una zona explícitamente declarada por el proveedor ni una confirmación longitudinal**.

## Alcance de la evidencia

- **Comprobado:** funcionamiento de Java en los dos sistemas, esquema del CSV de Oracle, respuestas exitosas, datos de estación, tratamiento funcional de errores en servidor HTTP simulado, pausas y ejecución segura de una ronda real por origen.
- **Pendiente de comprobación independiente:** el RAR contiene el diagnóstico local y su resumen, pero **no incluye los CSV reales originales de la laptop**. Las cifras locales son las calculadas por el analizador y no una segunda auditoría de los registros primarios.
- **No observado:** una respuesta real con código 429 o `Retry-After`, fallos intermitentes en ANH, variaciones de `saldo`/`updated_at` entre rondas de una misma estación, continuidad de 72 h. Estos escenarios se verificaron funcionalmente mediante simulaciones, no en producción.

## Decisiones de ingeniería y criterios

| Decisión | Valor operativo propuesto | Fundamento y condición |
| --- | --- | --- |
| P-29 | 10 min entre inicios de ciclo | Intervalo conservador; **no representa la periodicidad medida** de actualización de ANH |
| P-30 | 6 h desde última lectura válida del backend | Mantiene RN-05.5 y RN-09.17; no garantiza que los litros sigan disponibles |
| P-32 | 12 s por petición, 1 reintento elegible, pausa ≥1 s, respeto de `Retry-After` | Los tiempos observados fueron inferiores a 12 s; no acreditan percentiles ni errores bajo carga |

Las pruebas abreviadas de conectividad están **completadas**. La campaña real de 72 horas queda fuera del alcance de esta corrección y, por tanto, **CP-157 no se acredita** con la evidencia disponible; CP-158 tampoco debe marcarse como confirmado empíricamente. P-29, P-30 y P-32 permanecen como **Propuesta** hasta que exista evidencia suficiente y una aprobación identificable del equipo, o hasta que el criterio del issue se modifique formalmente. No se debe presentar la simulación como medición real.

## Documentación y Git

En el PR #123, `08_decisiones_parametros.md`, `03_modelo_dominio.md`, `05_contratos.md` y `CU-09_ConsultarEstadoANH.md` ya contienen cambios de T-117. Esta corrección devuelve P-29, P-30 y P-32 a **Propuesta**, mantiene la regla general de vigencia de las demás fuentes y deja una sola copia de la evidencia puntual de laptop/Oracle.

**La rama `feature/T117-java-medir-api-anh` y el PR #123 ya existen.** No crear una segunda rama equivalente ni declarar cerrado el issue #112 con estas rondas puntuales. No subir datos simulados, informes con rutas locales, IP ni `estado.properties` sin revisar su pertinencia y privacidad.
