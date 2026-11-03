# T-117 — Cierre operativo y trazabilidad

Este medidor implementa las reglas de consulta ANH de CU-09 y registra una fila por petición y por estación–combustible. Consta evidencia real de una ronda por entorno, sin intervalos longitudinales.

La decisión del solicitante confirma **P-29 = 10 minutos entre inicios**, **P-30 = 6 h desde la última lectura válida** y **P-32 = timeout de 12 s, un reintento ante fallo elegible, pausa >=1 s y respeto de Retry-After**. No representan estimaciones realizadas sobre 72 horas ni permiten conocer exactamente cuándo actualiza ANH una estación.

En el análisis puntual, Windows registró 166 ms de media y Oracle 660 ms; 3 GET HTTP 200 y 57 pares estación–combustible por origen. El RAR de evidencia conservado fuera del repositorio incluye los CSV originales de Oracle, pero para Windows solo un resumen del analizador. Las simulaciones de 432 ciclos y la suite de pruebas no son tráfico real de ANH. CP-157 original no se considera acreditado.

`updated_at` se recibe sin zona explícita en la muestra. Se interpreta `America/La_Paz` según RN-09.13, que es una decisión contractual, no una confirmación publicada por ANH. El aviso `/api/Disclaimer` caracteriza los saldos como referenciales. Se consultó una sola vez y su respuesta original está en el archivo de evidencia privado del paquete, no en los CSV de estaciones.

La carpeta `EVIDENCIA_T117` contiene únicamente CSV reales de Oracle y resúmenes verificados; no contiene secretos, IP ni cuerpos de respuestas. Los registros con estaciones y saldo deben revisarse antes de publicar en un repositorio público.
