-- #172 (CU-04, RN-04.25, D-45): la congestion que detecta el motor guarda el trazado de su tramo, para que la app la
-- dibuje a lo largo de la calle y no como un punto. 06_base_de_datos.md.
-- Se guarda cuando nace la congestion y no cambia al extenderla, porque el tramo es el mismo (RN-04.11). Las
-- congestiones guardadas antes de esta version quedan sin trazado y la app las sigue mostrando como punto.
ALTER TABLE evento_vial ADD COLUMN trazado geography(LineString, 4326);

-- Solo una congestion de telemetria, que siempre tiene tramo, puede traer trazado (D-04).
ALTER TABLE evento_vial ADD CONSTRAINT ck_evento_vial_trazado CHECK (trazado IS NULL OR tramo_id IS NOT NULL);
