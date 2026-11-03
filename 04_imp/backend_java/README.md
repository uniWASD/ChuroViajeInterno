# ChuroViaje - Backend Java

Backend de ChuroViaje con Java 21, Spring Boot y Maven Wrapper.

## Organización de paquetes

El proyecto usa un solo MVC (D-01):

- `config`: configuración de la aplicación y seguridad.
- `controladores`: endpoints REST, DTO y validación de entrada. Para una consulta responden el DTO directamente; al crear algo o al rechazar, un `ResponseEntity` con el código HTTP del contrato. Nunca devuelven un objeto de dominio ([D-43](../../03_des/02_Arquitectura/08_decisiones_parametros.md)).
- `servicios`: un servicio por caso de uso, que recibe sus repositorios por el constructor y coordina el dominio y los repositorios.
- `dominio`: objetos y valores que contienen las reglas de negocio. Con cada historia se incorporan sus objetos, los DTO de consulta (`record` de solo lectura) y la interfaz de su repositorio ([D-41](../../03_des/02_Arquitectura/08_decisiones_parametros.md), [D-42](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). Nada aquí accede a la base de datos.
- `infraestructura/pg`: implementaciones de los repositorios para PostgreSQL, con JDBC y sentencias preparadas ([D-37](../../03_des/02_Arquitectura/08_decisiones_parametros.md), [D-41](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). Se crea junto con el primer repositorio.
- `motor`: comunicación con el núcleo C++ mediante `MotorDeRutas` y pipe (D-15).
- `errores`: traducción de errores al formato de rechazo del contrato.

## Requisitos

- JDK 21. Maven se descarga mediante el wrapper incluido.
- Docker con contenedores Linux para desarrollo y pruebas.
- PostgreSQL 16 con PostGIS 3.6 en producción. Compose y Testcontainers usan `postgis/postgis:16-3.6-alpine`.

## Perfiles y configuración

Ejecuta los comandos desde `04_imp/backend_java`. No guardes credenciales en archivos versionados.

### Desarrollo

El perfil predeterminado es `dev`. Compose publica la base `churoviaje_dev` en `127.0.0.1:5433`, con usuario `churoviaje`.

```bash
read -rsp 'Contraseña local de PostgreSQL: ' DB_PASSWORD
export DB_PASSWORD
docker compose up -d
./mvnw spring-boot:run
```

Puedes cambiar `DB_HOST`, `DB_PORT`, `DB_NAME` y `DB_USER`; `DB_PASSWORD` es obligatoria.

La seguridad (CU-08, D-13) pide tres variables más, sin valor por defecto: si falta alguna, el backend no arranca y dice cuál.

| Variable | Qué es |
| --- | --- |
| `JWT_SECRET` | Clave que firma el JWT: al menos 32 caracteres, larga y aleatoria. Por ejemplo, `openssl rand -base64 48` |
| `GOOGLE_CLIENT_ID` | Client ID de la app en Google, contra el que se valida el token de identidad |
| `SERVICE_CREDENTIAL` | Credencial del servicio de WhatsApp/LLM: al menos 32 caracteres. Es el mismo valor que usa el servicio (`SERVICE_TOKEN`) | La cobertura usa `../datos/mancha_urbana_tarija.geojson`, salvo que definas `CHUROVIAJE_GEOJSON_PATH`.

### Pruebas

JUnit usa el perfil `pruebas` y Testcontainers inicia una base PostGIS desechable; no hace falta levantar Compose. Ejecuta el mismo comando que el CI:

```bash
./mvnw -B clean test
```

En Windows: `.\mvnw.cmd -B clean test`.

Un arranque manual con este perfil requiere `TEST_DATABASE_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD` para una base PostGIS de pruebas.

### Producción

Define `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USER`, `DB_PASSWORD` y `CHUROVIAJE_GEOJSON_PATH`. Este perfil deshabilita Swagger y rechaza coberturas provisionales. Define también `JWT_SECRET`, `GOOGLE_CLIENT_ID` y `SERVICE_CREDENTIAL`, que viven solo en el servidor con acceso restringido.

## Acceso a datos

El backend accede a PostgreSQL con JDBC y `PreparedStatement`, sin JPA ni `JdbcTemplate` ([D-37](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). Cada operación pide la conexión, la usa y la cierra con `try-with-resources`; la conexión sale de un pool HikariCP, que viene con Spring Boot ([D-39](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). El driver es la dependencia `org.postgresql:postgresql` de Maven, sin `.jar` en una carpeta `lib` ([D-38](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). La dirección, el usuario y la contraseña salen de `DB_URL`, `DB_USER` y `DB_PASSWORD` ([D-40](../../03_des/02_Arquitectura/08_decisiones_parametros.md)).

Qué falta para llegar ahí:

- El `pom.xml` todavía declara `spring-boot-starter-data-jpa` y `application.yml` y `application-prod.yml` tienen la sección `spring.jpa`; ninguna clase de producción los usa. Para tener HikariCP sin JPA se usa `spring-boot-starter-jdbc`.
- Los archivos `application*.yml` versionados solo traen marcadores `${...}` y valores de ejemplo, sin credenciales. [D-40](../../03_des/02_Arquitectura/08_decisiones_parametros.md) pide dejar en el repositorio únicamente `application-example.yml` y poner el archivo real en `.gitignore`; las pruebas y el CI leen hoy los perfiles versionados, así que ese cambio queda pendiente.

## Endpoints de utilidad

- Swagger, habilitado en `dev` y `pruebas`: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).
- Salud: `curl http://127.0.0.1:8081/actuator/health`.

## Base de datos

Todos los perfiles usan PostgreSQL/PostGIS, Flyway V1/V2 y `ddl-auto: validate`. El ejemplo de `POST /eventos` devuelve el evento sin persistirlo hasta HU-01 (#53). Una base que ya ejecutó una versión anterior de V2 puede tener un checksum distinto; no borres sus datos ni ejecutes `repair` automáticamente.

### Catálogos

V1 crea PostGIS y los catálogos de eventos y combustibles. V2 carga 14 establecimientos, 36 alias únicos y 20 denominaciones aceptadas del prototipo. `ypfb` aislado se excluye por ambiguo. Los nombres oficiales también se guardan como alias normalizados.

Las coordenadas usan `GEOGRAPHY(Point,4326)`. SQL y GeoJSON reciben longitud-latitud; el dominio recibe latitud-longitud. Los comentarios de V2 identifican las fuentes. Los puntos de OpenStreetMap conservan la atribución: © OpenStreetMap contributors, ODbL 1.0.

### Cobertura

`../datos/mancha_urbana_tarija.geojson` delimita la cobertura operativa de Tarija, Tomatitas y el corredor de El Rancho; no es un límite administrativo ni catastral. El backend carga el archivo al iniciar: reinícialo si sustituyes la geometría.

La raíz y cada entidad deben tener metadata de aprobación. Producción rechaza archivos sin aprobar y cualquier intento de habilitar borradores. Las fuentes y la revisión están registradas en el propio GeoJSON.

## Motor de rutas (núcleo C++)

`MotorDeRutas` es la única clase que habla con el núcleo (pipe, una línea de JSON por mensaje). El backend solo arranca el núcleo si se configura:

| Variable | Qué es |
|---|---|
| `MOTOR_HABILITADO` | `true` para que el backend arranque el núcleo. Por defecto `false`: las operaciones del motor responden 503. |
| `MOTOR_PATH` | Ruta del ejecutable del núcleo. |
| `RED_VIAL_PATH` | Ruta del archivo de la red vial; se le pasa al núcleo con `--red <ruta>`. |

Los tiempos (`motor.tiempo-maximo` 3 s, `motor.espera-arranque`, `motor.espera-reinicio`) están en `application.yml`. Las pruebas usan un núcleo falso en `src/test/resources/nucleo_falso/NucleoFalso.java` y no necesitan Docker ni el ejecutable de C++.

`POST /rutas` (HU-05) es la primera operación que usa el motor: comprueba que origen, destino y paradas estén en la mancha urbana sin llamar al núcleo y, si lo están, le pide la ruta. El formato está en [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md#rutas-post-rutas). Sus pruebas reemplazan `MotorDeRutas` por un doble; con `MOTOR_PATH` y `RED_VIAL_PATH` definidos, `MotorDeRutasNucleoRealTest` y `RutaConNucleoRealTest` usan el núcleo real (por ejemplo, con `04_imp/motor_cpp/tests/datos/red_irregular.json`). Con `MOTOR_PATH` y `RED_VIAL_REAL` (la red real de Tarija), `RutasReferenciaNucleoRealTest` calcula los viajes de referencia de [04_imp/datos](../datos/README.md#viajes-de-referencia) a través de `MotorDeRutas` (T-120).
