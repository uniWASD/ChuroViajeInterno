-- T-118 (CU-09): vinculos entre los surtidores del catalogo y las estaciones de ANH (RN-09.10).
-- Generado con 05_pro/PruebaApiANH/vinculacion/proponer_vinculos.py; revisado a mano por Marcelo Quiroga el 2026-11-23.
-- Cada surtidor que no aparece aqui queda sin estacion de ANH, y CU-09 ignora sus datos (RN-09.9).
UPDATE surtidor SET anh_id = 2390 WHERE nombre = 'Agrupa'; -- a 0 m
UPDATE surtidor SET anh_id = 2911 WHERE nombre = 'Campesino'; -- a 8 m; Nombre distinto; V2 dice «Las Americas - YPFB, ANH id2911»
UPDATE surtidor SET anh_id = 2804 WHERE nombre = 'Don Daniel'; -- a 0 m
UPDATE surtidor SET anh_id = 2977 WHERE nombre = 'El Molle'; -- a 0 m
UPDATE surtidor SET anh_id = 2472 WHERE nombre = 'El Portillo'; -- a 0 m
UPDATE surtidor SET anh_id = 2341 WHERE nombre = 'Ex Terminal'; -- a 28 m
UPDATE surtidor SET anh_id = 2642 WHERE nombre = 'Las Vegas'; -- a 6 m; Nombre distinto; V2 cita «surtidor Las Vegas, avenida Circunvalación»
UPDATE surtidor SET anh_id = 2627 WHERE nombre = 'Moto Mendez'; -- a 221 m
UPDATE surtidor SET anh_id = 2388 WHERE nombre = 'Panamericano'; -- a 651 m; Excepción a P-31: ANH tiene mal el punto (V2 lo anota); se reconoce por el nombre
UPDATE surtidor SET anh_id = 2688 WHERE nombre = 'San Jorge'; -- a 0 m
UPDATE surtidor SET anh_id = 2364 WHERE nombre = 'Sointa'; -- a 0 m
UPDATE surtidor SET anh_id = 2386 WHERE nombre = 'Surtidor Tarija'; -- a 0 m; Nombre distinto; V2 tomó esta coordenada de ANH
UPDATE surtidor SET anh_id = 2339 WHERE nombre = 'Tacuarandi'; -- a 0 m
UPDATE surtidor SET anh_id = 2974 WHERE nombre = 'YPFB Parada Chaco'; -- a 38 m

-- Si un nombre del catalogo cambio y algun UPDATE no encontro su fila, la migracion falla en vez de seguir.
DO $$
BEGIN
    IF (SELECT count(*) FROM surtidor WHERE anh_id IS NOT NULL) <> 14 THEN
        RAISE EXCEPTION 'No se cargaron todos los vinculos de ANH';
    END IF;
END $$;
