# Issue #74 — Java: recepción HTTP de telemetría (dependencia de Flutter #75)

**Rama:** `feature/HU13-CU04-java-telemetria`
**Commit original obtenido desde Git:** `6745c59d1b3fce5aaf767cbea8d3ace4bf7f9514`
**Fecha de pruebas comunicadas:** 16/11/2026.
**Entorno de prueba:** Java 21, Spring Boot 3.5, PostGIS Docker independiente, API en `192.168.137.1:18080` (solo LAN de pruebas), administración en `127.0.0.1:18081`.

## Qué se verificó

- `mvnw.cmd -B clean test`: 189 tests contabilizados por Maven, **184 ejecutados/aprobados, 5 omitidos, 0 fallos y 0 errores**; salida final `BUILD SUCCESS`.
- La suite `TelemetriaIntegracionTest` informó **11 pruebas sin errores**.
- En el backend real, `/actuator/health` respondió `UP`; `/v3/api-docs` publicó `POST /telemetria`; un POST sin JWT fue rechazado con `401`.
- En el registro de la ejecución Android aportado se identificaron **54 aceptaciones HTTP que contenían 67 lecturas**. Las entradas `CP122_TELEMETRIA_HTTP_ACEPTADA lecturas=N` no imprimen ubicación ni identidad del conductor.
- Un tramo del log muestra cuatro POST recibidos en los tiempos `22:12:54`, `22:13:09`, `22:13:24`, `22:13:39` (UTC−04:00), aproximadamente cada 15 s.

## Límites: leer antes de interpretar el 202

El backend se ejecutó con `MOTOR_HABILITADO=false`. El registro Java informa que el motor no estaba disponible y **descartó el análisis de las lecturas después de aceptarlas por HTTP**. Por tanto, este registro demuestra recepción, no detección de congestión ni cálculo de velocidad en C++.

La cantidad de lecturas por grupo varió entre una y tres en el intervalo señalado; no equivale a lecturas reales de GPS cada tres segundos. La vinculación con el momento de minimizar/revocar permiso depende de marcas y vídeos pendientes de anexar.

El único ajuste adicional al código original #74 en esta entrega es un `LOG.info` agregado en `TelemetriaControlador` para observar el conteo del lote, sin almacenar JWT, coordenadas, conductor ni payload.

Los reportes completos de pruebas y logs locales pueden contener datos operativos o secretos generados; **no deben subirse al repositorio sin sanitización y revisión de privacidad**.
