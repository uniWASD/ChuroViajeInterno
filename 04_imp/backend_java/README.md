# ChuroViaje - Backend Java

Backend de ChuroViaje con Java 21, Spring Boot y Maven Wrapper.

## Organización de paquetes

El proyecto usa un solo MVC (D-01):

- `config`: configuración de la aplicación y seguridad.
- `controladores`: endpoints REST, DTO y validación de entrada.
- `servicios`: coordinación del dominio y los repositorios para completar cada caso de uso.
- `dominio`: objetos y valores que contienen las reglas de negocio; las entidades persistentes se incorporan con sus historias.
- `repositorios`: acceso a datos mediante Spring Data JPA, cuando se incorporen las entidades.
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

Puedes cambiar `DB_HOST`, `DB_PORT`, `DB_NAME` y `DB_USER`; `DB_PASSWORD` es obligatoria. La cobertura usa `../datos/mancha_urbana_tarija.geojson`, salvo que definas `CHUROVIAJE_GEOJSON_PATH`.

### Pruebas

JUnit usa el perfil `pruebas` y Testcontainers inicia una base PostGIS desechable; no hace falta levantar Compose. Ejecuta el mismo comando que el CI:

```bash
./mvnw -B clean test
```

En Windows: `.\mvnw.cmd -B clean test`.

Un arranque manual con este perfil requiere `TEST_DATABASE_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD` para una base PostGIS de pruebas.

### Producción

Define `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USER`, `DB_PASSWORD` y `CHUROVIAJE_GEOJSON_PATH`. Este perfil deshabilita Swagger y rechaza coberturas provisionales. `JWT_SECRET` y `SERVICE_CREDENTIAL` se incorporarán con las historias de seguridad correspondientes.

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
