# Correcciones para Franz: [T-089] Base de datos, catálogos y zona de cobertura (#41)

**Rama revisada:** `feature/T89-java-base-de-datos` (commit `d899840`, un solo commit sobre el `develop` actual)
**Issue:** #41 · **Casos de prueba:** CP-22, CP-23 · **Reglas:** RN-01.6, RN-03.3 · **Decisiones y parámetros:** D-04, D-10, P-06

Franz, el avance está bien encaminado. La estructura (dominio separado, migración V1, carga del polígono con control de estado, tests con Testcontainers) es sólida y se nota que leíste la arquitectura. Lo que falta es sobre todo **datos reales** y algunos ajustes para que encaje con T-088. Está ordenado por prioridad: lo bloqueante tiene que estar resuelto antes de abrir el PR a `develop`.

---

## 1. Bloqueante (sin esto el issue no se puede cerrar)

### 1.1 Falta la migración de surtidores (`V2__surtidores_revisados.sql`)
- El criterio del issue pide que `surtidor` y `surtidor_alias` lleven los surtidores de Tarija con su ubicación y sus alias. Hoy la V1 solo crea las tablas y los catálogos de tipo de evento y de combustible; no hay ningún `INSERT` de surtidores.
- `MigracionesPostgisTest` (CP-23) espera dos cosas que hoy no se cumplen: que se ejecuten al menos 2 migraciones (hay una sola) y que existan surtidores y el archivo `V2__surtidores_revisados.sql`. **Ese test falla** tal como está.
- **El orden importa:** tu propio script `generar_migracion_surtidores.py` se niega a generar la V2 mientras el polígono no esté `APROBADO` (ver 1.4). Por eso el orden real es: (1) polígono bueno y aprobado, (2) surtidores revisados, (3) generar la V2, (4) CP-23 en verde.
- Si la revisión completa no llega a tiempo, una opción es una V2 solo con los surtidores ya verificados y el resto en una V3 más adelante (lo decide Marcelo). Aun así, la V2 necesita el polígono aprobado.
- Una vez que una migración se aplicó en algún ambiente, no se edita: los cambios van en una versión nueva.

### 1.2 Las coordenadas de los surtidores no están verificadas
- En `04_imp/datos_pendientes/surtidores_revision.csv` están los 21 surtidores del prototipo. Todas las columnas de revisión están en `NO`/`PENDIENTE`: ninguno tiene `revision_franz = SI` ni `aprobado_equipo = SI`.
- De esos 21, 8 tienen coordenadas marcadas `GUIA_SIN_VERIFICAR` y 13 no tienen coordenadas.
- El script generador rechaza todo esto, así que el bloqueo es de datos, no de código.
- Qué hacer:
  - Para cada surtidor, ubicarlo de verdad (mapa, Street View o visita) y anotar la fuente en `fuente_ubicacion`.
  - Marcar `revision_franz = SI` solo cuando lo hayas comprobado vos.
  - Los que no se puedan ubicar: `decision = EXCLUIR` con `motivo_exclusion`, en vez de dejarlos en blanco.
  - `aprobado_equipo` lo marca Marcelo después de revisar.
- Detalle de limpieza: la fila de Moto Mendez tiene el mismo alias repetido (`moto mendez|moto mendez`). El script lo tolera, pero conviene dejarlo bien.
- Algunos alias muy cortos o genéricos (`daniel`, `molle`, `campesino`, `vegas`) pueden generar coincidencias falsas con mensajes de los grupos. Vale la pena revisarlos con el equipo de WhatsApp/LLM.

### 1.3 Las 6 estaciones adicionales propuestas no deben entrar todavía
- `estaciones_adicionales_propuestas.csv` tiene 6 estaciones, todas `GUIA_SIN_VERIFICAR` y sin alias.
- Hay posibles duplicados: «La Terminal» parece ser la misma que «Ex Terminal» del prototipo (que además tiene el alias `la terminal`, y el script lo marcaría como ambiguo), y «YPFB Panamericana» queda a unos 170 m de «Tacuarandi». Confirmá si son estaciones distintas.
- El issue habla de la lista del prototipo revisada con vos. Dejalas en `datos_pendientes` y no las pases a la migración hasta que el equipo las apruebe; si alguna es real, va en un issue aparte.

### 1.4 El polígono sigue siendo un borrador
- `mancha_urbana_tarija.geojson` está con `estado_validacion: PROVISIONAL` y `revision_equipo: PENDIENTE`. Los vértices son aproximados (unos 12,4 × 10,8 km), no salen de cartografía.
- El issue pide dibujarlo a mano (por ejemplo con geojson.io) sobre la mancha urbana real, siguiendo el límite de la Visión. Mientras sea provisional, `prod` se niega a arrancar (eso está bien y es intencional).
- Verifiqué con tu polígono actual que los 14 puntos con coordenadas (de los dos CSV) caen dentro y que San Lorenzo cae fuera, así que la lógica de CP-22 funciona; el problema es la precisión del contorno, no el código.
- Al exportar desde geojson.io, revisá que sigan los campos que agregaste a mano (`estado_validacion` en la raíz, `revision_equipo` en la propiedad del polígono); si el editor los pierde, hay que volver a ponerlos.
- Hoy el backend en Java solo mira `estado_validacion`, mientras que el script de Python mira también `revision_equipo` de cada polígono. Alineá los dos para que "aprobado" signifique lo mismo en ambos.
- Cuando esté listo, el paso a `APROBADO` lo hace Marcelo tras revisarlo. Ojo: el polígono también lo va a usar el motor para recortar la red vial, así que conviene no dejarlo para el final.

### 1.5 Todavía no se pudo compilar ni correr nada
- La rama sale de `develop`, pero T-088 (#40, Jorge, `feature/T88-java-estructura-base`) todavía no está integrada y no hay `pom.xml`/`build.gradle` en `04_imp/backend_java`.
- Consecuencia: nada de lo tuyo se compiló ni se probó contra Postgres. Cuando T-088 entre a `develop`, **rebasá tu rama sobre ella**, corré todos los tests y pegá el resultado en el PR.
- Coordiná con Jorge la estructura de paquetes y las dependencias (Flyway, Testcontainers, driver de PostgreSQL) para no pisarse.

---

## 2. Medio (conviene corregir en este mismo PR)

### 2.1 Qué pasa con una ubicación fuera de cobertura en una ruta
- `Ubicacion.validarParaRuta` lanza una excepción con código `UBICACION_INVALIDA` y tu manejador la convierte en un 422. Pero `05_contratos.md` trata el «punto inválido» como un resultado normal de `POST /rutas`, igual que «sin ruta», que dice explícitamente que **no es un rechazo**. Y el motor devuelve `PUNTO_INVALIDO` como un estado correcto (también cuando el punto no está cerca de ninguna intersección, RN-03.4).
- Para reportes no hay duda: 422 por RN-01.6 está en la tabla de rechazos del contrato, y lo tuyo calza.
- Para rutas, lo más probable es que la respuesta deba ser el mismo «punto inválido» con el mensaje «Origen o destino inválido» (RN-03.3), y no un 422. Hablalo con Marcelo antes de dejarlo; en sus notas figura como decisión. Mientras tanto, no cierres el contrato de `validarParaRuta` como definitivo.
- Recordá también que RN-03.3 incluye las paradas, no solo origen y destino; el mensaje podría decir «punto» en general.

### 2.2 El manejador del 422 puede duplicar el global de T-088
- Creaste `ManejadorUbicacionFueraCobertura` (`@RestControllerAdvice`) con su propio record `RespuestaError(codigo, mensaje)`. T-088 va a traer el manejador global de errores con el cuerpo `{codigo, mensaje}` y todos los códigos de la tabla de rechazos.
- Lo ideal es que la excepción de dominio se traduzca en ese manejador global y no tener dos clases con el mismo cometido. Coordinalo con Jorge.

### 2.3 Nombres de paquetes y capas distintos a la arquitectura
- Usaste `dominio/ubicacion`, `dominio/catalogo`, `infraestructura/...`. T-088 pide paquetes de controladores REST, servicios del modelo, objetos de dominio, repositorios y motor (`02_mvc.md`). Alineá los nombres con lo que fije Jorge.
- `CatalogosJdbc` usa `JdbcTemplate`, mientras que T-088 incluye Spring Data JPA en el stack. Pregúntale a Jorge cuál usar para repositorios, para que haya un solo estilo.
- Según `02_mvc.md`, los repositorios no contienen reglas. `buscarPorNombreOAlias` normaliza el texto dentro del repositorio; esa normalización debería hacerla el servicio o el objeto de dominio, y el repositorio recibir la clave ya lista.
- Además, `buscarPorNombreOAlias` busca solo por alias: funciona porque el script agrega el nombre del surtidor como alias, pero el método dice «nombre o alias». O se documenta, o se busca también por nombre.

### 2.4 `CatalogosJdbc` no tiene tests (y no estaba pedido)
- Tiene `tiposEvento()`, `tiposCombustible()`, `surtidoresCercanos()` y `buscarPorNombreOAlias()`, pero ningún test lo ejercita. Si lo dejás, probalo con Testcontainers: orden por distancia, búsqueda por alias, un surtidor con varios alias.
- El issue pide migraciones y el objeto `Ubicación`; si preferís ir más liviano, mové el repositorio a un issue posterior.

### 2.5 Los colores de los tipos de evento son provisionales
- La Visión y las historias dan como ejemplo rojo para accidentes y azul para controles; tus códigos hexadecimales para esos dos son razonables. Pero P-06 deja **pendientes los colores de bloqueo, otro y congestión**, que dependen del diseño de la app (Canva); los tuyos son inventados.
- Dejá un comentario en la migración diciendo que son provisionales y consultá con Alex. Si cambian, van en una migración nueva, no editando la V1.

### 2.6 Orden inconsistente de latitud y longitud
- `Coordenada(longitud, latitud)` usa longitud primero (como GeoJSON), pero `Ubicacion(latitud, longitud, ...)`, `contiene(lat, lon)` y todo el contrato usan latitud primero. Como ambos son `double`, es fácil invertirlos sin que el compilador avise.
- Unificá el orden en el dominio (el contrato dice latitud y longitud) y dejá la inversión solo en el lector de GeoJSON, con un comentario.

### 2.7 `.gitignore`
- No cubre `.env` ni `target/`. El `compose.yaml` ya exige `DB_PASSWORD` por variable (bien), pero conviene asegurar que nadie suba un `.env` por accidente.

### 2.8 Commits, PR y trazabilidad
- Según `CONTRIBUTING.md`, los commits llevan Conventional Commits y `Refs #N` en intermedios; `Closes #N` solo en el final o en el PR. Tu commit pone `#41` al final del título y no tiene `Refs #41`. Corregilo.
- Un solo commit de 29 archivos (1201 líneas) dificulta la revisión; si podés, dividilo (compose y configuración, migración, dominio, cobertura, tests, datos).
- Todavía no hay PR. Cuando estés listo: PR a `develop` con `Closes #41`, con CP-22 y CP-23 y el resultado de los tests. Marcelo lo revisa antes de fusionar.
- Actualizá la fila de T-089 en `06_proyect/TRAZABILIDAD.md` cuando se fusione (hoy figura como Pendiente).

---

## 3. Menor (si te da el tiempo)

- **Tests de migración duplicados:** `MigracionesPostgisTest` y `MigracionesPostgisV1Test` se pisan bastante; con uno parametrizado alcanza. Además, en el primero comprobar que existe el archivo `V2__...` es frágil y redundante: ya lo cubre el conteo de surtidores.
- **Rutas relativas en tests:** usás `System.getProperty("t089.raiz","../..")`. Funciona desde el módulo, pero se rompe si se corre desde otro directorio; mejor fijar la propiedad en el build o resolver el recurso por classpath.
- **Test fuera de lugar:** en `ClaveAliasTest` hay un caso (`desconocidoNoEsOpcionDeRecarga`) que prueba `TipoCombustible`; va en su propio test. También sobran dos imports de su mismo paquete.
- **El punto de la «plaza» en CP-22:** el test usa `-21.533912, -64.734297` y lo llama Plaza Luis de Fuentes. El centro que usa el contrato como ejemplo es `-21.5355, -64.7296`, a unos 500 m. Comprobá que el punto sea de verdad la plaza o cambiale el nombre.
- **Orden por `codigo`:** en `tiposEvento()` y `tiposCombustible()` ordenás por código; si la app necesita un orden de presentación, hace falta una columna de orden.
- **Alias normalizados en la base:** `ClaveAlias.normalizar` está en Java, pero la base no obliga a que los alias ya estén normalizados. Un `CHECK` evitaría que un alias mal cargado nunca coincida.
- **Geometría propia:** `Anillo` implementa el algoritmo de punto en polígono con validaciones, y está bien probado, pero es la tercera copia de la misma idea (Java, Python y lo que PostGIS ya resuelve con `ST_Contains`). No es obligatorio cambiarlo; si lo dejás, que `generar_migracion_surtidores.py` tenga al menos una prueba.
- **Perfil por defecto:** sin perfil activo, `permitir-provisional` es `false` y la app no arranca con el polígono actual. Documentá que se usa `dev` (README de `04_imp`).
- **Ruta del GeoJSON:** el valor por defecto (`../datos/mancha_urbana_tarija.geojson`) queda fuera del jar. Para prod ya lo pedís por variable; documentá cómo se despliega.
- **Documentación:** actualizá el README de `04_imp` con cómo levantar la base (`compose.yaml`), correr las migraciones y los tests.
- **Casos de prueba:** poné en los tests de CP-22 y CP-23 un comentario con la HU/CU/regla que validan, como pide `CONTRIBUTING.md`.

---

## 4. Lo que está bien hecho (no lo toques)

- No hay secretos en el commit; el compose exige `DB_PASSWORD` y publica solo en `127.0.0.1`.
- Flyway con `clean-disabled: true`, `validate-on-migrate: true` y `ddl-auto: validate`: justo lo que queremos.
- V1 coincide con `06_base_de_datos.md` (tablas, claves foráneas con nombre, `ON DELETE CASCADE` en alias, índice GIST, vigencias de 60 y 30 minutos, congestión no reportable).
- `Ubicacion` y `CoberturaManchaUrbana` cumplen RN-01.6/RN-03.3 y CP-22 (incluyen bordes, huecos y multipolígonos).
- El bloqueo en `prod` cuando el polígono no está `APROBADO` es un buen diseño.
- El script generador valida todo antes de emitir SQL (aprobaciones, fuentes, coordenadas dentro del polígono, alias ambiguos).

---

## Checklist final antes del PR

- [ ] Polígono dibujado a mano, revisado por Marcelo y marcado `APROBADO`
- [ ] Surtidores revisados y aprobados; V2 generada desde ellos
- [ ] CP-23 pasa en base vacía (V1 + V2)
- [ ] CP-22 pasa con el polígono final
- [ ] Rebase sobre T-088 y todos los tests en verde
- [ ] Decidido con Marcelo qué responde una ubicación inválida en una ruta
- [ ] Manejador de errores alineado con el global de T-088 y con `05_contratos.md`
- [ ] Orden latitud/longitud unificado en el dominio
- [ ] Colores provisionales documentados, consulta a Alex hecha (P-06)
- [ ] `.gitignore` con `.env` y `target/`
- [ ] Commits con `Refs #41`, PR con `Closes #41`
- [ ] TRAZABILIDAD.md actualizado al fusionar
