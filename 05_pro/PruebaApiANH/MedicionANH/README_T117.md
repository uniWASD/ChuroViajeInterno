> Para las **pruebas abreviadas autocontenidas**, utiliza primero `README_PRUEBA_CORTA.md` o `INICIAR_AQUI.md` en la raíz del paquete. Este documento describe la opción de medición prolongada y su análisis.

# T-117 · Medir ANH (Java 17+, sin Maven)

**Issue:** #112 · **Casos:** CP-157 y CP-158 · **Rama de destino:** `feature/T117-java-medir-api-anh`, creada desde `develop`.

El contenido de este directorio es una **adición**. No reemplaza `anh_tarija.py` de T-115 ni sobrescribe los documentos de arquitectura. La arquitectura que se adjuntó como ZIP T-84 es más antigua que `develop`: contiene tres contratos en vez de los cuatro de la versión actual y no tiene CU-09 ni los parámetros P-29/P-30/P-32. **No descomprima ese ZIP antiguo sobre la rama vigente.**

## 1. Estado de la entrega

- Implementación Java independiente compilada en `medicion-anh.jar`, que funciona en Windows y ARM con Java 17 o superior (JAR generado con `javac --release 17`). No requiere Maven, Gradle ni certificado TLS deshabilitado.
- Funciona con el flujo del cliente T-115: descubre Tarija por nombre en el catálogo; consulta gasolina (`0`) y diésel (`1`) por separado; valida `decCodigo`, `oResultado`, identificadores, paginación y duplicados. No hace solicitudes a detalles de estaciones; no modifica datos del backend.
- Un ciclo inicia como máximo cada 10 minutos. Internamente hace tres GET normales (catálogo, gasolina, diésel), con pausa **después** de la petición anterior (1,1 segundos mínimos), y un reintento por error transitorio o 429 según `Retry-After`. Un error de un combustible no anula el otro.
- No almacena respuestas completas; guarda solo identificación de consulta, identificador de estación y producto, saldo, `updated_at`, horas UTC, latencias, códigos HTTP y categorías de error. Nunca guarda nombres, direcciones, coordenadas ni fechas de venta.
- La hora de consulta se escribe en UTC. `updated_at` se conserva tal como llega y se clasifica como `SI` (zona explícita), `NO` (sin zona), `AUSENTE` o `INVALIDO`, sin suponer UTC. CU-09 RN-09.13 ya establece que, sin zona, corresponde la de Tarija (`America/La_Paz`, UTC-04:00).
- El programa guarda `estado.properties` con la fecha de inicio, próximos ciclos, último contacto y suspensión por `Retry-After`. No borra archivos al reanudarse. Abra una carpeta diferente para cada ejecución real. Para un solo ciclo exploratorio, `--una-vez` no ignora la siguiente fecha permitida.

**Importante:** no se ejecutaron 48+24 horas reales ni una conexión SSH a Oracle; no hay evidencia para marcar los parámetros como «Confirmada». Los registros de pruebas de servidor simulado **no** son evidencia CP-157.

## 2. Compilar en Windows (PowerShell, en este directorio)

Java 17+ instalado, con `java`, `javac` y `jar` en PATH:

```powershell
java -version
javac -version
New-Item -ItemType Directory -Force build | Out-Null
javac --release 17 -encoding UTF-8 -d build .\java\MedicionANH.java
jar --create --file medicion-anh.jar --main-class MedicionANH -C build .
```

También puede usar el JAR ya preparado. No comprometerlo en Git salvo decisión expresa del equipo; conservar el fuente Java.

## 3. Inicio local real (48 horas)

En PowerShell, dentro de este directorio:

```powershell
java -jar .\medicion-anh.jar --origen local --horas 48 --salida .\datos-local --disclaimer
java -jar .\medicion-anh.jar --origen local --horas 48 --salida .\datos-local
```

La primera orden intenta **una sola vez** `GET /api/Disclaimer` y guarda una evidencia separada en `disclaimer-evidencia-NO-COMMIT.txt` dentro de `datos-local`; la segunda arranca la medición. No volver a llamar al aviso si ya fue intentado; verificar manualmente las condiciones, cuotas y contacto institucional. El servicio podría no responder: **no afirmar que ANH autoriza el uso simplemente porque hay un endpoint público.**

Mantener el equipo encendido y sin suspensión durante las 48 horas. Si el proceso se interrumpe, volver a lanzar exactamente el mismo comando y carpeta; el cronograma original se conserva, y el analizador reflejará cualquier hueco. **No modificar `--intervalo-segundos` en el servicio real a menos de 600 segundos**: el programa rechaza esa opción.

Se generan `medicion-local.csv`, `estaciones-local.csv`, `estado.properties`. Los CSV no incluyen cuerpos JSON.

## 4. Oracle Cloud (24 horas, al terminar la etapa local)

En la laptop, copiar solo el JAR con `scp` usando **la llave privada que ya exista realmente en su equipo**. Las rutas SSH compartidas en el chat no son archivos disponibles para este entorno; una línea `ssh-ed25519 ...` es una **clave pública**, no la privada necesaria para autenticar. No subir claves ni la IP del servidor al repositorio.

```powershell
scp -i $HOME\.ssh\churoviaje-deploy .\medicion-anh.jar deploy@<IP_PUBLICA>:~/
ssh -i $HOME\.ssh\churoviaje-deploy deploy@<IP_PUBLICA>
```

En el servidor (sin sudo ni puertos entrantes):

```bash
java -version
curl -sS -o /dev/null -w "%{http_code} %{time_total}\n" "https://abastecimiento.prod.anh.gob.bo/api/departamentos/?producto=0"
nohup java -jar ~/medicion-anh.jar --origen oracle --horas 24 --salida ~/datos-oracle > ~/medicion-oracle.log 2>&1 &
ps aux | grep '[m]edicion-anh.jar'
tail -n 30 ~/medicion-oracle.log
```

Verificar que Java 17+ exista, que la instancia tenga salida HTTPS y que Marcelo confirme que no habrá reinicios de Oracle durante esas 24 horas. La prueba `curl` es una consulta a ANH: no repetirla innecesariamente y mantener al menos un segundo antes de comenzar el monitor. No subir compiladores a Oracle.

Después de las 24 horas:

```powershell
scp -r -i $HOME\.ssh\churoviaje-deploy deploy@<IP_PUBLICA>:~/datos-oracle .\datos-oracle
```

Necesita **los CSV y el `estado.properties`** de Oracle, no solo `medicion-oracle.csv`. Conservar también el archivo de log de proceso para diagnosticar reinicios. No confundir la carpeta `datos-local` con `datos-oracle`.

## 5. Análisis y validación de evidencias

Con Python 3.10+ (solo biblioteca estándar), ejecutar desde este directorio:

```powershell
python .\scripts\analizar_medicion.py --local .\datos-local --oracle .\datos-oracle --salida .\analisis
```

Archivos: `analisis/informe_medicion.md` y `analisis/resumen_medicion.json`.

El analizador calcula, por origen, tiempos promedio, p95 y máximos, errores HTTP, `Retry-After`, número de consultas, huecos, clasificación de `updated_at`, cambios observados de saldo y fecha por estación/producto. Comprueba continuidad, origen real declarado y separación de peticiones; **rechaza datos simulados e incompletos para CP-157**. Esto no prueba por sí solo el origen criptográfico de los CSV: el responsable debe revisar trazas y servicio.

**P-29:** usar los cambios observados para decidir, sin aumentar las consultas por encima de la política prudente. **P-30:** establecer y justificar la vigencia desde la última lectura válida del backend; mantener o cambiar las 6 horas según evidencia, no por suposición. **P-32:** comparar las latencias y los rechazos local/Oracle. La observación cada 10 minutos es discreta: no demuestra el segundo exacto en que ANH cambió los datos.

## 6. Actualización restringida de arquitectura (solo cuando CP-157 exista)

En `scripts/decisiones-T117-EJEMPLO.json` hay un **esquema sin parámetros rellenados**; crear una copia propia con valores aprobados, la conclusión documentada del Disclaimer y el responsable. `actualizar_arquitectura.py` se niega a editar con CP-157 incompleto. Trabaja **sobre un checkout reciente de `develop`** y exactamente con:

- `03_des/02_Arquitectura/08_decisiones_parametros.md` (solo P-29, P-30 y P-32).
- `03_des/02_Arquitectura/03_modelo_dominio.md` (solo tres valores iniciales ANH).
- `03_des/02_Arquitectura/05_contratos.md` (solo la sección «Backend con la API de ANH»).
- `03_des/02_Arquitectura/04_reglas/CU-09_ConsultarEstadoANH.md` (solo un bloque con los valores de CU-09).

Primero crear la rama auténtica de Git:

```bash
git checkout develop
git pull
git checkout -b feature/T117-java-medir-api-anh
```

Luego vista previa (sin escribir):

```powershell
python .\scripts\actualizar_arquitectura.py --repo <CHECKOUT_ACTUAL> --resumen .\analisis\resumen_medicion.json --decision .\decisiones-aprobadas.json --disclaimer .\datos-local\disclaimer-evidencia-NO-COMMIT.txt
```

Y solo tras revisar y aprobar la evidencia:

```powershell
python .\scripts\actualizar_arquitectura.py --repo <CHECKOUT_ACTUAL> --resumen .\analisis\resumen_medicion.json --decision .\decisiones-aprobadas.json --disclaimer .\datos-local\disclaimer-evidencia-NO-COMMIT.txt --aplicar --respaldos <CARPETA_FUERA_DEL_REPO>
```

El script exige anclajes únicos y se detiene en vez de improvisar si el documento cambió. Los respaldos se guardan fuera del repositorio. Verificar siempre `git diff --check` y `git diff` antes de confirmar.

## 7. Pruebas automáticas y control Git

Desde este directorio:

```powershell
python -m unittest discover -s tests -v
```

Todas las pruebas llaman a `http://127.0.0.1` dentro de un servidor simulado temporal. **No consultan a ANH**. Incluyen duplicados, datos con/sin zona, 429, Retry-After, reanudación, redirección, no-truncado, rangos, rechazo de datos simulados y validación de cambios delimitados de Markdown.

Agregar únicamente los archivos que correspondan al issue y los registros reales previamente revisados, nunca `git add .`. No subir `estado.properties`, la evidencia completa del Disclaimer sin revisión previa, ni llaves/IP del servidor. No abrir PR hasta que los 72 horas y CP-158 estén completos. En el PR vincular `#112`, `CP-157` y `CP-158`.

## 8. Alcances que esta entrega no acredita

No se ha validado desde este entorno el certificado TLS del servidor ANH ni una petición real al API, tampoco el acceso SSH, la versión de Java de Oracle, ni la disponibilidad de T-119. El hecho de compilar y pasar las pruebas simuladas **no** constituye prueba de conectividad real. La medición necesita transcurrir 48+24 horas y su revisión antes de documentar parámetros como confirmados.
