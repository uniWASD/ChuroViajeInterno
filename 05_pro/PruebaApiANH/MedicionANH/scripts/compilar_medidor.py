#!/usr/bin/env python3
"""Compila MedicionANH.java a medicion-anh.jar sin Maven/Gradle.
El JAR es un artefacto local ignorado por Git.
"""
from pathlib import Path
import shutil, subprocess, sys, zipfile

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'java'/'MedicionANH.java'
BUILD=ROOT/'build'
JAR=ROOT/'medicion-anh.jar'

def main():
    for cmd in ('javac','jar'):
        if not shutil.which(cmd):
            print(f'ERROR: falta {cmd} en PATH; se requiere JDK 17+.', file=sys.stderr)
            return 2
    if not SRC.is_file():
        print(f'ERROR: falta {SRC}.', file=sys.stderr); return 2
    BUILD.mkdir(parents=True,exist_ok=True)
    r=subprocess.run(['javac','--release','17','-encoding','UTF-8','-d',str(BUILD),str(SRC)])
    if r.returncode: return r.returncode
    if JAR.exists(): JAR.unlink()
    r=subprocess.run(['jar','--create','--file',str(JAR),'--main-class','MedicionANH','-C',str(BUILD),'.'])
    if r.returncode: return r.returncode
    try:
        with zipfile.ZipFile(JAR) as z:
            if 'MedicionANH.class' not in z.namelist(): raise RuntimeError('falta MedicionANH.class')
            if 'Main-Class: MedicionANH' not in z.read('META-INF/MANIFEST.MF').decode('utf-8'):
                raise RuntimeError('manifiesto sin Main-Class')
    except Exception as exc:
        print(f'ERROR: JAR inválido: {exc}', file=sys.stderr); return 2
    print(f'JAR generado localmente: {JAR}')
    return 0

if __name__=='__main__':
    raise SystemExit(main())
