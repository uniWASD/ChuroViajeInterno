-- T-118 (CU-09): el identificador de la estacion de ANH de cada surtidor. 06_base_de_datos.md.
-- Las columnas de ANH de estado_combustible (litros, estado_anh y consultado_en) ya las creo V8.
-- Los vinculos los carga una migracion de datos aparte, despues de que el equipo los revise a mano (RN-09.10).
ALTER TABLE surtidor ADD COLUMN anh_id INTEGER;

-- RN-09.10: un surtidor tiene a lo sumo un identificador de ANH y cada identificador pertenece a un solo surtidor.
-- La columna admite un solo valor por fila; el indice unico impide que dos surtidores compartan uno.
ALTER TABLE surtidor ADD CONSTRAINT uq_surtidor_anh_id UNIQUE (anh_id);

-- ANH numera sus estaciones con enteros positivos.
ALTER TABLE surtidor ADD CONSTRAINT ck_surtidor_anh_id CHECK (anh_id IS NULL OR anh_id > 0);
