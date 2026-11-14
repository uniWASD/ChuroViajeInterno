# 04_imp - Implementación (Implementation)

Aquí vive el código fuente del sistema de software bajo diseño (SUD).

### ¿Qué archivos deben subirse aquí?
*   El código fuente de la aplicación móvil multiplataforma desarrollada en Flutter[cite: 1].
*   El código del backend desarrollado en Java que expone los servicios REST[cite: 1].
*   El código definitivo del motor core en C++ que ejecuta los algoritmos de grafos (Dijkstra o A* modificado)[cite: 1, 2].

## Cómo correr y probar cada pieza

| Pieza | Carpeta | Pruebas | Qué necesita |
| --- | --- | --- | --- |
| Backend | `backend_java` | `mvn test` | Java 21 y Docker (las pruebas de base de datos usan Testcontainers con PostGIS). Las pruebas del wrapper `MotorDeRutas` usan un núcleo falso en Java y no necesitan Docker ni C++ |
| Núcleo del motor | `motor_cpp` | `cmake --build build --target pruebas` | CMake y un compilador de C++17. Las dependencias (nlohmann/json y GoogleTest) se descargan solas |
| Servicio de WhatsApp/LLM | `servicio_whatsapp` | `npm test` | Node.js 22.13 o posterior. Las pruebas usan dobles de Baileys: no se conectan a WhatsApp |
| App | `app_flutter` | `flutter test` | Flutter |

## Variables de entorno

Ningún secreto va al repositorio ([D-13](../03_des/02_Arquitectura/08_decisiones_parametros.md)). Cada pieza explica las suyas en su README; esta es la lista completa.

| Variable | Pieza | Qué es |
| --- | --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Backend | Conexión a PostgreSQL ([D-40](../03_des/02_Arquitectura/08_decisiones_parametros.md)). En desarrollo se pueden usar `DB_HOST`, `DB_PORT` y `DB_NAME` en lugar de `DB_URL` |
| `JWT_SECRET` | Backend | Clave que firma los JWT. Obligatoria, de 32 bytes o más |
| `GOOGLE_CLIENT_ID` | Backend | Identificador de cliente de la app en Google. Obligatoria |
| `SERVICE_CREDENTIAL` | Backend | Credencial del servicio de WhatsApp/LLM. Obligatoria, de 32 caracteres o más. Es el mismo valor que `SERVICE_TOKEN` |
| `MOTOR_HABILITADO`, `MOTOR_PATH`, `RED_VIAL_PATH` | Backend | Arranque del núcleo C++ ([D-35](../03_des/02_Arquitectura/08_decisiones_parametros.md)). Sin `MOTOR_HABILITADO=true`, el motor queda apagado |
| `BACKEND_URL`, `SERVICE_TOKEN`, `ALLOWED_GROUP_IDS` | Servicio de WhatsApp/LLM | Dirección del backend, credencial de servicio y grupos que se leen |
| `WHATSAPP_ENABLED`, `WHATSAPP_PHONE_NUMBER`, `WHATSAPP_SESSION_PATH`, `WHATSAPP_AUTHORIZATIONS_FILE`, `WHATSAPP_RECONNECT_MS` | Servicio de WhatsApp/LLM | Lectura de los grupos ([D-36](../03_des/02_Arquitectura/08_decisiones_parametros.md)) |
| `LLM_API_KEY`, `LLM_MODEL`, `ANALYSIS_INTERVAL_MS`, `MAINTENANCE_INTERVAL_MS` | Servicio de WhatsApp/LLM | Clave y modelo de Gemini, y ciclos de análisis (5 min) y de vencidos (1 min). Sin `LLM_API_KEY` no se llama al LLM ([D-17](../03_des/02_Arquitectura/08_decisiones_parametros.md)) |
