# Backend Java - ChuroViaje

Estructura base del servidor backend construida con **Java 21** y **Spring Boot 3**.

## Gestor de construcción
Se eligió **Maven** (`pom.xml`) para la gestión de dependencias y el ciclo de construcción del proyecto.

## Organización de paquetes (`02_mvc.md`)
- `controladores`: Controladores REST. Validan el formato de la petición y el JWT, llaman al modelo y traducen el resultado a HTTP. No contienen reglas de negocio ni acceden a la base de datos.
- `servicios`: Servicios del modelo. Coordinan a los objetos de dominio y a los repositorios para completar cada caso de uso sin decidir reglas de negocio.
- `dominio`: Objetos de dominio donde residen los datos y todas las reglas de negocio.
- `repositorios`: Interfaces de Spring Data JPA para guardar y recuperar objetos de dominio en PostgreSQL.
- `motor`: Contiene el wrapper `MotorDeRutas`, encargado de comunicarse por entrada/salida estándar (pipe) con el núcleo en C++.
- `errores`: Manejador global de errores (`@RestControllerAdvice`) que responde con `{codigo, mensaje}` en español según la tabla «Respuestas de rechazo» de `05_contratos.md` (códigos 400, 401, 403, 404, 409, 422, 429 y 503).
- `config`: Configuraciones de infraestructura (Spring Security, etc.).

## Perfiles y variables de entorno
El proyecto cuenta con tres perfiles (`dev`, `pruebas` y `prod`). Ningún secreto se guarda en el repositorio; se leen de las siguientes variables de entorno:
- `DB_URL`, `DB_USER`, `DB_PASSWORD`: Conexión a PostgreSQL.
- `JWT_SECRET`: Clave para firmar los tokens JWT.
- `SERVICE_CREDENTIAL`: Credencial del servicio de monitoreo WhatsApp/LLM.
- `LLM_API_KEY`: Clave de API del proveedor LLM.

## Cómo compilar, probar y ejecutar

Ubicarse primero dentro de la carpeta del proyecto:
```bash
cd 04_imp/backend_java
```

### 1. Compilar el proyecto
```bash
mvn clean compile
```

### 2. Ejecutar las pruebas (JUnit 5) con un solo comando
Ejecuta la suite de pruebas (incluyendo `CP-21`) usando el perfil `pruebas`:
```bash
mvn test
```

### 3. Ejecutar la aplicación
Para levantar el servidor en el perfil `dev` (por defecto):
```bash
mvn spring-boot:run
```

Para ejecutar con un perfil específico (por ejemplo, `prod`):
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

## Endpoints de infraestructura
- **Documentación OpenAPI / Swagger UI (solo disponible en perfil `dev`):** `http://localhost:8080/swagger-ui.html` (y JSON en `http://localhost:8080/v3/api-docs`).
- **Endpoint de salud local (RNF-08):** `http://127.0.0.1:8081/actuator/health` (solo responde dentro de la propia máquina, inaccesible desde internet).