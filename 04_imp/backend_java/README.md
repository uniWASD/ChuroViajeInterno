# ChuroViaje - Backend Java

Backend principal para el sistema ChuroViaje, construido con **Java 21**, **Spring Boot 3** y gestionado con **Maven**.

## Organización de Paquetes
El proyecto usa un solo MVC (D-01):
- `config`: Configuraciones de la aplicación y seguridad (Spring Security).
- `controladores`: Endpoints REST, DTOs y validación de entrada.
- `servicios`: Coordinan objetos de dominio y repositorios para completar un caso de uso, pero no deciden reglas.
- `dominio`: Objetos de dominio y valores (ej. `Ubicacion`); las entidades persistentes se incorporan con sus historias. Los objetos de dominio guardan sus datos y hacen cumplir sus propias reglas. Es el único lugar con reglas de negocio.
- `repositorios`: Interfaces de acceso a base de datos con Spring Data JPA cuando se incorporen entidades persistentes.
- `motor`: Wrapper `MotorDeRutas`, la cara en Java del motor core, que habla con el núcleo C++ por pipe (D-15).
- `errores`: Manejador global (`@RestControllerAdvice`) para centralizar el formato de rechazos.

## Requisitos
- Java 21
- Docker con contenedores Linux para las pruebas y para la base local de desarrollo.
- PostgreSQL 16 con PostGIS 3.6 en producción; desarrollo y pruebas usan `postgis/postgis:16-3.6-alpine`.

## Compilación, Ejecución y Pruebas
El proyecto incluye el Maven Wrapper (`mvnw`), por lo que no es necesario instalar Maven localmente.

**Para ejecutar las pruebas:**
```bash
./mvnw -B clean test
```

**Para ejecutar en desarrollo (perfil `dev` por defecto):**
Configura `DB_PASSWORD` y levanta la base con `docker compose up -d` antes de iniciar el backend; el procedimiento completo está en «Desarrollo y configuración».
```bash
./mvnw spring-boot:run
```
*(Si necesitas forzar explícitamente un perfil, usa: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`)*

## Endpoints de Utilidad (Perfil dev)
- **Documentación de API (Swagger):** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Estado de Salud:** `curl http://127.0.0.1:8081/actuator/health`

## Perfiles y Configuración
El proyecto cuenta con tres perfiles (`dev` es el activo por defecto):
- `dev`: PostgreSQL/PostGIS local mediante Compose; sin H2. Documentación de API habilitada.
- `pruebas`: Entorno de testing para QA. La documentación de API también se encuentra habilitada para validaciones.
- `prod`: Entorno de producción (requiere PostgreSQL con PostGIS). Documentación de API deshabilitada.

*Nota de seguridad: En el servidor es obligatorio definir la variable de entorno `SPRING_PROFILES_ACTIVE=prod` al arrancar la aplicación.*

## Variables de Entorno (Producción)
Para arrancar el backend en el entorno de producción, se deben proveer obligatoriamente las siguientes variables:
- `DB_URL`: URL de conexión a la base de datos PostgreSQL.
- `DB_USER`: Usuario de conexión.
- `DB_PASSWORD`: Contraseña de conexión.
- `CHUROVIAJE_GEOJSON_PATH`: Ruta al GeoJSON de cobertura aprobado.

*Nota sobre secretos futuros:* Las variables de entorno `JWT_SECRET` y `SERVICE_CREDENTIAL` serán implementadas y requeridas más adelante cuando se completen los tickets de seguridad HU-10 (#46) y HU-08 (#57).

## Base de datos y cobertura

El ejemplo de `POST /eventos` devuelve el evento sin persistirlo hasta HU-01 (#53). Todos los perfiles usan PostgreSQL/PostGIS y mantienen `ddl-auto: validate`.

### Base y pruebas

Desde `04_imp/backend_java`, con JDK 21 y Docker Desktop en modo Linux:

```powershell
.\mvnw.cmd -v
.\mvnw.cmd -B clean verify
```

Testcontainers inicia `postgis/postgis:16-3.6-alpine`; no hace falta levantar Compose para las pruebas. CP-21 y las migraciones comparten `PruebaIntegracionPostgis` y `@ServiceConnection`. Una sola prueba de migraciones crea dos bases vacías con `template0`, aplica V1/V2, valida, verifica idempotencia y compara los catálogos completos. También comprueba las restricciones de color, alias normalizados/únicos, FK/cascada e índice GIST. No hay pruebas excluidas ni H2.

### Catálogos y validación

V1 crea PostGIS y los catálogos; conserva los colores acordados, NOT NULL, vigencias de 60 minutos para eventos reportables y 30 para congestión, que no es reportable. V2 contiene **14 establecimientos físicos, 37 alias únicos y las 21 denominaciones del prototipo**. CP-23 comprueba individualmente a qué establecimiento apunta cada denominación; el recuento de establecimientos se deriva de los destinos distintos. No se crean filas duplicadas para nombres coloquiales de la misma estación. Las Vegas está incluido y `ypfb` aislado está excluido por ambiguo.

Los comentarios de V2 identifican las fuentes. Las coordenadas se almacenan como `GEOGRAPHY(Point,4326)`; SQL y GeoJSON usan longitud-latitud, mientras que el dominio recibe latitud-longitud. La consulta documental de ANH no incorpora un cliente de integración ni `anh_id` al modelo de T-089.

### Cobertura aprobada

`../datos/mancha_urbana_tarija.geojson` tiene 173 vértices más el cierre. El alcance operativo comprende Tarija, Tomatitas y el corredor urbanizado de El Rancho. Incluye los 14 surtidores y el centro del ejemplo del contrato, y excluye el punto de San Lorenzo. CP-22 comprueba el archivo real y las filas realmente migradas en ambas bases.

Los controles territoriales comprueban además el área residencial y el Bosquecillo de Tomatitas, la calzada de sus puentes y la continuidad de las vías F1 del corredor hasta El Rancho. También verifican Compuerta y el área residencial del borde sureste; los centros de Cadillar y Obrajes permanecen fuera del alcance. El borde no se define por un número mínimo de vértices ni por la ubicación de los surtidores.

`CoberturaTerritorialTest` usa 37 geometrías de control independientes guardadas en `src/test/resources/cobertura/controles_tarija.geojson`. Las fuentes OSM y la atribución ODbL están en ese archivo; las pruebas no consultan servicios externos. Se muestrean los recorridos y perímetros aproximadamente cada cinco metros, junto con puntos interiores y controles de exclusión. La digitalización usa Esri World Imagery (zoom 16/17 en los sectores corregidos); su fecha de captura no está acreditada y no representa un límite administrativo o catastral.

El archivo registra `estado_validacion=APROBADO` y `revision_equipo=APROBADO`. Es cobertura operativa digitizada sobre imagen aérea, no un límite catastral. Se conserva el control de producción que rechaza cualquier raíz o feature sin aprobar y cualquier intento de habilitar borradores en `prod`. Los perfiles entregados requieren la cobertura aprobada; las pruebas de rechazo usan copias temporales con metadatos deliberadamente inválidos.

### Desarrollo y configuración

```powershell
$env:DB_PASSWORD = Read-Host 'Contrasena local de PostgreSQL'
$env:SPRING_PROFILES_ACTIVE = 'dev'
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Compose publica `churoviaje_dev`, usuario `churoviaje`, en `127.0.0.1:5433`. Variables de desarrollo: `DB_HOST=127.0.0.1`, `DB_PORT=5433`, `DB_NAME=churoviaje_dev`, `DB_USER=churoviaje`, `DB_PASSWORD`. Producción requiere `DB_URL`, `DB_USER`, `DB_PASSWORD` y `CHUROVIAJE_GEOJSON_PATH`. Fuera de `prod`, la ruta por defecto es `../datos/mancha_urbana_tarija.geojson`.

JUnit usa Testcontainers con el perfil `pruebas`. Un arranque manual de ese perfil necesita `TEST_DATABASE_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD` apuntando a una base PostGIS de pruebas. No se deben introducir credenciales en archivos versionados.
