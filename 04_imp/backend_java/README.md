# ChuroViaje - Backend Java

Backend de ChuroViaje con Java 21, Spring Boot y Maven Wrapper.

## Organización de paquetes

El proyecto usa un solo MVC (D-01):

- `config`: configuración de la aplicación, de la seguridad y de las conexiones de los repositorios (`ConfiguracionAccesoDatos`).
- `controladores`: endpoints REST y validación de entrada. Para una consulta responden el DTO directamente; al crear algo o al rechazar, un `ResponseEntity` con el código HTTP del contrato. Nunca devuelven un objeto de dominio ([D-43](../../03_des/02_Arquitectura/08_decisiones_parametros.md)).
- `dto`: DTO de las peticiones y respuestas que no salen de una consulta de un repositorio: `POST /rutas` y la respuesta del voto.
- `servicios`: un servicio por caso de uso, que recibe por el constructor las interfaces de sus repositorios y coordina el dominio y los repositorios.
- `dominio`: objetos y valores que contienen las reglas de negocio, las interfaces de los repositorios (`EventoRepository`, `VotoRepository`, `ConductorRepository`, `SesionRepository` y, en `dominio/catalogo`, `CatalogoRepository`) y los DTO de consulta (`record` de solo lectura) junto a su repositorio: `EventoVialRespuesta`, `EventosRespuesta` y `CatalogosRespuesta` ([D-41](../../03_des/02_Arquitectura/08_decisiones_parametros.md), [D-42](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). Nada aquí accede a la base de datos.
- `infraestructura/pg`: implementaciones de los repositorios para PostgreSQL (`PgEventoRepository`, `PgVotoRepository`, `PgConductorRepository`, `PgSesionRepository` y `PgCatalogoRepository`), con JDBC y sentencias preparadas ([D-37](../../03_des/02_Arquitectura/08_decisiones_parametros.md), [D-41](../../03_des/02_Arquitectura/08_decisiones_parametros.md)).
- `motor`: comunicación con el núcleo C++ mediante `MotorDeRutas` y pipe (D-15).
- `seguridad`: el JWT del conductor, los tokens de renovación y el filtro que los comprueba (CU-08).
- `errores`: traducción de errores al formato de rechazo del contrato.

## Requisitos

- JDK 21. Maven se descarga mediante el wrapper incluido.
- Docker con contenedores Linux para desarrollo y pruebas.
- PostgreSQL 16 con PostGIS 3.6 en producción. Compose y Testcontainers usan `postgis/postgis:16-3.6-alpine`.

## Perfiles y configuración

Ejecuta los comandos desde `04_imp/backend_java`. No guardes credenciales en archivos versionados.

Los archivos `application*.yml` versionados solo traen marcadores `${...}` y valores de ejemplo, sin credenciales. [D-40](../../03_des/02_Arquitectura/08_decisiones_parametros.md) pide dejar en el repositorio únicamente `application-example.yml` y poner el archivo real en `.gitignore`; las pruebas y el CI leen hoy los perfiles versionados, así que ese cambio queda pendiente.

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

El backend accede a PostgreSQL con JDBC (`java.sql`) y `PreparedStatement`, sin JPA ni `JdbcTemplate` ([D-37](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). Los valores van siempre como parámetros `?`; el SQL nunca se arma con un valor. Cada operación pide la conexión, la usa y la cierra con `try-with-resources`; la conexión sale de un pool HikariCP, que trae `spring-boot-starter-jdbc` ([D-39](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). El driver es la dependencia `org.postgresql:postgresql` de Maven, sin `.jar` en una carpeta `lib` ([D-38](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). La dirección, el usuario y la contraseña salen de `DB_URL`, `DB_USER` y `DB_PASSWORD` ([D-40](../../03_des/02_Arquitectura/08_decisiones_parametros.md)).

Cómo está armado:

- Un servicio solo conoce la interfaz del repositorio, que está en `dominio`; Spring le pasa por el constructor la implementación de `infraestructura/pg` ([D-41](../../03_des/02_Arquitectura/08_decisiones_parametros.md)).
- Las consultas para mostrar tienen un nombre que dice qué buscan (por ejemplo `vigentesParaMostrar`) y devuelven DTO; las búsquedas para operar con las reglas (por ejemplo `buscarParaVotar`) devuelven el objeto de dominio ([D-42](../../03_des/02_Arquitectura/08_decisiones_parametros.md)).
- Las implementaciones no reciben el pool directamente, sino el `DataSource` `conexionesDeLaTransaccion` de `ConfiguracionAccesoDatos`: un `TransactionAwareDataSourceProxy` sobre el pool. Dentro de un `@Transactional`, cada `getConnection()` entrega la conexión de la transacción, y cerrarla no la suelta antes de tiempo. Así el bloqueo del conductor al reportar y el del evento al votar duran hasta el final de la transacción, y si algo falla no queda nada guardado. Fuera de una transacción, cada operación toma una conexión del pool y la devuelve al cerrarla. Lo prueba `ConexionDeLaTransaccionIntegracionTest` (CP-170).
- Las altas que pueden repetirse se escriben con `INSERT ... ON CONFLICT`: el conductor de una cuenta de Google que entra dos veces a la vez y el voto repetido.
- Un error de la base que no es una regla de negocio sale como `ErrorDeBaseDeDatos`: deshace la transacción y la petición responde 500.

## Endpoints de utilidad

- Swagger, habilitado en `dev` y `pruebas`: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).
- Salud: `curl http://127.0.0.1:8081/actuator/health`.

## Base de datos

Todos los perfiles usan PostgreSQL/PostGIS y las migraciones de Flyway, que crean el esquema y comprueban al arrancar que las migraciones ya aplicadas no cambiaron (`validate-on-migrate`). La aplicación no crea tablas. `POST /eventos` (HU-01) guarda el evento en `evento_vial` (V4), con la ubicación como punto PostGIS. Una base que ya ejecutó una versión anterior de V2 puede tener un checksum distinto; no borres sus datos ni ejecutes `repair` automáticamente.

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

Los tiempos (`motor.tiempo-maximo` 3 s, `motor.espera-arranque`, `motor.espera-reinicio`) están en `application.yml`. Cada vez que un evento nace, se retira o vence, `TraficoDelMotor` le avisa al núcleo con `actualizar_trafico` después de guardar el cambio; si el núcleo no está disponible, el aviso queda pendiente y se reintenta cada 5 segundos. Al arrancar o reiniciar el núcleo, el wrapper le envía todos los eventos vigentes (T-099). Con `MOTOR_PATH` y `RED_VIAL_PATH` apuntando a `04_imp/motor_cpp/tests/datos/red_irregular.json`, `TraficoConNucleoRealTest` prueba CP-99 con el núcleo real. Las pruebas usan un núcleo falso en `src/test/resources/nucleo_falso/NucleoFalso.java` y no necesitan Docker ni el ejecutable de C++.

`POST /rutas` (HU-05) es la primera operación que usa el motor: comprueba que origen, destino y paradas estén en la mancha urbana sin llamar al núcleo y, si lo están, le pide la ruta. El formato está en [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md#rutas-post-rutas). Sus pruebas reemplazan `MotorDeRutas` por un doble; con `MOTOR_PATH` y `RED_VIAL_PATH` definidos, `MotorDeRutasNucleoRealTest` y `RutaConNucleoRealTest` usan el núcleo real (por ejemplo, con `04_imp/motor_cpp/tests/datos/red_irregular.json`). Con `MOTOR_PATH` y `RED_VIAL_REAL` (la red real de Tarija), `RutasReferenciaNucleoRealTest` calcula los viajes de referencia de [04_imp/datos](../datos/README.md#viajes-de-referencia) a través de `MotorDeRutas` (T-120).
