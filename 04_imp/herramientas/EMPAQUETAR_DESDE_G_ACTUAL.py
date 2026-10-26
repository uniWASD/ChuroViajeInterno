#!/usr/bin/env python3
"""Un solo ZIP de código actual desde G: (SIN alterar G: ni Git).
Incluye documentos complementarios de esta entrega si faltan en G.
El snapshot ANH solo se agrega si su SHA-256 esperado coincide.
"""
from __future__ import annotations
import argparse, hashlib, json, sys, zipfile
from datetime import datetime, timezone
from pathlib import Path
EXPECTED_ANH = "0214fb4252301b8acaea4edb7783b165a8f6c411dbbe35fa53ba09a2a6a9c320"

EXCLUDE_DIRS = {".git", "target", "__pycache__", ".idea", ".venv", "venv", "node_modules", ".gradle", "build"}
EXCLUDE_SUFFIX = {".pyc", ".pyo", ".class", ".zip", ".rar", ".7z", ".exe"}

def sha(p: Path):
    h = hashlib.sha256()
    with p.open("rb") as f:
        for block in iter(lambda: f.read(1_048_576), b""):
            h.update(block)
    return h.hexdigest()

def should_skip(rel: Path):
    if any(s in EXCLUDE_DIRS for s in rel.parts[:-1]): return True
    if rel.suffix.lower() in EXCLUDE_SUFFIX: return True
    name=rel.name.lower()
    if name == ".env" or name.startswith(".env.") or name.startswith("id_rsa") or name.endswith(".key"):
        return True
    return False

def main():
    a=argparse.ArgumentParser()
    a.add_argument("--proyecto-g",required=True,type=Path)
    a.add_argument("--snapshot-anh",required=True,type=Path)
    a.add_argument("--salida",required=True,type=Path)
    opts=a.parse_args()
    origen=opts.proyecto_g.resolve(strict=True)
    complemento=Path(__file__).resolve().parent.parent
    snap=opts.snapshot_anh.resolve(strict=True)
    destino=opts.salida.resolve()
    if sha(snap)!=EXPECTED_ANH:
        raise ValueError("Snapshot ANH distinto: no se empaqueta fuente no validada")
    if not (origen / "backend_java" / "pom.xml").is_file():
        raise FileNotFoundError("Falta pom.xml en G:")
    if not (origen / "evidencias" / "T089_VALIDACION_16_PRUEBAS.txt").is_file():
        raise FileNotFoundError("Falta el log completo de las 16 pruebas en G:")
    if destino.exists():
        raise FileExistsError("Ya existe el ZIP de salida: no sobrescribir " + str(destino))
    destino.parent.mkdir(parents=True,exist_ok=True)
    archivos={}
    # El contenido de G: tiene prioridad: no cambiar la versión actual del usuario
    for p in origen.rglob("*"):
        if not p.is_file(): continue
        rel=p.relative_to(origen)
        if should_skip(rel):continue
        archivos[rel.as_posix()] = p
    # La copia complementaria solo aporta faltantes (informes y propuestas), sin reemplazos
    added=[]
    for p in complemento.rglob("*"):
        if not p.is_file():continue
        rel=p.relative_to(complemento)
        if should_skip(rel):continue
        key=rel.as_posix()
        if key not in archivos:
            archivos[key]=p
            added.append(key)
    snapshot_key="evidencias/ANH_SNAPSHOT_ORIGINAL_20261024_VERIFICADO.json"
    if snapshot_key in archivos:
        raise ValueError("Ya existe un snapshot con ese nombre en G:, no duplicarlo")
    archivos[snapshot_key]=snap
    # Los nombres ZIP son internos; no se altera el directorio de origen
    readme=("# Acta automática del paquete derivado de G:\n\n"
            "Generado (UTC): "+datetime.now(timezone.utc).isoformat()+"\n\n"
            "- Origen: proyecto local G:; se preservaron archivos originales.\n"
            "- Completados únicamente archivos ausentes desde el kit de T-089.\n"
            "- Snapshot ANH SHA256: `"+EXPECTED_ANH+"`\n"
            "- Archivos copiados del proyecto y complemento: "+str(len(archivos))+"\n"
            "- Complementos no existentes en G:: "+str(len(added))+"\n"
            "- No se hizo Git commit, push, merge ni cambios de archivos del proyecto.\n"
            "- La aprobación de cobertura y estaciones aún está PENDIENTE.\n")
    with zipfile.ZipFile(destino,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=7) as z:
        for key,p in sorted(archivos.items()):
            z.write(p, "T089_ChuroViaje_04_imp/"+key)
        z.writestr("T089_ChuroViaje_04_imp/evidencias/ACTA_EMPAQUETADO_DESDE_G.md",readme)
    print(json.dumps({"zip":str(destino),"archivos":len(archivos)+1,"complementos":len(added),"snapshot_anh_sha256":EXPECTED_ANH,"zip_sha256":sha(destino)},ensure_ascii=False,indent=2))

if __name__ == '__main__':
    try: main()
    except Exception as exc:
        print('ERROR:',exc,file=sys.stderr)
        sys.exit(2)
