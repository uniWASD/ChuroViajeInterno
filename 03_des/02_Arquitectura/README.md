# Arquitectura de ChuroViaje

Este documento define cómo se construye ChuroViaje: qué componentes tiene, cómo se organiza con el patrón Modelo-Vista-Controlador (MVC) y qué reglas de negocio gobiernan cada proceso. Es el paso previo al backlog de Construcción: los issues de cada tecnología se derivan de lo que aquí queda escrito.

Esta carpeta es la versión oficial del documento de arquitectura (revisión del 06/10/2026). Cada tema vive en su propio archivo para encontrarlo rápido; el documento completo en Word se arma uniendo estos archivos.

## Contenido

| Archivo | Qué contiene | Lo consulta sobre todo |
| --- | --- | --- |
| [01_vision_general.md](01_vision_general.md) | Metas, restricciones, componentes, despliegue y comunicación | Todo el equipo |
| [02_mvc.md](02_mvc.md) | Qué es vista, controlador y modelo, reglas de reparto y vistas de la app | Todo el equipo, Flutter |
| [03_modelo_dominio.md](03_modelo_dominio.md) | Los 19 objetos de dominio, sus estados, catálogos y valores | Java, C++ |
| [04_reglas/](04_reglas/README.md) | Las 131 reglas de negocio y el recorrido de cada caso de uso, un archivo por CU | Cada uno, según su CU |
| [05_contratos.md](05_contratos.md) | API de la app, llamadas al motor core, servicio de WhatsApp/LLM y códigos de rechazo | Flutter, Java, C++, LLM |
| [06_base_de_datos.md](06_base_de_datos.md) | Qué se guarda y cuánto tiempo, tablas e integridad | Java, simulador |
| [07_seguridad_privacidad.md](07_seguridad_privacidad.md) | Accesos, defensa contra el abuso, privacidad y servidor | Java, LLM |
| [08_decisiones_parametros.md](08_decisiones_parametros.md) | Decisiones D-01 a D-14 y parámetros P-01 a P-22 | Todo el equipo |
| [diagramas/](diagramas/) | Fuentes PlantUML (.puml) de los tres diagramas y su imagen | Todo el equipo |

La relación entre requisitos, casos de uso, historias de usuario y reglas está en la matriz [TRAZABILIDAD.md](../../06_proyect/TRAZABILIDAD.md), columna Reglas.

## Propósito

- Fijar qué hace cada componente y qué no le corresponde.
- Dejar escritas las reglas de cada proceso y el objeto de dominio que las hace cumplir.
- Dar al equipo un mismo vocabulario antes de repartir el trabajo por tecnología.

## Alcance

Cubre los ocho casos de uso (CU-01 a CU-08) y las trece historias de usuario (HU-01 a HU-13) del MVP. Queda fuera lo que la Visión excluye: zonas fuera de la mancha urbana de Tarija, integración con los sistemas de las gasolineras, rastreo en segundo plano y monetización.

## Referencias

| Documento | Versión | Qué aporta a la arquitectura |
| --- | --- | --- |
| Documento de Visión | 0.4 (06/10/2026) | Alcance, componentes, restricciones y requisitos no funcionales |
| Especificación de Casos de Uso, CU-01 a CU-08 | 06/10/2026 | Flujos, extensiones y valores de cada proceso |
| Historias de Usuario, HU-01 a HU-13 | 06/10/2026 | Criterios de aceptación que las reglas deben cumplir |
| Matriz de Requisitos y Trazabilidad | 06/10/2026 | Códigos RF-01 a RF-14 y RNF-01 a RNF-13 |
| Decisión de arquitectura: conexión a WhatsApp | 1.3 (06/10/2026) | Servicio de WhatsApp/LLM y su relación con el backend |
| Repositorio ChuroViajeInterno | Issues y PR al 06/10/2026 | Prototipos de `05_pro`, diseño de interfaz y propuestas de backlog |

Cuando un archivo del repositorio contradice a los documentos corregidos el 06/10/2026, mandan los corregidos. Las diferencias encontradas se registran como issues del repositorio.

## Definiciones

| Término | Significado en este documento |
| --- | --- |
| Vista | Lo que el conductor ve y toca: las pantallas de la app |
| Controlador | La pieza que recibe una petición, comprueba sesión y formato, llama al modelo y devuelve la respuesta |
| Modelo | Los objetos de dominio, sus reglas y los datos que guardan |
| Objeto de dominio | Un concepto del negocio (un evento vial, un surtidor) con sus datos y con las reglas que le pertenecen |
| Regla de negocio | Una condición que el sistema debe cumplir siempre, sin importar desde qué pantalla o cliente llegue la petición |
| Vigencia | Tiempo durante el cual un dato se toma en cuenta; pasado ese tiempo deja de mostrarse |
| Consulta periódica | La app pregunta al backend cada cierto tiempo si hay novedades, en lugar de mantener una conexión abierta |
| JWT | Token de acceso con expiración que el backend emite tras verificar la cuenta de Google |

## Cómo leer los códigos

| Código | Significado | Dónde está |
| --- | --- | --- |
| [RN-01.4](04_reglas/CU-01_ReportarEventoVial.md) | Cuarta regla de negocio del caso de uso CU-01 | [04_reglas/](04_reglas/README.md) |
| [D-08](08_decisiones_parametros.md) | Decisión de arquitectura número 8 | [08_decisiones_parametros.md](08_decisiones_parametros.md) |
| [P-02](08_decisiones_parametros.md) | Parámetro número 2, con su valor y su estado | [08_decisiones_parametros.md](08_decisiones_parametros.md) |
| c.3 / ext. 3a | Criterio de aceptación 3 de una HU / extensión 3a de un caso de uso | Documentos de requisitos en 02_req |
