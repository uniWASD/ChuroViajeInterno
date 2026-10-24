# Backlog de Construcción — Servidor y despliegue

[Índice del backlog](README.md)

Responsable: Marcelo, que además revisa los Pull Requests. Guías y archivos de configuración en `06_proyect/servidor`.

Todo ChuroViaje corre en la instancia `churoviaje-server` de Oracle Cloud: Ubuntu sobre procesador ARM (Ampere A1), 2 OCPU y 12 GB de memoria, dentro del nivel Always Free ([D-16](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). Allí corren el backend, el núcleo C++, el servicio de WhatsApp/LLM y PostgreSQL. Solo el backend queda expuesto a internet, por HTTPS ([07_seguridad_privacidad.md](../../03_des/02_Arquitectura/07_seguridad_privacidad.md)).

Dos cosas que hay que tener presentes durante toda la Construcción:
- En Oracle Cloud un puerto se abre en dos lugares: en las reglas de red de la VCN (lista de seguridad o NSG) y en el firewall del propio Ubuntu, que en las imágenes de Oracle viene cerrado con iptables. Si falta uno de los dos, el puerto no responde.
- La instancia no debe terminarse ni recrearse hasta el 30/11: cambiaría su IP pública si no está reservada, y el servicio de WhatsApp perdería su sesión.

| Sprint | Issues |
| --- | --- |
| S1 (15/10–21/10) | Preparar la instancia de Oracle Cloud |
| S2 (22/10–28/10) | Dominio, Nginx y HTTPS · Comprobar la salida del servidor hacia la API de ANH |
| S3 (29/10–04/11) | Desplegar el backend con el núcleo C++ |
| S4 (05/11–11/11) | Desplegar el servicio de WhatsApp/LLM |
| S6 (19/11–25/11) | Ambiente de pruebas para el simulador · Respaldos de la base de datos y registros |
| S7 (26/11–30/11) | Despliegue final y revisión de seguridad |

---

## Sprint 1

### [T-092][Servidor] Preparar la instancia de Oracle Cloud

**Tipo:** Tarea de infraestructura (cubre todos los casos de uso)
**Tecnología:** Servidor · **Sprint:** Sprint 1 (15/10–21/10) · **Apoyo:** el dueño de la cuenta de Oracle Cloud
**Depende de:** nada
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:servidor`

**Descripción:** Dejar la instancia segura y con todo lo que necesitan los componentes instalado, para poder desplegar desde el sprint 3.

**Criterios de aceptación**
- [ ] La instancia tiene una IP pública reservada, para que no cambie.
- [ ] Se entra por SSH solo con llave, sin contraseña y sin entrar como root; cada integrante que despliega tiene su propia llave.
- [ ] Solo el puerto SSH está abierto, tanto en las reglas de la VCN como en el firewall de Ubuntu. HTTP y HTTPS se abren en el sprint 2.
- [ ] Las actualizaciones de seguridad del sistema se instalan solas.
- [ ] Están instalados, en su versión para ARM: Java 21, las herramientas de compilación de C++ y CMake, la versión LTS de Node.js, y PostgreSQL con PostGIS.
- [ ] PostgreSQL solo acepta conexiones desde la propia máquina y la aplicación usa un usuario de base de datos sin permisos de administrador.
- [ ] Todo lo hecho queda en una guía en `06_proyect/servidor`, sin contraseñas ni llaves.

**Tareas**
- [ ] Reservar la IP pública en Oracle Cloud y asignarla a la instancia.
- [ ] Crear el usuario de despliegue, cargar las llaves SSH y desactivar el acceso con contraseña y el de root.
- [ ] Revisar las reglas de la VCN y del firewall de Ubuntu.
- [ ] Activar las actualizaciones automáticas de seguridad.
- [ ] Instalar Java, las herramientas de C++, Node.js, PostgreSQL y PostGIS.
- [ ] Crear la base de datos `churoviaje` y su usuario.
- [ ] Escribir la guía.

**Casos de prueba**
- [ ] CP-27 Caso de prueba: desde fuera, solo el puerto SSH responde; un intento de SSH con contraseña se rechaza.
- [ ] CP-28 Caso de prueba: desde fuera, el puerto de PostgreSQL no responde.

**Relacionado:** RNF-08 · Visión 6.3 · D-03, D-16 · [07_seguridad_privacidad.md](../../03_des/02_Arquitectura/07_seguridad_privacidad.md)

Lo subes en una nueva rama llamada "feature/T92-servidor-preparar-instancia" (No olvides que el source de la rama es la de develop)

---

## Sprint 2

### [T-097][Servidor] Dominio, Nginx y HTTPS

**Tipo:** Tarea de infraestructura (cubre todos los casos de uso)
**Tecnología:** Servidor · **Sprint:** Sprint 2 (22/10–28/10)
**Depende de:** [T-092][Servidor] Preparar la instancia de Oracle Cloud #44
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:servidor`

**Descripción:** Darle al backend una dirección propia con HTTPS. Nginx recibe las peticiones de internet y las pasa al backend, que solo escucha dentro de la máquina.

**Criterios de aceptación**
- [ ] Hay un dominio propio que apunta a la IP reservada (D-26).
- [ ] Nginx atiende HTTPS con un certificado de Let's Encrypt que se renueva solo, y redirige HTTP a HTTPS.
- [ ] Los puertos 80 y 443 están abiertos en las reglas de la VCN y en el firewall de Ubuntu; el 80 solo redirige a HTTPS y atiende la validación de Let's Encrypt. Ningún otro puerto nuevo ([07_seguridad_privacidad.md](../../03_des/02_Arquitectura/07_seguridad_privacidad.md), Servidor).
- [ ] Nginx pasa las peticiones al puerto local del backend, que no es accesible desde fuera.
- [ ] La configuración de HTTPS obtiene nota A o mejor en SSL Labs.

**Tareas**
- [ ] Comprar el dominio y crear el registro DNS.
- [ ] Instalar y configurar Nginx como proxy inverso.
- [ ] Obtener el certificado con certbot y comprobar la renovación automática.
- [ ] Abrir los puertos 80 y 443 en los dos lugares.
- [ ] Actualizar la guía.

**Casos de prueba**
- [ ] CP-51 Caso de prueba: `http://dominio` redirige a `https://dominio`, y el certificado es válido en un teléfono.

**Relacionado:** RNF-08 · RNF-14 · Visión 4.4 y 6.3 · D-03, D-16, D-26

Lo subes en una nueva rama llamada "feature/T97-servidor-dominio-https" (No olvides que el source de la rama es la de develop)

---

### [T-119][Servidor] Comprobar la salida del servidor hacia la API de ANH

**Tipo:** Tarea de infraestructura (cubre CU-09)
**Tecnología:** Servidor · **Sprint:** Sprint 2 (22/10–28/10)
**Depende de:** [T-092][Servidor] Preparar la instancia de Oracle Cloud #44
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:servidor`

**Descripción:** Antes de que el backend dependa de ANH, comprobar desde la instancia de Oracle Cloud que las consultas llegan: la salida HTTPS hacia el servidor de ANH, la verificación de su certificado con los certificados de la instancia y la respuesta en el formato que describe el informe técnico de ANH (T-115). La instancia es ARM, sale a internet por la red de Oracle y puede comportarse distinto del equipo donde se hizo la prueba.

**Criterios de aceptación**
- [ ] Desde la instancia, la consulta del catálogo de departamentos de ANH responde 200, con `decCodigo` igual a 1, por HTTPS y con el certificado verificado; en ningún momento se desactiva la verificación (RN-09.3).
- [ ] Desde la instancia, el listado de estaciones de Tarija de gasolina responde 200 y trae estaciones con saldo.
- [ ] El almacén de certificados de Java de la instancia valida el certificado de ANH; si no lo valida, se anota qué certificado falta y se instala, sin saltarse la verificación.
- [ ] La salida hacia ANH no abre ningún puerto de entrada nuevo ([07_seguridad_privacidad.md](../../03_des/02_Arquitectura/07_seguridad_privacidad.md), Servidor).
- [ ] La guía del servidor anota el resultado, el tiempo de respuesta y si ANH rechaza o limita las peticiones que llegan desde la IP de Oracle.

**Tareas**
- [ ] Probar con `curl` y con el cliente de prueba de `05_pro/PruebaApiANH` (T-115) desde la instancia.
- [ ] Probar la conexión desde Java con el almacén de certificados de la instancia.
- [ ] Anotar el resultado en la guía del servidor, en `06_proyect/servidor`.

**Casos de prueba**
- [ ] CP-156 Caso de prueba: desde la instancia, el listado de estaciones de Tarija de gasolina responde 200 con el certificado verificado y trae al menos una estación con saldo.

**Relacionado:** RF-15 · RNF-14 · CU-09 · RN-09.3 · D-29 · Visión 4.3 y 6.3

Lo subes en una nueva rama llamada "feature/T119-servidor-salida-anh" (No olvides que el source de la rama es la de develop)

---

## Sprint 3

### [T-098][Servidor] Desplegar el backend con el núcleo C++

**Tipo:** Tarea de infraestructura (cubre todos los casos de uso)
**Tecnología:** Servidor · **Sprint:** Sprint 3 (29/10–04/11) · **Apoyo:** Jorge
**Depende de:** [T-097][Servidor] Dominio, Nginx y HTTPS #52 · [HU-10][CU-08][Java] Iniciar sesión con Google y emitir el JWT #46 · [T-095][Java] Wrapper MotorDeRutas #49 · [T-096][C++] Cargar la red vial en el núcleo #50 · [T-111][C++] Preparar la red vial de Tarija desde OpenStreetMap #93
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:servidor`

**Descripción:** Poner el backend en el servidor, con el núcleo C++ compilado para ARM y la red vial de Tarija, para que la app pruebe contra el sistema real desde el sprint 3. Desde aquí, cada sprint termina con lo fusionado en `develop` desplegado.

**Criterios de aceptación**
- [ ] El backend corre como un servicio del sistema que arranca solo con la máquina y se reinicia si se cae.
- [ ] El núcleo C++ se compila en el servidor y el backend lo arranca como proceso hijo; su proceso no abre ningún puerto (D-15).
- [ ] El archivo de la red vial está en el servidor y el backend sabe dónde encontrarlo.
- [ ] Los secretos (clave del JWT, Client ID de Google, credencial de servicio, contraseña de la base) están en un archivo de entorno legible solo por el usuario del servicio, fuera del repositorio.
- [ ] El backend corre con el perfil `prod`: credenciales de prueba deshabilitadas.
- [ ] Hay un script o una guía corta para desplegar una versión nueva desde `develop` y para volver a la anterior.
- [ ] El endpoint de salud responde dentro de la máquina y no desde fuera; desde fuera, el inicio de sesión de la app funciona por HTTPS.

**Tareas**
- [ ] Crear el servicio de systemd del backend.
- [ ] Compilar el núcleo y preparar la red vial en el servidor.
- [ ] Crear el archivo de entorno con sus permisos.
- [ ] Escribir el script de despliegue y de vuelta atrás.
- [ ] Desplegar y comprobar el inicio de sesión desde la app.

**Casos de prueba**
- [ ] CP-79 Caso de prueba: tras reiniciar la instancia, el backend y el núcleo vuelven solos y el inicio de sesión funciona desde la app.
- [ ] CP-80 Caso de prueba: al matar el proceso del núcleo, el backend lo vuelve a arrancar.

**Relacionado:** RNF-02 · RNF-08 · D-03, D-08, D-15, D-16

Lo subes en una nueva rama llamada "feature/T98-servidor-desplegar-backend" (No olvides que el source de la rama es la de develop)

---

## Sprint 4

### [T-100][Servidor] Desplegar el servicio de WhatsApp/LLM

**Tipo:** Tarea de infraestructura (cubre CU-06)
**Tecnología:** Servidor · **Sprint:** Sprint 4 (05/11–11/11) · **Apoyo:** Franz
**Depende de:** [T-098][Servidor] Desplegar el backend con el núcleo C++ #60 · [HU-08][CU-06][WhatsApp/LLM] Analizar los mensajes por lotes y enviar el resultado #59
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:servidor`

**Descripción:** Poner el servicio de WhatsApp/LLM en el servidor para que empiece a llenar el estado de los surtidores con datos reales.

**Criterios de aceptación**
- [ ] El servicio corre como un servicio del sistema (systemd o pm2) que arranca solo y se reinicia si se cae.
- [ ] La sesión del número de WhatsApp, la credencial del LLM y la credencial de servicio solo las puede leer el usuario del servicio.
- [ ] El servicio habla con el backend dentro de la máquina, sin pasar por internet.
- [ ] El servicio no abre ningún puerto hacia fuera.
- [ ] La guía explica cómo vincular de nuevo el número si se pierde la sesión.

**Tareas**
- [ ] Crear el servicio y su archivo de entorno.
- [ ] Vincular el número dedicado en el servidor.
- [ ] Comprobar el ciclo completo con el grupo de prueba.
- [ ] Actualizar la guía.

**Casos de prueba**
- [ ] CP-101 Caso de prueba: un mensaje en el grupo de prueba queda en la tabla `estado_combustible` en menos de 6 minutos (RNF-04). La comprobación en la app se hace en el sprint 5.

**Relacionado:** RNF-04 · RNF-11 · CU-06 · D-13, D-16

Lo subes en una nueva rama llamada "feature/T100-servidor-desplegar-whatsapp" (No olvides que el source de la rama es la de develop)

---

## Sprint 6

### [T-106][Servidor] Ambiente de pruebas para el simulador

**Tipo:** Tarea de infraestructura (cubre CU-07)
**Tecnología:** Servidor · **Sprint:** Sprint 6 (19/11–25/11), al inicio · **Apoyo:** Antonio
**Depende de:** [T-098][Servidor] Desplegar el backend con el núcleo C++ #60
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:servidor`

**Descripción:** Tener un segundo backend con su propia base de datos para correr el simulador sin tocar producción (RN-07.12). Con 12 GB de memoria, la instancia aguanta los dos.

**Criterios de aceptación**
- [ ] Hay una base `churoviaje_pruebas` separada y un segundo backend con el perfil `pruebas`, con su propio núcleo.
- [ ] Ese backend tiene habilitadas las credenciales de prueba y el modo demostración; el de producción no (P-20).
- [ ] Se llega a él por HTTPS en un subdominio propio (por ejemplo, `pruebas.dominio`).
- [ ] Se puede encender y apagar sin afectar a producción, y tiene la CPU limitada (por ejemplo, con `CPUQuota` de systemd) para que una prueba de carga no deje sin recursos al backend de producción.

**Tareas**
- [ ] Crear la base y el usuario de pruebas.
- [ ] Crear el servicio del backend de pruebas y su archivo de entorno.
- [ ] Agregar el subdominio en Nginx con su certificado.
- [ ] Actualizar la guía.

**Casos de prueba**
- [ ] CP-145 Caso de prueba: `POST /sesion/prueba` funciona en `pruebas.dominio` y responde 403 en el dominio de producción.

**Relacionado:** RNF-12 · CU-07 · RN-07.5, RN-07.12, RN-07.13 · P-20 · D-16

Lo subes en una nueva rama llamada "feature/T106-servidor-ambiente-pruebas" (No olvides que el source de la rama es la de develop)

---

### [T-103][Servidor] Respaldos de la base de datos y registros

**Tipo:** Tarea de infraestructura (cubre todos los casos de uso)
**Tecnología:** Servidor · **Sprint:** Sprint 6 (19/11–25/11) · **Apoyo:** Antonio
**Depende de:** [T-098][Servidor] Desplegar el backend con el núcleo C++ #60
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:servidor`

**Descripción:** Evitar perder los datos por un error y que los registros llenen el disco.

**Criterios de aceptación**
- [ ] Cada día se hace un respaldo de la base de datos en el almacenamiento de objetos de Oracle Cloud, fuera de la instancia, y se guardan los últimos 7 ([06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md)).
- [ ] Hay al menos una restauración probada en una base aparte, con la guía de cómo hacerla.
- [ ] Los registros de Nginx, del backend, del núcleo y del servicio de WhatsApp se borran a los 14 días, salvo los de las pruebas de carga ([06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md)).
- [ ] Los registros de acceso de Nginx no guardan la consulta de las URL, que en `GET /surtidores` lleva la ubicación del conductor.
- [ ] Ningún registro guarda textos de mensajes de WhatsApp ni datos de los conductores más allá de su identificador interno.

**Tareas**
- [ ] Crear el bucket en el almacenamiento de objetos (dentro del nivel Always Free) y programar el respaldo diario con `pg_dump`.
- [ ] Probar una restauración.
- [ ] Configurar la rotación de registros y el formato de registro de Nginx sin la consulta de las URL.
- [ ] Actualizar la guía.

**Casos de prueba**
- [ ] CP-129 Caso de prueba: el respaldo de ayer se restaura en una base aparte y tiene los mismos eventos y surtidores.

**Relacionado:** RNF-10 · RNF-11 · D-16 · [06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md)

Lo subes en una nueva rama llamada "feature/T103-servidor-respaldos" (No olvides que el source de la rama es la de develop)

---

## Sprint 7

### [T-110][Servidor] Despliegue final y revisión de seguridad

**Tipo:** Tarea de infraestructura (cierre de la Construcción)
**Tecnología:** Servidor · **Sprint:** Sprint 7 (26/11–30/11)
**Depende de:** todos los issues de los sprints anteriores
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:servidor`

**Descripción:** Dejar desplegada la versión final y comprobar, punto por punto, lo que pide [07_seguridad_privacidad.md](../../03_des/02_Arquitectura/07_seguridad_privacidad.md).

**Criterios de aceptación**
- [ ] La versión de `main` está desplegada y la app de demostración apunta a ella.
- [ ] Desde fuera solo responden el 443, el 22 y el 80, que solo redirige; el resto de los puertos, incluidos los del ambiente de pruebas, no responden.
- [ ] En producción, las credenciales de prueba y el modo demostración están deshabilitados.
- [ ] Ningún secreto está en el repositorio; se revisa el historial de git.
- [ ] La base de producción no tiene ninguna fila marcada como simulada.
- [ ] La política de privacidad está publicada en el dominio.
- [ ] La lista de comprobación de seguridad queda firmada en `06_proyect/servidor`.

**Tareas**
- [ ] Desplegar la versión final.
- [ ] Revisar los puertos desde fuera.
- [ ] Revisar la configuración de cada componente contra `07_seguridad_privacidad.md`.
- [ ] Buscar secretos en el repositorio.
- [ ] Escribir la lista de comprobación.

**Casos de prueba**
- [ ] CP-154 Caso de prueba: un escaneo de puertos desde fuera solo encuentra el 22 y el 443 (y el 80, que redirige).

**Relacionado:** RNF-08 · RNF-10 · RNF-11 · RNF-12 · D-03, D-16 · [07_seguridad_privacidad.md](../../03_des/02_Arquitectura/07_seguridad_privacidad.md)

Lo subes en una nueva rama llamada "feature/T110-servidor-despliegue-final" (No olvides que el source de la rama es la de develop)
