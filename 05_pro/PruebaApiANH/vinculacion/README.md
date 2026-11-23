# Vincular las estaciones de ANH con los surtidores del catálogo (T-118, #113)

ANH devuelve todas las estaciones del departamento de Tarija. Para que la consulta del backend (CU-09) sepa a qué surtidor pertenece cada dato, cada surtidor del catálogo lleva el identificador de su estación en ANH (`surtidor.anh_id`, migración V12). Este script propone esos vínculos; **una persona del equipo los revisa a mano** antes de que entren en una migración (RN-09.10).

Solo usa la biblioteca estándar de Python 3.10 o posterior. No llama a ANH: trabaja con el CSV que deja el cliente de prueba de la carpeta de arriba.

## 1. Sacar las estaciones de ANH

Desde `05_pro/PruebaApiANH`, con el cliente de prueba (ver su `README.md`):

```powershell
python -m pip install -r requirements.txt
python .\anh_tarija.py --usar-certifi
```

Deja una carpeta nueva en `salida_tarija/` con `estaciones_tarija.csv`.

## 2. Proponer los pares

```powershell
python .\vinculacion\proponer_vinculos.py proponer .\salida_tarija\<carpeta>\estaciones_tarija.csv
```

El script:

- deja afuera las estaciones que no caen en la mancha urbana (`04_imp/datos/mancha_urbana_tarija.geojson`), como Bermejo o Yacuiba (RN-09.8);
- para cada una de las demás, busca en el catálogo de V2 un surtidor a menos de 1 km cuyo nombre o alias coincida con el de ANH;
- si ninguno coincide, propone el surtidor más cercano, siempre que esté a menos de 300 m.

Esas distancias solo sirven para buscar: **no son P-31**. P-31 sale de los pares que el equipo confirme.

Escribe una carpeta `vinculos_<fecha>` con tres archivos, separados por punto y coma para abrirlos en Excel:

| Archivo | Qué tiene |
| --- | --- |
| `pares_propuestos.csv` | Una fila por estación de ANH dentro de la mancha, con el surtidor propuesto, la distancia, si coincide el nombre y una de estas propuestas: **vincular** (coincide el nombre), **revisar** (está cerca, pero el nombre no coincide, o el surtidor se propone para dos estaciones) o **candidata** (no hay ningún surtidor del catálogo cerca; el equipo decide si entra al catálogo, RN-09.9). La columna `nota_v2` dice si los comentarios de V2 ya nombraban un id de ANH para ese surtidor |
| `surtidores_sin_estacion.csv` | Los surtidores del catálogo que no quedaron en ningún par, para revisar sus nombres y alias (#41) |
| `fuera_de_la_mancha.csv` | Las estaciones que se dejaron afuera, solo con su id y su nombre |

## 3. Revisar

Abre `pares_propuestos.csv` y escribe **si** en la columna `revisado` de cada par que confirmes. Compara el nombre y la ubicación en un mapa, por ejemplo pegando `latitud_anh, longitud_anh` en Google Maps. Usa `comentario` para lo que haya que contar.

- Un par equivocado se deja vacío, o se corrige el nombre del surtidor antes de poner «si».
- Un surtidor no puede quedar con dos estaciones, ni una estación con dos surtidores: el paso 4 lo comprueba.

## 4. Generar la migración

```powershell
python .\vinculacion\proponer_vinculos.py migracion .\vinculos_<fecha>\pares_propuestos.csv --revisado-por "Nombre" --version 13
```

Escribe `V13__vinculos_anh.sql` en `04_imp/backend_java/src/main/resources/db/migration/`, solo con los pares que tienen «si». Además muestra la **P-31 sugerida**: la mayor distancia entre los pares confirmados, redondeada hacia arriba a 50 m. Esa distancia va a `08_decisiones_parametros.md` como Confirmada.

Un par que se confirma aunque pase P-31 lleva en `comentario` un texto que empieza con «Excepción»: queda anotado en la migración y no cuenta para la P-31 sugerida.

La migración falla, en vez de seguir a medias, si un nombre del catálogo cambió y algún vínculo no encontró su surtidor. Si ya existe una migración con ese número, el script no la sobrescribe: usa el siguiente número libre.

## Pruebas

```powershell
python -m unittest discover -s 05_pro/PruebaApiANH/vinculacion
```

## La revisión del 23/11/2026

`pares_revisados_20261123.csv` guarda la revisión que generó `V13__vinculos_anh.sql`:

- **Las estaciones:** ANH devolvió 31 estaciones de Tarija. 17 quedan fuera de la mancha urbana, todas a más de 38 km de la plaza, y 14 dentro.
- **Los pares:** las 14 de adentro tienen su surtidor en el catálogo. 11 coinciden por nombre. Las otras 3 (Surtidor Tarija, Las Vegas y Campesino) están a menos de 10 m de su surtidor y las respaldan los comentarios de V2.
- **Candidatas y catálogo:** no hay candidatas nuevas, ni surtidores del catálogo sin estación.
- **P-31:** 250 m, porque el par más lejano está a 221 m (Moto Mendez). Panamericano, a 651 m, queda como excepción: ANH tiene mal su punto.

Revisó Marcelo Quiroga.
