# ChuroViaje - Backend Java

Backend principal para el sistema ChuroViaje.

## Requisitos
- Java 21
- PostgreSQL (Perfil de producción)

## Ejecución y Pruebas
Para correr las pruebas en cualquier entorno sin necesidad de instalar Maven, utiliza el Maven Wrapper:
```bash
./mvnw -B clean test
```

## Perfiles y Configuración
El proyecto cuenta con tres perfiles:
- `dev`: Entorno de desarrollo local, base de datos H2 en memoria. Documentación de API (Swagger) activa.
- `pruebas`: Entorno de testing para QA. Documentación de API deshabilitada.
- `prod`: Entorno de producción (requiere base de datos PostgreSQL). Documentación de API deshabilitada.

*Nota de seguridad: En el servidor es obligatorio definir la variable de entorno `SPRING_PROFILES_ACTIVE=prod` al arrancar la aplicación.*

## Variables de Entorno (Producción)
Para arrancar el backend en el entorno de producción, se deben proveer obligatoriamente las siguientes variables:
- `DB_URL`: URL de conexión a la base de datos PostgreSQL.
- `DB_USER`: Usuario de conexión.
- `DB_PASS`: Contraseña de conexión.

*Nota sobre secretos futuros:* Las variables de entorno `JWT_SECRET` y `SERVICE_CREDENTIAL` serán implementadas y requeridas más adelante cuando se completen los tickets de seguridad HU-10 (#46) y HU-08 (#57).