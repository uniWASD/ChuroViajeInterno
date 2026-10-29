-- Fuente ANH (identidades y coordenadas):
-- https://abastecimiento.prod.anh.gob.bo/api/estaciones/?departamento=6&producto=0
-- Imagen de contraste: Esri World Imagery z17, consultada el 2026-10-28.
-- https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer
-- La fecha de consulta no acredita la fecha de captura de las imagenes.

-- Las Vegas: centro de la cubierta de expendio digitizado en Google Satellite z19,
-- x167881/y294254, pixel local (184,54); contrastado con Esri World Imagery z17.
-- Referencia inicial: https://bo.near-place.com/surtidor-las-vegas-avenida-circunvalacion-tarija
-- El punto del directorio cae en el acceso; se usa el centro de la cubierta visible.
INSERT INTO surtidor (nombre, ubicacion) VALUES ('Las Vegas', ST_SetSRID(ST_MakePoint(-64.724772, -21.5234844), 4326)::geography);
-- Don Daniel: API ANH, coordenada contrastada con JSON crudo e imagen Esri World Imagery z17, 2026-10-28
INSERT INTO surtidor (nombre, ubicacion) VALUES ('Don Daniel', ST_SetSRID(ST_MakePoint(-64.7382423, -21.5133312), 4326)::geography);
-- Tacuarandi: API ANH, coordenada contrastada con JSON crudo e imagen Esri World Imagery z17, 2026-10-28
INSERT INTO surtidor (nombre, ubicacion) VALUES ('Tacuarandi', ST_SetSRID(ST_MakePoint(-64.7016073, -21.5461962), 4326)::geography);
-- Agrupa: API ANH, coordenada contrastada con JSON crudo e imagen Esri World Imagery z17, 2026-10-28
INSERT INTO surtidor (nombre, ubicacion) VALUES ('Agrupa', ST_SetSRID(ST_MakePoint(-64.7487867, -21.5111041), 4326)::geography);
-- El Portillo: API ANH, coordenada contrastada con JSON crudo e imagen Esri World Imagery z17, 2026-10-28
INSERT INTO surtidor (nombre, ubicacion) VALUES ('El Portillo', ST_SetSRID(ST_MakePoint(-64.6700224, -21.5666524), 4326)::geography);
-- Moto Mendez: https://www.openstreetmap.org/node/3207058741
-- Punto OSM sobre la cubierta visible en Esri World Imagery, consultado 2026-10-28.
-- El punto ANH (-21.4720998,-64.7576881) cae en terreno sin cubierta, a unos 222 m.
INSERT INTO surtidor (nombre, ubicacion) VALUES ('Moto Mendez', ST_SetSRID(ST_MakePoint(-64.7585048, -21.47394), 4326)::geography);
-- Ex Terminal / La Terminal: OSM node 9325867333; ANH id2341 en Victor Paz / Arturo Soruco.
INSERT INTO surtidor (nombre, ubicacion) VALUES ('Ex Terminal', ST_SetSRID(ST_MakePoint(-64.72413, -21.54194), 4326)::geography);
-- Sointa: API ANH, coordenada contrastada con JSON crudo e imagen Esri World Imagery z17, 2026-10-28
INSERT INTO surtidor (nombre, ubicacion) VALUES ('Sointa', ST_SetSRID(ST_MakePoint(-64.7075786, -21.5420577), 4326)::geography);
-- Panamericano: OSM node 1753664990, consultado 2026-10-28; punto sobre cubierta visible.
-- https://www.openstreetmap.org/node/1753664990 (Calle Batallon Colorados).
-- Contraste visual: Esri World Imagery, z17, x41972-41973, y73571.
-- ANH id2388 devuelve (-21.5441213,-64.711626), a 651 m; no se adopta ese punto.
-- Direccion oficial: https://www.anh.gob.bo/InsideFiles/Actividad/Dj/RA-2018/RADPS-ANH-DTJ-0008-2018.pdf
INSERT INTO surtidor (nombre, ubicacion) VALUES ('Panamericano', ST_SetSRID(ST_MakePoint(-64.7178035, -21.5429897), 4326)::geography);
-- San Jorge: API ANH, coordenada contrastada con JSON crudo e imagen Esri World Imagery z17, 2026-10-28
INSERT INTO surtidor (nombre, ubicacion) VALUES ('San Jorge', ST_SetSRID(ST_MakePoint(-64.6905788, -21.5538205), 4326)::geography);
-- El Molle: API ANH, coordenada contrastada con JSON crudo e imagen Esri World Imagery z17, 2026-10-28
INSERT INTO surtidor (nombre, ubicacion) VALUES ('El Molle', ST_SetSRID(ST_MakePoint(-64.7533344, -21.4530516), 4326)::geography);
-- Surtidor Tarija: API ANH, coordenada contrastada con JSON crudo e imagen Esri World Imagery z17, 2026-10-28
INSERT INTO surtidor (nombre, ubicacion) VALUES ('Surtidor Tarija', ST_SetSRID(ST_MakePoint(-64.74234, -21.53627), 4326)::geography);

-- Campesino: Las Americas - YPFB, OSM node 4415811348 y API ANH id2911.
-- OSM node 4415811348 (amenity=fuel): https://mapcarta.com/es/N4415811348
-- Referencia de identidad y zona: https://www.anh.gob.bo/InsideFiles/Documentos/Documentos_Id-20-150310-0311-0.pdf
-- Punto OSM conservado; coordenada ANH id2911 cercana, contrastada con el JSON crudo.
INSERT INTO surtidor (nombre, ubicacion) VALUES ('Campesino', ST_SetSRID(ST_MakePoint(-64.74407, -21.51957), 4326)::geography);
-- YPFB Parada Chaco: ubicación de EE.SS Presidente Arce junto a Parada al Chaco.
-- OSM node 12377595317 (amenity=fuel): https://mapcarta.com/es/N12377595317
-- ANH: Presidente Arce en Av. Panamericana, zona Morros Blancos:
-- https://www.anh.gob.bo/InsideFiles/Inicio/elecciones031217.pdf
-- API id2974, OSM y direccion ANH respaldan Parada Chaco / Morros Blancos.
-- Contraste visual: punto junto a la cubierta de expendio; no duplicar el predio.
INSERT INTO surtidor (nombre, ubicacion) VALUES ('YPFB Parada Chaco', ST_SetSRID(ST_MakePoint(-64.70014, -21.54698), 4326)::geography);

INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'las vegas' FROM surtidor WHERE nombre = 'Las Vegas';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'la vegas' FROM surtidor WHERE nombre = 'Las Vegas';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'vegas' FROM surtidor WHERE nombre = 'Las Vegas';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'mas vegas' FROM surtidor WHERE nombre = 'Las Vegas';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'don daniel' FROM surtidor WHERE nombre = 'Don Daniel';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'daniel' FROM surtidor WHERE nombre = 'Don Daniel';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'tacuarandi' FROM surtidor WHERE nombre = 'Tacuarandi';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'tacurandi' FROM surtidor WHERE nombre = 'Tacuarandi';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'agrupa' FROM surtidor WHERE nombre = 'Agrupa';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'el portillo' FROM surtidor WHERE nombre = 'El Portillo';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'portillo' FROM surtidor WHERE nombre = 'El Portillo';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'moto mendez' FROM surtidor WHERE nombre = 'Moto Mendez';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'moto mendes' FROM surtidor WHERE nombre = 'Moto Mendez';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'ex terminal' FROM surtidor WHERE nombre = 'Ex Terminal';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'exterminal' FROM surtidor WHERE nombre = 'Ex Terminal';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'la terminal' FROM surtidor WHERE nombre = 'Ex Terminal';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'laterminal' FROM surtidor WHERE nombre = 'Ex Terminal';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'sointa' FROM surtidor WHERE nombre = 'Sointa';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'sointar' FROM surtidor WHERE nombre = 'Sointa';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'panamericano' FROM surtidor WHERE nombre = 'Panamericano';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'san jorge' FROM surtidor WHERE nombre = 'San Jorge';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'san gorje' FROM surtidor WHERE nombre = 'San Jorge';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'el molle' FROM surtidor WHERE nombre = 'El Molle';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'molle' FROM surtidor WHERE nombre = 'El Molle';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'surtidor tarija' FROM surtidor WHERE nombre = 'Surtidor Tarija';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'el tarija' FROM surtidor WHERE nombre = 'Surtidor Tarija';

INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'campesino' FROM surtidor WHERE nombre = 'Campesino';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'ypfb parada chaco' FROM surtidor WHERE nombre = 'YPFB Parada Chaco';
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'parada chaco' FROM surtidor WHERE nombre = 'YPFB Parada Chaco';

-- Alias local respaldado por el prototipo: 05_pro/PruebaClasificacion/Chats/Chat1.txt,
-- linea 3194 y mensajes 4360-4364: Surtidor Tarija junto a la Domingo Savio.
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'domingo savio' FROM surtidor WHERE nombre = 'Surtidor Tarija';

-- YPFB Morros Blancos: ANH 2017 identifica Presidente Arce en Morros Blancos. API 2974 y OSM lo sitúan junto a Parada al Chaco. Es la misma estación del registro existente.
-- Fuente: https://www.anh.gob.bo/InsideFiles/Inicio/elecciones031217.pdf#page=3
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'ypfb morros blancos' FROM surtidor WHERE nombre = 'YPFB Parada Chaco';

-- San Geronimo: Chat1.txt 410–421 identifica Panamericano como San Geronimo. Coincide con la zona de la dirección oficial de ANH.
-- Fuente: https://www.openstreetmap.org/node/1753664990
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'san geronimo' FROM surtidor WHERE nombre = 'Panamericano';

-- San Martin: Chat1.txt 3122–3124 vincula Surtidor Tarija con Puente San Martin. ANH lo sitúa en barrio San Martín. El id ANH 2979 SAN MARTIN está en Yacuiba y no se usa.
-- Fuente: https://www.anh.gob.bo/InsideFiles/Inicio/elecciones031217.pdf#page=3
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'san martin' FROM surtidor WHERE nombre = 'Surtidor Tarija';

-- Pimentel: Chat2.txt 170–174: Pimentel se identifica como Daniel. ANH 2015 relaciona Don Daniel con el representante de apellido Pimentel; se usa como respaldo histórico, no como prueba de titularidad actual.
-- Fuente: https://www.anh.gob.bo/InsideFiles/Documentos/Documentos_Id-208-150415-0139-0.pdf#page=19
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'pimentel' FROM surtidor WHERE nombre = 'Don Daniel';

-- Villanueva: Chat1.txt 280 y 2146 relaciona Villanueva con San Geronimo. ANH 2015 relaciona Panamericano con el representante de apellido Villanueva; respaldo histórico de la denominación.
-- Fuente: https://www.anh.gob.bo/InsideFiles/Documentos/Documentos_Id-208-150415-0139-0.pdf#page=19
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'villanueva' FROM surtidor WHERE nombre = 'Panamericano';

-- Coliseo Universitario: Chat1.txt 607–608 ubica Panamericano pasando el Coliseo Universitario; 3455–3458 vincula San Geronimo con ese coliseo. OSM e imagen aérea respaldan la posición.
-- Fuente: https://www.openstreetmap.org/node/1753664990
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'coliseo universitario' FROM surtidor WHERE nombre = 'Panamericano';

-- Nombre oficial ANH del registro conservado como YPFB Parada Chaco.
INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, 'presidente arce' FROM surtidor WHERE nombre = 'YPFB Parada Chaco';
