#!/usr/bin/env python3
"""Sube a GitHub los issues del backlog de Construcción de ChuroViaje.

Lee los issues de los archivos por tecnología de esta carpeta y los crea en el
orden de 00_Indice_de_subida.md, con su Milestone y sus etiquetas. Al subir,
numera las tareas (T-XXX) y los casos de prueba (CP-XX) y agrega el número de
GitHub (#N) a cada dependencia.

Requisitos: Python 3.8 o más nuevo y GitHub CLI (gh) con la sesión iniciada
(`gh auth login`). Los issues quedan a nombre de la cuenta con la que se
inició sesión en gh.

Uso (desde cualquier carpeta):

  1. Simular (no toca GitHub; deja los issues finales en _simulacion/ para revisarlos):
       python subir_issues.py --primera-tarea 87 --primer-cp 20

  2. Crear de verdad:
       python subir_issues.py --primera-tarea 87 --primer-cp 20 --crear

Si la subida se corta, se vuelve a ejecutar el mismo comando: el avance queda en
estado_subida.json y los issues ya creados no se repiten. Al terminar, el script
escribe los números asignados (T, CP y #N) en los archivos del backlog, para
hacer commit de ellos.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import time

try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')
except AttributeError:
    pass

AQUI = os.path.dirname(os.path.abspath(__file__))
CARPETA_REPO = '06_proyect/Backlog_Construccion'
INDICE = '00_Indice_de_subida.md'
ARCHIVOS = ['01_Flutter.md', '02_Java.md', '03_Cpp.md', '04_WhatsApp_LLM.md', '05_Simulador.md', '06_Servidor.md']
ESTADO = os.path.join(AQUI, 'estado_subida.json')
SIMULACION = os.path.join(AQUI, '_simulacion')
COLORES = {
    'caso-uso:': ('1D76DB', 'Caso de uso al que pertenece el issue'),
    'tipo:historia-usuario': ('0E8A16', 'Historia de usuario'),
    'tipo:tarea': ('FBCA04', 'Tarea puntual, no ligada a una HU completa'),
    'tipo:prueba': ('D93F0B', 'Relacionado a un caso de prueba'),
    'fase:': ('5319E7', 'Fase RUP'),
    'tec:': ('C5DEF5', 'Tecnología del issue'),
}


class Error(Exception):
    pass


# ---------------------------------------------------------------- archivos

def leer(nombre):
    with open(os.path.join(AQUI, nombre), 'rb') as f:
        crudo = f.read().decode('utf-8')
    return crudo.replace('\r\n', '\n'), '\r\n' in crudo


def escribir(nombre, texto, crlf):
    if crlf:
        texto = texto.replace('\n', '\r\n')
    with open(os.path.join(AQUI, nombre), 'wb') as f:
        f.write(texto.encode('utf-8'))


SECCION = re.compile(r'^### (?P<titulo>[^\n]+)\n(?P<cuerpo>.*?)(?=^---[ \t]*$|\Z)', re.M | re.S)


def leer_issues():
    """Devuelve {titulo: {archivo, titulo, cuerpo}} con el texto tal como está en los archivos."""
    issues = {}
    for archivo in ARCHIVOS:
        texto, _ = leer(archivo)
        for m in SECCION.finditer(texto):
            titulo = m.group('titulo').strip()
            if titulo in issues:
                raise Error(f'Título repetido: {titulo}')
            issues[titulo] = {'archivo': archivo, 'titulo': titulo, 'cuerpo': m.group('cuerpo').strip('\n')}
    return issues


def leer_indice():
    texto, _ = leer(INDICE)
    milestones, filas = {}, []
    for linea in texto.split('\n'):
        c = [x.strip() for x in linea.strip().strip('|').split('|')]
        if len(c) == 4 and re.fullmatch(r'Sprint \d', c[0]) and re.fullmatch(r'\d{4}-\d\d-\d\d', c[2]):
            milestones[c[0]] = {'fechas': c[1], 'cierre': c[2]}
        elif len(c) == 8 and c[0].isdigit():
            filas.append({'orden': int(c[0]), 'titulo': c[1], 'milestone': c[3],
                          'etiquetas': [e.strip() for e in c[4].split(',') if e.strip()]})
    filas.sort(key=lambda f: f['orden'])
    if [f['orden'] for f in filas] != list(range(1, len(filas) + 1)):
        raise Error('La tabla «Orden de creación» no va de 1 a N sin saltos.')
    return milestones, filas


# ---------------------------------------------------------------- numeración

def planificar(filas, issues, primera_tarea, primer_cp):
    """Asigna T y CP en el orden de creación. No depende de GitHub."""
    t, cp = primera_tarea, primer_cp
    plan = []
    for f in filas:
        i = issues.get(f['titulo'])
        if i is None:
            raise Error(f"El issue {f['orden']} del índice no está en ningún archivo: {f['titulo']}")
        titulo, cuerpo = f['titulo'], i['cuerpo']
        if titulo.startswith('[T-XXX]'):
            titulo = titulo.replace('[T-XXX]', f'[T-{t:03d}]', 1)
            if cuerpo.count('feature/TXX-') != 1:
                raise Error(f'{f["titulo"]}: no tiene exactamente una rama feature/TXX-')
            cuerpo = cuerpo.replace('feature/TXX-', f'feature/T{t}-')
            t += 1
        elif 'T-XXX' in titulo:
            raise Error(f'T-XXX en un lugar inesperado del título: {titulo}')

        def numerar(_):
            nonlocal cp
            cp += 1
            return f'CP-{cp - 1:02d}'
        cuerpo = re.sub(r'CP-XX\b', numerar, cuerpo)
        plan.append(dict(f, archivo=i['archivo'], original=f['titulo'], titulo_final=titulo, cuerpo_base=cuerpo))
    for p in plan:
        if re.search(r'T-XXX|CP-XX\b|TXX', re.sub(r'^\*\*Depende de:\*\*.*$', '', p['cuerpo_base'], flags=re.M)):
            raise Error(f"Quedó un T-XXX, TXX o CP-XX sin numerar en: {p['titulo_final']}")
    return plan, t - 1, cp - 1


def con_dependencias(p, plan, numeros, simulacion):
    """Reescribe la línea «Depende de» con los títulos numerados y el #N de GitHub."""
    por_original = sorted(plan, key=lambda q: -len(q['original']))

    def linea(m):
        texto = m.group(0)
        marcas = {}
        for k, q in enumerate(por_original):
            if q['original'] in texto:
                if q['orden'] >= p['orden']:
                    raise Error(f"{p['titulo_final']} depende de {q['original']}, que se crea después")
                n = numeros.get(str(q['orden']))
                ref = f' #{n}' if n else (f' #?(orden {q["orden"]})' if simulacion else '')
                if not n and not simulacion:
                    raise Error(f"Falta el número de GitHub de la dependencia {q['titulo_final']}")
                marca = f'\x00{k}\x00'
                marcas[marca] = q['titulo_final'] + ref
                texto = texto.replace(q['original'], marca)
        for marca, valor in marcas.items():
            texto = texto.replace(marca, valor)
        return texto
    return re.sub(r'^\*\*Depende de:\*\*.*$', linea, p['cuerpo_base'], flags=re.M)


def enlaces_absolutos(cuerpo, repo, rama):
    """Los enlaces relativos del backlog apuntan al repo en GitHub, para que funcionen dentro del issue."""
    def cambiar(m):
        destino = m.group(1)
        ruta, ancla = (destino.split('#', 1) + [''])[:2]
        completa = os.path.normpath(os.path.join(CARPETA_REPO, ruta)).replace('\\', '/')
        return f'](https://github.com/{repo}/blob/{rama}/{completa}' + (f'#{ancla}' if ancla else '') + ')'
    return re.sub(r'\]\((?!https?://|#|mailto:)([^)\s]+)\)', cambiar, cuerpo)


# ---------------------------------------------------------------- GitHub

def gh(*args, entrada=None):
    try:
        r = subprocess.run(['gh', *args], input=entrada, capture_output=True,
                           text=True, encoding='utf-8', errors='replace')
    except FileNotFoundError:
        raise Error('No se encontró gh. Instálalo desde https://cli.github.com e inicia sesión con «gh auth login».')
    if r.returncode != 0:
        raise Error(f"gh {' '.join(args[:3])} falló:\n{r.stderr.strip() or r.stdout.strip()}")
    return r.stdout


def preparar_github(repo, plan, milestones, estado, primera_tarea, primer_cp):
    gh('auth', 'status')
    print(f'Sesión de gh: {gh("api", "user", "--jq", ".login").strip()}')

    existentes = json.loads(gh('issue', 'list', '--repo', repo, '--state', 'all', '--limit', '2000',
                               '--json', 'number,title,body'))
    mios = set(estado['creados'].values())
    ajenos = [e for e in existentes if e['number'] not in mios]
    max_t = max([int(x) for e in ajenos for x in re.findall(r'\bT-(\d{3})\b', e['title'])] or [0])
    max_cp = max([int(x) for e in ajenos for x in re.findall(r'\bCP-(\d+)\b', e['body'] or '')] or [0])
    print(f'En GitHub, la última tarea es T-{max_t:03d} y el último caso de prueba es CP-{max_cp:02d}.')
    if primera_tarea <= max_t or primer_cp <= max_cp:
        raise Error(f'Los números chocan con los que ya hay en GitHub. Usa --primera-tarea {max_t + 1} '
                    f'y --primer-cp {max_cp + 1} (y revisa TRAZABILIDAD.md).')

    titulos = {e['title']: e['number'] for e in existentes}
    for p in plan:
        k = str(p['orden'])
        if k not in estado['creados'] and p['titulo_final'] in titulos:
            raise Error(f"Ya existe en GitHub un issue con el título «{p['titulo_final']}» (#{titulos[p['titulo_final']]}) "
                        'que no está en estado_subida.json. Revisa antes de seguir para no duplicar.')

    hay = {m['title'] for m in json.loads(gh('api', f'repos/{repo}/milestones?state=all&per_page=100'))}
    for nombre in sorted({p['milestone'] for p in plan}):
        if nombre in hay:
            print(f'Milestone «{nombre}»: ya existe.')
            continue
        datos = milestones[nombre]
        gh('api', '-X', 'POST', f'repos/{repo}/milestones', '-f', f'title={nombre}',
           '-f', f"due_on={datos['cierre']}T12:00:00Z", '-f', f"description=Construcción, {datos['fechas']}")
        print(f'Milestone «{nombre}»: creado, cierra el {datos["cierre"]}.')

    etiquetas = {e['name'] for e in json.loads(gh('label', 'list', '--repo', repo, '--limit', '500', '--json', 'name'))}
    for nombre in sorted({e for p in plan for e in p['etiquetas']}):
        if nombre in etiquetas:
            continue
        color, desc = next((v for k, v in COLORES.items() if nombre.startswith(k)), ('BFDADC', ''))
        gh('label', 'create', nombre, '--repo', repo, '--color', color, '--description', desc)
        print(f'Etiqueta «{nombre}»: creada.')


def crear_issue(repo, p, cuerpo, proyecto, propietario):
    with tempfile.NamedTemporaryFile('w', encoding='utf-8', suffix='.md', delete=False) as f:
        f.write(cuerpo + '\n')
        ruta = f.name
    try:
        args = ['issue', 'create', '--repo', repo, '--title', p['titulo_final'], '--body-file', ruta,
                '--milestone', p['milestone']]
        for e in p['etiquetas']:
            args += ['--label', e]
        url = gh(*args).strip().splitlines()[-1]
    finally:
        os.remove(ruta)
    m = re.search(r'/issues/(\d+)$', url)
    if not m:
        raise Error(f'No se entendió la respuesta de gh: {url}')
    if proyecto:
        try:
            gh('project', 'item-add', str(proyecto), '--owner', propietario, '--url', url)
        except Error as e:
            print(f'  Aviso: no se pudo agregar al tablero ({e}). Agrégalo a mano.')
    return int(m.group(1))


# ---------------------------------------------------------------- estado y archivos

def cargar_estado(args):
    if not os.path.exists(ESTADO):
        return None
    with open(ESTADO, encoding='utf-8') as f:
        estado = json.load(f)
    for clave, valor in (('repo', args.repo), ('primera_tarea', args.primera_tarea), ('primer_cp', args.primer_cp)):
        if estado[clave] != valor:
            raise Error(f'estado_subida.json se hizo con {clave} = {estado[clave]}, no {valor}. '
                        'Usa los mismos valores para continuar.')
    return estado


def guardar_estado(estado):
    with open(ESTADO, 'w', encoding='utf-8') as f:
        json.dump(estado, f, ensure_ascii=False, indent=2)


def actualizar_archivos(plan, numeros):
    """Escribe en el backlog los títulos numerados, los CP, las ramas y los #N."""
    por_archivo = {}
    for p in plan:
        por_archivo.setdefault(p['archivo'], []).append(p)
    for archivo, lista in por_archivo.items():
        texto, crlf = leer(archivo)
        for p in lista:
            viejo = f"### {p['original']}\n"
            if texto.count(viejo) != 1:
                raise Error(f'No se encontró una sola vez el encabezado de {p["original"]} en {archivo}')
            inicio = texto.index(viejo)
            m = SECCION.match(texto, inicio)
            crudo = m.group('cuerpo')
            antes = crudo[:len(crudo) - len(crudo.lstrip('\n'))]
            despues = crudo[len(crudo.rstrip('\n')):]
            nuevo = f"### {p['titulo_final']}\n{antes}{con_dependencias(p, plan, numeros, False)}{despues}"
            texto = texto[:m.start()] + nuevo + texto[m.end():]
        escribir(archivo, texto, crlf)

    texto, crlf = leer(INDICE)
    lineas = texto.split('\n')
    por_orden = {p['orden']: p for p in plan}
    for k, linea in enumerate(lineas):
        c = linea.strip().strip('|').split('|')
        if len(c) == 8 and c[0].strip().isdigit():
            p = por_orden[int(c[0].strip())]
            c[1] = f" {p['titulo_final']} "
            c[7] = f" #{numeros[str(p['orden'])]} "
            lineas[k] = '|' + '|'.join(c) + '|'
    escribir(INDICE, '\n'.join(lineas), crlf)


# ---------------------------------------------------------------- principal

def simular(plan, args, ultima_t, ultimo_cp):
    os.makedirs(SIMULACION, exist_ok=True)
    with open(os.path.join(SIMULACION, '.gitignore'), 'w', encoding='utf-8') as f:
        f.write('*\n')
    resumen = []
    for p in plan:
        cuerpo = enlaces_absolutos(con_dependencias(p, plan, {}, True), args.repo, args.rama)
        nombre = f"{p['orden']:02d}_{re.sub(r'[^A-Za-z0-9]+', '-', p['titulo_final']).strip('-')[:60]}.md"
        with open(os.path.join(SIMULACION, nombre), 'w', encoding='utf-8') as f:
            f.write(f"Título: {p['titulo_final']}\nMilestone: {p['milestone']}\n"
                    f"Etiquetas: {', '.join(p['etiquetas'])}\n\n{cuerpo}\n")
        resumen.append(f"{p['orden']:>2}. {p['milestone']} · {p['titulo_final']}")
    with open(os.path.join(SIMULACION, '00_resumen.txt'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(resumen) + '\n')
    print('\n'.join(resumen))
    print(f'\nSimulación lista: {len(plan)} issues, tareas T-{args.primera_tarea:03d} a T-{ultima_t:03d}, '
          f'casos de prueba CP-{args.primer_cp:02d} a CP-{ultimo_cp:02d}.')
    print(f'Revisa los archivos de {SIMULACION} (git los ignora).')
    print('Las dependencias aparecen como «#?(orden N)»: el número real se pone al crear.')
    print('Para crear los issues, repite el comando agregando --crear.')


def principal():
    ap = argparse.ArgumentParser(description='Sube a GitHub los issues del backlog de Construcción.')
    ap.add_argument('--primera-tarea', type=int, required=True, help='Número de la primera tarea libre, por ejemplo 87 para T-087')
    ap.add_argument('--primer-cp', type=int, required=True, help='Número del primer caso de prueba libre, por ejemplo 20 para CP-20')
    ap.add_argument('--repo', default='uniWASD/ChuroViajeInterno')
    ap.add_argument('--rama', default='develop', help='Rama a la que apuntan los enlaces a documentos')
    ap.add_argument('--crear', action='store_true', help='Crear los issues en GitHub (sin esto, solo simula)')
    ap.add_argument('--proyecto', type=int, help='Número del tablero (Project) donde agregar cada issue (opcional)')
    ap.add_argument('--propietario', default='uniWASD', help='Dueño del tablero, si se usa --proyecto')
    ap.add_argument('--pausa', type=float, default=3.0, help='Segundos entre issue e issue')
    args = ap.parse_args()

    estado = cargar_estado(args)
    if estado and estado.get('archivos_actualizados'):
        raise Error('Esta subida ya terminó y los archivos ya tienen los números. Si quieres repetirla, '
                    'borra estado_subida.json a sabiendas.')

    milestones, filas = leer_indice()
    issues = leer_issues()
    sobran = set(issues) - {f['titulo'] for f in filas}
    if sobran:
        raise Error(f'Hay issues en los archivos que no están en el índice: {sorted(sobran)}')
    plan, ultima_t, ultimo_cp = planificar(filas, issues, args.primera_tarea, args.primer_cp)
    for p in plan:
        if p['milestone'] not in milestones:
            raise Error(f"El Milestone «{p['milestone']}» no está en la tabla de Milestones del índice.")
        con_dependencias(p, plan, {}, True)  # comprueba que no dependa de algo posterior

    if not args.crear:
        simular(plan, args, ultima_t, ultimo_cp)
        return

    if estado is None:
        estado = {'repo': args.repo, 'primera_tarea': args.primera_tarea, 'primer_cp': args.primer_cp,
                  'creados': {}, 'archivos_actualizados': False}
    preparar_github(args.repo, plan, milestones, estado, args.primera_tarea, args.primer_cp)

    for p in plan:
        k = str(p['orden'])
        if k in estado['creados']:
            print(f"{p['orden']:>2}. ya estaba: #{estado['creados'][k]} {p['titulo_final']}")
            continue
        cuerpo = enlaces_absolutos(con_dependencias(p, plan, estado['creados'], False), args.repo, args.rama)
        numero = crear_issue(args.repo, p, cuerpo, args.proyecto, args.propietario)
        estado['creados'][k] = numero
        guardar_estado(estado)
        print(f"{p['orden']:>2}. creado: #{numero} {p['titulo_final']}")
        time.sleep(args.pausa)

    actualizar_archivos(plan, estado['creados'])
    estado['archivos_actualizados'] = True
    guardar_estado(estado)
    print(f'\nListo: {len(plan)} issues en GitHub, tareas T-{args.primera_tarea:03d} a T-{ultima_t:03d}, '
          f'casos de prueba CP-{args.primer_cp:02d} a CP-{ultimo_cp:02d}.')
    print('Los archivos del backlog ya tienen los números. Revisa el cambio, actualiza TRAZABILIDAD.md y haz commit '
          '(estado_subida.json puede borrarse después).')


if __name__ == '__main__':
    try:
        principal()
    except Error as e:
        print(f'\nError: {e}', file=sys.stderr)
        sys.exit(1)
