-- HU-14 (CU-09), V15: el updated_at de ANH se renueva aunque el saldo no cambie (D-50), asi que un dato de ANH
-- siempre parecia el mas reciente y tapaba los avisos de WhatsApp y las confirmaciones de los conductores.
-- Desde V15, en ANH fecha_dato es desde cuando ANH dice lo mismo (RN-09.14) y anh_actualizado guarda el updated_at
-- de la ultima lectura aceptada, para no aceptar una lectura mas vieja.
ALTER TABLE estado_combustible ADD COLUMN anh_actualizado TIMESTAMPTZ;

-- Los datos de ANH que ya hay no saben desde cuando dicen lo mismo: se toma su updated_at, que es lo unico que hay.
UPDATE estado_combustible SET anh_actualizado = fecha_dato WHERE origen = 'ANH';

-- Solo ANH. No se exige en ANH para que volver a la version anterior (volver.sh), que no conoce la columna, siga
-- guardando datos de ANH; si falta, el backend usa fecha_dato en su lugar.
ALTER TABLE estado_combustible ADD CONSTRAINT ck_estado_combustible_solo_anh_actualizado CHECK (
    origen = 'ANH' OR anh_actualizado IS NULL);
