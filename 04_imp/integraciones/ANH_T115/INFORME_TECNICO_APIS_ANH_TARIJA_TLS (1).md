# Informe técnico de integración de las APIs de ANH Abastecimiento para Tarija

## 1. Objeto, alcance y fundamento del informe

El proyecto consulta nombres de estaciones de servicio, tipos de combustible, ubicaciones, cantidades de litros y estados de saldo reportados. La fecha de última venta se excluyó por solicitud del usuario. La última recepción física de combustible continúa sin acreditarse: el cliente mantiene `fecha_ultima_carga_confirmada` en `null` y no la sustituye por una fecha de venta, despacho o actualización. [C1: `normalizar_estaciones`, `excluir_fecha_venta`]

Se distinguen tres tipos de información:

| Fundamento | Qué permite afirmar | Qué no permite afirmar |
|---|---|---|
| **Ejecución compartida por el usuario — E1** | El cliente completó una extracción desde su equipo con verificación TLS y mostró 31 estaciones y 57 registros. | Que esos saldos sigan vigentes cuando se lea este informe o que constituyan todo el inventario físico departamental. |
| **Código de la versión 2.2 — C1** | Qué rutas están implementadas, cuándo se consultan, cómo se transforman los datos y qué controles aplica el programa. | Que todas las rutas opcionales se hayan ejecutado en la consulta compartida. |
| **Documentación técnica externa — T1 a T12** | Cómo funcionan HTTP, JSON, TLS, los certificados, `certifi` y los enlaces de mapas. | Qué combustible tiene una estación concreta ni cuál fue la causa exacta del error previo en Windows. |

El documento anterior de investigación se utiliza como antecedente para los endpoints adicionales y el catálogo de productos. No se convierte retrospectivamente en evidencia de una nueva prueba. Las condiciones institucionales de reutilización, las cuotas y una garantía de disponibilidad del servicio no quedan acreditadas por los materiales revisados. [E3]

## 2. Resultado de la consulta que funcionó

La transcripción muestra esta secuencia:

```text
Successfully installed certifi-2026.10.18
TLS verificado: SI | Almacén: certifi_exclusivo
Consulta completa: 31 estaciones; 57 registros.
```

La versión de `certifi` anterior es la que figura en ese registro; no se presenta como una versión obligatoria para futuras instalaciones. [E1, líneas 58–67]

El recuento de las filas de consola produjo:

| Combustible | Registros estación–combustible | Saldo mayor que cero | Saldo explícito de cero |
|---|---:|---:|---:|
| Gasolina | 31 | 13 | 18 |
| Diésel | 26 | 17 | 9 |
| **Total** | **57** | **30** | **27** |

**Método del recuento:** se identificaron las líneas con la forma `nombre | combustible | cantidad L | estado` y se clasificó su cantidad numérica. Las 57 filas mostraron también dirección y coordenadas. No se contaron los enlaces de mapa como registros adicionales. [E1, líneas 68–295]

El total de 57 representa combinaciones de estación y combustible, no 57 establecimientos. La clave utilizada en el programa es `(estacion_id, producto_id)`. La ausencia de una fila de diésel no equivale a un saldo de cero: significa que esa combinación no está representada en el resultado recibido. [C1: `normalizar_estaciones`]

El programa informó esta carpeta:

```text
C:\Users\USUARIO\Downloads\API_Prueba\anh_tarija_python\salida_tarija\20261018T175107_518113Z
```

El nombre de la carpeta identifica el inicio de la ejecución en UTC. No es la fecha de actualización de todos los saldos ni una fecha de recarga. [E1, línea 296; C1: `main`]

## 3. Qué es la API y cómo la utiliza el proyecto

En esta integración, la **API** es la interfaz de consulta del servicio ANH. Cada **endpoint** es una ruta concreta: por ejemplo, una para departamentos y otra para estaciones. No son proveedores diferentes; son operaciones de una misma base.

```text
https://abastecimiento.prod.anh.gob.bo/api/
```

El cliente solicita datos con `GET`, sin cuerpo y sin operaciones de escritura. Envía cabeceras para pedir JSON e identificar el programa; no contiene un token de ANH, una API key ni un inicio de sesión. Este comportamiento está acreditado por el código y por la ejecución aportada, no por un compromiso contractual de acceso permanente. [C1: `HTTPSOrigenRestringido`, `ClienteANH.get`; E1]

`GET` se utiliza en HTTP para recuperar una representación de un recurso. El código de estado HTTP describe el resultado de la petición; el contenido se interpreta después. Por ello, una respuesta HTTP satisfactoria no sustituye las comprobaciones del JSON ni las reglas del negocio. [T8]

**JSON** es un formato de intercambio con objetos, listas, números, cadenas de texto, booleanos y `null`. El cliente lo convierte en estructuras de Python antes de validar los registros. [T9; C1: `ClienteANH.get`]

El flujo implementado es:

```text
Cliente Python con el almacén certifi
                ↓
Catálogo de departamentos → localizar Tarija por nombre
                ↓
Listado de gasolina + listado de diésel
                ↓
Detalle de estaciones con identidad/ubicación incompleta, cuando corresponde
                ↓
Validación y normalización
                ↓
JSON + CSV + GeoJSON + evidencias de la ejecución
```

El extractor no necesita abrir el mapa de la web ni leer visualmente su interfaz para obtener estos datos. Consume las respuestas JSON mediante las rutas siguientes. [C1]

## 4. APIs utilizadas en la extracción normal

### 4.1. Catálogo de departamentos

**Método y ruta:**

```http
GET /api/departamentos/?producto=0
```

**URL completa:**

```text
https://abastecimiento.prod.anh.gob.bo/api/departamentos/?producto=0
```

Su función dentro del programa es encontrar el identificador de Tarija. La búsqueda normaliza el nombre y exige una única coincidencia con `Tarija`; después valida su `id`. No selecciona un departamento por posición en la lista ni asume silenciosamente el número 6. [C1: `encontrar_tarija`]

Cuando el primer catálogo no permite resolverlo por un error distinto de TLS, existe esta alternativa:

```text
https://abastecimiento.prod.anh.gob.bo/api/departamentos/?producto=1
```

La versión 2.2 detiene la extracción ante un error de certificado durante la consulta del catálogo. No cambia automáticamente al modo inseguro ni intenta el segundo producto como supuesto remedio de TLS. Si el primer catálogo funciona, no necesita solicitar el segundo. [C1: `obtener_datos_tarija`]

El antecedente describe también cantidades de estaciones por producto en este servicio, pero el cliente actual utiliza el catálogo para resolver el departamento; no depende de esos totales. [E3, sección 2; C1]

### 4.2. Listado de estaciones y gasolina

**Método y plantilla de URL:**

```http
GET /api/estaciones/?departamento={id_tarija}&producto=0
```

```text
https://abastecimiento.prod.anh.gob.bo/api/estaciones/?departamento={id_tarija}&producto=0
```

`{id_tarija}` debe sustituirse por el identificador obtenido del catálogo. Este listado es la fuente principal de identidad, ubicación y saldo para gasolina. El normalizador admite tanto campos de producto en el registro de la estación como un arreglo `productos`, del que selecciona el producto solicitado. [C1: `obtener_datos_tarija`, `normalizar_estaciones`]

Cuando la fuente los proporciona, el cliente interpreta `id`, `nombre`, `departamento_id`, `direccion`, `zona`, `lat`, `lng`, `producto_id`, `producto_nombre`, `saldo_litros`, `saldo_estado`, `con_venta` y `updated_at`. Los campos opcionales ausentes no se inventan. [C1]

### 4.3. Listado de estaciones y diésel

**Método y plantilla de URL:**

```http
GET /api/estaciones/?departamento={id_tarija}&producto=1
```

```text
https://abastecimiento.prod.anh.gob.bo/api/estaciones/?departamento={id_tarija}&producto=1
```

Tiene la misma función, pero solicita diésel. En la ejecución documentada, los dos listados terminaron correctamente y aportaron conjuntamente las 57 filas. Sus peticiones son secuenciales; el resultado no constituye una fotografía transaccional de todos los tanques tomada en un único instante. [E1; C1: `obtener_datos_tarija`]

**Ejemplos con el identificador candidato 6:**

```text
https://abastecimiento.prod.anh.gob.bo/api/estaciones/?departamento=6&producto=0
https://abastecimiento.prod.anh.gob.bo/api/estaciones/?departamento=6&producto=1
```

Estos ejemplos solo deben utilizarse si el catálogo confirma `id=6` para Tarija. La consola compartida no muestra el identificador devuelto: debe consultarse en `datos_tarija.json`, `fuente_catalogo` y las evidencias de peticiones. El valor 6 también existe como candidato diagnóstico en el código, pero no sustituye la validación del catálogo en la extracción normal. [C1: `probar_apis`, `encontrar_tarija`]

### 4.4. Detalle de una estación: consulta condicional

```http
GET /api/estaciones/{id_estacion}
```

```text
https://abastecimiento.prod.anh.gob.bo/api/estaciones/{id_estacion}
```

El identificador procede del listado; no se adivina ni se reemplaza por el nombre comercial. El cliente solicita este detalle cuando falta el nombre, la dirección o una ubicación suficiente para generar el mapa. El límite predeterminado es de cinco estaciones y puede configurarse entre cero y veinte. [C1: `completar_identidad`, `main`]

Antes de incorporar información, comprueba que el detalle corresponda a la estación y al departamento solicitados. Solo completa identidad y ubicación. No utiliza un saldo del detalle para sobrescribir el saldo del listado, evitando mezclar innecesariamente observaciones de momentos distintos. [C1: `validar_detalle`, `completar_identidad`]

**Límite de la evidencia:** la consola final no permite determinar si esta ruta llegó a ejecutarse. Su utilización es condicional; para acreditarla en una ejecución concreta debe revisarse `respuestas/peticiones.json`.

### 4.5. Parámetros y número de solicitudes

| Parámetro | Interpretación en este cliente |
|---|---|
| `producto=0` | Gasolina. |
| `producto=1` | Diésel. |
| `departamento={id_tarija}` | Identificador resuelto mediante el catálogo. |
| `{id_estacion}` | Identificador de una estación previamente obtenida. |

El código admite además `2` y `3`, etiquetados como Gasolina Premium y Diésel ULS según el antecedente. No forman parte de la consulta predeterminada ni quedaron acreditados en la ejecución de 57 registros. [C1: `PRODUCTOS`; E3, líneas 40–45]

Sin errores, redirecciones ni necesidad de detalles, consultar gasolina y diésel requiere **tres peticiones de aplicación**: un catálogo y dos listados. El número aumenta cuando se utiliza el catálogo alternativo, se completan estaciones o se ejecutan reintentos. No son 57 solicitudes obligatorias para obtener 57 registros. [C1]

## 5. Rutas de diagnóstico y servicios no integrados

Con `--probar-apis`, el programa puede consultar también:

| Método y ruta | Finalidad descrita | Utilización actual |
|---|---|---|
| `GET /api/estaciones/{id_estacion}/estado` | Estado de los productos de una estación. | Diagnóstico; valida únicamente la envoltura, no un contrato interno completo. |
| `GET /api/Disclaimer` | Advertencia del servicio sobre sus datos. | Diagnóstico auxiliar; no se incorpora automáticamente a la ficha de una estación. |
| `GET /api/precios/1` | Información publicada de productos/precios. | Diagnóstico auxiliar; los precios no forman parte del extractor solicitado. |

La función diagnóstica obtiene identificadores para las pruebas de detalle y estado solo cuando dispone de catálogo y listados válidos. La ejecución normal compartida no acredita que estas tres rutas auxiliares hayan respondido correctamente. [C1: `probar_apis`; E3, sección 2]

Los canales `stream`, las rutas de cisternas, las notificaciones, el portal de YPFB y los servicios institucionales de AGETIC **no son dependencias del extractor de la versión 2.2**. No se utiliza un seguimiento de cisterna ni un despacho de YPFB para inventar una fecha de recepción. [C1; E3]

### El enlace de Google Maps no es otra API de combustible

El cliente construye localmente este formato con las coordenadas recibidas:

```text
https://www.google.com/maps/search/?api=1&query={latitud},{longitud}
```

No consulta Google para completar direcciones ni extraer inventario. El enlace abre el mapa cuando alguien lo utiliza. Google documenta que Maps URLs no requiere una API key; esto no debe extrapolarse a todas las APIs de Google Maps Platform. [C1: `enlace_mapa`; T10]

## 6. Diccionario e interpretación de los datos

### 6.1. Identidad, ubicación y combustible

| Campo de salida | Origen | Significado y tratamiento |
|---|---|---|
| `estacion_id` | `id` | Identificador de estación; se exige un entero válido. |
| `nombre_estacion` | `nombre` | Nombre reportado; puede completarse desde el detalle. |
| `departamento_id` | Catálogo y validación del registro | Departamento solicitado. |
| `verificacion_departamento` | Generado por el cliente | Distingue `campo_y_filtro` de `solo_filtro_api`. |
| `direccion`, `zona` | Campos de la estación | Ubicación textual; se conserva lo disponible. |
| `latitud`, `longitud` | `lat`, `lng` | Coordenadas numéricas; valores no válidos quedan nulos. |
| `url_mapa` | Generado a partir de coordenadas | Enlace de visualización; no es una ubicación obtenida de Google. |
| `producto_id` | Producto solicitado y validado | Identifica el combustible de la fila. |
| `tipo_combustible` | `producto_nombre` o catálogo local | Nombre del producto. |
| `origen_nombre_combustible` | Generado por el cliente | Indica si el nombre vino de la API o del código del antecedente. |

La validación geográfica comprueba rangos de latitud y longitud y rechaza el par `0,0`. **No ejecuta una prueba espacial contra un polígono oficial de Tarija.** Si falta `departamento_id` en una fila, el alcance depende del filtro de la API y queda advertido. [C1: `normalizar_estaciones`]

### 6.2. Cantidad, disponibilidad y fechas

| Campo | Interpretación correcta |
|---|---|
| `cantidad_litros` | Saldo reportado, tomado de `saldo_litros`; no es el volumen de la última recarga. |
| `disponibilidad_reportada` | Categoría textual de `saldo_estado`, por ejemplo `alto`, `medio` o `bajo`. |
| `saldo_positivo_reportado` | Resultado de `cantidad_litros > 0`; permanece nulo si la cantidad es desconocida. |
| `venta_reportada` | Indicador booleano de `con_venta`; no contiene la fecha de última venta. |
| `atencion_al_publico_verificada` | Permanece en `false`: el cliente no verifica atención presencial. No significa que el local esté cerrado. |
| `fecha_actualizacion_reportada` | `updated_at` conservado; no se le asigna una zona horaria que la fuente no especifique. |
| `observado_en_utc` | Hora de observación del cliente; no equivale a la hora de actualización de ANH. |
| `fecha_ultima_carga_confirmada` | `null`: no existe evidencia suficiente de recepción física en el contrato utilizado. |
| `tls_verificado` | Estado de validación del transporte; no certifica el inventario físico. |
| `uso_datos`, `fuente_url`, `advertencias` | Procedencia y limitaciones para interpretar cada registro. |

El estado `bajo` no tiene un umbral numérico definido por el cliente. En el registro compartido, SAN JORGE figura con 37 litros de gasolina y con cero litros de diésel; ambos aparecen como `bajo`. Presentar ambos casos automáticamente como «sin combustible» perdería información. [E1, líneas 200–207; C1]

Tampoco deben confundirse **cero**, **desconocido** y **no consultado**. El código invalida cantidades negativas en lugar de convertirlas en cero. Cuando una consulta falla, conserva el fallo; no fabrica un inventario vacío. [C1]

### 6.3. Ejemplo documental

El siguiente objeto es una representación parcial elaborada a partir de la consola compartida. **No es una copia del JSON bruto de ANH ni un dato obtenido de nuevo.** Se omite el identificador porque la consola no lo mostró. [E1, líneas 84–87]

```json
{
  "nombre_estacion": "EL MOLLE S.R.L.",
  "tipo_combustible": "Gasolina",
  "cantidad_litros": 26194,
  "disponibilidad_reportada": "alto",
  "direccion": "MENDEZ - CARRETERA A SAN LORENZO KM.11 ZONA: EL RANCHO",
  "latitud": -21.4530516,
  "longitud": -64.7533344
}
```

## 7. Certificados, HTTPS y TLS: qué son y por qué importan

### 7.1. La conexión segura no depende únicamente de que la URL diga HTTPS

TLS proporciona un canal con autenticación, confidencialidad e integridad. El certificado participa en la autenticación del servidor; el protocolo negocia el material criptográfico con el que se protege el tráfico. El certificado no es, por sí solo, el mecanismo que cifra todos los datos. [T1]

Aplicado al proyecto: una respuesta podría conservar el aspecto de un JSON correcto y, aun así, haber sido alterada por un intermediario si no se autenticó al servidor. Un escenario hipotético sería modificar una coordenada o presentar un saldo de cero como 20.000 litros. Que los datos sean públicos no elimina el interés de proteger su procedencia e integridad. Esta es una explicación del riesgo, no una afirmación de que haya ocurrido un ataque. [T1; análisis aplicado]

### 7.2. Qué contiene un certificado

Un certificado X.509 vincula una clave pública con una identidad e incluye emisor, número de serie, período de vigencia, extensiones y una firma. Los campos `notBefore` y `notAfter` delimitan su validez temporal. La validación se apoya en una cadena que termina en una autoridad de confianza. [T2]

```text
Certificado del servidor
        ↓ firmado por
Autoridad certificadora intermedia
        ↓ respaldada por
Ancla de confianza del almacén del cliente
```

Los nombres autorizados del servidor se expresan mediante identificadores del certificado, particularmente la extensión `subjectAltName` para nombres DNS. El cliente debe contrastarlos con el nombre al que intenta conectarse; un certificado confiable para otro dominio no basta. [T3]

Para este proyecto, el nombre esperado es:

```text
abastecimiento.prod.anh.gob.bo
```

El certificado del servidor **no es una API key, una contraseña del usuario ni una autorización institucional para reutilizar datos**. El código no utiliza autenticación con certificado de cliente ni requiere la clave privada del servidor. [C1; distinción conceptual]

### 7.3. Qué verifica la configuración utilizada

La función `crear_contexto_tls` utiliza el propósito normal de autenticación de servidor, mantiene las verificaciones del contexto y fija TLS 1.2 como versión mínima. En modo `--usar-certifi` no establece `CERT_NONE`, no desactiva `check_hostname` y no rebaja `verify_flags`. Estos son controles observables en el código. [C1: líneas 341–372]

La documentación de Python explica que `create_default_context` carga el almacén indicado mediante `cafile`; cuando no se especifica uno, puede cargar los certificados predeterminados. `PROTOCOL_TLS_CLIENT` habilita la exigencia de certificado y la comprobación del nombre. [T4]

**Lo que TLS no demuestra:** que el combustible esté físicamente disponible, que la estación esté atendiendo, que la información sea reciente o que no exista un error en la propia fuente. Además, el código no implementa una política adicional de revocación mediante OCSP o listas CRL. No debe describirse su validación como una auditoría criptográfica integral. [C1; T4]

## 8. Validación del JSON, protección de los datos y archivos de salida

### 8.1. Controles que ya implementa el cliente

El transporte exige HTTP 200, una cabecera de contenido JSON y un cuerpo de hasta 10 MiB. Después, la validación exige `decCodigo` entero igual a `1` y un `oResultado` del tipo esperado. No trata `decCodigo=-1` como una lista vacía por defecto. [C1: `ClienteANH.get`, `resultado`]

La normalización detecta productos distintos de los solicitados, identificadores incompatibles, duplicados estación–producto y algunos indicadores explícitos de paginación no soportada. Frente a estos últimos, se detiene en lugar de publicar un listado que sabe truncado. Esto no acredita detección de cualquier esquema de paginación imaginable. [C1]

Las respuestas se filtran recursivamente para eliminar la fecha de última venta y las variantes reconocidas por el código. Los JSON archivados **no son copias byte a byte de la respuesta original**. Se guarda una huella SHA-256 del cuerpo recibido antes del filtrado; esa huella no se reproduce calculándola sobre el JSON ya filtrado. [C1: `excluir_fecha_venta`, `ClienteANH.get`]

El contexto HTTPS se restringe al origen de ANH y a peticiones `GET` sin cuerpo. En modo verificado, las redirecciones fuera del origen o hacia HTTP se bloquean; en modo inseguro se bloquean todas. No se realizan operaciones de escritura sobre ANH. [C1]

### 8.2. Archivos generados

```text
salida_tarija/
└── <identificador_UTC_de_ejecucion>/
    ├── datos_tarija.json
    ├── estaciones_tarija.csv
    ├── estaciones_tarija.geojson
    ├── ejecucion.json
    └── respuestas/
        ├── peticiones.json
        └── respuesta_###_sin_fecha_venta.json
```

| Archivo | Utilidad |
|---|---|
| `datos_tarija.json` | Resultado normalizado, metadatos, estado completo/parcial y advertencias. |
| `estaciones_tarija.csv` | Revisión tabular y exportación de los campos seleccionados. |
| `estaciones_tarija.geojson` | Puntos geográficos de los registros con ubicación utilizable. |
| `ejecucion.json` | Resumen de la ejecución, errores, configuración y código de salida. |
| `respuestas/peticiones.json` | URLs realmente solicitadas, intentos, estados y tiempos; permite verificar si se consultaron detalles. |
| `respuesta_###_sin_fecha_venta.json` | JSON recibido y filtrado; evidencia para revisar el contrato. |

GeoJSON utiliza posiciones en orden `[longitud, latitud]`. La salida actual produce un punto por registro estación–combustible, por lo que gasolina y diésel de una misma estación pueden compartir coordenadas. Para mostrar un único marcador por establecimiento conviene agrupar por `estacion_id`. [T11; C1: `exportar`; recomendación de presentación]

Una extracción completamente fallida no genera los tres archivos de inventario como si fueran un resultado vacío. Si solo falla un combustible, se exporta el resultado disponible marcado como parcial. El diagnóstico TLS y el diagnóstico de APIs guardan sus propios informes y no sustituyen una extracción de inventario. [C1: `main`]

## 9. Ejecución e integración reproducible

### 9.1. Comando que funcionó en Windows

```powershell
Set-Location "$env:USERPROFILE\Downloads\API_Prueba\anh_tarija_python"
python .\anh_tarija_v2_2.py --usar-certifi --mostrar-ubicacion
```

El archivo debe conservar ese nombre o ajustarse en el comando. No hace falta reinstalar `certifi` en cada consulta. Para instalarlo o actualizarlo deliberadamente con el mismo intérprete:

```powershell
python -m pip install --upgrade certifi
```

Para gasolina o diésel por separado:

```powershell
python .\anh_tarija_v2_2.py --usar-certifi --productos 0 --mostrar-ubicacion
python .\anh_tarija_v2_2.py --usar-certifi --productos 1 --mostrar-ubicacion
```

Para diagnóstico, sin omitir TLS:

```powershell
python .\anh_tarija_v2_2.py --usar-certifi --diagnosticar-tls --reintentos 0
python .\anh_tarija_v2_2.py --usar-certifi --probar-apis --rondas 2 --reintentos 0
```

El primer diagnóstico hace una consulta verificada y una inspección separada del certificado sin petición HTTP. La inspección queda identificada como no autenticada y no genera inventario. El segundo revisa rutas y contratos dentro del alcance programado. [C1]

### 9.2. Utilización desde otro archivo Python

Este ejemplo usa el cliente existente, no implementa una API diferente. El archivo `anh_tarija_v2_2.py` debe estar importable, por ejemplo en la misma carpeta del ejemplo.

```python
from anh_tarija_v2_2 import ClienteANH, ErrorANH, obtener_datos_tarija


def main() -> int:
    try:
        cliente = ClienteANH(usar_certifi=True)
        resultado = obtener_datos_tarija(
            productos=(0, 1),
            cliente=cliente,
            max_detalles=5,
        )
    except (ErrorANH, ValueError, OSError) as error:
        print(f"No se pudo completar la consulta: {error}")
        return 2

    if resultado.get("tls_verificado") is not True:
        print("No se utilizará un resultado sin transporte verificado.")
        return 2

    print(f"Estado: {resultado['estado']}")
    for estacion in resultado["registros"]:
        litros = estacion["cantidad_litros"]
        cantidad = "no reportada" if litros is None else f"{litros} L"
        print(
            f"{estacion['nombre_estacion']} | "
            f"{estacion['tipo_combustible']} | {cantidad} | "
            f"{estacion['direccion']}"
        )

    return 0 if resultado["estado"] == "completa" else 3


if __name__ == "__main__":
    raise SystemExit(main())
```

El ejemplo devuelve datos en memoria y muestra un resumen; no invoca `exportar`. El comando de consola normal sí realiza la exportación. [C1; ejemplo de integración elaborado para este informe]

### 9.3. Códigos de salida y límites operativos

En el **modo de extracción**, `0` indica consulta completa; `3`, parcial; `2`, error; y `4`, resultado obtenido en modo inseguro de pruebas. Los modos diagnósticos tienen su propia lógica: por ejemplo, un diagnóstico TLS satisfactorio no demuestra que todos los listados funcionen. [C1: `main`]

El cliente tiene un tiempo de espera predeterminado de 12 segundos para operaciones de socket, una pausa entre peticiones de un segundo y un reintento configurado. El tiempo total puede ser mayor por resolución DNS, distintas operaciones de red o reintentos. Maneja `Retry-After` y limita reintentos, pero no implementa un recolector periódico ni una caché compartida. Estos valores son decisiones del programa, no cuotas publicadas por ANH. [C1]

## 10. Mantenimiento, riesgos y recomendaciones para el proyecto

**Interpretación territorial.** El resultado incluye ubicaciones en Bermejo y Yacuiba. Para limitar el proyecto a la ciudad de Tarija hace falta un criterio adicional verificable, preferentemente territorial; no basta con presentar todo el departamento bajo una etiqueta urbana. [E1, líneas 72–98]

**Presentación de disponibilidad.** Propongo mostrar «Saldo reportado: … litros», «Estado ANH: …» y «Última consulta: …». Ante un fallo, conservar el último resultado válido con una advertencia de antigüedad, en lugar de mostrar cero. Esta conservación es una propuesta para la aplicación; el script actual no mantiene automáticamente una última versión vigente compartida.

**Actualización.** Centralizar las consultas en un backend evita que cada usuario de la interfaz genere su propia extracción. La frecuencia debe acordarse con las condiciones del proveedor y ajustarse a las necesidades del proyecto; no hay evidencia aquí de una frecuencia oficial permitida. La pausa de un segundo del cliente no constituye autorización de sondeo continuo.

**CORS y navegador.** CORS es un mecanismo aplicado por navegadores para controlar el acceso de scripts a respuestas de otro origen. No sustituye TLS ni es una API key. El cliente Python no depende de que el navegador permita leer ANH. La observación antigua de `Access-Control-Allow-Origin: *` no acredita la configuración presente del servicio. [T12; E3]

**Trazabilidad.** Registrar configuración, URLs, tiempos, advertencias y versión de dependencias permite comparar ejecuciones. Una huella SHA-256 comprueba coincidencia con una referencia conocida; no convierte por sí sola un archivo en confiable. En el caso de la descarga, la comparación se realizó contra el valor esperado del script entregado. [E1, líneas 32–36 y 55–56; análisis del procedimiento]

**Diagnóstico por capas.** Un error DNS impide resolver el destino; un fallo TLS impide completar la validación de la conexión; un error HTTP puede ocurrir después de una conexión TLS válida; un cambio del JSON puede impedir interpretar una respuesta HTTP correcta. Ninguno de estos casos demuestra que una estación tenga cero litros. El programa mantiene categorías separadas para estas situaciones. [C1]

**Estado de la solución.** Existe una extracción funcional demostrada desde el equipo del usuario. No equivale a una certificación de servicio permanente, de cobertura completa de todas las estaciones o de última recepción física. La configuración que debe conservarse es la que funcionó: cliente 2.2 con `--usar-certifi`, sin modo inseguro. [E1; C1]

## 11. Fuentes
### 11.1. Documentación técnica externa

Fuentes primarias o documentación del mantenedor, consultadas el 18 de octubre de 2026. Sustentan las explicaciones técnicas generales, no los saldos de combustible.

**T1. IETF — RFC 8446, The Transport Layer Security (TLS) Protocol Version 1.3.** Sección 1: autenticación, confidencialidad, integridad y negociación del canal.
https://datatracker.ietf.org/doc/html/rfc8446#section-1

**T2. IETF — RFC 5280, Internet X.509 Public Key Infrastructure Certificate and Certificate Revocation List (CRL) Profile.** Estructura del certificado, vigencia y validación de rutas.
https://datatracker.ietf.org/doc/html/rfc5280

**T3. IETF — RFC 9525, Service Identity in TLS.** Identificadores de servicio y comprobación de nombres.
https://datatracker.ietf.org/doc/html/rfc9525

**T4. Python Software Foundation — Documentación de `ssl` para Python 3.13.** `create_default_context`, `PROTOCOL_TLS_CLIENT`, `CERT_NONE`, `CERT_REQUIRED` y controles de verificación.
https://docs.python.org/3.13/library/ssl.html

**T5. Mantenedores de certifi — Repositorio oficial `certifi/python-certifi`.** Origen del almacén y utilización de `certifi.where()`.
https://github.com/certifi/python-certifi

**T6. OpenSSL — `openssl-verification-options`.** Construcción de cadenas, preferencia del almacén de confianza y validación temporal.
https://docs.openssl.org/3.0/man1/openssl-verification-options/

**T7. OpenSSL — `X509_STORE_CTX_get_error`.** Significado de `X509_V_ERR_CERT_HAS_EXPIRED`.
https://docs.openssl.org/3.0/man3/X509_STORE_CTX_get_error/

**T8. IETF — RFC 9110, HTTP Semantics.** Métodos, respuestas y semántica de HTTP.
https://datatracker.ietf.org/doc/html/rfc9110

**T9. IETF — RFC 8259, The JavaScript Object Notation (JSON) Data Interchange Format.** Tipos y estructura del formato JSON.
https://datatracker.ietf.org/doc/html/rfc8259

**T10. Google for Developers — Maps URLs: Get Started.** Enlaces universales de mapa y ausencia de requisito de API key para Maps URLs.
https://developers.google.com/maps/documentation/urls/get-started

**T11. IETF — RFC 7946, The GeoJSON Format.** Representación de posiciones y geometrías.
https://datatracker.ietf.org/doc/html/rfc7946

**T12. MDN Web Docs — Cross-Origin Resource Sharing (CORS).** Control de acceso entre orígenes en navegadores.
https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS

---

**Síntesis técnica:** el proyecto utiliza el catálogo de departamentos y los listados de estaciones de ANH como fuentes principales, con detalle de estación solo cuando hace falta completar identidad o ubicación. `certifi` interviene en la confianza TLS, no en los datos de combustible. La consulta verificada desde el equipo del usuario está acreditada; los saldos siguen siendo reportes de la fuente y la última recepción física permanece sin confirmar.
