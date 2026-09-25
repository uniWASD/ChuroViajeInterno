import re
import sys
import warnings
from pathlib import Path
from collections import Counter, defaultdict

import numpy as np
import pandas as pd

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split, StratifiedKFold, cross_val_score
from sklearn.metrics import classification_report, balanced_accuracy_score
from sklearn.pipeline import Pipeline

import nltk
for recurso in ['punkt', 'punkt_tab', 'stopwords']:
    try:
        nltk.download(recurso, quiet=True)
    except Exception:
        pass

from nltk.corpus import stopwords

try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    import seaborn as sns
    sns.set_theme(style='whitegrid')
    TIENE_MATPLOTLIB = True
except ImportError:
    TIENE_MATPLOTLIB = False

warnings.filterwarnings('ignore')

DIRECTORIO_BASE = Path(__file__).resolve().parent
DIRECTORIO_CHATS = DIRECTORIO_BASE / 'Chats'
ARCHIVOS_CHAT = [
    DIRECTORIO_CHATS / 'Chat1.txt',
    DIRECTORIO_CHATS / 'Chat2.txt',
]

CAT_HAY = "hay combustible"
CAT_NO_HAY = "no hay combustible"
CAT_NO_RELEVANTE = "mensaje no relevante/ambiguo"
UMBRAL_CONFIANZA = 0.60

PATRON_FORMATO1 = re.compile(
    r"""
    ^\[
    (?P<fecha>\d{1,2}/\d{1,2}/\d{2,4})
    ,\s+
    (?P<hora>\d{1,2}:\d{2}:\d{2})
    \s+(?P<ampm>AM|PM|am|pm)
    \]\s+
    (?:(?P<autor>[^:]+):\s*(?P<mensaje>.*)|(?P<sistema>.*))
    $
    """,
    re.VERBOSE,
)

PATRON_FORMATO2 = re.compile(
    r"""
    ^(?P<fecha>\d{1,2}/\d{1,2}/\d{2,4})
    ,\s+
    (?P<hora>\d{1,2}:\d{2})
    \s*[-\u2013\u2014]\s+
    (?:(?P<autor>[^:]+):\s*(?P<mensaje>.*)|(?P<sistema>.*))
    $
    """,
    re.VERBOSE,
)

PATRON_FORMATO_ORIG = re.compile(
    r"""
    ^\[?
    (?P<fecha>\d{1,2}[/-]\d{1,2}[/-]\d{2,4})
    ,?\s+
    (?P<hora>\d{1,2}:\d{2}(?::\d{2})?)
    (?:\s*(?P<ampm>[apAP]\.\s*m\.|[apAP]m|AM|PM|am|pm))?
    \]?\s*[-\u2013\u2014]\s+
    (?:(?P<autor>[^:]+):\s*(?P<mensaje>.*)|(?P<sistema>.*))
    $
    """,
    re.VERBOSE,
)

def leer_texto_con_codificacion(path):
    path = Path(path)
    for encoding in ['utf-8-sig', 'utf-8', 'latin-1', 'cp1252']:
        try:
            return path.read_text(encoding=encoding), encoding
        except UnicodeDecodeError:
            pass
    return path.read_text(encoding='utf-8', errors='replace'), 'utf-8-replace'

def normalizar_linea(linea):
    for caracter in ['\u202f', '\xa0', '\u200e', '\u200f']:
        linea = linea.replace(caracter, ' ')
    return linea.strip('\ufeff').strip()

def intentar_match(linea):
    for patron in [PATRON_FORMATO1, PATRON_FORMATO2, PATRON_FORMATO_ORIG]:
        m = patron.match(linea)
        if m:
            return m
    return None

def parsear_chat_whatsapp(path):
    texto, encoding = leer_texto_con_codificacion(path)
    filas = []
    lineas_sistema = []
    actual = None

    for raw in texto.splitlines():
        linea = normalizar_linea(raw)
        if not linea:
            continue

        match = intentar_match(linea)

        if match:
            if match.group('autor'):
                actual = {
                    'archivo': Path(path).name,
                    'fecha': match.group('fecha'),
                    'hora': match.group('hora'),
                    'ampm': (match.groupdict().get('ampm') or ''),
                    'autor': match.group('autor').strip(),
                    'mensaje': (match.group('mensaje') or '').strip(),
                }
                filas.append(actual)
            else:
                actual = None
                lineas_sistema.append(linea)
        elif actual is not None:
            actual['mensaje'] += '\n' + linea
        elif linea.strip():
            lineas_sistema.append(linea)

    columnas = ['archivo', 'fecha', 'hora', 'ampm', 'autor', 'mensaje']
    df_chat = pd.DataFrame(filas, columns=columnas)
    info = {
        'archivo': Path(path).name,
        'encoding': encoding,
        'mensajes_parseados': len(df_chat),
        'lineas_sistema': len(lineas_sistema),
    }
    return df_chat, info

SURTIDORES = {
    'Las Vegas': [
        r'\blas\s*vegas\b', r'\bla\s*vegas\b', r'\bvegas\b', r'\bmas\s*vegas\b',
    ],
    'Don Daniel': [
        r'\bdon\s*daniel\b', r'\bdaniel\b',
    ],
    'Tacuarandi': [
        r'\btacuarandi\b', r'\btacurandi\b',
    ],
    'Campesino': [
        r'\bcampesino\b',
    ],
    'YPFB Morros Blancos': [
        r'\bypfb\b.*\bmorros\b', r'\bmorros\s*blancos\b',
        r'\bypfb\b(?!.*\b(?:campesino|parada|chaco)\b)',
    ],
    'Agrupa': [
        r'\bagrupa\b',
    ],
    'El Portillo': [
        r'\bportillo\b',
    ],
    'Moto Mendez': [
        r'\bmoto\s*m[eé]ndez\b', r'\bmoto\s*mende[sz]\b',
    ],
    'Ex Terminal': [
        r'\bex\s*terminal\b', r'\bexterminal\b',
        r'\bla\s*terminal\b', r'\blaterminal\b',
    ],
    'Sointa': [
        r'\bsointa\b', r'\bsointar\b',
    ],
    'Panamericano': [
        r'\bpanamericano\b',
    ],
    'San Jorge': [
        r'\bsan\s*jorge\b', r'\bsan\s*gorje\b',
    ],
    'San Geronimo': [
        r'\bsan\s*ger[oó]nimo\b',
    ],
    'El Molle': [
        r'\bel\s*molle\b', r'\bmolle\b',
    ],
    'Surtidor Tarija': [
        r'\bsurtidor\s*tarija\b', r'\bel\s*tarija\b',
    ],
    'San Martin': [
        r'\bsan\s*mart[ií]n\b',
    ],
    'Pimentel': [
        r'\bpimentel\b',
    ],
    'Villanueva': [
        r'\bvillanueva\b', r'\bvilla\s*nueva\b',
    ],
    'YPFB Parada Chaco': [
        r'\bparada\s*chaco\b',
    ],
    'Domingo Savio': [
        r'\bdomingo\s*savio\b',
    ],
    'Coliseo Universitario': [
        r'\bcoliseo\s*universitario\b', r'\bcoliseo\s*univ\b',
    ],
}

COMBUSTIBLES = {
    'Gasolina Especial': [
        r'\bespecial\b',
        r'\bclarita\b', r'\bcalarita\b', r'\bclara\b',
        r'\bblanquita\b', r'\bblanca\b', r'\bblankita\b', r'\bblanc?a\b',
        r'\blimpia\b',
        r'\bbuena\b(?=.*\bgasolina\b|\bgaso\b)',
        r'\bchura\b', r'\blinda\b', r'\bwena\b',
    ],
    'Etanol': [
        r'\betanol\b', r'\betanos\b',
    ],
    'Gasolina Plus': [
        r'\bplus\b',
        r'\bamarilla\b',
    ],
    'Diesel': [
        r'\bdi[eé]ss?el\b', r'\bdiessel\b',
    ],
}

PATRONES_PREGUNTA = re.compile(
    r'(?:alguien\s*sabe|donde\s*hay|dnd\s*hay|d[oó]nde\s*hay|'
    r'saben\s*d[oó]nde|no\s*saben|avisen|reporten|'
    r'alguien\s*que\s*(?:sepa|pase)|por\s*favor|xf|porfavor|'
    r'alguien\s*sabe\s*si|donde\s*puedo|'
    r'en\s*qu[eé]\s*surtidor|'
    r'donde\s*est[aá]n\s*(?:vendiendo|cargando|repartiendo)|'
    r'donde\s*(?:ay|venden)|buen\s*d[ií]a.*gasolina|'
    r'buenas\s*(?:tardes|noches).*gasolina|'
    r'no\s*saben\s*donde|urgente|'
    r'alguien\s*cargo)',
    re.IGNORECASE
)

PATRONES_HAY = re.compile(
    r'(?:ya\s*lleg[oó]\s*gasolina|lleg[oó]\s*gasolina|ya\s*lleg[oó]|'
    r'acabo\s*de\s*cargar|cargu[eé]|cargue|'
    r'hay\s*(?:gasolina|etanol|especial|plus|la\s*plus|diesel|la\s*especial|la\s*clarita|blanquita)|'
    r'est[aá]n?\s*(?:vendiendo|cargando|dando|descargando|repartiendo)|'
    r'sin\s*fila|no\s*(?:hay|mucha)\s*fila|'
    r'venta\s*normal|'
    r'recién\s*(?:cargue|cargu[eé])|'
    r'est[aá]\s*(?:clarita|clara|blanquita|blanca|amarilla)|'
    r'tiene[n]?\s*(?:gasolina|la\s*especial|la\s*plus|etanol|la\s*clarita|di[eé]sel)|'
    r'ya\s*tiene|descarg(?:ando|aron)|ay\s*(?:clarita|blanquita|diessel|etanol|gasolina))',
    re.IGNORECASE
)

PATRONES_NO_HAY = re.compile(
    r'(?:se\s*acab[oó]|se\s*termin[oó]|ya\s*no\s*hay|'
    r'no\s*hay\s*(?:gasolina|etanol|especial|plus|diesel|nada)?|'
    r'no\s*est[aá]n\s*vendiendo|se\s*fue\s*la\s*cisterna|'
    r'no\s*lleg[oó]|sin\s*combustible|sin\s*gasolina)',
    re.IGNORECASE
)

stopwords_es = set(stopwords.words('spanish'))

STOPWORDS_CUSTOM = {
    'archivo', 'adjunto', 'stk', 'webp', 'jpg', 'jpeg', 'png', 'gif', 'pdf',
    'doc', 'docx', 'multimedia', 'omitido', 'omitida', 'mensaje', 'elimino',
    'editado', 'cifrado', 'cifrados', 'llamadas', 'http', 'https', 'www',
    'com', 'tiktok', 'youtube', 'wa', 'img', 'vid', 'aud', 'ptt',
}

PALABRAS_MUY_COMUNES = {
    'vos', 'che', 'ya', 'si', 'ps', 'pues', 'nomas',
    'xd', 'jaja', 'jajaja', 'aja', 'ah', 'eh',
}

STOPWORDS_TOTALES = stopwords_es | STOPWORDS_CUSTOM | PALABRAS_MUY_COMUNES

PATRON_RUIDO = re.compile(
    r'<multimedia omitido>|Multimedia omitido|multimedia omitido|'
    r'mensaje eliminado|se elimin[oó] este mensaje|'
    r'imagen omitida|video omitido|audio omitido|sticker omitido|'
    r'documento omitido|archivo adjunto|'
    r'https?://|www\.|'
    r'los mensajes y las llamadas est[aá]n cifrados|'
    r'BEGIN:VCARD|END:VCARD|'
    r'mensaje de voz omitido|Mensaje de album|'
    r'Reenviado.*omitida|'
    r'\.vcf\b',
    re.IGNORECASE
)

def es_ruido(texto):
    return bool(PATRON_RUIDO.search(texto))

def es_pregunta(texto):
    return bool(PATRONES_PREGUNTA.search(texto))

def es_reporte(texto):
    if es_pregunta(texto):
        return False
    return bool(PATRONES_HAY.search(texto))

def detectar_con_diccionario(texto, diccionario):
    texto_lower = texto.lower()
    for clase, patrones in diccionario.items():
        for patron in patrones:
            if re.search(patron, texto_lower):
                return clase
    return None

def detectar_todos_surtidores(texto):
    texto_lower = texto.lower()
    encontrados = []
    for clase, patrones in SURTIDORES.items():
        for patron in patrones:
            if re.search(patron, texto_lower):
                if clase not in encontrados:
                    encontrados.append(clase)
                break
    return encontrados

def detectar_todos_combustibles(texto):
    texto_lower = texto.lower()
    encontrados = []
    for clase, patrones in COMBUSTIBLES.items():
        for patron in patrones:
            if re.search(patron, texto_lower):
                if clase not in encontrados:
                    encontrados.append(clase)
                break
    return encontrados

def limpiar_tokens(texto):
    texto = str(texto).lower()
    texto = re.sub(r'https?://\S+|www\.\S+', ' ', texto)
    texto = re.sub(r'\b\S+\.(?:webp|jpg|jpeg|png|gif|pdf|docx?|vcf)\b', ' ', texto)
    texto = re.sub(
        r'archivo adjunto|multimedia omitido|se elimin[oó] este mensaje',
        ' ', texto
    )
    texto = re.sub(r'[^a-zaeiouñü\s]', ' ', texto)
    texto = re.sub(r'\s+', ' ', texto).strip()
    tokens = [
        t for t in texto.split()
        if len(t) >= 3 and t not in STOPWORDS_TOTALES
        and not re.fullmatch(r'(.)\1+', t)
    ]
    return ' '.join(tokens)

def clasificar_mensaje(mensaje, pipeline_ml=None, umbral=UMBRAL_CONFIANZA):
    msg_norm = normalizar_linea(mensaje)
    surtidor = detectar_con_diccionario(msg_norm, SURTIDORES)
    combustible = detectar_con_diccionario(msg_norm, COMBUSTIBLES)

    if es_ruido(msg_norm) or es_pregunta(msg_norm) or len(msg_norm) < 4:
        return {
            'categoria': CAT_NO_RELEVANTE,
            'surtidor': surtidor,
            'combustible': combustible,
            'confianza': 1.0,
            'motivo': 'No relevante (pregunta/ruido/saludo)'
        }

    if PATRONES_NO_HAY.search(msg_norm):
        return {
            'categoria': CAT_NO_HAY,
            'surtidor': surtidor,
            'combustible': combustible,
            'confianza': 0.95,
            'motivo': 'Reporte de falta de combustible'
        }

    if PATRONES_HAY.search(msg_norm):
        return {
            'categoria': CAT_HAY,
            'surtidor': surtidor,
            'combustible': combustible,
            'confianza': 0.95,
            'motivo': 'Reporte claro de disponibilidad'
        }

    if pipeline_ml is not None:
        tokens = limpiar_tokens(msg_norm)
        if len(tokens) >= 3:
            probas = pipeline_ml.predict_proba([tokens])[0]
            max_idx = np.argmax(probas)
            max_proba = probas[max_idx]
            cat_pred = pipeline_ml.classes_[max_idx]
            if max_proba >= umbral:
                return {
                    'categoria': cat_pred,
                    'surtidor': surtidor,
                    'combustible': combustible,
                    'confianza': float(max_proba),
                    'motivo': 'Modelo ML'
                }

    return {
        'categoria': CAT_NO_RELEVANTE,
        'surtidor': surtidor,
        'combustible': combustible,
        'confianza': 0.50,
        'motivo': 'Baja confianza (por debajo del umbral)'
    }

def entrenar_modelo_disponibilidad(df):
    y = []
    for _, row in df.iterrows():
        msg = row['mensaje']
        if es_pregunta(msg) or es_ruido(msg):
            y.append(CAT_NO_RELEVANTE)
        elif PATRONES_NO_HAY.search(msg):
            y.append(CAT_NO_HAY)
        elif PATRONES_HAY.search(msg):
            y.append(CAT_HAY)
        else:
            y.append(CAT_NO_RELEVANTE)

    df_ml = df.copy()
    df_ml['categoria_target'] = y
    df_valid = df_ml[df_ml['mensaje_limpio'].str.len() > 0]

    X = df_valid['mensaje_limpio'].values
    y_labels = df_valid['categoria_target'].values

    pipeline = Pipeline([
        ('tfidf', TfidfVectorizer(max_features=3000, ngram_range=(1, 2), min_df=2)),
        ('clf', LogisticRegression(class_weight='balanced', max_iter=2000, random_state=42))
    ])

    pipeline.fit(X, y_labels)
    return pipeline

def analizar_relaciones(df):
    df_directo = df[(df['surtidor'].notna()) & (df['combustible'].notna())].copy()
    relaciones = defaultdict(lambda: defaultdict(int))
    for _, row in df_directo.iterrows():
        relaciones[row['surtidor']][row['combustible']] += 1

    for archivo in df['archivo'].unique():
        df_arch = df[df['archivo'] == archivo].reset_index(drop=True)
        for i in range(len(df_arch)):
            for j in range(max(0, i - 3), min(len(df_arch), i + 4)):
                if i == j:
                    continue
                msg_i = df_arch.iloc[i]
                msg_j = df_arch.iloc[j]

                if pd.notna(msg_i['surtidor']) and pd.isna(msg_i['combustible']):
                    if pd.notna(msg_j['combustible']) and pd.isna(msg_j['surtidor']):
                        relaciones[msg_i['surtidor']][msg_j['combustible']] += 0.5

                if pd.notna(msg_i['combustible']) and pd.isna(msg_i['surtidor']):
                    if pd.notna(msg_j['surtidor']) and pd.isna(msg_j['combustible']):
                        relaciones[msg_j['surtidor']][msg_i['combustible']] += 0.5

    return relaciones

def contar_demanda_combustible(df):
    demanda = defaultdict(int)
    for _, row in df.iterrows():
        if es_pregunta(row['mensaje']):
            combs = detectar_todos_combustibles(row['mensaje'])
            if combs:
                for c in combs:
                    demanda[c] += 1
            else:
                demanda['Gasolina (sin especificar)'] += 1
    return dict(demanda)

def contar_reportes_disponibilidad(df):
    disponibilidad = defaultdict(lambda: defaultdict(int))
    for _, row in df.iterrows():
        if es_reporte(row['mensaje']):
            surts = detectar_todos_surtidores(row['mensaje'])
            combs = detectar_todos_combustibles(row['mensaje'])
            for s in surts:
                if combs:
                    for c in combs:
                        disponibilidad[s][c] += 1
                else:
                    disponibilidad[s]['Gasolina (tipo no especificado)'] += 1
    return disponibilidad

def ejecutar_casos_de_prueba(pipeline_ml=None):
    print('\n===========================================================================')
    print(' EJECUCION DE CASOS DE PRUEBA (CRITERIOS DE ACEPTACION CU-06)')
    print('===========================================================================')

    print('\n--- CP-06: Mensaje claro de disponibilidad de combustible ---')
    cp06 = [
        'ya llegó gasolina',
        'surtidor moto mendez ya tiene diésel',
        'En el tacuarandi acabo de cargar especial',
        'Don Daniel tiene la clarita sin fila',
    ]
    ok_06 = 0
    for msg in cp06:
        res = clasificar_mensaje(msg, pipeline_ml)
        es_ok = res['categoria'] == CAT_HAY
        if es_ok:
            ok_06 += 1
        st = 'OK' if es_ok else 'FALLO'
        print(f'  [{st}] "{msg}"')
        print(f'       -> Categoria: "{res["categoria"]}" | Surtidor: {res["surtidor"]} | Combustible: {res["combustible"]}')
    print(f'  Resultado CP-06: {ok_06}/{len(cp06)} OK')

    print('\n--- CP-07: Mensaje ambiguo o irrelevante ---')
    cp07 = [
        'buen dia a todos',
        'alguien sabe donde hay gasolina especial por favor?',
        'jajaja xd',
        'quien vende autos usados',
    ]
    ok_07 = 0
    for msg in cp07:
        res = clasificar_mensaje(msg, pipeline_ml)
        es_ok = res['categoria'] == CAT_NO_RELEVANTE
        if es_ok:
            ok_07 += 1
        st = 'OK' if es_ok else 'FALLO'
        print(f'  [{st}] "{msg}"')
        print(f'       -> Categoria: "{res["categoria"]}" | Surtidor: {res["surtidor"]}')
    print(f'  Resultado CP-07: {ok_07}/{len(cp07)} OK')

    print('\n--- CP-08: Mensaje con errores ortográficos y emojis ---')
    cp08 = [
        'En don daniel hay clarita 👍',
        'en el tacurandi ay diessel ⛽',
        'ypfb morros blancos estan dando blanquita',
        'en la mas vegas ay la plus 🚗',
    ]
    ok_08 = 0
    for msg in cp08:
        res = clasificar_mensaje(msg, pipeline_ml)
        es_ok = res['categoria'] == CAT_HAY
        if es_ok:
            ok_08 += 1
        st = 'OK' if es_ok else 'FALLO'
        print(f'  [{st}] "{msg}"')
        print(f'       -> Categoria: "{res["categoria"]}" | Surtidor: {res["surtidor"]} | Combustible: {res["combustible"]}')
    print(f'  Resultado CP-08: {ok_08}/{len(cp08)} OK')

    total_ok = ok_06 + ok_07 + ok_08
    total_casos = len(cp06) + len(cp07) + len(cp08)
    print(f'\n  TOTAL CASOS DE PRUEBA: {total_ok}/{total_casos} PASARON EXITOSAMENTE')
    print('===========================================================================')

def main():
    datasets = []
    for ruta in ARCHIVOS_CHAT:
        if not ruta.exists():
            continue
        df_chat, info = parsear_chat_whatsapp(ruta)
        datasets.append(df_chat)

    if not datasets:
        print('  ERROR: No se encontraron archivos de chat.')
        sys.exit(1)

    columnas = ['archivo', 'fecha', 'hora', 'ampm', 'autor', 'mensaje']
    df = pd.concat(datasets, ignore_index=True)
    for col in columnas:
        if col not in df.columns:
            df[col] = ''
    df['mensaje'] = df['mensaje'].fillna('').astype(str).str.strip()
    df['autor'] = df['autor'].fillna('').astype(str).str.strip()

    df = df[df['mensaje'].str.len() > 0].copy()
    df = df[~df['mensaje'].apply(es_ruido)].copy()
    df = df[df['mensaje'].str.len() >= 4].copy()

    df['surtidor'] = df['mensaje'].apply(lambda x: detectar_con_diccionario(x, SURTIDORES))
    df['combustible'] = df['mensaje'].apply(lambda x: detectar_con_diccionario(x, COMBUSTIBLES))
    df['es_pregunta'] = df['mensaje'].apply(es_pregunta)
    df['es_reporte'] = df['mensaje'].apply(es_reporte)
    df['mensaje_limpio'] = df['mensaje'].apply(limpiar_tokens)
    df = df[df['mensaje_limpio'].str.len() > 0].copy()

    pipeline_ml = entrenar_modelo_disponibilidad(df)

    res_list = df['mensaje'].apply(lambda msg: clasificar_mensaje(msg, pipeline_ml))
    df['categoria'] = [r['categoria'] for r in res_list]
    df['confianza'] = [r['confianza'] for r in res_list]

    print('=' * 75)
    print(' REPORTE DE MONITOR DE SURTIDORES VIA WHATSAPP (CU-06)')
    print('=' * 75)

    demanda = contar_demanda_combustible(df)
    demanda_ordenada = sorted(demanda.items(), key=lambda x: x[1], reverse=True)

    print('\n COMBUSTIBLE MAS SOLICITADO (DEMANDA DE USUARIOS):')
    print('-' * 75)
    print(f'  {"Combustible":<30} {"Consultas":>10}  {"Porcentaje":>10}')
    print('  ' + '-' * 55)
    total_preguntas = sum(demanda.values()) or 1
    for i, (comb, cnt) in enumerate(demanda_ordenada, 1):
        pct = cnt / total_preguntas * 100
        barra = '#' * int(pct / 2)
        print(f'  {i}. {comb:<28} {cnt:>8}    {pct:>5.1f}%  {barra}')

    if demanda_ordenada:
        mas_solicitado = demanda_ordenada[0][0]
        print(f'\n  >>> EL COMBUSTIBLE MAS SOLICITADO ES: {mas_solicitado} <<<')

    print('\n DISPONIBILIDAD: SURTIDORES DONDE SE ENCUENTRA CADA COMBUSTIBLE:')
    print('-' * 75)

    disponibilidad = contar_reportes_disponibilidad(df)
    relaciones = analizar_relaciones(df)

    combustible_a_surtidores = defaultdict(lambda: defaultdict(float))
    for surt, combs in relaciones.items():
        for comb, peso in combs.items():
            combustible_a_surtidores[comb][surt] += peso

    for surt, combs in disponibilidad.items():
        for comb, cnt in combs.items():
            combustible_a_surtidores[comb][surt] += cnt

    for comb_tipo in ['Gasolina Especial', 'Etanol', 'Gasolina Plus', 'Diesel']:
        print(f'\n  --- {comb_tipo.upper()} ---')
        if comb_tipo in combustible_a_surtidores and combustible_a_surtidores[comb_tipo]:
            surtidores = sorted(
                combustible_a_surtidores[comb_tipo].items(),
                key=lambda x: x[1], reverse=True
            )
            print(f'  {"Surtidor":<30} {"Menciones/Reportes":>20}')
            print('  ' + '-' * 52)
            for surt, peso in surtidores:
                peso_int = int(round(peso))
                if peso_int > 0:
                    barra = '#' * min(peso_int, 25)
                    print(f'  {surt:<30} {peso_int:>18}  {barra}')
        else:
            print('  (Sin datos de disponibilidad)')

    ejecutar_casos_de_prueba(pipeline_ml)

if __name__ == '__main__':
    main()
