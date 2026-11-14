-- HU-13 (CU-04): la congestion que detecta el motor con la telemetria guarda el tramo donde se midio. 06_base_de_datos.md.
-- El nivel ya existe desde V4. Con el tramo, el backend encuentra la congestion vigente de ese tramo para extenderla en
-- lugar de crear otra (RN-04.11), y se la vuelve a pasar al motor para que afecte solo ese sentido (D-18).
ALTER TABLE evento_vial ADD COLUMN tramo_id VARCHAR(128);

-- D-04: los eventos de conductor no tienen tramo. El servicio siempre guarda el tramo de una congestion nueva; las
-- de telemetria guardadas antes de esta version quedan sin el (el motor las ubica en el tramo mas cercano).
ALTER TABLE evento_vial ADD CONSTRAINT ck_evento_vial_tramo CHECK (origen = 'TELEMETRIA' OR tramo_id IS NULL);

-- RN-04.11: una sola congestion activa por tramo, y por separado las simuladas (RN-04.20). Segunda barrera: la
-- primera es el servicio, que extiende la existente.
CREATE UNIQUE INDEX uq_evento_vial_congestion_activa ON evento_vial (tramo_id, simulado)
    WHERE tramo_id IS NOT NULL AND estado IN ('ACTIVO', 'CONFIRMADO');

-- RN-04.11: al extender una congestion su momento pasa a la ultima medicion, que es posterior al registro. Lo que
-- sigue prohibido es un momento en el futuro: nunca despues de la ultima actualizacion de la fila.
ALTER TABLE evento_vial DROP CONSTRAINT ck_evento_vial_no_futuro;
ALTER TABLE evento_vial ADD CONSTRAINT ck_evento_vial_no_futuro CHECK (
    ocurrio_en <= registrado_en OR (origen = 'TELEMETRIA' AND ocurrio_en <= actualizado_en));
