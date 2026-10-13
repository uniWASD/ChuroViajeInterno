# Plan de Construcción y Backlog del Backend Java

**Relacionado con:** HU-01, HU-03, HU-05 a HU-10 / CU-01 a CU-08
**Objetivo:** Definir la lista de issues, arquitectura base y dependencias lógicas para la construcción del backend de gestión de ChuroViaje.

---

## 1. Decisiones Técnicas Base

Para soportar la concurrencia, el manejo de datos geográficos y la comunicación en tiempo real, la arquitectura del backend se basará en las siguientes tecnologías:

*   **Framework Backend:** **Spring Boot (Java)** con Maven/Gradle. Permite una rápida configuración de endpoints REST, inyección de dependencias y perfiles nativos (`dev`, `prod`).
*   **Base de Datos:** **PostgreSQL + extensión PostGIS**. Es la solución estándar y más eficiente en la industria para consultas geográficas complejas, permitiendo buscar eventos o surtidores dentro de un área visible del mapa (bounding box) de forma nativa.
*   **Mecanismo de Tiempo Real:** **WebSockets (con STOMP/SockJS)**. Para la propagación de eventos en un rango de 5-10 segundos, el *polling* tradicional saturaría el servidor con peticiones HTTP redundantes. WebSockets mantiene una conexión bidireccional abierta y eficiente, ideal para mapas en vivo.

---

## 2. Backlog de Issues (Tareas de Construcción)

### Fase 1: Estructura y Seguridad Core
*   **[ISSUE-01] [Técnica] Estructura base del proyecto**
    *   *Descripción:* Inicialización del proyecto Spring Boot, configuración de Maven/Gradle, perfiles (dev/prod) y variables de entorno.
    *   *Dependencias:* Ninguna.
*   **[ISSUE-02] [Técnica] Modelo de datos y migraciones (Flyway/Liquibase)**
    *   *Descripción:* Creación de tablas base (usuarios, eventos viales, surtidores, telemetría) y habilitación de PostGIS.
    *   *Dependencias:* ISSUE-01.
*   **[ISSUE-03] [HU-10 / CU-08] Endpoint de Login**
    *   *Descripción:* Recibir y validar token de Google, registrar usuario si es nuevo, y emitir un JWT propio del backend con tiempos de expiración.
    *   *Dependencias:* ISSUE-01, ISSUE-02.
*   **[ISSUE-04] [HU-10 / CU-08] Filtro de seguridad JWT**
    *   *Descripción:* Implementar filtro (Spring Security) para interceptar peticiones, validar el JWT y proteger los endpoints autenticados.
    *   *Dependencias:* ISSUE-03.

### Fase 2: Gestión de Eventos Viales (Reportes de Tráfico)
*   **[ISSUE-05] [HU-01 / CU-01] Endpoint crear evento vial**
    *   *Descripción:* API REST para recibir un reporte de usuario (tipo, lat/lon, timestamp) y guardarlo en la base de datos.
    *   *Dependencias:* ISSUE-02, ISSUE-04.
*   **[ISSUE-06] [HU-01 / CU-01] Rate limiting y control de duplicados**
    *   *Descripción:* Lógica para evitar spam de reportes, restringiendo la cantidad de eventos idénticos reportados por el mismo usuario en un radio y tiempo corto.
    *   *Dependencias:* ISSUE-05.
*   **[ISSUE-07] [HU-03 / CU-02] Endpoint listar eventos vigentes (Bounding Box)**
    *   *Descripción:* Consulta PostGIS para devolver solo los eventos dentro del área del mapa que el usuario está viendo, omitiendo datos personales del creador.
    *   *Dependencias:* ISSUE-02, ISSUE-04.
*   **[ISSUE-08] [HU-03 / CU-02] Expiración de eventos**
    *   *Descripción:* Tarea programada (Cron Job/Scheduler) que marca como inactivos los eventos viales superado su tiempo de vigencia.
    *   *Dependencias:* ISSUE-05.
*   **[ISSUE-09] [HU-01 / CU-01] Propagación en tiempo real (WebSocket)**
    *   *Descripción:* Configurar el broker de WebSockets para emitir un mensaje o broadcast a los clientes conectados cada vez que se valide un nuevo evento vial.
    *   *Dependencias:* ISSUE-01, ISSUE-05.

### Fase 3: Integración Externa y Telemetría
*   **[ISSUE-10] [HU-05 / CU-03] Endpoint de cálculo de ruta**
    *   *Descripción:* Endpoint intermediario que recibe origen/destino del móvil, se comunica por red con el motor core en C++ y devuelve la ruta óptima.
    *   *Dependencias:* ISSUE-04 (y disponibilidad del motor C++).
*   **[ISSUE-11] [HU-06 / CU-04] Endpoint telemetría pasiva**
    *   *Descripción:* API de alta frecuencia para recibir datos GPS crudos (posición, tiempo) y reenviarlos al motor C++ para el cálculo de retraso vehicular.
    *   *Dependencias:* ISSUE-04, ISSUE-09.
*   **[ISSUE-12] [HU-07 / CU-05] Endpoint estado de surtidores**
    *   *Descripción:* Retornar surtidores cercanos, indicando si hay combustible y el origen del dato (API oficial o WhatsApp).
    *   *Dependencias:* ISSUE-02, ISSUE-07.
*   **[ISSUE-13] [HU-08 / CU-06] Servicio de ingestión (WhatsApp)**
    *   *Descripción:* Webhook o servicio listener que recibe los datos parseados del bot de WhatsApp para actualizar el stock de los surtidores en la base de datos.
    *   *Dependencias:* ISSUE-12.

### Fase 4: Despliegue, Testing y Entornos
*   **[ISSUE-14] [HU-09 / CU-07] Soporte para el simulador**
    *   *Descripción:* Crear perfil/ambiente aislado para pruebas de estrés y sistema de logs para auditar las pruebas de carga del simulador de tráfico.
    *   *Dependencias:* ISSUE-01, ISSUE-10, ISSUE-11.
*   **[ISSUE-15] [Técnica] Pruebas unitarias e integración**
    *   *Descripción:* Implementar tests con JUnit/Mockito para validar los flujos de seguridad, inserción espacial e integración con el motor de rutas.
    *   *Dependencias:* Transversal a todos los issues de desarrollo.
*   **[ISSUE-16] [Técnica] Despliegue seguro en VPS**
    *   *Descripción:* Configuración final de servidor de producción: Certificados HTTPS/TLS, configuración de firewall (UFW) y acceso exclusivo mediante llave SSH.
    *   *Dependencias:* Finalización de las Fases 1, 2 y 3.
