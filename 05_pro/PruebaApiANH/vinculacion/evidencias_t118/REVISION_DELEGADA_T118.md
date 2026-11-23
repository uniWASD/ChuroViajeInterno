# Revisión técnica documental delegada — T-118 / HU-14

**Fecha:** 23/11/2026  
**Delegación:** el solicitante pidió expresamente que se realizara la revisión técnica de coordenadas y correspondencias.  
**Naturaleza:** revisión documental y geoespacial asistida; no se afirma visita presencial ni inspección física.

## Criterio aplicado

- P-31 aceptado por el solicitante: 50 m para la distancia efectiva.
- Identidad: nombre oficial ANH, nombre comercial/alias documentado y dirección oficial.
- Geografía: coordenada de la API, coordenada del catálogo V2, dirección oficial ANH y referencia OSM cuando corresponde.
- Si la coordenada cruda de la API está desplazada, se conserva en `latitud_anh_original` / `longitud_anh_original` y se registra la referencia geográfica corregida en `latitud_anh` / `longitud_anh`, con evidencia separada.
- No se altera la respuesta original de ANH. El vínculo persistido es `anh_id` → surtidor de catálogo.

## Resultado

| ANH ID | Estación ANH | Surtidor V2 | Distancia cruda (m) | Distancia efectiva (m) | Dictamen |
|---:|---|---|---:|---:|---|
| 2339 | TACUARANDI S.R.L. | Tacuarandi | 0,000 | 0,000 | Vincular |
| 2341 | ESTACION DE SERVICIOS LA TERMINAL S.A. | Ex Terminal | 28,050 | 28,050 | Vincular |
| 2364 | SOINTA S.R.L. | Sointa | 0,000 | 0,000 | Vincular |
| 2388 | ESTACION DE SERVICIO PANAMERICANO | Panamericano | 651,191 | 0,000 | Vincular tras corrección geográfica documentada |
| 2390 | AGRUPA S.R.L. | Agrupa | 0,000 | 0,000 | Vincular |
| 2472 | ESTACION DE SERVICIOS EL PORTILLO S.R.L. | El Portillo | 0,000 | 0,000 | Vincular |
| 2627 | ESTACION DE SERVICIO MOTO MENDEZ S.R.L. | Moto Mendez | 221,386 | 0,000 | Vincular tras corrección geográfica documentada |
| 2688 | ESTACION DE SERVICIO SAN JORGE S.R.L. | San Jorge | 0,000 | 0,000 | Vincular |
| 2804 | ESTACION DE SERVICIO DON DANIEL | Don Daniel | 0,000 | 0,000 | Vincular |
| 2974 | PRESIDENTE ARCE | YPFB Parada Chaco | 37,759 | 37,759 | Vincular por alias/dirección documentados |
| 2977 | EL MOLLE S.R.L. | El Molle | 0,000 | 0,000 | Vincular |
| 2386 | ESTACION DE SERVICIOS TARIJA S.R.L. | Surtidor Tarija | 0,000 | 0,000 | Vincular por razón social/dirección documentadas |
| 2642 | ESTACION DE SERVICIOS AVENIDA CIRCUNVALACION S.R.L. | Las Vegas | 6,251 | 6,251 | Vincular por identidad física/dirección documentadas |
| 2911 | LAS AMERICAS | Campesino | 8,214 | 8,214 | Vincular por alias/zona documentados |

## Resolución de los dos desplazamientos de georreferenciación

### ANH 2388 — Panamericano

La respuesta de la API entrega un punto a 651,191 m del punto de catálogo. La ANH publica la dirección actual Av. Jaime Paz Zamora, zona San Gerónimo; una resolución oficial de la propia ANH identifica Panamericano en Av. Jaime Paz Zamora esq. Calle Batallón Colorados. El nodo OSM 1753664990/Mapcarta corresponde al establecimiento en Calle Batallón Colorados y coincide con el punto de catálogo V2. Se mantiene el punto crudo de API en los campos `*_original`; la coordenada efectiva se toma del punto OSM documentado y la distancia revisada queda en 0 m.

Fuentes:
- Directorio ANH: https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40
- Resolución ANH RADPS-ANH-DTJ-0008-2018: https://www.anh.gob.bo/InsideFiles/Actividad/Dj/RA-2018/RADPS-ANH-DTJ-0008-2018.pdf
- OSM/Mapcarta node 1753664990: https://mapcarta.com/N1753664990

### ANH 2627 — Moto Méndez

La respuesta de la API entrega un punto a 221,386 m del punto de catálogo. El directorio ANH publica la dirección carretera Tarija–San Lorenzo km 8, Rancho Sud. El nodo OSM 3207058741/Mapcarta identifica Moto Méndez sobre esa carretera y coincide con la ubicación V2. Se conserva el punto crudo de API y se usa la referencia geográfica del establecimiento documentada por OSM, dejando 0 m de distancia efectiva.

Fuentes:
- Directorio ANH: https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40
- OSM/Mapcarta node 3207058741: https://mapcarta.com/N3207058741

## Equivalencias de nombre comercial

- **2386 Tarija S.R.L. / Surtidor Tarija:** el directorio ANH vigente identifica la razón social en Av. Héroes de la Independencia, zona San Martín; el documento oficial ANH/YPFB 2017 también registra la estación Tarija en esa avenida y barrio. V2 ubica Surtidor Tarija en la misma posición.
- **2911 Las Américas / Campesino:** documento oficial ANH 2015 ubica Las Américas en Av. Panamericana, zona Mercado Campesino; V2 registra Campesino con alias Las Américas y el punto OSM correspondiente.
- **2642 Avenida Circunvalación S.R.L. / Las Vegas:** el directorio y la resolución ANH sitúan la estación en Av. Circunvalación esq. Ayoroa, barrio La Florida; fuentes locales identifican el surtidor comercial Las Vegas en Av. Circunvalación. La separación entre puntos ANH y catálogo es 6,251 m. Se vincula el sitio físico, no se afirma que la marca comercial sea la razón social.
- **2341 La Terminal / Ex Terminal:** el directorio ANH sitúa La Terminal S.A. en Av. Jaime Paz Zamora esq. Arturo Soruco, zona El Tejar; el punto V2 Ex Terminal queda a 28,050 m.
- **2974 Presidente Arce / YPFB Parada Chaco:** el documento oficial ANH/YPFB 2017 y la nota de V2 documentan la equivalencia en la zona Morros Blancos; la separación es 37,759 m.

Fuentes adicionales:
- Directorio ANH: https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40
- ANH/YPFB 2017: https://www.anh.gob.bo/InsideFiles/Inicio/elecciones031217.pdf
- ANH 2015, estaciones Tarija: https://www.anh.gob.bo/InsideFiles/Documentos/Documentos_Id-20-150310-0311-0.pdf
- ANH RA-2019 Circunvalación: https://www.anh.gob.bo/InsideFiles/Actividad/Dj/RA-2019/RADPS-ANH-DTJ-0001-2019.pdf
- Referencia local Las Vegas: https://elguadalupano.com.bo/delincuente-armado-roba-bs-3-000-en-surtidor-de-la-avenida-circunvalacion-a-punta-de-cuchillo/

## Migración y prueba

`V14__vinculos_anh.sql` carga los 14 vínculos. El SQL valida existencia y unicidad de cada nombre e ID, impide sobrescribir un vínculo diferente y aborta si la actualización no afecta exactamente una fila.

`ConsultaAnhVinculosRealesGetIntegracionTest` aplica V14 en Testcontainers/PostGIS, consulta ANH simulado para ID 2977, verifica la persistencia de gasolina y diésel y consulta `GET /surtidores` con JWT.

La prueba debe ejecutarse localmente con Maven/ Docker Desktop; el entorno de preparación no dispone de acceso a Maven Central ni Docker. La evidencia Maven se añade al cierre cuando el usuario ejecute el test.
