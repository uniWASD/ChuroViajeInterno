-- Resumen de un respaldo (T-103, #79): cuántas filas tiene cada tabla que importa y una huella de su contenido,
-- en una sola línea. Lo calcula respaldar-bd.sh en la misma instantánea que pg_dump, y probar-restauracion.sh
-- sobre la base restaurada: si las dos líneas coinciden, el respaldo tiene exactamente lo mismo (CP-129).
SELECT 'eventos=' || (SELECT count(*) FROM evento_vial)
  || ' huella_eventos=' || (SELECT coalesce(md5(string_agg(id || ':' || estado || ':' || ocurrio_en, ',' ORDER BY id)), '-') FROM evento_vial)
  || ' votos=' || (SELECT count(*) FROM voto)
  || ' surtidores=' || (SELECT count(*) FROM surtidor)
  || ' huella_surtidores=' || (SELECT coalesce(md5(string_agg(id || ':' || nombre, ',' ORDER BY id)), '-') FROM surtidor)
  || ' estados_combustible=' || (SELECT count(*) FROM estado_combustible)
  || ' conductores=' || (SELECT count(*) FROM conductor);
