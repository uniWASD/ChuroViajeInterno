# Evidencias observadas en las salidas del usuario — 26/10/2026

No se adjunta `T089_VALIDACION_16_PRUEBAS.txt` como si lo hubiésemos ejecutado aquí. El usuario mostró el resultado **Maven BUILD SUCCESS — Tests run: 16, Failures: 0, Errors: 0, Skipped: 0** en su PowerShell de G:.

- Las clases reportadas incluyen `ClaveAliasTest` (1), `ServicioBusquedaSurtidoresTest` (1), `TipoCombustibleTest` (1), `CoberturaGeoJsonTest` (4), `ConfiguracionCoberturaTest` (4), `ManejadorCoberturaTest` (1), `T089_ManejadoresMvcTest` (1), `CatalogosJdbcPostgisTest` (1), `MigracionesPostgisV1Test` (1) y `T089ArranqueAisladoTest` (1).
- PostgreSQL 16.15 y Docker Engine 26.1.4 responden; Flyway migró V1 y V1.1 a `churoviaje_dev`.
- `SELECT COUNT(*) FROM surtidor` = 0 mientras no se ejecute V2 real.
- `SELECT to_regclass('public.eventos_viales')` = NULL, bloqueo externo HU-01.
- Spring Boot con JPA completo falló con `Schema-validation: missing table [eventos_viales]` y Spring Boot T089 aislado arrancó con Tomcat puerto temporal y pasó test.
- Python 53 pruebas, Java dominio aislado 45 comprobaciones, según historial del usuario.

Este archivo resume evidencia ajena visible. **Pruebas del ZIP deben ejecutarse por el destinatario**, y no deben atribuirse resultados nuevos al creador del ZIP.
