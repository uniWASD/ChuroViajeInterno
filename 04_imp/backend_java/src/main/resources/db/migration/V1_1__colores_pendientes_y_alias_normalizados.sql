-- T-089 / #41: corrección evolutiva sin alterar el checksum de V1 original.
-- Los HEX que trae V1 proceden de propuestas provisionales, NO de una aprobación.
-- Se limpian SOLO los valores iniciales conocidos, sin borrar otros colores ya revisados.
ALTER TABLE tipo_evento ALTER COLUMN color DROP NOT NULL;
UPDATE tipo_evento SET color = NULL WHERE (codigo, color) IN (
    ('ACCIDENTE', '#E53935'),
    ('CONTROL_POLICIAL', '#1E88E5'),
    ('BLOQUEO', '#FB8C00'),
    ('OTRO', '#757575'),
    ('CONGESTION', '#FDD835')
);

-- La clave normalizada en Java y Python admite únicamente ASCII minúsculo,
-- dígitos y espacios simples; protege futuras migraciones/INSERTs de alias.
ALTER TABLE surtidor_alias
    ADD CONSTRAINT ck_surtidor_alias_normalizado
    CHECK (alias ~ '^[a-z0-9]+( [a-z0-9]+)*$');
