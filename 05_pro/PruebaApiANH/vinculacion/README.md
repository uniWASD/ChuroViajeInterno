# Vinculación ANH ↔ surtidores — T-118 / HU-14 (V5)

La migración V14 asigna los identificadores ANH a los 14 surtidores del catálogo V2. La revisión se realizó documental/geoespacialmente por delegación expresa del solicitante el 23/11/2026; no se afirma visita presencial.

## Evidencia

En `evidencias_t118/` están:

- `pares_para_revision_final_delegada.csv`: 14 pares, `revisado=si`, comentario y fuentes.
- `estaciones_tarija_fuente_anh_snapshot_14.csv`: snapshot de 14 estaciones reconstruido desde la matriz técnica. Para la comprobación más fuerte, usar el CSV original de 31 estaciones que dejó `anh_tarija.py`.
- `REVISION_DELEGADA_T118.md`: justificación de cada correspondencia y correcciones geográficas.
- `Matriz_revision_delegada_T118.xlsx`: matriz legible para auditoría.
- `estado_cierre/ESTADO_CIERRE.md`: estado automático del cierre.

## P-31 y correcciones geográficas

P-31 = **50 m** de distancia efectiva. Dos coordenadas crudas de la API estaban desplazadas:

- ANH 2388 Panamericano: 651,191 m de distancia cruda. Se conserva el punto original y se documenta la referencia OSM 1753664990 junto con la dirección oficial ANH; el punto efectivo V2 queda en 0 m.
- ANH 2627 Moto Méndez: 221,386 m de distancia cruda. Se conserva el punto original y se documenta la referencia OSM 3207058741 junto con la dirección oficial ANH; el punto efectivo V2 queda en 0 m.

Las equivalencias Tarija S.R.L./Surtidor Tarija, Las Américas/Campesino, Avenida Circunvalación/Las Vegas, La Terminal/Ex Terminal y Presidente Arce/YPFB Parada Chaco se justifican en el expediente. La asociación Las Vegas es por el sitio físico; no afirma que la marca sea la razón social.

## V14

`04_imp/backend_java/src/main/resources/db/migration/V14__vinculos_anh.sql` carga los 14 vínculos. Cada asignación verifica que el surtidor exista, que el ID ANH no pertenezca a otro surtidor, que no se reemplace un vínculo diferente y que el `UPDATE` afecte exactamente una fila.

Para regenerar una copia de V14 contra el CSV original de 31 estaciones (desde `05_pro/PruebaApiANH`):

```powershell
$CsvAnh = '.\salida_tarija\20261123T172823_989807Z\estaciones_tarija.csv'
$Pares = '.\vinculacion\evidencias_t118\pares_para_revision_final_delegada.csv'
$Preview = Join-Path $env:TEMP 'T118_V14_preview'
New-Item -ItemType Directory -Force -Path $Preview | Out-Null
python .\vinculacion\proponer_vinculos.py migracion $Pares --fuente-anh $CsvAnh `
  --revisado-por 'Revisión técnica documental delegada por el solicitante' --version 14 --salida $Preview
```

## Pruebas

Pruebas Python (38 tests):

```powershell
python -m unittest discover -s .\vinculacion -v
```

Pruebas Java/PostGIS y E2E (desde `04_imp/backend_java`):

```powershell
.\mvnw.cmd -B '-Dtest=MigracionesPostgisTest,ConsultaAnhVinculosRealesGetIntegracionTest' test
.\mvnw.cmd -B clean test
```

`ConsultaAnhVinculosRealesGetIntegracionTest` aplica V14 en Testcontainers/PostGIS, consulta un servidor ANH falso para ID 2977 (El Molle), persiste gasolina y diésel y valida que `GET /surtidores` los exponga con JWT. Es una prueba E2E de integración; no es una consulta de producción.

## Cierre automatizado

La verificación exige tanto el log de la prueba E2E como el log de la suite completa. Desde `05_pro/PruebaApiANH`:

```powershell
$CsvAnh = '.\salida_tarija\20261123T172823_989807Z\estaciones_tarija.csv'
$Pares = '.\vinculacion\evidencias_t118\pares_para_revision_final_delegada.csv'
python .\vinculacion\verificar_cierre.py --pares $Pares --fuente-anh $CsvAnh `
  --evidencia-maven '.\vinculacion\evidencias_t118\ejecuciones\T118_V14_E2E.log' `
  --evidencia-suite '.\vinculacion\evidencias_t118\ejecuciones\T118_V14_FULL.log' --estricto
```

## Un solo comando para ejecutar todo

Desde la raíz del repositorio:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Finalizar-HU14-T118.ps1
```

El script ejecuta las 38 pruebas Python, las pruebas dirigidas de V14/E2E, la suite Java completa y el verificador de cierre. Conserva ambos logs en `evidencias_t118/ejecuciones/`, fuera de `target`, para que `clean test` no los elimine.
