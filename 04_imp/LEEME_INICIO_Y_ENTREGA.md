# ChuroViaje — entrega de código T-089 / Issue #41

**Contenido:** el proyecto original del `.rar` más compilación Maven, Java, pruebas JDBC/PostGIS/MVC, cliente ANH, decisiones técnicas preliminares, scripts, cobertura y **todos los informes en `.md`**. No contiene `target/`, caches Python, logs inventados, credenciales, ni Git.

## Qué abrir

- **Informe principal:** `INFORME_FINAL_T089_ISSUE_41.md`.
- **Comprobación de criterios:** `MAPA_CUMPLIMIENTO_ISSUE_41.md` (original, de 24/10; prevalece el informe actualizado).
- **Decisiones de 21 nombres:** `datos_pendientes/PROPUESTA_DECISION_TECNICA_21_NO_APROBADA.csv` y `propuestas/SQL_PROPUESTA_8_NO_EJECUTAR.sql.txt`.
- **Código:** `backend_java/src/main/java/`, `backend_java/src/main/resources/db/migration/`.
- **Pruebas:** `backend_java/src/test/java/` y `herramientas/tests/`.
- **Fuente documental:** `referencias_t089/`, `integraciones/ANH_T115/`.

## Ejecutar local (desde la raíz `T089_ChuroViaje_04_imp` en PowerShell)

Requisitos Java 21+, Maven 3.9+, Docker Desktop Linux, Python 3.10+; Maven descargará dependencias la primera vez.

```powershell
$env:DB_PASSWORD = Read-Host 'Clave DB solo en terminal local'
docker compose -f .\backend_java\compose.yaml up -d db
mvn -B -ntp -f .\backend_java\pom.xml '-Dtest=ClaveAliasTest,ServicioBusquedaSurtidoresTest,TipoCombustibleTest,CoberturaGeoJsonTest,ConfiguracionCoberturaTest,ManejadorCoberturaTest,MigracionesPostgisV1Test,T089_ManejadoresMvcTest,CatalogosJdbcPostgisTest,T089ArranqueAisladoTest' test
```

El ZIP añade `T089StandaloneApplication` para arrancar **solo T-089**. Ejecutar desde el directorio `backend_java`, para resolver correctamente `../datos`:

```powershell
Set-Location .\backend_java
mvn '-Dspring-boot.run.profiles=dev' spring-boot:run
```

Java JPA se excluye **solo de este lanzador T-089**, pues la entidad provisional `EventoVial` de HU-01/T-088 no tiene migración SQL. No cambia la configuración de la aplicación MVC real de T-088. La base dev contiene solo V1/V1.1 y cero surtidores hasta V2 aprobada.

El `pom.xml` omite las pruebas `MigracionesPostgisTest` y `CatalogosJdbcIntegrationTest` de `mvn test` por defecto: ambas requieren V2 real. Luego de las aprobaciones, `mvn -Pcp23-final test` vuelve a ejecutarlas. NO representar su omisión como CP-23 cumplido.

## Recuperar archivos exactos de tu G: sin sobrescribir

Este ZIP **no incluye los 11 archivos originales T-088** que estaban en tu G: actual ni el JSON ANH completo que estaba en tu PC pero no dentro del RAR adjunto. Para comparar sin modificar nada:

```powershell
.\herramientas\COMPROBAR_DIFERENCIAS_CON_G.ps1 -ProyectoG 'G:\Otros ordenadores\Mi portátil\Tercer_ano(6_semestre)\Taller_de_Sistemas_de_Informacion_(Ing_Guerrero_Guerrero_Ronal_Mario)\Unidad_1\Practicos\V0_ProyectoFinal\Pruebas_Funciones\T089_ChuroViaje_04_imp'
```

Para elaborar la entrega complementaria con el JSON crudo **ya existente en tu G:**, usa el script auxiliar `herramientas/preparar_entrega_t089.py` apuntando al proyecto G: y el `datos_tarija.json` original; este comprueba SHA256 y no certifica firmas.

## Crear una versión EXACTA de tu G: con todo lo que no estaba en el RAR

Para un ZIP de entrega que contenga **las 11 clases T-088 originales de tu G:, las 16 pruebas con sus fuentes exactas y el JSON ANH auténtico**, usa la herramienta de lectura `herramientas/EMPAQUETAR_DESDE_G_ACTUAL.py`. Genera únicamente un ZIP; no crea otra carpeta del proyecto, no reemplaza tus archivos y no modifica GitHub. Ejecútala desde donde extrajiste este paquete, con la ruta a tu proyecto G: y al JSON que ya tienes en C:. El script valida la huella del snapshot.

```powershell
$G = 'G:\Otros ordenadores\Mi portátil\Tercer_ano(6_semestre)\Taller_de_Sistemas_de_Informacion_(Ing_Guerrero_Guerrero_Ronal_Mario)\Unidad_1\Practicos\V0_ProyectoFinal\Pruebas_Funciones\T089_ChuroViaje_04_imp'
$S = Join-Path $env:USERPROFILE 'T089_Local\integraciones\ANH_T115\salida_tarija\20261024T181654_139720Z\datos_tarija.json'
$Z = Join-Path $env:USERPROFILE 'Downloads\T089_COMPLETO_REAL_DESDE_G_20261026.zip'
python .\herramientas\EMPAQUETAR_DESDE_G_ACTUAL.py --proyecto-g $G --snapshot-anh $S --salida $Z
```

El ZIP preparado aquí ya es utilizable para revisión de código; **este último paso solo es necesario si deseas que la entrega sea fiel a los archivos posteriores que existen únicamente en tu computadora**.

## Política de datos

La selección propuesta **8 incluir / 13 excluir de carga inicial** sirve para revisión técnica. Los 21 nombres de prototipo no son una lista de estaciones aprobadas. Las dos cartografías siguen PROVISIONAL/PENDIENTE. Por ello V2 no está incluida en las migraciones aplicables. No cambiar aprobaciones sin revisión del equipo ni la tabla de eventos HU-01 para superar arranque.

**El archivo ZIP es completo respecto a T-089 disponible en las fuentes adjuntas y al material nuevo generado, pero no es una copia exacta del directorio G: del usuario.**
