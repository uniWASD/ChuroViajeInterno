# Issue #75 — HU-13 / CU-04 — Evidencias de telemetría Flutter

**Rama:** `feature/HU13-CU04-flutter-telemetria`
**Referencia de origen informada:** `b78ee5b7413f062f2d24006a26b2bc7eda09b0e3`
**Ensayos realizados por:** operador del entorno de pruebas, 16/11/2026.
**Dispositivo:** Android 14, API 34 (arquitectura ARM64).
**Backend utilizado:** Java de #74, `192.168.137.1:18080`, PostGIS aislado `127.0.0.1:55434`.

## Resultados comunicados

| Elemento | Evidencia aportada | Resultado e interpretación |
|---|---|---|
| `flutter analyze` | Registro PowerShell `No issues found` | Aprobado en el equipo de pruebas |
| `flutter test test/telemetria_test.dart` | Registro `All tests passed` | 7 casos automatizados aprobados, con dobles de prueba |
| `flutter test` | Registro `+131: All tests passed` | 131 pruebas aprobadas, no equivalen a GPS real |
| `flutter build apk --debug` | Gradle `Built ... app-debug.apk` | Compilación correcta; la APK no está adjunta a esta carpeta |
| Sesión y red | ADB reconoció dispositivo; navegador Android pudo consultar `/v3/api-docs` | Conectividad con el backend real comprobada |
| CP-122, recepción | Log Java registrado con `CP122_TELEMETRIA_HTTP_ACEPTADA` | 54 peticiones aceptadas y 67 lecturas en el registro remitido; incluye un tramo de cuatro POST cada ~15 s |
| CP-122, segundo plano | El operador afirma que comprobó la ausencia de nuevos POST tras minimizar | Favorable, pero falta vídeo/periodo etiquetado para validación documental independiente |
| CP-123, permiso revocado | Captura del mapa visible con aviso de ubicación denegada; operador afirma cese de POST | Mapa visible y sin posición; faltan vídeo de acciones y ventana temporal verificable |
| RNF-18, batería | ChuroViaje 66→59 %; Google Maps 66→58 %, 30 min por aplicación, sin cargar | 7 pp frente a 8 pp: criterio comparativo **cumplido en el ensayo declarado** |
| RNF-18, fluidez | Operador declara mapa fluido y sin bloqueos | Cumple en la observación declarada; falta evidencia de la ejecución íntegra |

## Serie temporal extractada del log Java aportado (UTC−04:00)

| Fecha/hora Java | Lecturas del lote | Separación respecto al anterior |
|---|---:|---:|
| 2026-11-16 22:12:54.499 | 2 | — |
| 2026-11-16 22:13:09.496 | 3 | 14.997 s |
| 2026-11-16 22:13:24.500 | 2 | 15.004 s |
| 2026-11-16 22:13:39.505 | 1 | 15.005 s |

Estos cuatro registros muestran la recepción de cuatro lotes consecutivos en un intervalo de 45 s entre el primero y el último. **No permiten, por sí solos, reconstruir qué ocurrió exactamente durante el minuto posterior a la minimización** ni cuántas lecturas nuevas produjo el GPS cada 3 s.

## Observaciones a conservar al cerrar

- CP-122: añadir vídeo de 60 s con la aplicación visible y 60 s minimizada, junto con las marcas de hora y las cuatro respuestas recibidas por Java; descartar los POST iniciados antes de minimizar como caso separado.
- CP-123: añadir vídeo de la revocación real en Ajustes y de la interacción con el mapa; adjuntar verificación del cese de envíos.
- RNF-18: adjuntar capturas inicial/final de batería y anotación de brillo, datos móviles/Wi-Fi, GPS, duración y fluidez. Repetir si se requiere mayor precisión porque la diferencia es pequeña.
- La telemetría llegó a Java, **pero `MOTOR_HABILITADO=false`** impide afirmar que el C++ haya producido congestión.
- La captura de porcentajes y las observaciones proceden del operador. No se afirma auditoría independiente de dispositivo ni pruebas del motor C++.

No incluir credenciales, payloads GPS, correos, teléfonos, tokens, vídeos privados ni logs completos de Java en el repositorio. Los originales quedan bajo control del equipo.
