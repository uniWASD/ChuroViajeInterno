-- D-47 (Sprint 5.5, T-123): calles y lugares conocidos que se pueden buscar (CU-03).
-- No trae datos: el backend llena estas tablas al arrancar con el JSON que prepara
-- motor_cpp/preparacion/preparar_lugares.py desde OpenStreetMap (variable LUGARES_PATH).
-- Los surtidores no se copian aqui: la busqueda los toma del catalogo de V2.
CREATE TABLE lugar (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave VARCHAR(60) NOT NULL,
    nombre VARCHAR(200) NOT NULL,
    -- El nombre en minusculas, sin tildes ni signos y con las abreviaturas comunes escritas completas.
    nombre_busqueda VARCHAR(200) NOT NULL,
    categoria VARCHAR(20) NOT NULL,
    ubicacion GEOGRAPHY(Point, 4326) NOT NULL,
    CONSTRAINT uq_lugar_clave UNIQUE (clave),
    CONSTRAINT ck_lugar_nombre_busqueda CHECK (nombre_busqueda ~ '^[a-z0-9]+( [a-z0-9]+)*$'),
    CONSTRAINT ck_lugar_categoria CHECK (categoria IN ('CALLE', 'PLAZA', 'PARQUE', 'SALUD', 'EDUCACION',
        'MERCADO', 'GOBIERNO', 'TRANSPORTE', 'TURISMO', 'HOTEL', 'BANCO', 'COMIDA', 'DEPORTE', 'RELIGION',
        'COMERCIO'))
);

CREATE INDEX idx_lugar_ubicacion ON lugar USING GIST (ubicacion);

-- Que extracto esta cargado. Una sola fila: si la fecha del archivo es la misma, no se vuelve a cargar.
CREATE TABLE lugar_extracto (
    id SMALLINT PRIMARY KEY,
    fecha_extracto DATE NOT NULL,
    cantidad INTEGER NOT NULL,
    cargado_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_lugar_extracto_unico CHECK (id = 1)
);
