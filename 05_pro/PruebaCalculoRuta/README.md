# Prueba de concepto — Cálculo de ruta (CU-03, HU-05)

Prototipo en Python del motor de ruteo (A* con multiplicadores de tráfico) para
validar la lógica antes de llevarla al motor core en C++.

| Archivo | Contenido |
|---|---|
| `red_de_calles.py` | Modelo: `RedDeCalles`, niveles de tráfico, eventos, A* y `ResultadoRuta` |
| `redes_de_prueba.py` | Redes de prueba: `construir_red_irregular()` y `construir_red_grande()` |
| `demo_ruteo.py` | Muestra los casos por pantalla (para explicar en clase) |
| `tests/test_cu03_ruteo.py` | Casos de prueba CP-09 a CP-19, ejecutables con pytest |

## Cómo ejecutar

Desde esta carpeta (`05_pro/PruebaCalculoRuta`):

```
pip install -r requirements.txt
python -m pytest -v
python demo_ruteo.py
```
