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
- `dev`: Entorno de desarrollo local, base de datos H2 en memoria. Documentación de API (Swagger) y consola H2 activas.
- `pruebas`: Entorno de testing para QA. La documentación de API también se encuentra habilitada para validaciones.
- `prod`: Entorno de producción (requiere base de datos PostgreSQL). Documentación de API deshabilitada.

*Nota de seguridad: En el servidor es obligatorio definir la variable de entorno `SPRING_PROFILES_ACTIVE=prod` al arrancar la aplicación.*

## Variables de Entorno (Producción)
Para arrancar el backend en el entorno de producción, se deben proveer obligatoriamente las siguientes variables:
- `DB_URL`: URL de conexión a la base de datos PostgreSQL.
- `DB_USER`: Usuario de conexión.
- `DB_PASSWORD`: Contraseña de conexión.

*Nota sobre secretos futuros:* Las variables de entorno `JWT_SECRET` y `SERVICE_CREDENTIAL` serán implementadas y requeridas más adelante cuando se completen los tickets de seguridad HU-10 (#46) y HU-08 (#57).