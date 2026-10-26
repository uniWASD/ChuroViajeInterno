# ANH Tarija — cliente Python para revisión

Cliente de consulta de la interfaz pública ANH Abastecimiento. Realiza solicitudes **GET** al departamento de Tarija (Bolivia) y obtiene nombre de estación, dirección, coordenadas, gasolina, diésel, saldo en litros y estado de disponibilidad **reportados por ANH**. No es una herramienta oficial de ANH.

## Archivos

- `anh_tarija.py`: cliente principal versión 2.2.0, sin comentarios ni docstrings, con los mismos procedimientos de consulta.
- `requirements.txt`: dependencia `certifi` para validar TLS.
- `ejecutar_windows.bat`: instalación, comprobación sintáctica y consulta desde Windows.
- `README.md`: estas instrucciones.

## Requisitos

- Windows con **Python 3.10 o posterior**, `pip` y conexión a Internet.
- Acceso HTTPS desde el equipo a `https://abastecimiento.prod.anh.gob.bo/`.
- No requiere claves API. Los resultados dependen del funcionamiento y la disponibilidad del servicio externo.

## Ejecución rápida

Descomprimir el ZIP completo en una carpeta y hacer doble clic en `ejecutar_windows.bat`. El archivo instala `certifi` y verifica la sintaxis antes de consultar. Alternativamente, abrir PowerShell en la carpeta y ejecutar:

```powershell
python -m pip install -r requirements.txt
python -m py_compile anh_tarija.py
python .\anh_tarija.py --usar-certifi --mostrar-ubicacion
```

Para consultar un único combustible:

```powershell
python .\anh_tarija.py --usar-certifi --productos 0 --mostrar-ubicacion
python .\anh_tarija.py --usar-certifi --productos 1 --mostrar-ubicacion
```

Código `0`: consulta completa. Código `3`: parcial; código `2`: error. Nunca interprete un fallo de red como cero litros. La consulta estándar trabaja con gasolina (`0`) y diésel (`1`); el código también acepta `2` y `3` si el servicio los devuelve.

## API utilizada

Base: `https://abastecimiento.prod.anh.gob.bo/api/`

| Método GET | Finalidad |
|---|---|
| `/departamentos/?producto=0` | Identificar Tarija en el catálogo. |
| `/departamentos/?producto=1` | Alternativa si el primer catálogo no resuelve Tarija. |
| `/estaciones/?departamento={id}&producto=0` | Estaciones y saldo de gasolina. |
| `/estaciones/?departamento={id}&producto=1` | Estaciones y saldo de diésel. |
| `/estaciones/{id}` | Completar nombre o ubicación cuando falten. |

`{id}` es un parámetro: el cliente utiliza identificadores procedentes de los listados. Las rutas `/estaciones/{id}/estado`, `/Disclaimer` y `/precios/1` se emplean **solo en el modo de diagnóstico**, no son indispensables para el listado.

## Resultados

Cada ejecución crea una carpeta con fecha dentro de `salida_tarija/`, con:

- `datos_tarija.json`: resultado principal, incluidos metadatos de validación y filas estación–combustible.
- `estaciones_tarija.csv`: tabla para hoja de cálculo.
- `estaciones_tarija.geojson`: puntos geográficos cuando se dispone de coordenadas.
- `ejecucion.json` y `respuestas/`: trazabilidad de la consulta.

Los datos abarcan **el departamento de Tarija**, no exclusivamente la ciudad. Un registro corresponde a una combinación estación–combustible. La fecha de última venta se elimina de los datos almacenados; la fecha de última recepción física no se presume ni se inventa. Los saldos y estados son reportados, no disponibilidad física comprobada.

## Verificación HTTPS

La opción `--usar-certifi` conserva la verificación del certificado, del nombre del dominio y de su vigencia, utilizando el conjunto de autoridades certificadoras de `certifi` en vez del almacén predeterminado de Windows. Así se logró una consulta verificada en el equipo que originó el proyecto. **No utilizar `--tls-inseguro-solo-pruebas` para la revisión o producción**.

Para ver opciones: `python .\anh_tarija.py --help`.
