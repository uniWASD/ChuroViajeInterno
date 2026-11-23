"""Verificador de cierre documental HU-14/T-118.

No consulta ANH, no modifica la base ni aplica migraciones. Comprueba el CSV revisado,
el snapshot ANH, la migración V14 y, opcionalmente, el log Maven de la prueba E2E.
"""
from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path

from proponer_vinculos import (
    _abrir_csv, _numero, leer_catalogo, leer_estaciones, pares_confirmados,
    validar_con_fuente_anh, distancia_m, normalizar, ErrorRevision,
)
from auditar_vinculos import PARES_ESPERADOS

RAIZ = Path(__file__).resolve().parents[3]
DECISIONES = RAIZ / "03_des/02_Arquitectura/08_decisiones_parametros.md"
MIGRACIONES = RAIZ / "04_imp/backend_java/src/main/resources/db/migration"
PRUEBA_E2E = RAIZ / (
    "04_imp/backend_java/src/test/java/com/churoviaje/backend/anh/"
    "ConsultaAnhVinculosRealesGetIntegracionTest.java"
)


def estado_parametros(ruta: Path) -> dict[str, str]:
    filas = ruta.read_text(encoding="utf-8").splitlines()
    resultado = {}
    for clave in ("P-29", "P-30", "P-31", "P-32"):
        matches = [line for line in filas if line.startswith("| " + clave + " |")]
        resultado[clave] = matches[0].split("|")[-2].strip() if len(matches) == 1 else "NO ENCONTRADO"
    return resultado


def validar_inventario_completo(filas: list[dict[str, str]], fuente_anh: Path) -> None:
    if len(filas) != len(PARES_ESPERADOS):
        raise ErrorRevision(f"El lote debe tener {len(PARES_ESPERADOS)} pares; se recibieron {len(filas)}")
    estaciones, _ = leer_estaciones(fuente_anh)
    original = {e.anh_id: e for e in estaciones}
    catalogo = {c.nombre: c for c in leer_catalogo()}
    vistos, nombres = set(), set()
    for numero, fila in enumerate(filas, 2):
        crudo = (fila.get("anh_id") or "").strip()
        if not crudo.isdigit() or int(crudo) not in PARES_ESPERADOS:
            raise ErrorRevision(f"Línea {numero}: ID ANH fuera del inventario: {crudo!r}")
        anh_id = int(crudo)
        if anh_id in vistos:
            raise ErrorRevision(f"Línea {numero}: ID ANH repetido {anh_id}")
        vistos.add(anh_id)
        nombre = (fila.get("surtidor") or "").strip()
        if nombre != PARES_ESPERADOS[anh_id][0] or nombre not in catalogo or nombre in nombres:
            raise ErrorRevision(f"Línea {numero}: surtidor {nombre!r} no coincide o está repetido")
        nombres.add(nombre)
        estacion = original.get(anh_id)
        if estacion is None or normalizar(estacion.nombre) != normalizar(fila.get("nombre_anh")):
            raise ErrorRevision(f"Línea {numero}: estación ANH {anh_id} no coincide con el snapshot fuente")

        corregidas = normalizar(fila.get("coordenadas_corregidas")) == "si"
        lat_raw = _numero(fila.get("latitud_anh_original")) if corregidas else _numero(fila.get("latitud_anh"))
        lon_raw = _numero(fila.get("longitud_anh_original")) if corregidas else _numero(fila.get("longitud_anh"))
        if lat_raw is None or lon_raw is None or estacion.latitud is None or estacion.longitud is None:
            raise ErrorRevision(f"Línea {numero}: coordenadas originales ausentes")
        if distancia_m(lat_raw, lon_raw, estacion.latitud, estacion.longitud) > 1:
            raise ErrorRevision(f"Línea {numero}: coordenadas ANH originales cambiadas para id {anh_id}")

        lat_eff, lon_eff = _numero(fila.get("latitud_anh")), _numero(fila.get("longitud_anh"))
        distancia = _numero(fila.get("distancia_m"))
        if lat_eff is None or lon_eff is None or distancia is None:
            raise ErrorRevision(f"Línea {numero}: coordenadas efectivas o distancia ausentes")
        calculada = distancia_m(lat_eff, lon_eff, catalogo[nombre].latitud, catalogo[nombre].longitud)
        if abs(calculada - distancia) > 1 or calculada > 50:
            raise ErrorRevision(f"Línea {numero}: distancia efectiva inválida o superior a P-31 para {nombre}")
        if corregidas:
            evidencia = (fila.get("evidencia_georreferenciacion") or "").strip()
            distancia_original = _numero(fila.get("distancia_original_m"))
            calculada_original = distancia_m(lat_raw, lon_raw, catalogo[nombre].latitud,
                                              catalogo[nombre].longitud)
            if not evidencia or distancia_original is None or abs(calculada_original - distancia_original) > 1:
                raise ErrorRevision(f"Línea {numero}: corrección geográfica sin evidencia/distancia original")
    if vistos != set(PARES_ESPERADOS):
        raise ErrorRevision("Faltan IDs ANH del inventario auditado")


# Windows PowerShell 5.1 puede escribir UTF-16 con Tee-Object. PowerShell 7
# y Maven también pueden producir UTF-8. Se conserva la evidencia original.
def leer_log_maven(ruta: Path | None) -> str:
    if ruta is None or not ruta.is_file():
        return ""
    bruto = ruta.read_bytes()
    if bruto.startswith((b"\xff\xfe", b"\xfe\xff")):
        texto = bruto.decode("utf-16", errors="replace")
    elif b"\x00" in bruto[:200]:
        # También admitir UTF-16 sin BOM (p. ej., un log exportado por otro editor).
        muestra = bruto[:200]
        encoding = "utf-16-le" if muestra[1::2].count(0) >= muestra[::2].count(0) else "utf-16-be"
        texto = bruto.decode(encoding, errors="replace")
    else:
        texto = bruto.decode("utf-8-sig", errors="replace")
    return re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", texto)


_RESUMEN = r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)"
_CLASES_REQUERIDAS = (
    "com.churoviaje.backend.anh.ConsultaAnhVinculosRealesGetIntegracionTest",
    "com.churoviaje.backend.persistencia.MigracionesPostgisTest",
)


def maven_acredita_v14(texto: str, suite: bool = False) -> bool:
    # Un nombre de clase en el log no acredita su ejecución. Exigir el resumen
    # individual de Surefire, el total final, Flyway V14 y el resultado Maven.
    builds = re.findall(r"(?m)^\s*\[INFO\] BUILD (SUCCESS|FAILURE)\s*$", texto)
    if not builds or builds[-1] != "SUCCESS":
        return False
    partes = re.split(r"(?m)^\s*\[INFO\] Results:\s*$", texto)
    if len(partes) < 2:
        return False
    total = re.search(r"(?m)^\s*\[(?:INFO|WARNING)\] " + _RESUMEN + r"\s*$", partes[-1])
    if total is None:
        return False
    ejecutadas, fallos, errores, omitidas = map(int, total.groups())
    if fallos or errores or omitidas > ejecutadas or ejecutadas - omitidas <= 0:
        return False
    # V5 añade una prueba a las 306 de V4. No aceptar una ejecución parcial
    # como si fuera la suite completa. Las pruebas del núcleo C++ pueden omitirse.
    if (suite and ejecutadas < 307) or (not suite and omitidas != 0):
        return False
    for clase in _CLASES_REQUERIDAS:
        patron = (r"(?m)^\s*\[(?:INFO|WARNING)\] " + _RESUMEN
                  + r",[^\r\n]* -- in " + re.escape(clase) + r"\s*$")
        filas = re.findall(patron, partes[-2])
        if not filas:
            return False
        n, f, e, o = map(int, filas[-1])
        if n <= 0 or f or e or o:
            return False
    return re.search(r"Successfully applied \d+ migrations[^\r\n]*now at version v14\b", partes[-2], re.I) is not None


def evaluar(pares: Path, fuente_anh: Path, decisiones: Path = DECISIONES,
            migraciones: Path = MIGRACIONES, evidencia_maven: Path | None = None,
            evidencia_suite: Path | None = None) -> dict:
    filas = _abrir_csv(pares)
    if not filas:
        raise ErrorRevision("CSV de vinculaciones vacío o no legible")
    validar_inventario_completo(filas, fuente_anh)
    catalogo = leer_catalogo()
    aprobadas = pares_confirmados(filas, catalogo)
    validar_con_fuente_anh(filas, fuente_anh)
    rechazadas = [f for f in filas if normalizar(f.get("revisado")) == "no"
                  and (f.get("comentario") or "").strip()]
    sin_decision = [f.get("anh_id") for f in filas if normalizar(f.get("revisado")) == ""
                    or (normalizar(f.get("revisado")) == "no" and not (f.get("comentario") or "").strip())]
    params = estado_parametros(decisiones)
    versiones = sorted(p.name for p in migraciones.glob("V*__vinculos_anh.sql"))
    aceptacion = all(v.lower().startswith("aceptado por solicitante") for v in params.values())
    pares_revisados = not sin_decision and all((f.get("comentario") or "").strip() for f in filas)
    v14 = any(v.startswith("V14__") for v in versiones)
    e2e_ejecutada = maven_acredita_v14(leer_log_maven(evidencia_maven))
    suite_ejecutada = maven_acredita_v14(leer_log_maven(evidencia_suite), suite=True)
    controles = {
        "origen_anh_validado": True,
        "parametros_aceptados_por_solicitante": aceptacion,
        "14_pares_revisados_con_evidencia_delegada": pares_revisados,
        "14_vinculos_aprobados": len(aprobadas) == len(PARES_ESPERADOS),
        "migracion_v14_presente": v14,
        "prueba_e2e_get_surtidores_implementada": PRUEBA_E2E.exists(),
        "prueba_e2e_get_surtidores_ejecutada": e2e_ejecutada,
        "suite_java_completa_ejecutada": suite_ejecutada,
    }
    return {
        "fecha_utc": datetime.now(timezone.utc).isoformat(),
        "total_filas": len(filas),
        "aprobadas_id": [a[0] for a in aprobadas],
        "rechazadas_con_comentario_id": [f.get("anh_id") for f in rechazadas],
        "sin_decision_id": sin_decision,
        "parametros": params,
        "archivos_migracion_vinculos": versiones,
        "evidencia_maven": str(evidencia_maven) if evidencia_maven else None,
        "evidencia_suite": str(evidencia_suite) if evidencia_suite else None,
        "controles": controles,
        "cierre_documental_listo": all(controles.values()),
        "limitacion": (
            "La revisión de pares es documental/geoespacial delegada por el solicitante; no representa visita presencial. "
            "El cierre E2E solo se marca ejecutado si se adjunta el log Maven de la nueva prueba con BUILD SUCCESS."
        ),
    }


def markdown(resultado: dict) -> str:
    return "\n".join([
        "# Estado de cierre — T-118 / HU-14 (consulta ANH)", "",
        "**Cierre técnico documental delegado por el solicitante; no sustituye visita física ni despliegue productivo.**", "",
        f"Generado UTC: {resultado['fecha_utc']}",
        f"Estaciones urbanas en CSV: **{resultado['total_filas']}**", "",
        f"Vínculos aprobados en revisión delegada: **{len(resultado['aprobadas_id'])}** "
        f"({', '.join(map(str, resultado['aprobadas_id'])) or 'ninguno'})",
        f"No vinculados con decisión documentada: **{len(resultado['rechazadas_con_comentario_id'])}**",
        f"Sin decisión o justificación: **{len(resultado['sin_decision_id'])}** "
        f"({', '.join(map(str, resultado['sin_decision_id'])) or 'ninguno'})", "",
        "## Decisiones de parámetros", "",
        "Los valores están aceptados por el solicitante y documentados en arquitectura.", "",
        "| Código | Estado en documento |", "|---|---|",
        *[f"| {k} | {v} |" for k, v in resultado["parametros"].items()], "",
        "## Controles automáticos", "",
        "| Control | Resultado |", "|---|---|",
        *[f"| {k} | {'OK' if v else 'PENDIENTE'} |" for k, v in resultado["controles"].items()], "",
        f"Migración de vínculos encontrada: {', '.join(resultado['archivos_migracion_vinculos']) or 'ninguna'}", "",
        f"**Cierre técnico: {'COMPLETO' if resultado['cierre_documental_listo'] else 'PENDIENTE'}**", "",
        "### Límites", "",
        resultado["limitacion"], "",
        "La prueba E2E se ejecuta con servidor ANH falso y PostGIS/Testcontainers; no es una prueba de producción.", "",
    ])


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pares", type=Path, required=True, help="CSV final revisado")
    parser.add_argument("--fuente-anh", type=Path, required=True, help="snapshot de la extracción ANH")
    parser.add_argument("--salida", type=Path, default=None, help="carpeta de reportes")
    parser.add_argument("--evidencia-maven", type=Path, default=None,
                        help="log Maven de ConsultaAnhVinculosRealesGetIntegracionTest")
    parser.add_argument("--evidencia-suite", type=Path, default=None,
                        help="log Maven de la suite completa clean test")
    parser.add_argument("--estricto", action="store_true", help="devuelve 2 mientras haya pendientes")
    args = parser.parse_args(argv)
    try:
        resultado = evaluar(args.pares, args.fuente_anh, evidencia_maven=args.evidencia_maven,
                            evidencia_suite=args.evidencia_suite)
        carpeta = args.salida or args.pares.parent / "estado_cierre"
        carpeta.mkdir(parents=True, exist_ok=True)
        (carpeta / "ESTADO_CIERRE.md").write_text(markdown(resultado), encoding="utf-8")
        (carpeta / "ESTADO_CIERRE.json").write_text(json.dumps(resultado, ensure_ascii=False, indent=2) + "\n",
                                                     encoding="utf-8")
        print(f"CSV: {resultado['total_filas']} filas; aprobadas {len(resultado['aprobadas_id'])}; "
              f"sin decisión {len(resultado['sin_decision_id'])}")
        for clave, valor in resultado["parametros"].items():
            print(f"{clave}: {valor}")
        print("CIERRE TÉCNICO:", "COMPLETO" if resultado["cierre_documental_listo"] else "PENDIENTE")
        print("Informe:", carpeta / "ESTADO_CIERRE.md")
        return 2 if args.estricto and not resultado["cierre_documental_listo"] else 0
    except (OSError, ValueError, ErrorRevision) as exc:
        print("DATOS INVÁLIDOS:", exc)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
