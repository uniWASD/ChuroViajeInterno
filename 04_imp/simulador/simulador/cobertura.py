"""La zona de cobertura de Tarija (04_imp/datos/mancha_urbana_tarija.geojson), para validar las rutas."""

import json
from pathlib import Path


class Cobertura:
    def __init__(self, anillo: list[tuple[float, float]]):
        # El GeoJSON guarda (longitud, latitud).
        self._anillo = anillo

    @classmethod
    def desde_archivo(cls, ruta: Path) -> "Cobertura":
        datos = json.loads(Path(ruta).read_text(encoding="utf-8"))
        geometria = datos["features"][0]["geometry"] if datos.get("type") == "FeatureCollection" else datos["geometry"]
        if geometria["type"] != "Polygon":
            raise ValueError("La cobertura debe ser un polígono")
        return cls([(float(x), float(y)) for x, y in geometria["coordinates"][0]])

    def contiene(self, latitud: float, longitud: float) -> bool:
        """Par e impar de cruces (ray casting); basta para un polígono sin huecos."""
        dentro = False
        x, y = longitud, latitud
        n = len(self._anillo)
        for i in range(n):
            x1, y1 = self._anillo[i]
            x2, y2 = self._anillo[(i + 1) % n]
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                dentro = not dentro
        return dentro
