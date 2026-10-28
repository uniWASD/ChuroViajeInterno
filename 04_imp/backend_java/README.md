# ChuroViaje - Backend Java

Backend principal para el sistema ChuroViaje, construido con **Java 21**, **Spring Boot 3** y gestionado con **Maven**.

## Organización de Paquetes
El proyecto usa un solo MVC (D-01):
- `config`: Configuraciones de la aplicación y seguridad (Spring Security).
- `controladores`: Endpoints REST, DTOs y validación de entrada.
- `servicios`: Coordinan objetos de dominio y repositorios para completar un caso de uso, pero no deciden reglas.
- `dominio`: Entidades JPA y objetos de valor (ej. `Ubicacion`). Los objetos de dominio guardan sus datos y hacen cumplir sus propias reglas. Es el único lugar con reglas de negocio.
- `repositorios`: Interfaces de acceso a base de datos con Spring Data JPA.
- `motor`: Wrapper `MotorDeRutas`, la cara en Java del motor core, que habla con el núcleo C++ por pipe (D-15).
- `errores`: Manejador global (`@RestControllerAdvice`) para centralizar el formato de rechazos.

## Requisitos
- Java 21
- PostgreSQL (Perfil de producción)

## Compilación, Ejecución y Pruebas
El proyecto incluye el Maven Wrapper (`mvnw`), por lo que no es necesario instalar Maven localmente.

**Para ejecutar las pruebas:**
```bash
./mvnw -B clean test
```

**Para compilar y ejecutar en desarrollo (usa el perfil `dev` por defecto):**
```bash
./mvnw spring-boot:run
```
*(Si necesitas forzar explícitamente un perfil, usa: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`)*

## Endpoints de Utilidad (Perfil dev)
- **Documentación de API (Swagger):** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Estado de Salud:** `curl http://127.0.0.1:8081/actuator/health`

## Perfiles y Configuración
El proyecto cuenta con tres perfiles (`dev` es el activo por defecto):
- `dev`: PostgreSQL/PostGIS local mediante Compose; sin H2. Documentacion de API habilitada.
- `pruebas`: Entorno de testing para QA. La documentación de API también se encuentra habilitada para validaciones.
- `prod`: Entorno de producción (requiere base de datos PostgreSQL). Documentación de API deshabilitada.

*Nota de seguridad: En el servidor es obligatorio definir la variable de entorno `SPRING_PROFILES_ACTIVE=prod` al arrancar la aplicación.*

## Variables de Entorno (Producción)
Para arrancar el backend en el entorno de producción, se deben proveer obligatoriamente las siguientes variables:
- `DB_URL`: URL de conexión a la base de datos PostgreSQL.
- `DB_USER`: Usuario de conexión.
- `DB_PASSWORD`: Contraseña de conexión.

*Nota sobre secretos futuros:* Las variables de entorno `JWT_SECRET` y `SERVICE_CREDENTIAL` serán implementadas y requeridas más adelante cuando se completen los tickets de seguridad HU-10 (#46) y HU-08 (#57).

# Base de datos y cobertura: T-089 (#41)

Aplicar sobre `develop` con T-088 (#118) integrado. La superposicion conserva el arranque, controladores, contratos y Maven Wrapper de T-088. El ejemplo de `POST /eventos` devuelve el evento sin persistirlo hasta HU-01 (#53); se elimina `EventoVialRepositorio`. Todos los perfiles usan PostgreSQL/PostGIS y mantienen `ddl-auto: validate`.

## Base y pruebas

Desde `04_imp/backend_java`, con JDK 21 y Docker Desktop en modo Linux:

```powershell
.\mvnw.cmd -v
.\mvnw.cmd -B clean verify
```

Testcontainers inicia `postgis/postgis:16-3.6-alpine`; no hace falta levantar Compose para las pruebas. CP-21 y las migraciones comparten `PruebaIntegracionPostgis` y `@ServiceConnection`. Una sola prueba de migraciones crea dos bases vacias con `template0`, aplica V1/V2, valida, verifica idempotencia y compara los catalogos completos. Tambien comprueba las restricciones de color, alias normalizados/unicos, FK/cascada e indice GIST. No hay pruebas excluidas ni H2.

## Catalogos y criterio de aceptacion

V1 crea PostGIS y los catalogos; conserva los colores acordados, NOT NULL, vigencias de 60 minutos para eventos reportables y 30 para congestion, que no es reportable. V2 contiene **14 establecimientos fisicos, 37 alias unicos y las 21 denominaciones del prototipo**. CP-23 comprueba individualmente a que establecimiento apunta cada denominacion; el recuento de establecimientos se deriva de los destinos distintos. No se crean filas duplicadas para nombres coloquiales de la misma estacion. Las Vegas esta incluido y `ypfb` aislado esta excluido por ambiguo.

Los comentarios de V2 identifican las fuentes. Las coordenadas se almacenan como `GEOGRAPHY(Point,4326)`; SQL y GeoJSON usan longitud-latitud, mientras que el dominio recibe latitud-longitud. La consulta documental de ANH no incorpora un cliente de integracion ni `anh_id` al modelo de T-089.

## Cobertura aprobada

`../datos/mancha_urbana_tarija.geojson` tiene 132 vertices mas el cierre. Incluye los 14 surtidores y el centro del ejemplo del contrato, y excluye el punto de San Lorenzo. CP-22 comprueba el archivo real y las filas realmente migradas en ambas bases.

Franz confirmo la aprobacion de las revisiones el 2026-10-28. El archivo registra `estado_validacion=APROBADO` y `revision_equipo=APROBADO`. Es cobertura operativa digitizada sobre imagen aerea, no un limite catastral. Se conserva el control de produccion que rechaza cualquier raiz o feature sin aprobar y cualquier intento de habilitar borradores en `prod`. Los perfiles entregados requieren la cobertura aprobada; las pruebas de rechazo usan copias temporales con metadatos deliberadamente invalidos.

## Desarrollo y configuracion

```powershell
$env:DB_PASSWORD = Read-Host 'Contrasena local de PostgreSQL'
$env:SPRING_PROFILES_ACTIVE = 'dev'
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Compose publica `churoviaje_dev`, usuario `churoviaje`, en `127.0.0.1:5433`. Variables de desarrollo: `DB_HOST=127.0.0.1`, `DB_PORT=5433`, `DB_NAME=churoviaje_dev`, `DB_USER=churoviaje`, `DB_PASSWORD`. Produccion requiere `DB_URL`, `DB_USER`, `DB_PASSWORD` y `CHUROVIAJE_GEOJSON_PATH`. Fuera de `prod`, la ruta por defecto es `../datos/mancha_urbana_tarija.geojson`.

JUnit usa Testcontainers con el perfil `pruebas`. Un arranque manual de ese perfil necesita `TEST_DATABASE_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD` apuntando a una base PostGIS de pruebas. No se deben introducir credenciales en archivos versionados.

V1/V2 se ajustan antes de integrar esta rama. Para bases persistentes que ya aplicaron una version anterior, no ejecutar `repair` ni borrar volumenes automaticamente; las pruebas generan sus propias bases desechables.

## Entrega

Usar `Refs #41` en los commits y `Closes #41` en la descripcion del PR, acompañados por el resultado real de `clean verify` con JDK 21. La trazabilidad se actualiza al fusionar. Los scripts auxiliares, informes y evidencias se mantienen fuera del repositorio. Buscadores, endpoints de catalogos e integracion ANH quedan en sus respectivos issues.
