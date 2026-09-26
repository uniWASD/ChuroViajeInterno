from __future__ import annotations
import argparse
import hashlib
import json
import math
import os
import random
import re
import statistics
import sys
import time
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple
from urllib import error, parse, request
try:
    from zoneinfo import ZoneInfo
except ImportError:
    ZoneInfo = None
BASE_DIR = Path(__file__).resolve().parent
DEFAULT_CHATS = [BASE_DIR / 'Chats' / 'Chat1.txt', BASE_DIR / 'Chats' / 'Chat2.txt']
DEFAULT_OUTPUT = BASE_DIR / 'salida_cu06.json'
DEFAULT_BENCHMARK = BASE_DIR / '03_des' / 'HU08_CU06_benchmark.json'
INTERVALO_MINUTOS = 5
MAX_LLAMADAS_DIA = 24 * 60 // INTERVALO_MINUTOS
DEFAULT_PROVIDER = 'gemini'
TIMEOUT_API_SECONDS = 60
REINTENTOS_API = 5
MAX_REINTENTOS_429 = 1
RETRY_BASE_SECONDS = 1.5
RETRY_MAX_DELAY_SECONDS = 70.0
CHECKPOINT_VERSION = 3
DEFAULT_PARTIAL_EVERY = 10
DEFAULT_ULTIMOS_POR_CHAT = 25
PSEUDONYM_SALT = os.getenv('HU08_PSEUDONYM_SALT', 'hu08-cu06-v1')
PROVIDERS: Dict[str, Dict[str, Any]] = {'openai': {'name': 'OpenAI', 'model': 'gpt-5.6-luna', 'input_usd_per_m': 0.2, 'output_usd_per_m': 1.2, 'cached_input_usd_per_m': 0.02, 'free_api': 'No soportado', 'env_key': 'OPENAI_API_KEY'}, 'gemini': {'name': 'Google', 'model': 'gemini-3.1-flash-lite', 'input_usd_per_m': 0.25, 'output_usd_per_m': 1.5, 'cached_input_usd_per_m': None, 'free_api': 'Sí, sin costo financiero; límites activos varían por proyecto y se consultan en AI Studio', 'env_key': 'GEMINI_API_KEY'}, 'anthropic': {'name': 'Anthropic', 'model': 'claude-haiku-4-5-20251001', 'input_usd_per_m': 1.0, 'output_usd_per_m': 5.0, 'cached_input_usd_per_m': 0.1, 'free_api': 'Sin cuota API gratuita general documentada; uso vía créditos/prepago', 'env_key': 'ANTHROPIC_API_KEY'}}
SURTIDORES: Dict[str, List[str]] = {'Las Vegas': ['\\blas\\s*vegas\\b', '\\bla\\s*vegas\\b', '\\bvegas\\b', '\\bmas\\s*vegas\\b'], 'Don Daniel': ['\\bdon\\s*daniel\\b', '\\bdaniel\\b'], 'Tacuarandi': ['\\btacuarandi\\b', '\\btacurandi\\b'], 'Campesino': ['\\bcampesino\\b'], 'YPFB Morros Blancos': ['\\bypfb\\b.*\\bmorros\\b', '\\bmorros\\s*blancos\\b', '\\bypfb\\b(?!.*\\b(?:campesino|parada|chaco)\\b)'], 'Agrupa': ['\\bagrupa\\b'], 'El Portillo': ['\\bportillo\\b'], 'Moto Mendez': ['\\bmoto\\s*m[eé]ndez\\b', '\\bmoto\\s*mende[sz]\\b'], 'Ex Terminal': ['\\bex\\s*terminal\\b', '\\bexterminal\\b', '\\bla\\s*terminal\\b', '\\blaterminal\\b'], 'Sointa': ['\\bsointa\\b', '\\bsointar\\b'], 'Panamericano': ['\\bpanamericano\\b'], 'San Jorge': ['\\bsan\\s*jorge\\b', '\\bsan\\s*gorje\\b'], 'San Geronimo': ['\\bsan\\s*ger[oó]nimo\\b'], 'El Molle': ['\\bel\\s*molle\\b', '\\bmolle\\b'], 'Surtidor Tarija': ['\\bsurtidor\\s*tarija\\b', '\\bel\\s*tarija\\b'], 'San Martin': ['\\bsan\\s*mart[ií]n\\b'], 'Pimentel': ['\\bpimentel\\b'], 'Villanueva': ['\\bvillanueva\\b', '\\bvilla\\s*nueva\\b'], 'YPFB Parada Chaco': ['\\bparada\\s*chaco\\b'], 'Domingo Savio': ['\\bdomingo\\s*savio\\b'], 'Coliseo Universitario': ['\\bcoliseo\\s*universitario\\b', '\\bcoliseo\\s*univ\\b']}
COMBUSTIBLES: Dict[str, List[str]] = {'Gasolina Especial': ['\\bespecial\\b', '\\bclarita\\b', '\\bcalarita\\b', '\\bclara\\b', '\\bblanquita\\b', '\\bblanca\\b', '\\bblankita\\b', '\\blimpia\\b', '\\bbuena\\b(?=.*\\bgasolina\\b|\\bgaso\\b)', '\\bchura\\b', '\\blinda\\b', '\\bwena\\b'], 'Etanol': ['\\betanol\\b', '\\betanos\\b'], 'Gasolina Plus': ['\\bplus\\b', '\\bamarilla\\b'], 'Diesel': ['\\bdi[eé]ss?el\\b', '\\bdiessel\\b']}
CANON_SURTIDORES = list(SURTIDORES.keys())
CANON_COMBUSTIBLES = list(COMBUSTIBLES.keys())
PATRONES_PREGUNTA = re.compile('(?:\\?|alguien\\s+sabe|donde\\s+hay|dnd\\s+hay|d[oó]nde\\s+hay|saben\\s+d[oó]nde|no\\s+saben|avisen|reporten|alguien\\s+que\\s+(?:sepa|pase)|alguien\\s+sabe\\s+si|donde\\s+puedo|en\\s+qu[eé]\\s+surtidor|donde\\s+est[aá]n\\s+(?:vendiendo|cargando|repartiendo)|donde\\s*(?:ay|venden)|no\\s+saben\\s+donde|alguien\\s+carg[oó]|q\\s+surtidor|que\\s+surtidor)', re.IGNORECASE)
PATRONES_HAY = re.compile('(?:ya\\s*lleg[oó]\\s*gasolina|lleg[oó]\\s*gasolina|ya\\s*lleg[oó]|acabo\\s*de\\s*cargar|cargu[eé]|cargue|(?<!\\bno\\s)\\bhay\\s*(?:gasolina|etanol|especial|plus|la\\s*plus|diesel|di[eé]sel|la\\s*especial|la\\s*clarita|blanquita)|(?<!\\bno\\s)est[aá]n?\\s*(?:vendiendo|cargando|dando|descargando|repartiendo)|sin\\s*fila|no\\s*hay\\s*fila|venta\\s*normal|reci[eé]n\\s*(?:cargue|cargu[eé])|(?<!\\bno\\s)est[aá]\\s*(?:clarita|clara|blanquita|blanca|amarilla)|(?<!\\bno\\s)tiene[n]?\\s*(?:gasolina|(?:la\\s*)?especial|(?:la\\s*)?plus|etanol|(?:la\\s*)?clarita|di[eé]sel|amarilla|blanquita)|ya\\s*tiene|descarg(?:ando|aron)|\\bay\\s*(?:clarita|blanquita|diessel|etanol|gasolina)|\\bventa\\s+normal\\b)', re.IGNORECASE)
PATRONES_NO_HAY = re.compile('(?:se\\s*acab[oó]|se\\s*termin[oó]|ya\\s*no\\s*hay|\\bno\\s*hay\\b(?!\\s+(?:fila|cola|mucha\\s+fila|mucha\\s+cola))|\\bno\\s*tiene[n]?\\s*(?:gasolina|etanol|especial|plus|diesel|di[eé]sel|clarita|blanquita|amarilla)|\\bno\\s*est[aá]n\\s*(?:vendiendo|cargando|dando|descargando|repartiendo)|se\\s*fue\\s*la\\s*cisterna|\\bno\\s*lleg[oó]|\\bsin\\s*combustible|\\bsin\\s*gasolina)', re.IGNORECASE)
PATRON_RUIDO = re.compile('<multimedia omitido>|multimedia omitido|mensaje eliminado|se elimin[oó] este mensaje|imagen omitida|video omitido|audio omitido|sticker omitido|documento omitido|archivo adjunto|los mensajes y las llamadas est[aá]n cifrados|BEGIN:VCARD|END:VCARD|mensaje de voz omitido|\\.vcf\\b', re.IGNORECASE)
PATRON_SALUDO = re.compile('^(?:buen(?:os)?\\s+d[ií]as?|buenas\\s+(?:tardes|noches)|hola|holas|j+a+j+a+|xd+|gracias|ok|dale)[!. ]*$', re.IGNORECASE)
PATRONES_FILA = [('SIN_FILA', re.compile('\\bsin\\s+fila\\b|\\bno\\s+hay\\s+fila\\b|\\bvac[ií]o\\b|\\bvac[ií]a\\b', re.I)), ('CORTA', re.compile('\\bfila\\s+corta\\b|\\bpoca\\s+fila\\b|\\bpoquita\\s+fila\\b', re.I)), ('MEDIA', re.compile('\\bfila\\s+(?:media|mediana)\\b', re.I)), ('LARGA', re.compile('\\bfila\\s+larga\\b|\\bmucha\\s+fila\\b|\\bfil[oó]n\\b|\\bcola\\s+larga\\b', re.I))]
PATRON_CONTEXTO = re.compile('(?:\\bah[ií]\\b|\\ball[ií]\\b|\\bsigue\\b|\\bqueda\\b|\\bqued[aó]\\b|\\bnormal\\b|\\breci[eé]n\\b|\\bdicen\\b|\\bcreo\\b)', re.IGNORECASE)
PATRON_FORMATO1 = re.compile('^\\[(?P<fecha>\\d{1,2}/\\d{1,2}/\\d{2,4}),\\s+(?P<hora>\\d{1,2}:\\d{2}:\\d{2})\\s+(?P<ampm>AM|PM|am|pm)\\]\\s+(?:(?P<autor>[^:]+):\\s*(?P<mensaje>.*)|(?P<sistema>.*))$')
PATRON_FORMATO2 = re.compile('^(?P<fecha>\\d{1,2}/\\d{1,2}/\\d{2,4}),\\s+(?P<hora>\\d{1,2}:\\d{2})\\s*[-\\u2013\\u2014]\\s+(?:(?P<autor>[^:]+):\\s*(?P<mensaje>.*)|(?P<sistema>.*))$')
PATRON_FORMATO_ORIG = re.compile('^\\[?(?P<fecha>\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}),?\\s+(?P<hora>\\d{1,2}:\\d{2}(?::\\d{2})?)(?:\\s*(?P<ampm>[apAP]\\.\\s*m\\.|[apAP]m|AM|PM|am|pm))?\\]?\\s*[-\\u2013\\u2014]\\s+(?:(?P<autor>[^:]+):\\s*(?P<mensaje>.*)|(?P<sistema>.*))$')

def leer_texto_con_codificacion(path: Path) -> Tuple[str, str]:
    for encoding in ('utf-8-sig', 'utf-8', 'cp1252', 'latin-1'):
        try:
            return (path.read_text(encoding=encoding), encoding)
        except UnicodeDecodeError:
            continue
    return (path.read_text(encoding='utf-8', errors='replace'), 'utf-8-replace')

def normalizar_linea(linea: str) -> str:
    for caracter in ('\u202f', '\xa0', '\u200e', '\u200f'):
        linea = linea.replace(caracter, ' ')
    return linea.strip('\ufeff').strip()

def intentar_match(linea: str):
    for patron in (PATRON_FORMATO1, PATRON_FORMATO2, PATRON_FORMATO_ORIG):
        m = patron.match(linea)
        if m:
            return m
    return None

def _normalizar_ampm(ampm: str) -> str:
    x = (ampm or '').lower().replace('.', '').replace(' ', '')
    if x in ('am', 'pm'):
        return x.upper()
    return ''

def _inferir_orden_fecha_whatsapp(texto: str) -> str:
    dmy = 0
    mdy = 0
    vistos = 0
    for raw in texto.splitlines():
        linea = normalizar_linea(raw)
        m = intentar_match(linea)
        if not m:
            continue
        fecha = (m.groupdict().get('fecha') or '').replace('-', '/')
        parts = fecha.split('/')
        if len(parts) != 3:
            continue
        try:
            a, b, _ = [int(x) for x in parts]
        except ValueError:
            continue
        vistos += 1
        if a > 12 and b <= 12:
            dmy += 1
        elif b > 12 and a <= 12:
            mdy += 1
    if mdy > dmy:
        return 'MDY'
    return 'DMY'

def parse_datetime_whatsapp(fecha: str, hora: str, ampm: str='', date_order: str='DMY') -> Optional[datetime]:
    fecha = fecha.replace('-', '/')
    parts = fecha.split('/')
    if len(parts) != 3:
        return None
    try:
        a, b, year = [int(x) for x in parts]
        order = (date_order or 'DMY').upper()
        if order == 'MDY':
            month, day = (a, b)
        else:
            day, month = (a, b)
        if year < 100:
            year += 2000
        ampm_norm = _normalizar_ampm(ampm)
        formatos = ['%H:%M:%S', '%H:%M'] if not ampm_norm else ['%I:%M:%S %p', '%I:%M %p']
        value = hora if not ampm_norm else f'{hora} {ampm_norm}'
        dt_time = None
        for fmt in formatos:
            try:
                dt_time = datetime.strptime(value, fmt).time()
                break
            except ValueError:
                pass
        if dt_time is None:
            return None
        dt = datetime(year, month, day, dt_time.hour, dt_time.minute, dt_time.second)
        if ZoneInfo is not None:
            try:
                dt = dt.replace(tzinfo=ZoneInfo('America/La_Paz'))
            except Exception:
                pass
        return dt
    except (ValueError, TypeError):
        return None

def parsear_chat_whatsapp(path: Path) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
    texto, encoding = leer_texto_con_codificacion(path)
    date_order = _inferir_orden_fecha_whatsapp(texto)
    mensajes: List[Dict[str, Any]] = []
    lineas_sistema = 0
    actual: Optional[Dict[str, Any]] = None
    for raw in texto.splitlines():
        linea = normalizar_linea(raw)
        if not linea:
            continue
        match = intentar_match(linea)
        if match:
            gd = match.groupdict()
            if gd.get('autor'):
                dt = parse_datetime_whatsapp(gd.get('fecha', ''), gd.get('hora', ''), gd.get('ampm', ''), date_order=date_order)
                actual = {'archivo': path.name, 'fecha_raw': gd.get('fecha', ''), 'hora_raw': gd.get('hora', ''), 'ampm': gd.get('ampm', '') or '', 'datetime': dt, 'fecha': dt.isoformat() if dt else '', 'autor': gd.get('autor', '').strip(), 'mensaje': (gd.get('mensaje') or '').strip()}
                mensajes.append(actual)
            else:
                actual = None
                lineas_sistema += 1
        elif actual is not None:
            actual['mensaje'] += '\n' + linea
        else:
            lineas_sistema += 1
    for idx, msg in enumerate(mensajes, 1):
        msg['id'] = f'{path.stem}:{idx:06d}'
    timestamps_validos = sum((1 for m in mensajes if isinstance(m.get('datetime'), datetime)))
    return (mensajes, {'archivo': path.name, 'encoding': encoding, 'orden_fecha': date_order, 'mensajes_parseados': len(mensajes), 'timestamps_validos': timestamps_validos, 'timestamps_invalidos': len(mensajes) - timestamps_validos, 'lineas_sistema': lineas_sistema})
PATRON_EMAIL = re.compile('\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b', re.I)
PATRON_URL = re.compile('https?://\\S+|www\\.\\S+', re.I)
PATRON_TELEFONO = re.compile('(?<!\\w)(?:\\+?\\d[\\d\\s().-]{5,}\\d)(?!\\w)')

def pseudonimo_autor(autor: str) -> str:
    if not autor:
        return 'u_desconocido'
    digest = hashlib.sha256(f'{PSEUDONYM_SALT}|{autor}'.encode('utf-8', errors='ignore')).hexdigest()[:10]
    return f'u_{digest}'

def sanitizar_texto(texto: str) -> str:
    texto = PATRON_URL.sub('[URL]', texto)
    texto = PATRON_EMAIL.sub('[EMAIL]', texto)
    texto = PATRON_TELEFONO.sub('[TELEFONO]', texto)
    return texto.strip()

def detectar_todos(texto: str, catalogo: Dict[str, List[str]]) -> List[str]:
    lower = texto.lower()
    encontrados: List[str] = []
    for canon, patrones in catalogo.items():
        if any((re.search(p, lower, re.I) for p in patrones)):
            encontrados.append(canon)
    return encontrados

def detectar_surtidores(texto: str) -> List[str]:
    return detectar_todos(texto, SURTIDORES)

def detectar_combustibles(texto: str) -> List[str]:
    return detectar_todos(texto, COMBUSTIBLES)

def detectar_fila(texto: str) -> str:
    for nombre, patron in PATRONES_FILA:
        if patron.search(texto):
            return nombre
    return 'DESCONOCIDA'

def es_ruido(texto: str) -> bool:
    t = normalizar_linea(texto)
    return bool(PATRON_RUIDO.search(t) or PATRON_SALUDO.match(t))

def _event_from_local(msg: Dict[str, Any], surtidor: str, combustible: str, estado: str) -> Dict[str, Any]:
    return {'mensaje_id': msg['id'], 'surtidor': surtidor, 'combustible': combustible, 'estado': estado, 'fila': detectar_fila(msg['mensaje']), 'fecha': msg.get('fecha', ''), 'confianza': 96, 'evidencia': sanitizar_texto(msg['mensaje'])[:160], 'origen': 'preclasificador'}

def _discard_local(msg: Dict[str, Any], motivo: str, detalle: str) -> Dict[str, Any]:
    return {'mensaje_id': msg['id'], 'motivo': motivo, 'fecha': msg.get('fecha', ''), 'detalle': detalle[:160], 'origen': 'preclasificador'}

def preclasificar(msg: Dict[str, Any]) -> Dict[str, Any]:
    texto = normalizar_linea(str(msg.get('mensaje', '')))
    if len(texto) < 4 or es_ruido(texto):
        return {'decision': 'descartar', 'payload': _discard_local(msg, 'NO_RELEVANTE', 'Ruido/saludo/multimedia')}
    surtidores = detectar_surtidores(texto)
    combustibles = detectar_combustibles(texto)
    relevancia_combustible = bool(re.search('\\b(?:gasolina|combustible|di[eé]s?el|diessel|etanol|plus|especial|clarita|blanquita|amarilla)\\b', texto, re.I))
    if PATRONES_PREGUNTA.search(texto):
        if surtidores or combustibles or relevancia_combustible:
            return {'decision': 'descartar', 'payload': _discard_local(msg, 'PREGUNTA', 'Solicitud/pregunta; no confirma estado')}
        if PATRON_CONTEXTO.search(texto):
            return {'decision': 'llm', 'razon': 'Pregunta elíptica: requiere contexto inmediato'}
        return {'decision': 'descartar', 'payload': _discard_local(msg, 'NO_RELEVANTE', 'Pregunta sin relación identificable con combustible/surtidores')}
    hay = bool(PATRONES_HAY.search(texto))
    texto_sin_no_hay = re.sub('\\bno\\s+hay\\b', ' ', texto, flags=re.I)
    if surtidores and re.search('\\bhay\\b', texto_sin_no_hay, re.I):
        hay = True
    no_hay = bool(PATRONES_NO_HAY.search(texto))
    if hay and no_hay:
        return {'decision': 'llm', 'razon': 'Evidencia contradictoria en el mismo mensaje'}
    if hay ^ no_hay and len(surtidores) == 1 and (len(combustibles) <= 1):
        estado = 'HAY' if hay else 'NO_HAY'
        combustible = combustibles[0] if combustibles else 'NO_ESPECIFICADO'
        return {'decision': 'resolver', 'payload': _event_from_local(msg, surtidores[0], combustible, estado)}
    if hay or no_hay or surtidores or combustibles or PATRON_CONTEXTO.search(texto):
        return {'decision': 'llm', 'razon': 'Requiere contexto o desambiguación semántica'}
    return {'decision': 'descartar', 'payload': _discard_local(msg, 'NO_RELEVANTE', 'Sin relación identificable con combustible/surtidores')}
OUTPUT_SCHEMA: Dict[str, Any] = {'type': 'object', 'properties': {'eventos': {'type': 'array', 'items': {'type': 'object', 'properties': {'mensaje_id': {'type': 'string'}, 'surtidor': {'type': 'string', 'enum': CANON_SURTIDORES + ['NO_IDENTIFICADO']}, 'combustible': {'type': 'string', 'enum': CANON_COMBUSTIBLES + ['NO_ESPECIFICADO']}, 'estado': {'type': 'string', 'enum': ['HAY', 'NO_HAY', 'CONTRADICTORIO', 'DESCONOCIDO']}, 'fila': {'type': 'string', 'enum': ['SIN_FILA', 'CORTA', 'MEDIA', 'LARGA', 'DESCONOCIDA']}, 'fecha': {'type': 'string'}, 'confianza': {'type': 'integer', 'minimum': 0, 'maximum': 100}, 'evidencia': {'type': 'string'}}, 'required': ['mensaje_id', 'surtidor', 'combustible', 'estado', 'fila', 'fecha', 'confianza', 'evidencia'], 'additionalProperties': False}}, 'mensajes_descartados': {'type': 'array', 'items': {'type': 'object', 'properties': {'mensaje_id': {'type': 'string'}, 'motivo': {'type': 'string', 'enum': ['PREGUNTA', 'AMBIGUO', 'NO_RELEVANTE']}, 'fecha': {'type': 'string'}, 'detalle': {'type': 'string'}}, 'required': ['mensaje_id', 'motivo', 'fecha', 'detalle'], 'additionalProperties': False}}}, 'required': ['eventos', 'mensajes_descartados'], 'additionalProperties': False}

def _catalogo_para_prompt() -> str:
    partes = []
    for nombre, patrones in SURTIDORES.items():
        alias_legibles = []
        for p in patrones:
            limpio = p.replace('\\b', '').replace('\\s*', ' ').replace('\\s+', ' ')
            limpio = limpio.replace('(?:', '').replace(')', '').replace('?', '')
            alias_legibles.append(limpio)
        partes.append(f"- {nombre}: {', '.join(alias_legibles[:4])}")
    return '\n'.join(partes)
SYSTEM_PROMPT = f'Eres un clasificador de mensajes de grupos de WhatsApp de Tarija, Bolivia, para monitorear surtidores de combustible.\nTu tarea es EXTRAER HECHOS, no conversar. Debes entender español informal, abreviaturas, errores ortográficos y regionalismos.\n\nREGLAS CRITICAS:\n1. No inventes surtidores, combustibles, fechas, filas ni estados.\n2. Analiza únicamente mensajes con analizar=true. Los de analizar=false son contexto y NUNCA deben aparecer como evento/descartado.\n3. Una pregunta o solicitud explícita sobre combustible/surtidor ("dnd hay", "avisen", "alguien sabe") NO confirma disponibilidad: descártala como PREGUNTA.\n3a. Si una frase interrogativa depende de un antecedente ausente (por ejemplo "ahí sigue?") y no identifica qué combustible/surtidor consulta, usa AMBIGUO; no inventes el antecedente.\n4. Si un mensaje afirma claramente disponibilidad: estado=HAY. Si afirma agotado/no disponible: estado=NO_HAY.\n4a. Expresiones vagas como "está normal", "ahí sigue", "por ahí", "dicen", "creo" o similares NO bastan por sí solas para declarar HAY/NO_HAY; si falta evidencia explícita, usa AMBIGUO.\n5. Si el mismo mensaje contiene afirmaciones positiva y negativa sobre el mismo objetivo, decide así:\n   - CONTRADICTORIO cuando coexisten versiones incompatibles sin resolución fiable (por ejemplo "hay, pero otro dice que no" o "dicen que ya se acabó").\n   - NO_HAY cuando el propio mensaje establece una secuencia temporal o retractación inequívoca cuyo estado final es falta de combustible (por ejemplo "llegó pero se acabó al toque", "está vendiendo... mentira, ya no hay", "al final quedó sin combustible").\n   - HAY solo si la secuencia inequívoca termina confirmando disponibilidad actual.\n   No elijas una cláusula solo por aparecer al final: debe existir una señal temporal o retractación explícita.\n6. Si la frase no permite afirmar un estado con suficiente seguridad: descártala como AMBIGUO.\n7. Si no tiene relación con combustible/surtidores: NO_RELEVANTE.\n8. Si no se indica tipo de combustible, usa NO_ESPECIFICADO. Si no se puede identificar el surtidor, NO_IDENTIFICADO.\n9. Cada entrada incluye una ventana lógica. Puedes resolver pronombres/referencias usando SOLO mensajes de la misma ventana lógica. Nunca relaciones mensajes de ventanas distintas. Si no existe un antecedente explícito suficiente, no lo inventes. Si infieres por contexto, confianza <= 85.\n10. Conserva la fecha de entrada exactamente. No completes datos faltantes.\n11. Evidencia: máximo 160 caracteres y solo una paráfrasis breve del texto que sustenta la clasificación.\n12. Responde exclusivamente con el JSON que cumple el esquema solicitado.\n\nCATALOGO CANONICO DE SURTIDORES Y ALIAS:\n{_catalogo_para_prompt()}\n\nCOMBUSTIBLES Y ALIAS PRINCIPALES:\n- Gasolina Especial: especial, clarita, calarita, clara, blanquita, blanca, blankita, limpia, chura, linda, wena.\n- Etanol: etanol, etanos.\n- Gasolina Plus: plus, amarilla.\n- Diesel: diesel, diésel, diessel.\n\nFILA:\n- SIN_FILA: sin fila/no hay fila.\n- CORTA: poca/poquita/fila corta.\n- MEDIA: fila media/mediana.\n- LARGA: mucha fila/fila larga/filón/cola larga.\n- DESCONOCIDA: si no existe evidencia explícita.\n'

def construir_payload_mensajes(mensajes: Sequence[Dict[str, Any]], objetivos: set[str]) -> List[Dict[str, Any]]:
    salida = []
    for m in mensajes:
        salida.append({'id': m['id'], 'fecha': m.get('fecha', ''), 'usuario': pseudonimo_autor(m.get('autor', '')), 'analizar': m['id'] in objetivos, 'texto': sanitizar_texto(m.get('mensaje', ''))})
    return salida

class LLMError(RuntimeError):

    def __init__(self, message: str, trace: Optional[List[Dict[str, Any]]]=None):
        super().__init__(message)
        self.trace = list(trace or [])

@dataclass
class LLMResult:
    provider: str
    model: str
    data: Dict[str, Any]
    input_tokens: int
    output_tokens: int
    latency_ms: float
    raw_usage: Dict[str, Any]
    request_trace: Optional[List[Dict[str, Any]]] = None

    @property
    def cost_usd(self) -> float:
        cfg = PROVIDERS[self.provider]
        return self.input_tokens / 1000000 * cfg['input_usd_per_m'] + self.output_tokens / 1000000 * cfg['output_usd_per_m']

def _parse_retry_hint_seconds(retry_after: Optional[str]=None, error_body: Optional[str]=None) -> Optional[float]:
    candidates: List[float] = []
    if retry_after:
        try:
            value = float(str(retry_after).strip())
            if value >= 0:
                candidates.append(value)
        except (TypeError, ValueError):
            pass
    body = str(error_body or '')
    if body:
        for m in re.finditer('retry\\s+in\\s+([0-9]+(?:\\.[0-9]+)?)s', body, re.I):
            try:
                candidates.append(float(m.group(1)))
            except ValueError:
                pass
        try:
            obj = json.loads(body)
        except Exception:
            obj = None

        def walk(x: Any) -> None:
            if isinstance(x, dict):
                for k, v in x.items():
                    if k in {'retryDelay', 'retry_delay'} and isinstance(v, str):
                        mm = re.fullmatch('\\s*([0-9]+(?:\\.[0-9]+)?)s\\s*', v)
                        if mm:
                            try:
                                candidates.append(float(mm.group(1)))
                            except ValueError:
                                pass
                    walk(v)
            elif isinstance(x, list):
                for v in x:
                    walk(v)
        if obj is not None:
            walk(obj)
    if not candidates:
        return None
    return max(candidates)

def _quota_scope_from_error_body(error_body: Optional[str]) -> str:
    body = str(error_body or '')
    hay = body.lower()
    try:
        obj = json.loads(body)
    except Exception:
        obj = None
    values: List[str] = []

    def walk(x: Any) -> None:
        if isinstance(x, dict):
            for k, v in x.items():
                if isinstance(v, (str, int, float)):
                    values.append(f'{k}:{v}')
                walk(v)
        elif isinstance(x, list):
            for v in x:
                walk(v)
    if obj is not None:
        walk(obj)
    joined = (hay + ' ' + ' '.join(values)).lower()
    if re.search('per.?day|requestsperday|daily.?quota|request(?:s)? per day', joined):
        return 'daily'
    if re.search('per.?minute|requestsperminute|request(?:s)? per minute|rpm', joined):
        return 'minute'
    if re.search('per.?second|requestspersecond|request(?:s)? per second', joined):
        return 'second'
    if re.search('token.*per.?minute|inputtokensper|tpm', joined):
        return 'token'
    return 'unknown'

def _retry_delay_seconds(attempt_index: int, retry_after: Optional[str]=None, error_body: Optional[str]=None) -> float:
    hinted = _parse_retry_hint_seconds(retry_after, error_body)
    if hinted is not None:
        return round(min(hinted + random.uniform(0.35, 0.85), RETRY_MAX_DELAY_SECONDS), 3)
    base = min(RETRY_BASE_SECONDS * 2 ** attempt_index, RETRY_MAX_DELAY_SECONDS)
    return round(base + random.uniform(0.0, min(1.0, base * 0.25)), 3)

def _post_json(url: str, headers: Dict[str, str], payload: Dict[str, Any], timeout: int=TIMEOUT_API_SECONDS) -> Dict[str, Any]:
    body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
    trace: List[Dict[str, Any]] = []
    transient_http = {408, 409, 429, 500, 502, 503, 504}
    for intento in range(REINTENTOS_API):
        req = request.Request(url, data=body, headers=headers, method='POST')
        t_attempt = time.perf_counter()
        try:
            with request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read().decode('utf-8')
                elapsed = (time.perf_counter() - t_attempt) * 1000
                trace.append({'attempt': intento + 1, 'status': 'OK', 'http_status': int(getattr(resp, 'status', 200) or 200), 'latency_ms': round(elapsed, 2), 'retry': False})
                data = json.loads(raw)
                if isinstance(data, dict):
                    data['__client_trace__'] = trace
                return data
        except error.HTTPError as exc:
            detail = exc.read().decode('utf-8', errors='replace')
            elapsed = (time.perf_counter() - t_attempt) * 1000
            is_transient = exc.code in transient_http
            quota_scope = _quota_scope_from_error_body(detail) if exc.code == 429 else None
            if exc.code == 429:
                previous_429 = sum((1 for t in trace if t.get('http_status') == 429))
                can_retry = quota_scope != 'daily' and previous_429 < MAX_REINTENTOS_429 and (intento < REINTENTOS_API - 1)
            else:
                can_retry = is_transient and intento < REINTENTOS_API - 1
            delay = _retry_delay_seconds(intento, exc.headers.get('Retry-After') if exc.headers else None, detail) if can_retry else 0.0
            trace.append({'attempt': intento + 1, 'status': 'HTTP_ERROR', 'http_status': exc.code, 'latency_ms': round(elapsed, 2), 'retry': can_retry, 'retry_after_s': delay, 'quota_scope': quota_scope, 'error': detail[:300]})
            if not can_retry:
                raise LLMError(f'HTTP {exc.code}: {detail[:1000]}', trace=trace) from exc
            time.sleep(delay)
        except (error.URLError, TimeoutError) as exc:
            elapsed = (time.perf_counter() - t_attempt) * 1000
            can_retry = intento < REINTENTOS_API - 1
            delay = _retry_delay_seconds(intento) if can_retry else 0.0
            trace.append({'attempt': intento + 1, 'status': 'NETWORK_ERROR', 'http_status': None, 'latency_ms': round(elapsed, 2), 'retry': can_retry, 'retry_after_s': delay, 'error': str(exc)[:300]})
            if not can_retry:
                raise LLMError(f'Error de red: {exc}', trace=trace) from exc
            time.sleep(delay)
    raise LLMError('Fallo desconocido', trace=trace)

def _extract_openai_output_text(resp: Dict[str, Any]) -> str:
    if isinstance(resp.get('output_text'), str):
        return resp['output_text']
    texts: List[str] = []
    for item in resp.get('output', []) or []:
        for content in item.get('content', []) or []:
            if content.get('type') in ('output_text', 'text') and isinstance(content.get('text'), str):
                texts.append(content['text'])
    if not texts:
        raise LLMError('OpenAI no devolvió output_text')
    return '\n'.join(texts)

def call_openai(mensajes: List[Dict[str, Any]], model: Optional[str]=None) -> LLMResult:
    key = os.getenv('OPENAI_API_KEY')
    if not key:
        raise LLMError('Falta OPENAI_API_KEY')
    model = model or PROVIDERS['openai']['model']
    user_text = 'Analiza este lote:\n' + json.dumps(mensajes, ensure_ascii=False, separators=(',', ':'))
    payload = {'model': model, 'store': False, 'reasoning': {'effort': 'none'}, 'input': [{'role': 'system', 'content': [{'type': 'input_text', 'text': SYSTEM_PROMPT}]}, {'role': 'user', 'content': [{'type': 'input_text', 'text': user_text}]}], 'text': {'format': {'type': 'json_schema', 'name': 'analisis_surtidores', 'strict': True, 'schema': OUTPUT_SCHEMA}}}
    t0 = time.perf_counter()
    resp = _post_json('https://api.openai.com/v1/responses', {'Authorization': f'Bearer {key}', 'Content-Type': 'application/json'}, payload)
    latency = (time.perf_counter() - t0) * 1000
    trace = resp.pop('__client_trace__', []) if isinstance(resp, dict) else []
    text = _extract_openai_output_text(resp)
    data = json.loads(text)
    usage = resp.get('usage', {}) or {}
    return LLMResult(provider='openai', model=model, data=data, input_tokens=int(usage.get('input_tokens', 0) or 0), output_tokens=int(usage.get('output_tokens', 0) or 0), latency_ms=latency, raw_usage=usage, request_trace=trace)

def _gemini_api_key() -> Optional[str]:
    return os.getenv('GOOGLE_API_KEY') or os.getenv('GEMINI_API_KEY')

def call_gemini(mensajes: List[Dict[str, Any]], model: Optional[str]=None) -> LLMResult:
    key = _gemini_api_key()
    if not key:
        raise LLMError('Falta GEMINI_API_KEY o GOOGLE_API_KEY')
    model = model or PROVIDERS['gemini']['model']
    user_text = 'Analiza este lote:\n' + json.dumps(mensajes, ensure_ascii=False, separators=(',', ':'))
    payload = {'systemInstruction': {'parts': [{'text': SYSTEM_PROMPT}]}, 'contents': [{'role': 'user', 'parts': [{'text': user_text}]}], 'generationConfig': {'thinkingConfig': {'thinkingLevel': 'MINIMAL'}, 'responseFormat': {'text': {'mimeType': 'APPLICATION_JSON', 'schema': OUTPUT_SCHEMA}}}}
    url = f'https://generativelanguage.googleapis.com/v1beta/models/{parse.quote(model)}:generateContent'
    t0 = time.perf_counter()
    resp = _post_json(url, {'Content-Type': 'application/json', 'x-goog-api-key': key}, payload)
    latency = (time.perf_counter() - t0) * 1000
    trace = resp.pop('__client_trace__', []) if isinstance(resp, dict) else []
    candidates = resp.get('candidates', []) or []
    if not candidates:
        feedback = resp.get('promptFeedback', {}) or {}
        raise LLMError(f'Gemini sin candidates. promptFeedback={str(feedback)[:500]}')
    candidate = candidates[0]
    finish_reason = candidate.get('finishReason')
    if finish_reason not in (None, 'STOP'):
        raise LLMError(f"Gemini terminó con finishReason={finish_reason}: {str(candidate.get('finishMessage', ''))[:300]}")
    parts = candidate.get('content', {}).get('parts', []) or []
    text = '\n'.join((p.get('text', '') for p in parts if isinstance(p.get('text'), str)))
    if not text:
        raise LLMError('Gemini no devolvió texto')
    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        raise LLMError(f'Gemini devolvió JSON inválido: {exc}') from exc
    usage = resp.get('usageMetadata', {}) or {}
    prompt_tokens = int(usage.get('promptTokenCount', 0) or 0)
    candidate_tokens = int(usage.get('candidatesTokenCount', 0) or 0)
    thought_tokens = int(usage.get('thoughtsTokenCount', 0) or 0)
    return LLMResult(provider='gemini', model=model, data=data, input_tokens=prompt_tokens, output_tokens=candidate_tokens + thought_tokens, latency_ms=latency, raw_usage=usage, request_trace=trace)

def call_anthropic(mensajes: List[Dict[str, Any]], model: Optional[str]=None) -> LLMResult:
    key = os.getenv('ANTHROPIC_API_KEY')
    if not key:
        raise LLMError('Falta ANTHROPIC_API_KEY')
    model = model or PROVIDERS['anthropic']['model']
    user_text = 'Analiza este lote:\n' + json.dumps(mensajes, ensure_ascii=False, separators=(',', ':'))
    payload = {'model': model, 'max_tokens': 4096, 'system': SYSTEM_PROMPT, 'messages': [{'role': 'user', 'content': user_text}], 'output_config': {'format': {'type': 'json_schema', 'schema': OUTPUT_SCHEMA}}}
    t0 = time.perf_counter()
    resp = _post_json('https://api.anthropic.com/v1/messages', {'x-api-key': key, 'anthropic-version': '2023-06-01', 'Content-Type': 'application/json'}, payload)
    latency = (time.perf_counter() - t0) * 1000
    trace = resp.pop('__client_trace__', []) if isinstance(resp, dict) else []
    texts = [c.get('text', '') for c in resp.get('content', []) or [] if c.get('type') == 'text']
    if not texts:
        raise LLMError('Anthropic no devolvió bloque text')
    data = json.loads('\n'.join(texts))
    usage = resp.get('usage', {}) or {}
    return LLMResult(provider='anthropic', model=model, data=data, input_tokens=int(usage.get('input_tokens', 0) or 0), output_tokens=int(usage.get('output_tokens', 0) or 0), latency_ms=latency, raw_usage=usage, request_trace=trace)

def call_llm(provider: str, mensajes: List[Dict[str, Any]], model: Optional[str]=None) -> LLMResult:
    if provider == 'openai':
        return call_openai(mensajes, model)
    if provider == 'gemini':
        return call_gemini(mensajes, model)
    if provider == 'anthropic':
        return call_anthropic(mensajes, model)
    raise LLMError(f'Proveedor no soportado: {provider}')

def validar_salida_llm(data: Dict[str, Any], objetivos: set[str], fechas_esperadas: Optional[Dict[str, str]]=None) -> Dict[str, List[Dict[str, Any]]]:
    if not isinstance(data, dict):
        raise LLMError('Salida LLM no es objeto JSON')
    eventos = data.get('eventos', [])
    descartados = data.get('mensajes_descartados', [])
    if not isinstance(eventos, list) or not isinstance(descartados, list):
        raise LLMError('Salida LLM no cumple arrays esperados')
    clean_events: List[Dict[str, Any]] = []
    clean_discards: List[Dict[str, Any]] = []
    ids_cubiertos = set()
    for e in eventos:
        if not isinstance(e, dict) or e.get('mensaje_id') not in objetivos:
            continue
        if e.get('surtidor') not in CANON_SURTIDORES + ['NO_IDENTIFICADO']:
            continue
        if e.get('combustible') not in CANON_COMBUSTIBLES + ['NO_ESPECIFICADO']:
            continue
        if e.get('estado') not in ('HAY', 'NO_HAY', 'CONTRADICTORIO', 'DESCONOCIDO'):
            continue
        if e.get('estado') == 'DESCONOCIDO':
            mid = e['mensaje_id']
            clean_discards.append({'mensaje_id': mid, 'motivo': 'AMBIGUO', 'fecha': (fechas_esperadas or {}).get(mid, str(e.get('fecha', ''))), 'detalle': str(e.get('evidencia', ''))[:160] or 'Estado de disponibilidad no determinado', 'origen': 'validador'})
            ids_cubiertos.add(mid)
            continue
        if e.get('fila') not in ('SIN_FILA', 'CORTA', 'MEDIA', 'LARGA', 'DESCONOCIDA'):
            e['fila'] = 'DESCONOCIDA'
        e['confianza'] = max(0, min(100, int(e.get('confianza', 0) or 0)))
        e['evidencia'] = str(e.get('evidencia', ''))[:160]
        if fechas_esperadas and e['mensaje_id'] in fechas_esperadas:
            e['fecha'] = fechas_esperadas[e['mensaje_id']]
        e['origen'] = 'llm'
        firma = (e.get('mensaje_id'), e.get('surtidor'), e.get('combustible'), e.get('estado'), e.get('fila'))
        if not any(((x.get('mensaje_id'), x.get('surtidor'), x.get('combustible'), x.get('estado'), x.get('fila')) == firma for x in clean_events)):
            clean_events.append(e)
        ids_cubiertos.add(e['mensaje_id'])
    for d in descartados:
        if not isinstance(d, dict) or d.get('mensaje_id') not in objetivos:
            continue
        if d.get('mensaje_id') in ids_cubiertos:
            continue
        if d.get('motivo') not in ('PREGUNTA', 'AMBIGUO', 'NO_RELEVANTE'):
            continue
        d['detalle'] = str(d.get('detalle', ''))[:160]
        if fechas_esperadas and d['mensaje_id'] in fechas_esperadas:
            d['fecha'] = fechas_esperadas[d['mensaje_id']]
        d['origen'] = 'llm'
        clean_discards.append(d)
        ids_cubiertos.add(d['mensaje_id'])
    for mid in sorted(objetivos - ids_cubiertos):
        clean_discards.append({'mensaje_id': mid, 'motivo': 'AMBIGUO', 'fecha': (fechas_esperadas or {}).get(mid, ''), 'detalle': 'LLM no devolvió clasificación válida para este ID', 'origen': 'validador'})
    return {'eventos': clean_events, 'mensajes_descartados': clean_discards}

def _floor_window(dt: datetime, minutes: int=INTERVALO_MINUTOS) -> datetime:
    minute = dt.minute // minutes * minutes
    return dt.replace(minute=minute, second=0, microsecond=0)

def agrupar_ventanas(mensajes: Sequence[Dict[str, Any]]) -> List[Tuple[str, List[Dict[str, Any]]]]:
    grupos: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    for idx, m in enumerate(mensajes):
        dt = m.get('datetime')
        if isinstance(dt, datetime):
            key = _floor_window(dt).isoformat()
        else:
            key = f"sin_fecha:{m.get('archivo', '?')}:{idx // 25:05d}"
        grupos[key].append(m)
    return sorted(grupos.items(), key=lambda kv: kv[0])

def contexto_para_objetivos(batch: Sequence[Dict[str, Any]], objetivos: set[str], vecinos: int=1) -> List[Dict[str, Any]]:
    indices = [i for i, m in enumerate(batch) if m['id'] in objetivos]
    if not indices:
        return []
    incluir = set()
    for i in indices:
        for j in range(max(0, i - vecinos), min(len(batch), i + vecinos + 1)):
            incluir.add(j)
    return [batch[i] for i in sorted(incluir)]

def cargar_chats(paths: Sequence[Path], ultimos_por_chat: Optional[int]=None) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    if ultimos_por_chat is not None and ultimos_por_chat < 0:
        raise ValueError('ultimos_por_chat debe ser 0 o un entero positivo')
    all_messages: List[Dict[str, Any]] = []
    infos: List[Dict[str, Any]] = []
    for p in paths:
        if not p.exists():
            continue
        msgs, info = parsear_chat_whatsapp(p)
        total_disponible = len(msgs)
        if ultimos_por_chat:
            seleccionados = msgs[-ultimos_por_chat:]
        else:
            seleccionados = msgs
        info = dict(info)
        info['mensajes_disponibles'] = total_disponible
        info['mensajes_seleccionados'] = len(seleccionados)
        info['limite_ultimos_por_chat'] = ultimos_por_chat or None
        all_messages.extend(seleccionados)
        infos.append(info)
    all_messages.sort(key=lambda m: (m.get('fecha', '') or '9999', m.get('archivo', ''), m.get('id', '')))
    return (all_messages, infos)

def _analysis_fingerprint(paths: Sequence[Path], provider: str, model: Optional[str], ultimos_por_chat: Optional[int]=DEFAULT_ULTIMOS_POR_CHAT) -> str:
    files = []
    for raw in paths:
        p = Path(raw).resolve()
        if p.exists():
            st = p.stat()
            files.append({'path': str(p), 'size': int(st.st_size), 'mtime_ns': int(st.st_mtime_ns)})
        else:
            files.append({'path': str(p), 'missing': True})
    payload = {'files': files, 'provider': provider, 'model': model or (PROVIDERS.get(provider, {}).get('model') if provider != 'rules' else 'solo-reglas'), 'intervalo_minutos': INTERVALO_MINUTOS, 'ultimos_por_chat': ultimos_por_chat or None, 'checkpoint_version': CHECKPOINT_VERSION}
    raw = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')
    return hashlib.sha256(raw).hexdigest()

def _default_checkpoint_path(output_path: Path) -> Path:
    return output_path.with_name(output_path.stem + '.checkpoint.jsonl')

def _default_partial_path(output_path: Path) -> Path:
    return output_path.with_name(output_path.stem + '.partial.json')

def _atomic_write_json(path: Path, data: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + '.tmp')
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
    os.replace(tmp, path)

def _append_checkpoint_record(path: Path, record: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    line = json.dumps(record, ensure_ascii=False, separators=(',', ':')) + '\n'
    with path.open('a', encoding='utf-8', newline='\n') as fh:
        fh.write(line)
        fh.flush()
        try:
            os.fsync(fh.fileno())
        except OSError:
            pass

def _load_checkpoint(path: Path, expected_fingerprint: str) -> Tuple[Dict[str, Any], List[Dict[str, Any]]]:
    if not path.exists():
        raise FileNotFoundError(path)
    header: Optional[Dict[str, Any]] = None
    records: List[Dict[str, Any]] = []
    lines = path.read_text(encoding='utf-8').splitlines()
    for idx, line in enumerate(lines):
        if not line.strip():
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError:
            if idx == len(lines) - 1:
                break
            raise RuntimeError(f'Checkpoint corrupto en línea {idx + 1}: {path}')
        if obj.get('type') == 'header':
            header = obj
        elif obj.get('type') == 'window':
            records.append(obj)
    if not header:
        raise RuntimeError(f'Checkpoint sin cabecera válida: {path}')
    if header.get('fingerprint') != expected_fingerprint:
        raise RuntimeError('El checkpoint pertenece a otros chats/configuración. Usa --fresh para iniciar de cero o indica otro --checkpoint.')
    return (header, records)

def _format_duration(seconds: Optional[float]) -> str:
    if seconds is None or not math.isfinite(seconds) or seconds < 0:
        return '--:--:--'
    total = int(round(seconds))
    h, rem = divmod(total, 3600)
    m, s = divmod(rem, 60)
    return f'{h:02d}:{m:02d}:{s:02d}'

def _build_analysis_result(*, provider: str, model: Optional[str], mensajes_totales: int, parse_info: List[Dict[str, Any]], eventos: List[Dict[str, Any]], descartados: List[Dict[str, Any]], api_calls: int, api_attempts: int, api_retries: int, input_tokens: int, output_tokens: int, api_cost: float, latencies: List[float], request_trace: List[Dict[str, Any]], errores_api: List[Dict[str, Any]], batch_stats: List[Dict[str, Any]], status: str='COMPLETADO', progress: Optional[Dict[str, Any]]=None) -> Dict[str, Any]:
    ev = sorted(eventos, key=lambda e: (e.get('fecha', ''), e.get('mensaje_id', '')))
    de = sorted(descartados, key=lambda e: (e.get('fecha', ''), e.get('mensaje_id', '')))
    pending_ids = {str(x.get('mensaje_id')) for x in de if x.get('motivo') == 'PENDIENTE_API' and x.get('mensaje_id')}
    meta = {'caso': 'HU-08/CU-06', 'status': status, 'fuente': 'exportaciones .txt de WhatsApp', 'sin_conexion_whatsapp': True, 'provider': provider, 'model': model or PROVIDERS.get(provider, {}).get('model') if provider != 'rules' else 'solo-reglas', 'intervalo_minutos': INTERVALO_MINUTOS, 'max_llamadas_dia_por_intervalo': MAX_LLAMADAS_DIA, 'mensajes_totales': mensajes_totales, 'eventos': len(ev), 'mensajes_descartados': len(de), 'mensajes_pendientes_api': len(pending_ids), 'api_calls': api_calls, 'api_attempts': api_attempts, 'api_retries': api_retries, 'input_tokens': input_tokens, 'output_tokens': output_tokens, 'costo_api_usd': round(api_cost, 8), 'latencia_media_ms': round(statistics.mean(latencies), 2) if latencies else None, 'latencia_p95_ms': round(percentile(latencies, 95), 2) if latencies else None, 'errores_api': errores_api, 'request_trace': request_trace, 'parseo': parse_info, 'limite_ultimos_por_chat': parse_info[0].get('limite_ultimos_por_chat') if parse_info and all((x.get('limite_ultimos_por_chat') == parse_info[0].get('limite_ultimos_por_chat') for x in parse_info)) else None}
    if progress is not None:
        meta['progreso'] = progress
    return {'meta': meta, 'eventos': ev, 'mensajes_descartados': de, 'batches': batch_stats}

def analizar_chats(paths: Sequence[Path], provider: str, model: Optional[str], output_path: Path, *, checkpoint_path: Optional[Path]=None, resume: bool=False, fresh: bool=False, show_progress: bool=False, partial_every: int=DEFAULT_PARTIAL_EVERY, ultimos_por_chat: int=DEFAULT_ULTIMOS_POR_CHAT) -> Dict[str, Any]:
    mensajes, parse_info = cargar_chats(paths, ultimos_por_chat=ultimos_por_chat)
    if not mensajes:
        raise SystemExit('ERROR: No se encontraron mensajes. Verifica Chats/Chat1.txt y Chats/Chat2.txt o usa --chats.')
    output_path = Path(output_path)
    checkpoint_path = Path(checkpoint_path) if checkpoint_path else _default_checkpoint_path(output_path)
    partial_path = _default_partial_path(output_path)
    fingerprint = _analysis_fingerprint(paths, provider, model, ultimos_por_chat=ultimos_por_chat)
    ventanas = agrupar_ventanas(mensajes)
    total_ventanas = len(ventanas)
    eventos: List[Dict[str, Any]] = []
    descartados: List[Dict[str, Any]] = []
    api_calls = 0
    api_attempts = 0
    api_retries = 0
    input_tokens = 0
    output_tokens = 0
    api_cost = 0.0
    latencies: List[float] = []
    request_trace: List[Dict[str, Any]] = []
    errores_api: List[Dict[str, Any]] = []
    batch_stats: List[Dict[str, Any]] = []
    completed_windows: set[str] = set()
    processed_messages = 0
    if checkpoint_path.exists():
        if fresh:
            checkpoint_path.unlink(missing_ok=True)
            partial_path.unlink(missing_ok=True)
        elif resume:
            _, records = _load_checkpoint(checkpoint_path, fingerprint)
            for rec in records:
                ventana = str(rec.get('ventana', ''))
                if not ventana or ventana in completed_windows or rec.get('complete', True) is False:
                    continue
                completed_windows.add(ventana)
                eventos.extend(rec.get('eventos', []) or [])
                descartados.extend(rec.get('descartados', []) or [])
                batch_stats.append(rec.get('batch_stat', {}) or {})
                api_calls += int(rec.get('api_calls', 0) or 0)
                api_attempts += int(rec.get('api_attempts', 0) or 0)
                api_retries += int(rec.get('api_retries', 0) or 0)
                input_tokens += int(rec.get('input_tokens', 0) or 0)
                output_tokens += int(rec.get('output_tokens', 0) or 0)
                api_cost += float(rec.get('api_cost', 0.0) or 0.0)
                latencies.extend((float(x) for x in rec.get('latencies', []) or []))
                request_trace.extend(rec.get('request_trace', []) or [])
                errores_api.extend(rec.get('errores_api', []) or [])
                processed_messages += int(rec.get('mensajes', 0) or 0)
        else:
            raise RuntimeError(f'Existe un checkpoint previo: {checkpoint_path}. Usa --resume para continuarlo o --fresh para empezar desde cero.')
    if not checkpoint_path.exists():
        _append_checkpoint_record(checkpoint_path, {'type': 'header', 'version': CHECKPOINT_VERSION, 'fingerprint': fingerprint, 'provider': provider, 'model': model or PROVIDERS.get(provider, {}).get('model') if provider != 'rules' else 'solo-reglas', 'created_at': datetime.now().astimezone().isoformat(), 'output': str(output_path.resolve()), 'mensajes_totales': len(mensajes), 'ultimos_por_chat': ultimos_por_chat, 'ventanas_totales': total_ventanas, 'estrategia_llm': 'una_llamada_logica_por_ejecucion'})
    planes: List[Dict[str, Any]] = []
    local_total = 0
    llm_messages_total = 0
    llm_windows_total = 0
    for ventana, batch in ventanas:
        if ventana in completed_windows:
            continue
        objetivos: set[str] = set()
        rec_eventos: List[Dict[str, Any]] = []
        rec_descartados: List[Dict[str, Any]] = []
        local_events = 0
        local_discards = 0
        for msg in batch:
            pre = preclasificar(msg)
            if pre['decision'] == 'resolver':
                rec_eventos.append(pre['payload'])
                local_events += 1
                local_total += 1
            elif pre['decision'] == 'descartar':
                rec_descartados.append(pre['payload'])
                local_discards += 1
                local_total += 1
            else:
                objetivos.add(msg['id'])
        if objetivos:
            llm_windows_total += 1
            llm_messages_total += len(objetivos)
        planes.append({'ventana': ventana, 'batch': batch, 'objetivos': objetivos, 'eventos': rec_eventos, 'descartados': rec_descartados, 'local_events': local_events, 'local_discards': local_discards})
    if show_progress:
        print(f"[PLAN] mensajes={len(mensajes)} (máx. {ultimos_por_chat} por chat) | ventanas={total_ventanas} | locales={local_total} | derivados_llm={llm_messages_total} | ventanas_llm={llm_windows_total} | llamadas_llm_logicas={(1 if llm_messages_total and provider != 'rules' else 0)}", flush=True)
        if completed_windows:
            print(f'[RESUME] ventanas_recuperadas={len(completed_windows)} | mensajes_recuperados={processed_messages} | api_calls_recuperadas={api_calls}', flush=True)

    def persist_plan(plan: Dict[str, Any], complete: bool, rec_api: Dict[str, Any]) -> None:
        nonlocal api_calls, api_attempts, api_retries, input_tokens, output_tokens, api_cost, processed_messages
        ventana = plan['ventana']
        batch = plan['batch']
        objetivos = plan['objetivos']
        rec_eventos = plan['eventos']
        rec_descartados = plan['descartados']
        llm_event_count = sum((1 for e in rec_eventos if e.get('origen') == 'llm'))
        llm_discard_count = sum((1 for d in rec_descartados if d.get('origen') in ('llm', 'validador', 'fallback_api')))
        batch_stat = {'ventana': ventana, 'mensajes': len(batch), 'resueltos_local': plan['local_events'], 'descartados_local': plan['local_discards'], 'derivados_llm': len(objetivos), 'eventos_llm': llm_event_count, 'descartados_llm': llm_discard_count}
        share = rec_api.get('share', False)
        rec = {'type': 'window', 'complete': complete, 'ventana': ventana, 'mensajes': len(batch), 'eventos': rec_eventos, 'descartados': rec_descartados, 'batch_stat': batch_stat, 'api_calls': int(rec_api.get('api_calls', 0)) if share else 0, 'api_attempts': int(rec_api.get('api_attempts', 0)) if share else 0, 'api_retries': int(rec_api.get('api_retries', 0)) if share else 0, 'input_tokens': int(rec_api.get('input_tokens', 0)) if share else 0, 'output_tokens': int(rec_api.get('output_tokens', 0)) if share else 0, 'api_cost': float(rec_api.get('api_cost', 0.0)) if share else 0.0, 'latencies': list(rec_api.get('latencies', [])) if share else [], 'request_trace': list(rec_api.get('request_trace', [])) if share else [], 'errores_api': list(rec_api.get('errores_api', [])) if share else []}
        _append_checkpoint_record(checkpoint_path, rec)
        eventos.extend(rec_eventos)
        descartados.extend(rec_descartados)
        batch_stats.append(batch_stat)
        if share:
            api_calls += rec['api_calls']
            api_attempts += rec['api_attempts']
            api_retries += rec['api_retries']
            input_tokens += rec['input_tokens']
            output_tokens += rec['output_tokens']
            api_cost += rec['api_cost']
            latencies.extend(rec['latencies'])
            request_trace.extend(rec['request_trace'])
            errores_api.extend(rec['errores_api'])
        if complete:
            completed_windows.add(ventana)
            processed_messages += len(batch)
    pending_llm = [p for p in planes if p['objetivos']]
    local_only = [p for p in planes if not p['objetivos']]
    for plan in local_only:
        persist_plan(plan, True, {})
    run_t0 = time.perf_counter()
    if pending_llm:
        if provider == 'rules':
            for plan in pending_llm:
                by_id = {m['id']: m for m in plan['batch']}
                for mid in sorted(plan['objetivos']):
                    plan['descartados'].append(_discard_local(by_id[mid], 'AMBIGUO', 'Caso derivado a LLM; provider=rules no ejecuta API'))
                persist_plan(plan, True, {})
        else:
            all_obj: set[str] = set()
            expected_dates: Dict[str, str] = {}
            combined_payload: List[Dict[str, Any]] = []
            id_to_plan: Dict[str, Dict[str, Any]] = {}
            for plan in pending_llm:
                ventana = plan['ventana']
                objetivos = plan['objetivos']
                contexto = contexto_para_objetivos(plan['batch'], objetivos, vecinos=1)
                payload = construir_payload_mensajes(contexto, objetivos)
                for row in payload:
                    row['ventana'] = ventana
                    combined_payload.append(row)
                for m in plan['batch']:
                    if m['id'] in objetivos:
                        all_obj.add(m['id'])
                        expected_dates[m['id']] = m.get('fecha', '')
                        id_to_plan[m['id']] = plan
            if show_progress:
                print(f'[API] lote único | objetivos={len(all_obj)} | ventanas={len(pending_llm)} | provider={provider} ...', flush=True)
            try:
                result = call_llm(provider, combined_payload, model=model)
                valid = validar_salida_llm(result.data, all_obj, expected_dates)
                for e in valid['eventos']:
                    plan = id_to_plan.get(e.get('mensaje_id'))
                    if plan is not None:
                        plan['eventos'].append(e)
                for d in valid['mensajes_descartados']:
                    plan = id_to_plan.get(d.get('mensaje_id'))
                    if plan is not None:
                        plan['descartados'].append(d)
                trace = [{**t, 'lote': 'global'} for t in result.request_trace or []]
                rec_api = {'api_calls': 1, 'api_attempts': len(trace), 'api_retries': sum((1 for t in trace if t.get('retry'))), 'input_tokens': result.input_tokens, 'output_tokens': result.output_tokens, 'api_cost': result.cost_usd, 'latencies': [result.latency_ms], 'request_trace': trace, 'errores_api': []}
                for i, plan in enumerate(pending_llm):
                    persist_plan(plan, True, {**rec_api, 'share': i == 0})
            except KeyboardInterrupt:
                raise
            except Exception as exc:
                trace = [{**t, 'lote': 'global'} for t in getattr(exc, 'trace', []) or []]
                error_obj = {'lote': 'global', 'error': str(exc), 'trace': trace}
                quota = any((t.get('http_status') == 429 for t in trace))
                rec_api = {'api_calls': 0, 'api_attempts': len(trace), 'api_retries': sum((1 for t in trace if t.get('retry'))), 'input_tokens': 0, 'output_tokens': 0, 'api_cost': 0.0, 'latencies': [], 'request_trace': trace, 'errores_api': [error_obj]}
                for i, plan in enumerate(pending_llm):
                    by_id = {m['id']: m for m in plan['batch']}
                    for mid in sorted(plan['objetivos']):
                        m = by_id[mid]
                        plan['descartados'].append({'mensaje_id': mid, 'motivo': 'PENDIENTE_API', 'fecha': m.get('fecha', ''), 'detalle': 'Cuota/rate limit de API' if quota else f'Fallo de API: {str(exc)[:120]}', 'origen': 'fallback_api'})
                    persist_plan(plan, False, {**rec_api, 'share': i == 0})
    final_status = 'COMPLETADO'
    if errores_api:
        final_status = 'PARCIAL_CUOTA' if any((any((t.get('http_status') == 429 for t in e.get('trace', []) or [])) for e in errores_api)) else 'PARCIAL'
    final_result = _build_analysis_result(provider=provider, model=model, mensajes_totales=len(mensajes), parse_info=parse_info, eventos=eventos, descartados=descartados, api_calls=api_calls, api_attempts=api_attempts, api_retries=api_retries, input_tokens=input_tokens, output_tokens=output_tokens, api_cost=api_cost, latencies=latencies, request_trace=request_trace, errores_api=errores_api, batch_stats=batch_stats, status=final_status, progress={'ventanas_completadas': len(completed_windows), 'ventanas_totales': total_ventanas, 'mensajes_procesados': processed_messages, 'mensajes_totales': len(mensajes), 'checkpoint': str(checkpoint_path.resolve()), 'llamadas_llm_logicas': 1 if pending_llm and provider != 'rules' else 0, 'duracion_segundos': round(time.perf_counter() - run_t0, 3)})
    _atomic_write_json(output_path, final_result)
    if final_status == 'COMPLETADO':
        partial_path.unlink(missing_ok=True)
        checkpoint_path.unlink(missing_ok=True)
    else:
        _atomic_write_json(partial_path, final_result)
    return final_result
TEST_SET: List[Dict[str, Any]] = [{'id': 'T001', 'texto': 'Don Daniel tiene clarita sin fila', 'label': 'HAY', 'surtidor': 'Don Daniel', 'combustible': 'Gasolina Especial'}, {'id': 'T002', 'texto': 'en tacurandi ay diessel ⛽', 'label': 'HAY', 'surtidor': 'Tacuarandi', 'combustible': 'Diesel'}, {'id': 'T003', 'texto': 'la mas vegas ya tiene la plus, poquita fila', 'label': 'HAY', 'surtidor': 'Las Vegas', 'combustible': 'Gasolina Plus'}, {'id': 'T004', 'texto': 'morros blancos están dando blanquita', 'label': 'HAY', 'surtidor': 'YPFB Morros Blancos', 'combustible': 'Gasolina Especial'}, {'id': 'T005', 'texto': 'acabo de cargar etanol en el portillo', 'label': 'HAY', 'surtidor': 'El Portillo', 'combustible': 'Etanol'}, {'id': 'T006', 'texto': 'en sointar llegó gasolina especial', 'label': 'HAY', 'surtidor': 'Sointa', 'combustible': 'Gasolina Especial'}, {'id': 'T007', 'texto': 'san gorje tiene amarilla', 'label': 'HAY', 'surtidor': 'San Jorge', 'combustible': 'Gasolina Plus'}, {'id': 'T008', 'texto': 'moto mendez venta normal de diesel', 'label': 'HAY', 'surtidor': 'Moto Mendez', 'combustible': 'Diesel'}, {'id': 'T009', 'texto': 'en don daniel se acabó la clarita', 'label': 'NO_HAY', 'surtidor': 'Don Daniel', 'combustible': 'Gasolina Especial'}, {'id': 'T010', 'texto': 'tacuarandi ya no hay diessel', 'label': 'NO_HAY', 'surtidor': 'Tacuarandi', 'combustible': 'Diesel'}, {'id': 'T011', 'texto': 'portillo sin gasolina', 'label': 'NO_HAY', 'surtidor': 'El Portillo', 'combustible': 'NO_ESPECIFICADO'}, {'id': 'T012', 'texto': 'en agrupa no están vendiendo plus', 'label': 'NO_HAY', 'surtidor': 'Agrupa', 'combustible': 'Gasolina Plus'}, {'id': 'T013', 'texto': 'morros blancos se terminó la especial', 'label': 'NO_HAY', 'surtidor': 'YPFB Morros Blancos', 'combustible': 'Gasolina Especial'}, {'id': 'T014', 'texto': 'san martin se fue la cisterna, no hay diesel', 'label': 'NO_HAY', 'surtidor': 'San Martin', 'combustible': 'Diesel'}, {'id': 'T015', 'texto': 'ex terminal no llegó etanol', 'label': 'NO_HAY', 'surtidor': 'Ex Terminal', 'combustible': 'Etanol'}, {'id': 'T016', 'texto': 'dnd hay clarita?', 'label': 'PREGUNTA', 'surtidor': None, 'combustible': 'Gasolina Especial'}, {'id': 'T017', 'texto': 'alguien sabe si don daniel tiene plus?', 'label': 'PREGUNTA', 'surtidor': 'Don Daniel', 'combustible': 'Gasolina Plus'}, {'id': 'T018', 'texto': 'avisen donde están cargando diesel', 'label': 'PREGUNTA', 'surtidor': None, 'combustible': 'Diesel'}, {'id': 'T019', 'texto': 'en el portillo hay especial?', 'label': 'PREGUNTA', 'surtidor': 'El Portillo', 'combustible': 'Gasolina Especial'}, {'id': 'T020', 'texto': 'q surtidor tiene etanol ahorita?', 'label': 'PREGUNTA', 'surtidor': None, 'combustible': 'Etanol'}, {'id': 'T021', 'texto': 'saben dónde venden la amarilla sin fila?', 'label': 'PREGUNTA', 'surtidor': None, 'combustible': 'Gasolina Plus'}, {'id': 'T022', 'texto': 'alguien cargó en la terminal?', 'label': 'PREGUNTA', 'surtidor': 'Ex Terminal', 'combustible': None}, {'id': 'T023', 'texto': 'porfa reporten gasolina', 'label': 'PREGUNTA', 'surtidor': None, 'combustible': None}, {'id': 'T024', 'texto': 'dicen q hay en el de siempre', 'label': 'AMBIGUO', 'surtidor': None, 'combustible': None}, {'id': 'T025', 'texto': 'ahi sigue?', 'label': 'AMBIGUO', 'surtidor': None, 'combustible': None}, {'id': 'T026', 'texto': 'llegó hace rato pero no sé si queda', 'label': 'AMBIGUO', 'surtidor': None, 'combustible': None}, {'id': 'T027', 'texto': 'en daniel está normal', 'label': 'AMBIGUO', 'surtidor': 'Don Daniel', 'combustible': None}, {'id': 'T028', 'texto': 'la blanquita por ahí nomas', 'label': 'AMBIGUO', 'surtidor': None, 'combustible': 'Gasolina Especial'}, {'id': 'T029', 'texto': 'creo que sí hay, no estoy seguro', 'label': 'AMBIGUO', 'surtidor': None, 'combustible': None}, {'id': 'T030', 'texto': 'en moto mendez hay especial pero también dicen que ya se acabó', 'label': 'CONTRADICTORIO', 'surtidor': 'Moto Mendez', 'combustible': 'Gasolina Especial'}, {'id': 'T031', 'texto': 'don daniel tiene plus, aunque otro dice que no hay', 'label': 'CONTRADICTORIO', 'surtidor': 'Don Daniel', 'combustible': 'Gasolina Plus'}, {'id': 'T032', 'texto': 'tacurandi ya llegó diesel pero se acabó al toque', 'label': 'NO_HAY', 'surtidor': 'Tacuarandi', 'combustible': 'Diesel'}, {'id': 'T033', 'texto': 'en portillo hay y no hay, cada uno dice algo', 'label': 'CONTRADICTORIO', 'surtidor': 'El Portillo', 'combustible': None}, {'id': 'T034', 'texto': 'morros blancos está vendiendo especial, mentira ya no hay', 'label': 'NO_HAY', 'surtidor': 'YPFB Morros Blancos', 'combustible': 'Gasolina Especial'}, {'id': 'T035', 'texto': 'buen día a todos', 'label': 'NO_RELEVANTE', 'surtidor': None, 'combustible': None}, {'id': 'T036', 'texto': 'vendo llantas aro 16 baratas', 'label': 'NO_RELEVANTE', 'surtidor': None, 'combustible': None}, {'id': 'T037', 'texto': 'jajaja xd', 'label': 'NO_RELEVANTE', 'surtidor': None, 'combustible': None}, {'id': 'T038', 'texto': '<Multimedia omitido>', 'label': 'NO_RELEVANTE', 'surtidor': None, 'combustible': None}, {'id': 'T039', 'texto': 'alguien conoce mecánico bueno?', 'label': 'NO_RELEVANTE', 'surtidor': None, 'combustible': None}, {'id': 'T040', 'texto': 'partido esta noche a las 8', 'label': 'NO_RELEVANTE', 'surtidor': None, 'combustible': None}]

def _test_messages(casos: Sequence[Dict[str, Any]]) -> List[Dict[str, Any]]:
    base = datetime(2026, 9, 25, 8, 0)
    return [{'id': c['id'], 'fecha': (base + timedelta(seconds=i)).isoformat(), 'usuario': f'u_test_{i % 4}', 'analizar': True, 'texto': c['texto']} for i, c in enumerate(casos)]

def _label_from_output(data: Dict[str, Any], test_id: str) -> Tuple[str, Optional[Dict[str, Any]]]:
    for e in data.get('eventos', []) or []:
        if e.get('mensaje_id') == test_id:
            return (str(e.get('estado', 'DESCONOCIDO')), e)
    for d in data.get('mensajes_descartados', []) or []:
        if d.get('mensaje_id') == test_id:
            return (str(d.get('motivo', 'AMBIGUO')), d)
    return ('OMITIDO', None)

def percentile(values: Sequence[float], pct: float) -> float:
    if not values:
        return 0.0
    xs = sorted(values)
    if len(xs) == 1:
        return xs[0]
    k = (len(xs) - 1) * pct / 100.0
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return xs[int(k)]
    return xs[f] * (c - k) + xs[c] * (k - f)

def _test_case_message(c: Dict[str, Any], index: int=0) -> Dict[str, Any]:
    base = datetime(2026, 9, 25, 8, 0)
    dt = base + timedelta(seconds=index)
    return {'id': c['id'], 'archivo': 'test', 'fecha': dt.isoformat(), 'datetime': dt, 'autor': 'test', 'mensaje': c['texto']}

def _prediction_from_preclassifier(c: Dict[str, Any], index: int) -> Tuple[str, Optional[Dict[str, Any]], str]:
    pre = preclasificar(_test_case_message(c, index))
    if pre['decision'] == 'resolver':
        return (str(pre['payload'].get('estado', 'DESCONOCIDO')), pre['payload'], 'local')
    if pre['decision'] == 'descartar':
        return (str(pre['payload'].get('motivo', 'AMBIGUO')), pre['payload'], 'local')
    return ('DERIVAR_LLM', None, 'llm')

def _operationally_correct(expected_label: str, predicted_label: str) -> bool:
    event_labels = {'HAY', 'NO_HAY', 'CONTRADICTORIO', 'DESCONOCIDO'}
    discard_labels = {'PREGUNTA', 'AMBIGUO', 'NO_RELEVANTE'}
    if expected_label in event_labels:
        return predicted_label == expected_label
    if expected_label in discard_labels:
        return predicted_label in discard_labels
    return predicted_label == expected_label

def _count_entity_hallucination(exp: Dict[str, Any], got: Dict[str, Any]) -> int:
    label = got.get('label')
    obj = got.get('obj') or {}
    if label not in ('HAY', 'NO_HAY', 'CONTRADICTORIO', 'DESCONOCIDO'):
        return 0
    exp_s = exp.get('surtidor')
    pred_s = obj.get('surtidor')
    if exp_s and pred_s not in (exp_s, 'NO_IDENTIFICADO'):
        return 1
    if exp_s is None and pred_s not in (None, 'NO_IDENTIFICADO'):
        return 1
    return 0

def benchmark_provider_direct(provider: str, batch_size: int=10, model: Optional[str]=None) -> Dict[str, Any]:
    expected = {c['id']: c for c in TEST_SET}
    predicted: Dict[str, Dict[str, Any]] = {}
    latencies: List[float] = []
    traces: List[Dict[str, Any]] = []
    input_tokens = output_tokens = 0
    cost = 0.0
    calls = 0
    errors: List[str] = []
    for start in range(0, len(TEST_SET), batch_size):
        chunk_cases = TEST_SET[start:start + batch_size]
        payload = _test_messages(chunk_cases)
        try:
            result = call_llm(provider, payload, model=model)
            traces.extend(result.request_trace or [])
            objetivos = {c['id'] for c in chunk_cases}
            fechas_esperadas = {m['id']: m.get('fecha', '') for m in payload}
            valid = validar_salida_llm(result.data, objetivos, fechas_esperadas)
            calls += 1
            input_tokens += result.input_tokens
            output_tokens += result.output_tokens
            cost += result.cost_usd
            latencies.append(result.latency_ms)
            for c in chunk_cases:
                label, obj = _label_from_output(valid, c['id'])
                predicted[c['id']] = {'label': label, 'obj': obj, 'origen': 'llm'}
        except Exception as exc:
            traces.extend(getattr(exc, 'trace', []) or [])
            errors.append(f'batch {start // batch_size + 1}: {exc}')
            for c in chunk_cases:
                predicted[c['id']] = {'label': 'ERROR_API', 'obj': None, 'origen': 'api_error'}
    aciertos = 0
    operational = 0
    hallucinations = 0
    details = []
    for tid, exp in expected.items():
        got = predicted.get(tid, {'label': 'OMITIDO', 'obj': None, 'origen': 'missing'})
        exact = got['label'] == exp['label']
        op_ok = _operationally_correct(exp['label'], got['label'])
        aciertos += int(exact)
        operational += int(op_ok)
        hallucinations += _count_entity_hallucination(exp, got)
        details.append({'id': tid, 'texto': exp['texto'], 'esperado': exp['label'], 'obtenido': got['label'], 'correcto': exact, 'correcto_operativo': op_ok, 'origen': got.get('origen'), 'salida': got.get('obj') or {}})
    return {'provider': provider, 'model': model or PROVIDERS[provider]['model'], 'mode': 'direct', 'casos': len(TEST_SET), 'aciertos': aciertos, 'accuracy': round(aciertos / len(TEST_SET), 4), 'accuracy_operativa': round(operational / len(TEST_SET), 4), 'errores': len(TEST_SET) - aciertos, 'alucinaciones_entidad': hallucinations, 'api_calls_exitosas': calls, 'api_attempts': len(traces), 'reintentos': sum((1 for t in traces if t.get('retry'))), 'input_tokens': input_tokens, 'output_tokens': output_tokens, 'cost_usd': round(cost, 8), 'latencia_media_ms': round(statistics.mean(latencies), 2) if latencies else None, 'latencia_p95_ms': round(percentile(latencies, 95), 2) if latencies else None, 'errores_api': errors, 'request_trace': traces, 'detalle': details}

def benchmark_provider_hybrid(provider: str, model: Optional[str]=None) -> Dict[str, Any]:
    expected = {c['id']: c for c in TEST_SET}
    predicted: Dict[str, Dict[str, Any]] = {}
    pending: List[Tuple[int, Dict[str, Any]]] = []
    local_handled = 0
    local_exact = 0
    for i, c in enumerate(TEST_SET):
        label, obj, origin = _prediction_from_preclassifier(c, i)
        if label == 'DERIVAR_LLM':
            pending.append((i, c))
            continue
        local_handled += 1
        exact = label == c['label']
        local_exact += int(exact)
        predicted[c['id']] = {'label': label, 'obj': obj, 'origen': origin}
    latencies: List[float] = []
    attempt_latencies: List[float] = []
    traces: List[Dict[str, Any]] = []
    input_tokens = output_tokens = 0
    cost = 0.0
    calls = 0
    api_failed_cases = 0
    llm_responded = 0
    llm_exact = 0
    errors: List[Dict[str, Any]] = []
    for ordinal, (i, c) in enumerate(pending, 1):
        payload = _test_messages([c])
        try:
            result = call_llm(provider, payload, model=model)
            case_trace = result.request_trace or []
            traces.extend([{**t, 'case_id': c['id']} for t in case_trace])
            attempt_latencies.extend((float(t.get('latency_ms', 0) or 0) for t in case_trace))
            objetivos = {c['id']}
            fechas_esperadas = {m['id']: m.get('fecha', '') for m in payload}
            valid = validar_salida_llm(result.data, objetivos, fechas_esperadas)
            label, obj = _label_from_output(valid, c['id'])
            predicted[c['id']] = {'label': label, 'obj': obj, 'origen': 'llm'}
            calls += 1
            llm_responded += 1
            llm_exact += int(label == c['label'])
            input_tokens += result.input_tokens
            output_tokens += result.output_tokens
            cost += result.cost_usd
            latencies.append(result.latency_ms)
        except Exception as exc:
            case_trace = getattr(exc, 'trace', []) or []
            traces.extend([{**t, 'case_id': c['id']} for t in case_trace])
            attempt_latencies.extend((float(t.get('latency_ms', 0) or 0) for t in case_trace))
            api_failed_cases += 1
            errors.append({'case_id': c['id'], 'error': str(exc), 'trace': case_trace})
            predicted[c['id']] = {'label': 'ERROR_API', 'obj': None, 'origen': 'api_error'}
    aciertos = 0
    operational = 0
    hallucinations = 0
    details = []
    for tid, exp in expected.items():
        got = predicted.get(tid, {'label': 'OMITIDO', 'obj': None, 'origen': 'missing'})
        exact = got['label'] == exp['label']
        op_ok = _operationally_correct(exp['label'], got['label'])
        aciertos += int(exact)
        operational += int(op_ok)
        hallucinations += _count_entity_hallucination(exp, got)
        details.append({'id': tid, 'texto': exp['texto'], 'esperado': exp['label'], 'obtenido': got['label'], 'correcto': exact, 'correcto_operativo': op_ok, 'origen': got.get('origen'), 'salida': got.get('obj') or {}})
    transient_statuses = {408, 409, 429, 500, 502, 503, 504}
    transient_errors = sum((1 for t in traces if t.get('http_status') in transient_statuses and t.get('status') != 'OK'))
    return {'provider': provider, 'model': model or PROVIDERS[provider]['model'], 'mode': 'hybrid', 'casos': len(TEST_SET), 'local_handled': local_handled, 'local_coverage': round(local_handled / len(TEST_SET), 4), 'local_exact_on_handled': local_exact, 'local_accuracy_on_handled': round(local_exact / max(local_handled, 1), 4), 'llm_cases': len(pending), 'llm_responses': llm_responded, 'llm_api_failed_cases': api_failed_cases, 'llm_api_availability': round(llm_responded / max(len(pending), 1), 4), 'llm_exact_on_responses': llm_exact, 'llm_semantic_accuracy_on_responses': round(llm_exact / max(llm_responded, 1), 4), 'aciertos': aciertos, 'accuracy': round(aciertos / len(TEST_SET), 4), 'accuracy_operativa': round(operational / len(TEST_SET), 4), 'errores': len(TEST_SET) - aciertos, 'alucinaciones_entidad': hallucinations, 'api_calls_exitosas': calls, 'api_attempts': len(traces), 'reintentos': sum((1 for t in traces if t.get('retry'))), 'errores_transitorios': transient_errors, 'input_tokens': input_tokens, 'output_tokens': output_tokens, 'cost_usd': round(cost, 8), 'latencia_media_ms': round(statistics.mean(latencies), 2) if latencies else None, 'latencia_p95_ms': round(percentile(latencies, 95), 2) if latencies else None, 'latencia_intento_p95_ms': round(percentile(attempt_latencies, 95), 2) if attempt_latencies else None, 'errores_api': errors, 'request_trace': traces, 'detalle': details}

def benchmark_provider(provider: str, batch_size: int=10, model: Optional[str]=None, mode: str='hybrid') -> Dict[str, Any]:
    if mode == 'direct':
        return benchmark_provider_direct(provider, batch_size=batch_size, model=model)
    if mode == 'hybrid':
        return benchmark_provider_hybrid(provider, model=model)
    raise ValueError(f'Modo de benchmark no soportado: {mode}')

def baseline_preclassifier() -> Dict[str, Any]:
    aciertos = 0
    handled = 0
    handled_exact = 0
    routed = 0
    detalle = []
    for i, c in enumerate(TEST_SET):
        label, obj, origin = _prediction_from_preclassifier(c, i)
        if label == 'DERIVAR_LLM':
            routed += 1
        else:
            handled += 1
            if label == c['label']:
                handled_exact += 1
        ok = label == c['label']
        aciertos += int(ok)
        detalle.append({'id': c['id'], 'esperado': c['label'], 'preclasificador': label, 'exacto': ok, 'decision': origin, 'salida': obj or {}})
    return {'casos': len(TEST_SET), 'manejados_localmente': handled, 'derivados_llm': routed, 'cobertura_local': round(handled / len(TEST_SET), 4), 'aciertos_locales_sobre_manejados': handled_exact, 'precision_local_sobre_manejados': round(handled_exact / max(handled, 1), 4), 'aciertos_exactos_sin_llm': aciertos, 'accuracy_exacta': round(aciertos / len(TEST_SET), 4), 'nota': 'accuracy_exacta cuenta DERIVAR_LLM como no exacto; para evaluar reglas use precision_local_sobre_manejados y cobertura_local.', 'detalle': detalle}

def ejecutar_benchmark(providers: Sequence[str], out: Path, batch_size: int, mode: str='hybrid') -> Dict[str, Any]:
    report = {'fecha': datetime.now().isoformat(timespec='seconds'), 'test_set_count': len(TEST_SET), 'benchmark_mode': mode, 'baseline_preclassifier': baseline_preclassifier(), 'providers': []}
    for provider in providers:
        if provider not in PROVIDERS:
            report['providers'].append({'provider': provider, 'error': 'Proveedor no soportado'})
            continue
        env_key = PROVIDERS[provider]['env_key']
        tiene_key = bool(_gemini_api_key()) if provider == 'gemini' else bool(os.getenv(env_key))
        if not tiene_key:
            required = 'GEMINI_API_KEY o GOOGLE_API_KEY' if provider == 'gemini' else env_key
            report['providers'].append({'provider': provider, 'model': PROVIDERS[provider]['model'], 'mode': mode, 'status': 'NO_EJECUTADO', 'motivo': f'Falta {required}; no se inventan resultados de precisión/latencia.'})
            continue
        report['providers'].append(benchmark_provider(provider, batch_size=batch_size, mode=mode))
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    return report
SCENARIOS = {'bajo': 300, 'medio': 1500, 'alto': 5000}

def expected_nonempty_windows(items_per_day: float, windows: int=MAX_LLAMADAS_DIA) -> int:
    if items_per_day <= 0:
        return 0
    return min(windows, math.ceil(windows * (1.0 - math.exp(-items_per_day / windows))))

def estimar_escenarios(routed_rate: float=0.25, fixed_input_tokens_per_call: int=1600, input_tokens_per_routed_msg: int=35, output_tokens_per_routed_msg: int=55) -> List[Dict[str, Any]]:
    rows = []
    for nombre, mensajes_dia in SCENARIOS.items():
        routed = mensajes_dia * routed_rate
        calls = expected_nonempty_windows(routed)
        input_day = calls * fixed_input_tokens_per_call + routed * input_tokens_per_routed_msg
        output_day = routed * output_tokens_per_routed_msg
        tokens_call = (input_day + output_day) / max(calls, 1)
        row = {'escenario': nombre, 'mensajes_dia': mensajes_dia, 'porcentaje_derivado_llm': routed_rate, 'mensajes_llm_dia_estimados': round(routed, 1), 'llamadas_dia_estimadas': calls, 'llamadas_dia_maximas': MAX_LLAMADAS_DIA, 'tokens_totales_por_llamada_estimados': round(tokens_call, 1), 'input_tokens_mes': round(input_day * 30), 'output_tokens_mes': round(output_day * 30), 'costos_usd_mes': {}}
        for provider, cfg in PROVIDERS.items():
            cost = row['input_tokens_mes'] / 1000000 * cfg['input_usd_per_m'] + row['output_tokens_mes'] / 1000000 * cfg['output_usd_per_m']
            row['costos_usd_mes'][provider] = round(cost, 4)
        rows.append(row)
    return rows

def estimar_desde_chats(paths: Sequence[Path]) -> Dict[str, Any]:
    mensajes, infos = cargar_chats(paths)
    if not mensajes:
        return {'status': 'sin_chats', 'parseo': infos}
    by_day: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    pending_total = 0
    for m in mensajes:
        dt = m.get('datetime')
        day = dt.date().isoformat() if isinstance(dt, datetime) else 'sin_fecha'
        by_day[day].append(m)
        if preclasificar(m)['decision'] == 'llm':
            pending_total += 1
    days = [d for d in by_day if d != 'sin_fecha']
    msg_per_day = [len(by_day[d]) for d in days] or [len(mensajes)]
    pending_by_day = []
    calls_by_day = []
    for d in days or ['sin_fecha']:
        msgs = by_day[d] if d in by_day else mensajes
        pend = sum((1 for m in msgs if preclasificar(m)['decision'] == 'llm'))
        pending_by_day.append(pend)
        active = 0
        for _, batch in agrupar_ventanas(msgs):
            if any((preclasificar(m)['decision'] == 'llm' for m in batch)):
                active += 1
        calls_by_day.append(active)
    return {'status': 'ok', 'mensajes_totales': len(mensajes), 'dias_con_fecha': len(days), 'mensajes_dia_promedio': round(statistics.mean(msg_per_day), 2), 'mensajes_dia_max': max(msg_per_day), 'derivados_llm_total': pending_total, 'porcentaje_derivado_llm': round(pending_total / len(mensajes), 4), 'derivados_llm_dia_promedio': round(statistics.mean(pending_by_day), 2), 'llamadas_api_dia_promedio': round(statistics.mean(calls_by_day), 2), 'llamadas_api_dia_max': max(calls_by_day), 'limite_diseno_5min': MAX_LLAMADAS_DIA, 'parseo': infos}

def _paths(values: Optional[Sequence[str]]) -> List[Path]:
    if not values:
        return DEFAULT_CHATS
    return [Path(v).expanduser().resolve() for v in values]

def cmd_analyze(args: argparse.Namespace) -> int:
    if args.ultimos_por_chat < 0:
        raise SystemExit('ERROR: --ultimos-por-chat debe ser 0 o un entero positivo')
    output = Path(args.output)
    checkpoint = Path(args.checkpoint) if args.checkpoint else None
    try:
        result = analizar_chats(_paths(args.chats), args.provider, args.model, output, checkpoint_path=checkpoint, resume=args.resume, fresh=args.fresh, show_progress=not args.quiet, partial_every=max(0, args.partial_every), ultimos_por_chat=args.ultimos_por_chat)
    except KeyboardInterrupt:
        cp = checkpoint or _default_checkpoint_path(output)
        partial = _default_partial_path(output)
        print(json.dumps({'status': 'INTERRUMPIDO', 'checkpoint': str(cp.resolve()), 'partial': str(partial.resolve()), 'reanudar': 'Vuelve a ejecutar el mismo comando agregando --resume'}, ensure_ascii=False, indent=2))
        return 130
    meta = result['meta']
    print(json.dumps({'status': meta.get('status', 'COMPLETADO'), 'output': str(output.resolve()), 'mensajes': meta['mensajes_totales'], 'eventos': meta['eventos'], 'descartados': meta['mensajes_descartados'], 'pendientes_api': meta.get('mensajes_pendientes_api', 0), 'api_calls': meta['api_calls'], 'api_attempts': meta.get('api_attempts', 0), 'api_retries': meta.get('api_retries', 0), 'costo_usd': meta['costo_api_usd'], 'errores_api': len(meta['errores_api'])}, ensure_ascii=False, indent=2))
    return 0 if meta.get('status') == 'COMPLETADO' else 2

def cmd_benchmark(args: argparse.Namespace) -> int:
    report = ejecutar_benchmark(args.providers, Path(args.output), args.batch_size, mode=args.mode)
    compact = {'output': str(Path(args.output).resolve()), 'test_set_count': report['test_set_count'], 'benchmark_mode': report.get('benchmark_mode'), 'providers': [{'provider': p.get('provider'), 'model': p.get('model'), 'mode': p.get('mode'), 'status': p.get('status', 'EJECUTADO' if 'accuracy' in p else 'ERROR'), 'local_coverage': p.get('local_coverage'), 'local_accuracy_on_handled': p.get('local_accuracy_on_handled'), 'llm_cases': p.get('llm_cases'), 'llm_api_availability': p.get('llm_api_availability'), 'llm_semantic_accuracy_on_responses': p.get('llm_semantic_accuracy_on_responses'), 'accuracy': p.get('accuracy'), 'accuracy_operativa': p.get('accuracy_operativa'), 'alucinaciones_entidad': p.get('alucinaciones_entidad'), 'api_calls_exitosas': p.get('api_calls_exitosas'), 'api_attempts': p.get('api_attempts'), 'reintentos': p.get('reintentos'), 'cost_usd': p.get('cost_usd'), 'latencia_p95_ms': p.get('latencia_p95_ms'), 'motivo': p.get('motivo')} for p in report['providers']]}
    print(json.dumps(compact, ensure_ascii=False, indent=2))
    return 0

def cmd_estimate(args: argparse.Namespace) -> int:
    result: Dict[str, Any] = {'escenarios': estimar_escenarios()}
    if args.chats:
        result['medicion_chats'] = estimar_desde_chats(_paths(args.chats))
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0

def cmd_schema(args: argparse.Namespace) -> int:
    print(json.dumps(OUTPUT_SCHEMA, ensure_ascii=False, indent=2))
    return 0

def cmd_testset(args: argparse.Namespace) -> int:
    print(json.dumps(TEST_SET, ensure_ascii=False, indent=2))
    return 0
SMOKE_CASE_IDS = ['T001', 'T012', 'T017', 'T030', 'T035']

def ejecutar_smoke_gemini(model: Optional[str]=None) -> Dict[str, Any]:
    cases = [c for c in TEST_SET if c['id'] in SMOKE_CASE_IDS]
    payload = _test_messages(cases)
    result = call_gemini(payload, model=model)
    objetivos = {c['id'] for c in cases}
    fechas = {x['id']: x['fecha'] for x in payload}
    valid = validar_salida_llm(result.data, objetivos, fechas)
    detalle = []
    aciertos = 0
    for c in cases:
        got, obj = _label_from_output(valid, c['id'])
        ok = got == c['label']
        aciertos += int(ok)
        detalle.append({'id': c['id'], 'texto': c['texto'], 'esperado': c['label'], 'obtenido': got, 'correcto': ok, 'salida': obj})
    return {'status': 'OK' if aciertos == len(cases) else 'REVISAR', 'provider': 'gemini', 'model': result.model, 'casos': len(cases), 'aciertos': aciertos, 'accuracy': round(aciertos / max(len(cases), 1), 4), 'input_tokens': result.input_tokens, 'output_tokens': result.output_tokens, 'latency_ms': round(result.latency_ms, 2), 'cost_usd_estimado': round(result.cost_usd, 8), 'detalle': detalle}

def cmd_smoke_gemini(args: argparse.Namespace) -> int:
    if not _gemini_api_key():
        print(json.dumps({'status': 'NO_EJECUTADO', 'motivo': 'Falta GEMINI_API_KEY o GOOGLE_API_KEY', 'nota': 'La prueba live usa solo 5 mensajes sintéticos; no envía chats reales.'}, ensure_ascii=False, indent=2))
        return 2
    try:
        report = ejecutar_smoke_gemini(args.model)
    except Exception as exc:
        print(json.dumps({'status': 'ERROR', 'error': str(exc)}, ensure_ascii=False, indent=2))
        return 1
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report['status'] == 'OK' else 1

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description='HU-08/CU-06 - analizador híbrido de chats exportados de WhatsApp')
    sub = p.add_subparsers(dest='command', required=True)
    a = sub.add_parser('analyze', help='Analiza Chat1.txt/Chat2.txt o rutas indicadas')
    a.add_argument('--chats', nargs='*', help='Rutas a .txt exportados')
    a.add_argument('--provider', choices=['rules', 'openai', 'gemini', 'anthropic'], default=DEFAULT_PROVIDER)
    a.add_argument('--model', default=None, help='Sobrescribe el modelo del proveedor')
    a.add_argument('--output', default=str(DEFAULT_OUTPUT))
    a.add_argument('--checkpoint', default=None, help='Ruta del checkpoint JSONL (por defecto junto al output)')
    mx = a.add_mutually_exclusive_group()
    mx.add_argument('--resume', action='store_true', help='Reanuda una ejecución interrumpida desde checkpoint')
    mx.add_argument('--fresh', action='store_true', help='Descarta checkpoint/partial previo y comienza de cero')
    a.add_argument('--ultimos-por-chat', '--last-per-chat', dest='ultimos_por_chat', type=int, default=DEFAULT_ULTIMOS_POR_CHAT, help='Procesa solo los últimos N mensajes de cada chat (default 25; 0=todos)')
    a.add_argument('--partial-every', type=int, default=DEFAULT_PARTIAL_EVERY, help='Actualiza JSON parcial cada N ventanas (0 desactiva; default 10)')
    a.add_argument('--quiet', action='store_true', help='Oculta el progreso en tiempo real')
    a.set_defaults(func=cmd_analyze)
    b = sub.add_parser('benchmark', help='Ejecuta T-039 sobre 40 mensajes de prueba')
    b.add_argument('--providers', nargs='+', default=['gemini'], choices=list(PROVIDERS.keys()))
    b.add_argument('--mode', choices=['hybrid', 'direct'], default='hybrid', help='hybrid=arquitectura real reglas+LLM (recomendado); direct=LLM sobre los 40')
    b.add_argument('--batch-size', type=int, default=10, help='Solo aplica a --mode direct')
    b.add_argument('--output', default=str(DEFAULT_BENCHMARK))
    b.set_defaults(func=cmd_benchmark)
    e = sub.add_parser('estimate', help='Calcula T-034 y opcionalmente mide chats reales')
    e.add_argument('--chats', nargs='*', help='Si se pasan rutas, mide volumen real además de escenarios')
    e.set_defaults(func=cmd_estimate)
    s = sub.add_parser('schema', help='Imprime el JSON Schema de salida')
    s.set_defaults(func=cmd_schema)
    t = sub.add_parser('testset', help='Imprime el set T-037 de 40 mensajes')
    t.set_defaults(func=cmd_testset)
    g = sub.add_parser('smoke-gemini', help='Prueba LIVE de Gemini con 5 mensajes sintéticos; no envía chats reales')
    g.add_argument('--model', default=None, help='Sobrescribe el modelo Gemini')
    g.set_defaults(func=cmd_smoke_gemini)
    return p

def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return int(args.func(args))
if __name__ == '__main__':
    raise SystemExit(main())
