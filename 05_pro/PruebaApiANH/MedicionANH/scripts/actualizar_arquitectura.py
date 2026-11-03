#!/usr/bin/env python3
"""Actualiza exclusivamente los cuatro Markdown indicados en T-117, con evidencias COMPLETAS.
No usar con ZIP antiguo T-84; ejecutar sobre rama actual feature/T117 nacida de develop.
Por defecto solo muestra diff. Solo --aplicar escribe y crea copias fuera del repositorio.
"""
import argparse
import difflib
import hashlib
import json
import re
import shutil
from datetime import datetime, timezone
from pathlib import Path

PATHS = {
    'parametros': '03_des/02_Arquitectura/08_decisiones_parametros.md',
    'modelo': '03_des/02_Arquitectura/03_modelo_dominio.md',
    'contratos': '03_des/02_Arquitectura/05_contratos.md',
    'cu09': '03_des/02_Arquitectura/04_reglas/CU-09_ConsultarEstadoANH.md',
}


def pick(d, key, typ, minval, maxval):
    value = d.get(key)
    if type(value) is not typ or not minval <= value <= maxval:
        raise ValueError(f'{key}: debe ser {typ.__name__} entre {minval} y {maxval}.')
    return value


def line_row(doc, code, value):
    # Solo modifica la columna «Valor» y «Estado» de un parámetro existente.
    pat = re.compile(r'(?m)^(\|\s*'+re.escape(code)+r'\s*\|[^\n]+)$')
    matches = list(pat.finditer(doc))
    if len(matches)!=1: raise ValueError(f'Falta fila única {code}; develop es distinto, no se puede parchear.')
    raw = matches[0].group(1)
    parts = raw.split('|')
    if len(parts)!=7: raise ValueError(f'La fila {code} no tiene cinco columnas.')
    parts[-3] = ' '+value+' '
    parts[-2] = ' Confirmada '
    new = '|'.join(parts)
    return doc[:matches[0].start()] + new + doc[matches[0].end():]


def replace_model_row(doc, label, newval):
    pat = re.compile(r'(?m)^(\|\s*'+re.escape(label)+r'\s*\|)([^\n|]*)(\|[^\n]+)$')
    matches=list(pat.finditer(doc))
    if len(matches)!=1: raise ValueError(f'No se encuentra exactamente un valor inicial {label}.')
    m=matches[0]
    return doc[:m.start()]+m.group(1)+' '+newval+' '+m.group(3)+doc[m.end():]


def insert_once(doc, anchor, new, marker):
    if marker in doc: raise ValueError(f'Ya existe sección {marker}, revisión manual obligatoria.')
    if doc.count(anchor)!=1: raise ValueError(f'No se encuentra único anclaje {anchor!r}.')
    return doc.replace(anchor, new+'\n\n'+anchor)


def update(texts, config):
    if '## Backend con la API de ANH' not in texts['contratos']:
        raise ValueError('No se trata de la arquitectura develop con contrato ANH; no se modificará el ZIP T-84.')
    interval = pick(config,'P-29_minutos',int,10,1440)
    ttl = pick(config,'P-30_horas',int,1,72)
    timeout = pick(config,'P-32_timeout_segundos',int,1,120)
    retries = pick(config,'P-32_reintentos',int,0,2)
    pause = pick(config,'P-32_pausa_segundos',int,1,120)
    if not isinstance(config.get('condiciones_uso'), str) or len(config['condiciones_uso'].strip()) < 12:
        raise ValueError('condiciones_uso debe describir la evidencia de ANH, sin inventar términos.')
    if not isinstance(config.get('responsable'), str) or len(config['responsable'].strip()) < 3:
        raise ValueError('responsable debe identificar a quien revisó evidencias.')
    if not isinstance(config.get('disclaimer_http'), int) or not 100 <= config['disclaimer_http'] <=599:
        raise ValueError('disclaimer_http es obligatorio.')
    # Prohibir caracteres Markdown que podrían modificar columnas o crear encabezados involuntarios.
    for name in ('condiciones_uso','responsable'):
        if any(x in config[name] for x in ('\n','|','\r')): raise ValueError(f'{name} debe ser texto de una línea sin barras.')
    p32 = f'{timeout} segundos de espera por petición, {retries} reintento(s), {pause} segundo(s) de pausa entre peticiones; respetar Retry-After'
    out = dict(texts)
    for code, val in [('P-29',f'{interval} minutos entre inicios de ciclo ANH'),
                      ('P-30',f'{ttl} horas de vigencia desde la última lectura válida del backend (RN-09.17)'),
                      ('P-32',p32)]:
        out['parametros'] = line_row(out['parametros'],code,val)
    out['modelo'] = replace_model_row(out['modelo'], 'Vigencia del estado de un surtidor',
                                   f'6 horas sin dato nuevo para fuentes no ANH; para ANH {ttl} horas desde la última lectura válida del backend (P-30)')
    out['modelo'] = replace_model_row(out['modelo'], 'Ciclo de consulta a ANH',
                                   f'Cada {interval} minutos (P-29); el ciclo consulta catálogo, gasolina y diésel')
    out['modelo'] = replace_model_row(out['modelo'], 'Espera y reintentos de la consulta a ANH',p32)
    note = ('### T-117: parámetros y condiciones corroborados\n\n'
            f'- Evidencia: 48 h locales y 24 h Oracle verificadas mediante CP-157; revisión de {config["responsable"]}.\n'
            f'- P-29: {interval} minutos entre ciclos. P-30: {ttl} horas desde la lectura válida del backend. P-32: {p32}.\n'
            f'- `updated_at`: con zona explícita se respeta el desplazamiento recibido; si no contiene zona, se usa Tarija (America/La_Paz, UTC-04:00), de acuerdo con RN-09.13; si falta o es inválido, se descarta el dato.\n'
            f'- GET `/api/Disclaimer`: HTTP {config["disclaimer_http"]}. Condiciones de uso / cuotas / contacto: {config["condiciones_uso"]}.\n'
            '- Esta descripción no constituye autorización institucional para reutilizar el servicio.')
    out['contratos'] = insert_once(out['contratos'],'### Rutas que se usan',note,'### T-117: parámetros y condiciones corroborados')
    rule_note = ('### Valores confirmados por T-117\n\n'
                 f'- **P-29:** ciclo ANH cada {interval} minutos (RN-09.1).\n'
                 f'- **P-30:** vigencia ANH de {ttl} horas contadas desde la lectura válida en el backend; no desde `updated_at` (RN-09.17).\n'
                 f'- **P-32:** {p32} (RN-09.4).\n'
                 '- **RN-09.13:** si falta la zona de `updated_at`, se toma la de Tarija (America/La_Paz); una fecha inválida no se conserva.')
    out['cu09'] = insert_once(out['cu09'],'## Recorrido por Vista, Controlador y Modelo',rule_note,
                               '### Valores confirmados por T-117')
    return out


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo',required=True,help='Raíz de la rama actual basada en develop')
    p.add_argument('--resumen',required=True,help='resumen_medicion.json producido por analizar_medicion.py')
    p.add_argument('--decision',required=True,help='JSON de decisiones revisadas por el equipo')
    p.add_argument('--disclaimer',required=True,help='Evidencia real de GET /api/Disclaimer')
    p.add_argument('--aplicar',action='store_true',help='Aplicar cambios SOLO a los 4 .md de T-117')
    p.add_argument('--respaldos',help='Carpeta FUERA del repo para guardar originales, obligatoria con --aplicar')
    args=p.parse_args()
    summary=json.loads(Path(args.resumen).read_text(encoding='utf-8'))
    decision=json.loads(Path(args.decision).read_text(encoding='utf-8'))
    if summary.get('CP-157_apto') is not True:
        p.error('CP-157 no acreditado: faltan 48+24 h reales; los Markdown no se tocarán.')
    if not all(summary.get('etapas',{}).get(x,{}).get('es_entorno_real') for x in ('local','oracle')):
        p.error('Las mediciones simuladas no acreditan CP-157.')
    evidence=Path(args.disclaimer).read_text(encoding='utf-8')
    if 'http_status: '+str(decision.get('disclaimer_http')) not in evidence:
        p.error('La decisión no concuerda con la evidencia del GET /api/Disclaimer.')
    repo=Path(args.repo).resolve()
    if not (repo/'.git').exists():
        p.error('La raíz --repo debe contener una carpeta .git (checkout auténtico de develop).')
    if args.aplicar:
        if not args.respaldos: p.error('--respaldos externo obligatorio con --aplicar.')
        backups=Path(args.respaldos).resolve()
        if repo == backups or repo in backups.parents:
            p.error('Los respaldos deben guardarse FUERA del repositorio.')
    paths={k:repo/v for k,v in PATHS.items()}
    originals={k:path.read_bytes() for k,path in paths.items()}
    if any(b'\r\n' in content for content in originals.values()):
        p.error('Hay archivos con CRLF; no normalizarlos automáticamente.')
    text={k:content.decode('utf-8') for k,content in originals.items()}
    if '## Backend con la API de ANH' not in text['contratos']:
        p.error('La arquitectura es la versión T-84 desactualizada: se requiere la rama develop vigente.')
    revised=update(text,decision)
    for k in PATHS:
        diff=''.join(difflib.unified_diff(text[k].splitlines(keepends=True), revised[k].splitlines(keepends=True),
                                          fromfile=PATHS[k]+' (antes)',tofile=PATHS[k]+' (después)'))
        print(diff)
    if not args.aplicar:
        print('VISTA PREVIA; ningún archivo modificado. Use --aplicar con respaldos externos tras revisar el diff.')
        return
    backups.mkdir(parents=True,exist_ok=True)
    for k,path in paths.items():
        if path.read_bytes()!=originals[k]:
            raise RuntimeError(f'Cambió {path} entre lectura y escritura; NO aplicar.')
    # Generar respaldos antes de modificar ningún archivo.
    for k,path in paths.items():
        target=backups/PATHS[k]
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(originals[k])
    for k,path in paths.items():
        tmp=path.with_suffix(path.suffix+'.tmp-t117')
        tmp.write_text(revised[k],encoding='utf-8',newline='\n')
        tmp.replace(path)
    print('Actualizados exactamente 4 documentos autorizados, con respaldos externos. Revise git diff.')

if __name__=='__main__': main()
