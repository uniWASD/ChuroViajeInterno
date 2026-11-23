r"""Auditoría de pares ANH / catálogo para T-118.

No aplica migraciones. Preserva el CSV de origen y produce un informe preliminar con
indicios documentales, referencias y comprobaciones geométricas. La revisión final
puede documentarse por delegación expresa del solicitante; no equivale a visita física.

Uso PowerShell (desde 05_pro/PruebaApiANH):
    python .\vinculacion\auditar_vinculos.py .\vinculos_revision_20261123\pares_propuestos.csv
"""
from __future__ import annotations

import argparse
import csv
from datetime import datetime, timezone
from pathlib import Path
from collections import Counter
import math
import sys

from proponer_vinculos import _abrir_csv, _numero, leer_catalogo, distancia_m, normalizar

FUENTE_CATALOGO = "04_imp/backend_java/src/main/resources/db/migration/V2__surtidores_tarija.sql"
FUENTE_ANH_DIRECTORIO = "https://www.anh.gob.bo/w2026/contenido.php?D=6&R=1&s=40"
FUENTE_YFPB_2015 = "https://www.anh.gob.bo/InsideFiles/Documentos/Documentos_Id-20-150310-0311-0.pdf"
FUENTE_LASVEGAS = "https://bo.near-place.com/surtidor-las-vegas-avenida-circunvalacion-tarija"
FUENTE_OSM_AMERICAS = "https://mapcarta.com/es/N4415811348"
FUENTE_PANAMERICANO = "https://www.anh.gob.bo/InsideFiles/Actividad/Dj/RA-2018/RADPS-ANH-DTJ-0008-2018.pdf"

# Dictamen preliminar basado en catálogo V2, datos del CSV de la extracción y fuentes públicas.
# La aprobación final queda en un archivo separado con comentario y trazabilidad de la delegación.
PARES_ESPERADOS = {
    2339: ("Tacuarandi", "FAVORABLE_PARA_REVISION", "Nombre distintivo y coordenadas coincidentes; el catálogo V2 reutilizó la ubicación ANH. Confirmar predio.", FUENTE_ANH_DIRECTORIO),
    2341: ("Ex Terminal", "FAVORABLE_PARA_REVISION", "La Terminal/Ex Terminal: V2 menciona expresamente id ANH 2341 y ubicación OSM. Verificar identidad comercial.", FUENTE_ANH_DIRECTORIO),
    2364: ("Sointa", "FAVORABLE_PARA_REVISION", "Nombre distintivo y coordenadas coincidentes; confirmar ubicación física.", FUENTE_ANH_DIRECTORIO),
    2388: ("Panamericano", "EXCEPCION_COORDENADAS", "V2 advierte discrepancia ANH de aproximadamente 651 m; el punto del catálogo procede de OSM. Confirmar que se trata del mismo establecimiento antes de vincular.", FUENTE_PANAMERICANO),
    2390: ("Agrupa", "FAVORABLE_PARA_REVISION", "Nombre distintivo y coordenadas coincidentes; confirmar predio.", FUENTE_ANH_DIRECTORIO),
    2472: ("El Portillo", "FAVORABLE_PARA_REVISION", "Nombre distintivo y coordenadas coincidentes; confirmar predio.", FUENTE_ANH_DIRECTORIO),
    2627: ("Moto Mendez", "EXCEPCION_COORDENADAS", "V2 advierte que el punto ANH cae fuera de la cubierta, a unos 221 m. El punto del catálogo procede de OSM; validar físicamente.", "https://www.openstreetmap.org/node/3207058741"),
    2688: ("San Jorge", "FAVORABLE_PARA_REVISION", "Nombre distintivo y coordenadas coincidentes; confirmar predio.", FUENTE_ANH_DIRECTORIO),
    2804: ("Don Daniel", "FAVORABLE_PARA_REVISION", "Nombre distintivo y coordenadas coincidentes; confirmar predio.", FUENTE_ANH_DIRECTORIO),
    2974: ("YPFB Parada Chaco", "FAVORABLE_PARA_REVISION", "V2 documenta equivalencia Presidente Arce/YPFB Parada Chaco con ANH id 2974 y OSM; confirmar ubicación.", FUENTE_YFPB_2015),
    2977: ("El Molle", "FAVORABLE_PARA_REVISION", "Nombre distintivo y coordenadas coincidentes; confirmar predio.", FUENTE_ANH_DIRECTORIO),
    2386: ("Surtidor Tarija", "EQUIVALENCIA_DOCUMENTADA_POR_CONFIRMAR", "ANH ubica Tarija S.R.L. en avenida Héroes de la Independencia, barrio San Martín; V2 describe Surtidor Tarija en esa zona. Comprobar que no sea otro predio.", FUENTE_ANH_DIRECTORIO),
    2642: ("Las Vegas", "IDENTIDAD_COMERCIAL_NO_DEMOSTRADA", "ANH: Av. Circunvalación, esquina Ayoroa/La Florida; Las Vegas figura en Av. Circunvalación. Compartir avenida no acredita identidad comercial. Falta equivalencia explícita y contraste de accesos.", FUENTE_ANH_DIRECTORIO + " | " + FUENTE_LASVEGAS),
    2911: ("Campesino", "EQUIVALENCIA_DOCUMENTADA_POR_CONFIRMAR", "ANH documentó LAS AMÉRICAS en la zona del Mercado Campesino; V2 registra Campesino = Las Américas/YPFB. Confirmar punto antes de migrar.", FUENTE_YFPB_2015 + " | " + FUENTE_OSM_AMERICAS),
}

CAMPOS_ANEXOS = [
    "dictamen_tecnico", "observacion_de_auditoria", "distancia_calculada_m",
    "diferencia_distancia_m", "latitud_catalogo", "longitud_catalogo",
    "mapa_anh", "mapa_catalogo", "fuente_evidencia", "accion_pendiente",
]


def geo_url(lat: float | None, lon: float | None) -> str:
    if lat is None or lon is None:
        return ""
    return f"https://www.openstreetmap.org/?mlat={lat:.7f}&mlon={lon:.7f}#map=18/{lat:.7f}/{lon:.7f}"


def auditar(origen: Path, destino_base: Path | None) -> Path:
    if not origen.is_file():
        raise ValueError(f"No existe {origen}")
    filas = _abrir_csv(origen)
    if not filas:
        raise ValueError("CSV vacío")
    required = {"anh_id", "nombre_anh", "latitud_anh", "longitud_anh", "surtidor", "distancia_m", "propuesta", "revisado"}
    faltan = required - set(filas[0].keys())
    if faltan:
        raise ValueError("Columnas faltantes o delimitador CSV incorrecto: " + ", ".join(sorted(faltan)))
    for f in filas:
        if any(k in f for k in CAMPOS_ANEXOS):
            raise ValueError("Entrada previamente auditada: debe utilizarse pares_propuestos.csv original")
    if len(filas) != 14:
        raise ValueError(f"La ejecución del 23/11/2026 indicó 14 pares urbanos; este archivo tiene {len(filas)}. No se continuará con un inventario distinto sin revisión")
    catalogo = {s.nombre: s for s in leer_catalogo()}
    if len(catalogo) != 14:
        raise ValueError(f"El catálogo V2 tiene {len(catalogo)} surtidores, se esperaban 14; revisar cambios de versión")
    ids = []
    nombres = []
    avisos = []
    enriched = []
    for n, fila in enumerate(filas, 2):
        try:
            anh_id = int(fila["anh_id"])
        except (ValueError, TypeError):
            raise ValueError(f"Fila {n}: anh_id inválido: {fila['anh_id']!r}")
        ids.append(anh_id)
        esperado = PARES_ESPERADOS.get(anh_id)
        if esperado is None:
            raise ValueError(f"Fila {n}: id ANH inesperado {anh_id}; requiere revisión adicional")
        surtidor = fila["surtidor"].strip()
        nombres.append(surtidor)
        if surtidor != esperado[0]:
            raise ValueError(f"Fila {n}: el ID {anh_id} aparece asociado a {surtidor!r}, no al par {esperado[0]!r} identificado; no aceptar")
        if surtidor not in catalogo:
            raise ValueError(f"Fila {n}: surtidor {surtidor!r} no existe en V2")
        lat = _numero(fila.get("latitud_anh"))
        lon = _numero(fila.get("longitud_anh"))
        d_in = _numero(fila.get("distancia_m"))
        if lat is None or lon is None or not -90 <= lat <= 90 or not -180 <= lon <= 180 or d_in is None or d_in < 0:
            raise ValueError(f"Fila {n}: latitud, longitud o distancia ANH no numéricas/inválidas; puede haberse alterado al guardar desde Excel")
        s = catalogo[surtidor]
        d_calc = distancia_m(lat, lon, s.latitud, s.longitud)
        d_diff = abs(d_calc - d_in)
        if d_diff > 1.0:
            raise ValueError(f"Fila {n}: distancia declarada {d_in:.2f} m y calculada {d_calc:.2f} m difieren > 1 m; posible corrupción del CSV")
        estado, nota, fuentes = esperado[1:]
        if normalizar(fila.get("revisado")) in {"si", "sí", "ok", "x", "s", "yes"}:
            avisos.append(f"ID {anh_id}: ya tiene un 'sí' en el archivo original; confirmar que lo autorizó una persona del equipo")
        elif fila.get("revisado", "").strip():
            avisos.append(f"ID {anh_id}: columna 'revisado' contiene valor no reconocido: {fila['revisado']}")
        enriched.append({**fila,
            "dictamen_tecnico": estado,
            "observacion_de_auditoria": nota,
            "distancia_calculada_m": f"{d_calc:.3f}",
            "diferencia_distancia_m": f"{d_diff:.3f}",
            "latitud_catalogo": f"{s.latitud:.7f}",
            "longitud_catalogo": f"{s.longitud:.7f}",
            "mapa_anh": geo_url(lat, lon),
            "mapa_catalogo": geo_url(s.latitud, s.longitud),
            "fuente_evidencia": fuentes,
            "accion_pendiente": "Confirmación presencial/documental por responsable humano y anotación de evidencia en comentario",
        })
    if len(set(ids)) != 14 or len(set(nombres)) != 14:
        raise ValueError("Se detectaron ANH ID o surtidores repetidos: no generar migración")
    if set(ids) != set(PARES_ESPERADOS):
        raise ValueError("El conjunto de IDs de ANH difiere de los 14 documentados; no continuar")
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    dest = (destino_base or origen.parent) / f"revision_tecnica_{stamp}"
    dest.mkdir(parents=True, exist_ok=False)
    ruta_csv = dest / "pares_para_revision.csv"
    with ruta_csv.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(filas[0].keys()) + CAMPOS_ANEXOS, delimiter=";")
        writer.writeheader()
        writer.writerows(enriched)
    cuenta = Counter(f["dictamen_tecnico"] for f in enriched)
    pendientes = [f for f in enriched if normalizar(f.get("revisado")) not in {"si", "sí", "s", "x", "ok", "yes"}]
    texto = [
        "# Dictamen técnico asistido — T-118 / RN-09.10",
        "",
        "**No es aprobación humana.** La herramienta valida integridad y compara evidencias; no asigna `revisado=si`, no genera SQL ni altera el CSV original.",
        "",
        f"Archivo de origen: `{origen}`",
        f"Pares urbanos examinados: **{len(enriched)}**; sin aprobación humana: **{len(pendientes)}**.",
        "",
        "## Resultado por pareja",
        "",
        "| anh_id | ANH | Surtidor | Distancia (m) | Dictamen técnico |",
        "|---:|---|---|---:|---|",
    ]
    for f in enriched:
        texto.append(f"| {f['anh_id']} | {f['nombre_anh'].replace('|','/')} | {f['surtidor']} | {f['distancia_calculada_m']} | {f['dictamen_tecnico']} |")
    texto += ["", "## Evidencias y recomendaciones", ""]
    for f in enriched:
        texto += [f"### {f['anh_id']} — {f['nombre_anh']}", f"- Catálogo: **{f['surtidor']}**.",
                  f"- Estado: **{f['dictamen_tecnico']}**; separación: **{f['distancia_calculada_m']} m**.",
                  f"- Evaluación: {f['observacion_de_auditoria']}",
                  f"- [Mapa ANH]({f['mapa_anh']}) | [Mapa catálogo]({f['mapa_catalogo']})",
                  f"- Fuente: {f['fuente_evidencia']}", ""]
    texto += ["## Condiciones antes de aplicar SQL", "",
              "1. Revisión humana documentada de identidad y ubicación de **cada** par, especialmente los cinco que presentan excepciones o divergencias.",
              "2. Completar `revisado` solo con `si` para pares que realmente se aprobaron, con `comentario` que registre evidencia y nombre del revisor.",
              "3. Abrir el CSV anotado con importación de `;` y preservación de decimales; no modificar el CSV fuente.",
              "4. Ejecutar `proponer_vinculos.py migracion ... --revisado-por ... --version 14` solo con el CSV revisado y aprobación registrada.",
              "5. Confirmar P-31 en la arquitectura únicamente con el equipo y después de aceptar la ubicación real (no por las distancias anómalas de ANH).",
              "6. P-29, P-30 y P-32 siguen sujetos a cierre formal de T-117; no establecer valores definitivos automáticamente.",
              "7. Ejecutar las pruebas JUnit y Flyway después de añadir V14; la ejecución anterior validó V1-V13 y no incluye vínculos reales.",
              "", "## Alertas de lectura", ""]
    texto.extend([f"- {a}" for a in avisos] or ["- Ninguna alerta estructural."])
    (dest / "DICTAMEN_T118.md").write_text("\n".join(texto) + "\n", encoding="utf-8")
    print("VALIDACIÓN ESTRUCTURAL: 14 identificadores únicos, 14 surtidores únicos, coincidencia de distancias y catálogo V2.")
    for estado in sorted(cuenta):
        print(f"  {estado}: {cuenta[estado]}")
    print(f"Pares sin aprobación humana: {len(pendientes)}")
    print(f"Archivos en: {dest}")
    return dest


def main() -> int:
    p = argparse.ArgumentParser(description="Genera informe y CSV de revisión de vínculos ANH. No aprueba ni altera la base de datos")
    p.add_argument("csv", type=Path, help="Ruta a pares_propuestos.csv")
    p.add_argument("--salida", type=Path, default=None, help="Directorio padre de salida, crea subdirectorio fechado")
    opts = p.parse_args()
    try:
        auditar(opts.csv, opts.salida)
        return 0
    except (OSError, ValueError) as e:
        print(f"ERROR DE REVISIÓN: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
