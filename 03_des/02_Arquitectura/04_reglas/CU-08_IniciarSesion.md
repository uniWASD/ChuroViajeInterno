# CU-08 — Iniciar sesión

[Índice de reglas](README.md) · [Índice de la arquitectura](../README.md)

Cubre HU-10 y los requisitos RF-11, RNF-05 y RNF-08.

## Reglas

| Código | Regla | Objeto responsable | Fuente |
| --- | --- | --- | --- |
| RN-08.1 | La única forma de entrar es con una cuenta de Google. No hay registro propio ni contraseñas del sistema. | Conductor | CU-08 descripción, Visión 1.2.2 |
| RN-08.2 | Sin sesión iniciada, la única vista disponible es Inicio de sesión. | Sesión | HU-10 c.5, RNF-08 |
| RN-08.3 | El backend valida el token de identidad de Google antes de emitir nada. Si es inválido o expiró, rechaza la solicitud sin emitir token, y la app deja reintentar. | Sesión | CU-08 paso 3 y ext. 3a, HU-10 c.3 |
| RN-08.4 | En el primer ingreso se crea el `Conductor`; en los siguientes se usa el mismo. Hay un solo conductor por cuenta de Google. | Conductor | CU-08 paso 3 |
| RN-08.5 | Tras validar a Google, el backend emite un JWT propio con expiración. Ese JWT habilita todas las demás funciones. | Sesión | CU-08 paso 4 y postcondición, HU-10 c.1 |
| RN-08.6 | El sistema nunca guarda las credenciales de Google. | Sesión | CU-08 sección 7, Visión 6.3 |
| RN-08.7 | La app guarda el JWT en el almacenamiento seguro del teléfono. | Controlador de sesión de la app | CU-08 paso 5 |
| RN-08.8 | Si el conductor cancela la selección de cuenta o rechaza los permisos, vuelve a la pantalla de inicio sin sesión. | Sesión | CU-08 ext. 2a, HU-10 c.2 |
| RN-08.9 | Si se pierde la conexión durante el inicio, la app avisa del error de red y deja reintentar. No se emite token. | Sesión | CU-08 ext. 4a y garantía mínima, HU-10 c.4 |
| RN-08.10 | Si el teléfono guarda una sesión vigente, la app entra directo al mapa sin repetir el inicio, incluso sin conexión. | Sesión | CU-08 sección 8, HU-10 c.6 |
| RN-08.11 | Con la sesión vencida, el backend rechaza cualquier petición y la app lleva al inicio de sesión. Lo que el conductor estaba haciendo se reintenta después. | Sesión | CU-01 ext. 4a, HU-01 c.6 |
| RN-08.12 | El JWT dura 1 hora y se renueva sin volver a pasar por Google durante 30 días. Pasado ese plazo, el conductor inicia sesión de nuevo. | Sesión | CU-08 sección 7, [P-05](../08_decisiones_parametros.md) |
| RN-08.13 | Los usuarios fantasma no usan este proceso: entran con credenciales de prueba ([RN-07.4](CU-07_EjecutarSimuladorTrafico.md)). | Sesión | CU-07 sección 8 |

## Recorrido por Vista, Controlador y Modelo

1. **Controlador de sesión (app).** Al abrir la app, busca un JWT guardado. Si hay uno vigente, abre el mapa y termina aquí (RN-08.10). Si no, muestra la vista Inicio de sesión (RN-08.2).
2. **Vista Inicio de sesión.** El conductor toca «Iniciar sesión con Google».
3. **Controlador de sesión (app).** Abre el selector de cuentas de Google. Si el conductor cancela, vuelve a la vista de inicio (RN-08.8).
4. **Google Sign-In.** Entrega a la app el token de identidad de la cuenta elegida.
5. **Controlador de sesión (app).** Envía ese token al backend. Es la única petición que viaja sin JWT.
6. **Controlador REST de sesión (backend).** Comprueba el formato y llama al servicio del modelo que inicia sesiones.
7. **Modelo (backend).** Valida el token con Google (RN-08.3), busca o crea el `Conductor` (RN-08.4) y emite la `Sesión` con su JWT (RN-08.5, RN-08.6).
8. **Controlador REST de sesión (backend).** Devuelve el JWT, o el motivo del rechazo.
9. **Controlador de sesión (app).** Guarda el JWT de forma segura (RN-08.7) y abre la vista Mapa principal. Si hubo error, lo muestra y deja reintentar (RN-08.9).
