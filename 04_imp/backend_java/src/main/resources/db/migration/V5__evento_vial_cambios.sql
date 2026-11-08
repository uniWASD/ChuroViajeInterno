-- HU-03 (CU-02): marca de cuando cambio por ultima vez cada evento, para que la app pida solo los cambios
-- desde su ultima consulta (RN-02.6). Cambia al nacer, al confirmarse, al retirarse, al vencer y cuando una
-- congestion actualiza su momento (RN-04.11).
ALTER TABLE evento_vial ADD COLUMN actualizado_en TIMESTAMPTZ;
UPDATE evento_vial SET actualizado_en = registrado_en;
ALTER TABLE evento_vial ALTER COLUMN actualizado_en SET NOT NULL;
ALTER TABLE evento_vial ADD CONSTRAINT ck_evento_vial_actualizado CHECK (actualizado_en >= registrado_en);

CREATE INDEX idx_evento_vial_actualizado ON evento_vial (actualizado_en);
