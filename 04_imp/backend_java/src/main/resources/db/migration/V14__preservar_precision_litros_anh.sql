-- HU-14 (CU-09), V14: RN-09.12 exige conservar los litros recibidos de ANH sin redondearlos.
-- V8 definió NUMERIC(12,2), que redondea valores con más de dos decimales.
-- Se amplía a NUMERIC sin precisión/escala fija; los valores históricos ya almacenados no se pueden reconstruir.
ALTER TABLE estado_combustible
    ALTER COLUMN litros TYPE NUMERIC
    USING litros::NUMERIC;
