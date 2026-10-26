# Comprobaciones realizadas sobre el código empaquetado

- `python -m unittest discover -s herramientas/tests -p 'test*.py' -q`: **53 pruebas correctas (OK)**, ejecutadas contra esta entrega.
- `bash herramientas/probar_dominio_java.sh`: **45 comprobaciones correctas**, Java 21.
- Scripts Python compilados sintácticamente, `pom.xml` XML válido y archivos JSON/GeoJSON legibles.
- Comprobación de integridad del ZIP sin entradas corruptas.
- **Maven/Testcontainers no se ejecutó en este entorno**, dado que Maven y Docker no estaban disponibles. Las **16 pruebas Maven** son un resultado real que el usuario aportó desde G: y corresponde a su copia local, no a una prueba nueva de este ZIP.
- No se tocó GitHub ni se ejecutó ninguna migración V2.
