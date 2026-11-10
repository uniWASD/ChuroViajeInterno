CREATE TABLE evento_vial (
    id BIGSERIAL PRIMARY KEY,
    tipo VARCHAR(50) NOT NULL,
    ubicacion geometry(Point, 4326) NOT NULL, -- Requisito: punto PostGIS
    hora_confirmacion TIMESTAMP WITH TIME ZONE NOT NULL,
    hora_ocurrencia TIMESTAMP WITH TIME ZONE NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO', -- Requisito: nace Activo (RN-01.10)
    votos INTEGER NOT NULL DEFAULT 0, -- Requisito: nace sin votos
    conductor_id BIGINT NOT NULL, -- Se relaciona con la tabla conductor de la V3
    
    CONSTRAINT fk_evento_conductor FOREIGN KEY (conductor_id) REFERENCES conductor(id)
);

-- Índices espaciales y de tiempo para acelerar las búsquedas (RN-01.8 y RN-01.9)
CREATE INDEX idx_evento_vial_ubicacion ON evento_vial USING GIST (ubicacion);
CREATE INDEX idx_evento_vial_ocurrencia ON evento_vial(hora_ocurrencia);
CREATE INDEX idx_evento_vial_conductor ON evento_vial(conductor_id);