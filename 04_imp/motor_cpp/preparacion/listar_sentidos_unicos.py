"""Genera una hoja CSV para la revisión presencial de sentidos en el centro de Tarija."""
import argparse
import csv
from collections import defaultdict
import json
from pathlib import Path

from validar_red import validar_red
from verificar_casos_reales import cerca_del_centro


def filas_sentidos_unicos(red):
    grupos = defaultdict(list)
    for tramo in red["tramos"]:
        # En el contrato v1, [latitud, longitud]. El cálculo del radio usa (lon, lat).
        if tramo["opuesto"] is not None:
            continue
        puntos = [(lon, lat) for lat, lon in tramo["geometria"]]
        if not cerca_del_centro(puntos):
            continue
        grupos[(tramo["nombre"], tramo["tipo_via"])].append(tramo["id"])
    return [{"calle": nombre, "tipo_via": tipo, "cantidad_tramos": len(ids),
             "identificadores_tramos": "; ".join(sorted(ids)),
             "verificacion_senalizacion": "PENDIENTE", "observaciones": ""}
            for (nombre, tipo), ids in sorted(grupos.items())]


def exportar_csv(ruta_red, ruta_cobertura, ruta_salida):
    validar_red(ruta_red, ruta_cobertura)
    with open(ruta_red, encoding="utf-8") as archivo:
        red = json.load(archivo)
    filas = filas_sentidos_unicos(red)
    ruta_salida = Path(ruta_salida)
    ruta_salida.parent.mkdir(parents=True, exist_ok=True)
    with open(ruta_salida, "w", newline="", encoding="utf-8-sig") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=["calle", "tipo_via", "cantidad_tramos",
                                                         "identificadores_tramos", "verificacion_senalizacion",
                                                         "observaciones"])
        escritor.writeheader()
        escritor.writerows(filas)
    return filas


def main():
    parser = argparse.ArgumentParser(description="Lista tramos de sentido único a 1500 m de la Plaza Luis de Fuentes.")
    parser.add_argument("--red", type=Path, required=True)
    parser.add_argument("--cobertura", type=Path, required=True)
    parser.add_argument("--salida-csv", type=Path, required=True)
    args = parser.parse_args()
    try:
        filas = exportar_csv(args.red, args.cobertura, args.salida_csv)
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.exit(1, f"Error de preparación del CSV: {error}\n")
    print(f"Hoja creada: {len(filas)} grupos de calles de sentido único (pendientes de validación presencial). "
          f"Archivo: {args.salida_csv}")


if __name__ == "__main__":
    main()
