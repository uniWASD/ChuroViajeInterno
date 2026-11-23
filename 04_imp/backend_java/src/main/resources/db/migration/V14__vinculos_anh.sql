-- T-118 / RN-09.10: vínculos aprobados en revisión técnica documental delegada por el solicitante.
-- Revisión declarada por Revisión técnica documental asistida por ChatGPT, por delegación del solicitante el 2026-11-23.
-- La revisión es documental/geoespacial; no representa visita presencial ni inspección física.
-- Las coordenadas ANH originales y las correcciones justificadas constan en el CSV y expediente T-118.
-- Flyway ejecuta la migración como una transacción: cualquier error revierte el lote.
DO $vinculos_anh$
DECLARE
    filas_modificadas integer;
BEGIN
    -- Agrupa: ANH 2390, distancia efectiva 0.000 m
    -- Evidencia: Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el directorio/documentación ANH, catálogo V2 y fuentes indicadas. No se realizó visita presencial; el dictamen es documental y geoespacial. Directorio ANH vigente identifica Agrupa S.R.L. en Av. Panamericana esq. Circunvalación (https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40). Nombre y punto V2 coincidentes.
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Agrupa') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'Agrupa';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Agrupa' AND anh_id IS NOT NULL AND anh_id <> 2390) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'Agrupa';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2390 AND nombre <> 'Agrupa') THEN
        RAISE EXCEPTION 'El identificador ANH 2390 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2390 WHERE nombre = 'Agrupa' AND (anh_id IS NULL OR anh_id = 2390);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'Agrupa';
    END IF;
    -- Campesino: ANH 2911, distancia efectiva 8.214 m
    -- Evidencia: Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el directorio/documentación ANH, catálogo V2 y fuentes indicadas. No se realizó visita presencial; el dictamen es documental y geoespacial. Documento oficial ANH 2015 identifica Las Américas en la zona del Mercado Campesino, Av. Panamericana (https://www.anh.gob.bo/InsideFiles/Documentos/Documentos_Id-20-150310-0311-0.pdf); Mapcarta/OSM registra el punto de Mercado Campesino (https://mapcarta.com/es/N4415811348); V2 declara alias Campesino/Las Américas; separación 8,214 m.
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Campesino') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'Campesino';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Campesino' AND anh_id IS NOT NULL AND anh_id <> 2911) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'Campesino';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2911 AND nombre <> 'Campesino') THEN
        RAISE EXCEPTION 'El identificador ANH 2911 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2911 WHERE nombre = 'Campesino' AND (anh_id IS NULL OR anh_id = 2911);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'Campesino';
    END IF;
    -- Don Daniel: ANH 2804, distancia efectiva 0.000 m
    -- Evidencia: Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el directorio/documentación ANH, catálogo V2 y fuentes indicadas. No se realizó visita presencial; el dictamen es documental y geoespacial. Directorio ANH vigente identifica Don Daniel en Av. Circunvalación, zona Las Barrancas (https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40). Nombre y punto V2 coincidentes.
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Don Daniel') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'Don Daniel';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Don Daniel' AND anh_id IS NOT NULL AND anh_id <> 2804) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'Don Daniel';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2804 AND nombre <> 'Don Daniel') THEN
        RAISE EXCEPTION 'El identificador ANH 2804 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2804 WHERE nombre = 'Don Daniel' AND (anh_id IS NULL OR anh_id = 2804);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'Don Daniel';
    END IF;
    -- El Molle: ANH 2977, distancia efectiva 0.000 m
    -- Evidencia: Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el directorio/documentación ANH, catálogo V2 y fuentes indicadas. No se realizó visita presencial; el dictamen es documental y geoespacial. Directorio ANH vigente identifica El Molle S.R.L. en carretera Tarija–San Lorenzo km 11, El Rancho (https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40). Nombre y punto V2 coincidentes.
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'El Molle') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'El Molle';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'El Molle' AND anh_id IS NOT NULL AND anh_id <> 2977) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'El Molle';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2977 AND nombre <> 'El Molle') THEN
        RAISE EXCEPTION 'El identificador ANH 2977 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2977 WHERE nombre = 'El Molle' AND (anh_id IS NULL OR anh_id = 2977);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'El Molle';
    END IF;
    -- El Portillo: ANH 2472, distancia efectiva 0.000 m
    -- Evidencia: Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el directorio/documentación ANH, catálogo V2 y fuentes indicadas. No se realizó visita presencial; el dictamen es documental y geoespacial. Directorio ANH vigente identifica El Portillo S.R.L. sobre carretera Tarija–Al Chaco y Tarija–Bermejo (https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40). Nombre y punto V2 coincidentes.
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'El Portillo') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'El Portillo';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'El Portillo' AND anh_id IS NOT NULL AND anh_id <> 2472) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'El Portillo';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2472 AND nombre <> 'El Portillo') THEN
        RAISE EXCEPTION 'El identificador ANH 2472 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2472 WHERE nombre = 'El Portillo' AND (anh_id IS NULL OR anh_id = 2472);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'El Portillo';
    END IF;
    -- Ex Terminal: ANH 2341, distancia efectiva 28.050 m
    -- Evidencia: Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el directorio/documentación ANH, catálogo V2 y fuentes indicadas. No se realizó visita presencial; el dictamen es documental y geoespacial. Directorio ANH vigente: La Terminal S.A., Av. Jaime Paz Zamora esq. Arturo Soruco, zona El Tejar (https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40). V2 documenta Ex Terminal/La Terminal y separación 28,050 m.
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Ex Terminal') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'Ex Terminal';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Ex Terminal' AND anh_id IS NOT NULL AND anh_id <> 2341) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'Ex Terminal';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2341 AND nombre <> 'Ex Terminal') THEN
        RAISE EXCEPTION 'El identificador ANH 2341 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2341 WHERE nombre = 'Ex Terminal' AND (anh_id IS NULL OR anh_id = 2341);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'Ex Terminal';
    END IF;
    -- Las Vegas: ANH 2642, distancia efectiva 6.251 m
    -- Evidencia: Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el directorio/documentación ANH, catálogo V2 y fuentes indicadas. No se realizó visita presencial; el dictamen es documental y geoespacial. Directorio ANH vigente identifica Estación de Servicios Avenida Circunvalación S.R.L. en Av. Circunvalación esq. Ayoroa, barrio La Florida (https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40); resolución ANH 2019 confirma la misma razón social y dirección (https://www.anh.gob.bo/InsideFiles/Actividad/Dj/RA-2019/RADPS-ANH-DTJ-0001-2019.pdf); fuentes locales identifican el surtidor comercial Las Vegas sobre ...
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Las Vegas') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'Las Vegas';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Las Vegas' AND anh_id IS NOT NULL AND anh_id <> 2642) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'Las Vegas';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2642 AND nombre <> 'Las Vegas') THEN
        RAISE EXCEPTION 'El identificador ANH 2642 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2642 WHERE nombre = 'Las Vegas' AND (anh_id IS NULL OR anh_id = 2642);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'Las Vegas';
    END IF;
    -- Moto Mendez: ANH 2627, distancia efectiva 0.000 m
    -- Evidencia: Georreferenciación corregida; ANH original=(-21.4720998,-64.7576881); distancia original=221.385598 m; evidencia=Corrección geográfica documentada: ANH vigente ubica Moto Méndez en carretera Tarija–San Lorenzo km 8, Rancho Sud (https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40); registro OSM node 3207058741/Mapcarta identifica EE.SS. Moto Mendez en RN1 Tarija–San Lorenzo y sus coordenadas coinciden con el punto de catálogo V2 (https://mapcarta.com/N3207058741). Se conserva la coordenada cruda ANH y se usa la referencia OSM independiente.. Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el ...
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Moto Mendez') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'Moto Mendez';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Moto Mendez' AND anh_id IS NOT NULL AND anh_id <> 2627) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'Moto Mendez';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2627 AND nombre <> 'Moto Mendez') THEN
        RAISE EXCEPTION 'El identificador ANH 2627 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2627 WHERE nombre = 'Moto Mendez' AND (anh_id IS NULL OR anh_id = 2627);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'Moto Mendez';
    END IF;
    -- Panamericano: ANH 2388, distancia efectiva 0.000 m
    -- Evidencia: Georreferenciación corregida; ANH original=(-21.5441213,-64.7116260); distancia original=651.190855 m; evidencia=Corrección geográfica documentada: ANH vigente ubica Panamericano en Av. Jaime Paz Zamora, zona San Gerónimo (https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40); resolución ANH identifica Panamericano en Av. Jaime Paz Zamora esq. Calle Batallón Colorados (https://www.anh.gob.bo/InsideFiles/Actividad/Dj/RA-2018/RADPS-ANH-DTJ-0008-2018.pdf); nodo OSM 1753664990/Mapcarta ubica E.S. Panamericano en Calle Batallón Colorados, coordenada del catálogo V2 (https://mapcarta.com/N1753664990). Se conserva la coordenada cruda ANH y se usa la referencia geográfica independiente para el...
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Panamericano') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'Panamericano';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Panamericano' AND anh_id IS NOT NULL AND anh_id <> 2388) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'Panamericano';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2388 AND nombre <> 'Panamericano') THEN
        RAISE EXCEPTION 'El identificador ANH 2388 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2388 WHERE nombre = 'Panamericano' AND (anh_id IS NULL OR anh_id = 2388);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'Panamericano';
    END IF;
    -- San Jorge: ANH 2688, distancia efectiva 0.000 m
    -- Evidencia: Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el directorio/documentación ANH, catálogo V2 y fuentes indicadas. No se realizó visita presencial; el dictamen es documental y geoespacial. Directorio ANH vigente identifica San Jorge S.R.L. en Av. Panamericana, carretera a Bermejo, zona San Jorge II (https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40). Nombre y punto V2 coincidentes.
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'San Jorge') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'San Jorge';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'San Jorge' AND anh_id IS NOT NULL AND anh_id <> 2688) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'San Jorge';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2688 AND nombre <> 'San Jorge') THEN
        RAISE EXCEPTION 'El identificador ANH 2688 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2688 WHERE nombre = 'San Jorge' AND (anh_id IS NULL OR anh_id = 2688);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'San Jorge';
    END IF;
    -- Sointa: ANH 2364, distancia efectiva 0.000 m
    -- Evidencia: Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el directorio/documentación ANH, catálogo V2 y fuentes indicadas. No se realizó visita presencial; el dictamen es documental y geoespacial. Directorio ANH vigente: Sointa S.R.L., Av. Circunvalación, zona Morros Blancos (https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40). Nombre y punto V2 coincidentes.
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Sointa') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'Sointa';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Sointa' AND anh_id IS NOT NULL AND anh_id <> 2364) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'Sointa';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2364 AND nombre <> 'Sointa') THEN
        RAISE EXCEPTION 'El identificador ANH 2364 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2364 WHERE nombre = 'Sointa' AND (anh_id IS NULL OR anh_id = 2364);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'Sointa';
    END IF;
    -- Surtidor Tarija: ANH 2386, distancia efectiva 0.000 m
    -- Evidencia: Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el directorio/documentación ANH, catálogo V2 y fuentes indicadas. No se realizó visita presencial; el dictamen es documental y geoespacial. Directorio ANH vigente identifica Estación de Servicios Tarija S.R.L. en Av. Héroes de la Independencia, zona San Martín (https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40); documento oficial ANH/YPFB 2017 registra Estación de Servicio Tarija en la misma avenida y barrio (https://www.anh.gob.bo/InsideFiles/Inicio/elecciones031217.pdf); catálogo V2 Surtidor Tarija en la misma ubicación.
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Surtidor Tarija') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'Surtidor Tarija';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Surtidor Tarija' AND anh_id IS NOT NULL AND anh_id <> 2386) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'Surtidor Tarija';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2386 AND nombre <> 'Surtidor Tarija') THEN
        RAISE EXCEPTION 'El identificador ANH 2386 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2386 WHERE nombre = 'Surtidor Tarija' AND (anh_id IS NULL OR anh_id = 2386);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'Surtidor Tarija';
    END IF;
    -- Tacuarandi: ANH 2339, distancia efectiva 0.000 m
    -- Evidencia: Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el directorio/documentación ANH, catálogo V2 y fuentes indicadas. No se realizó visita presencial; el dictamen es documental y geoespacial. Directorio ANH vigente: Tacuarandi S.R.L., zona Morros Blancos, Av. Panamericana esq. Hna. Ana Conchentina (https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40). Nombre y coordenada de catálogo V2 coincidentes.
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Tacuarandi') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'Tacuarandi';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'Tacuarandi' AND anh_id IS NOT NULL AND anh_id <> 2339) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'Tacuarandi';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2339 AND nombre <> 'Tacuarandi') THEN
        RAISE EXCEPTION 'El identificador ANH 2339 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2339 WHERE nombre = 'Tacuarandi' AND (anh_id IS NULL OR anh_id = 2339);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'Tacuarandi';
    END IF;
    -- YPFB Parada Chaco: ANH 2974, distancia efectiva 37.759 m
    -- Evidencia: Revisión técnica documental realizada por ChatGPT por delegación expresa del solicitante el 23/11/2026. Identidad y ubicación contrastadas con el directorio/documentación ANH, catálogo V2 y fuentes indicadas. No se realizó visita presencial; el dictamen es documental y geoespacial. Documento oficial ANH 2017 identifica YPFB Presidente Arce en Av. Panamericana km 3, zona Morros Blancos (https://www.anh.gob.bo/InsideFiles/Inicio/elecciones031217.pdf); V2 documenta alias YPFB Parada Chaco para el ID 2974; separación 37,759 m.
    IF NOT EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'YPFB Parada Chaco') THEN
        RAISE EXCEPTION 'No existe el surtidor del catálogo: %', 'YPFB Parada Chaco';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE nombre = 'YPFB Parada Chaco' AND anh_id IS NOT NULL AND anh_id <> 2974) THEN
        RAISE EXCEPTION 'El surtidor % ya tiene un vínculo ANH diferente', 'YPFB Parada Chaco';
    END IF;
    IF EXISTS (SELECT 1 FROM surtidor WHERE anh_id = 2974 AND nombre <> 'YPFB Parada Chaco') THEN
        RAISE EXCEPTION 'El identificador ANH 2974 ya pertenece a otro surtidor';
    END IF;
    UPDATE surtidor SET anh_id = 2974 WHERE nombre = 'YPFB Parada Chaco' AND (anh_id IS NULL OR anh_id = 2974);
    GET DIAGNOSTICS filas_modificadas = ROW_COUNT;
    IF filas_modificadas <> 1 THEN
        RAISE EXCEPTION 'Se esperó una fila única para el surtidor %', 'YPFB Parada Chaco';
    END IF;
END $vinculos_anh$;
