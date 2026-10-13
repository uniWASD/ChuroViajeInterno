#!/usr/bin/env python3
"""Lleva a GitHub los cambios del backlog de Construcción.

Compara cada issue de los archivos de esta carpeta con el que está en GitHub y:
  - crea los que todavía no tienen número en 00_Indice_de_subida.md;
  - actualiza título, cuerpo, Milestone y etiquetas de los que cambiaron;
  - asigna cada issue a su responsable, según la tabla de 07_Estimacion.md;
  - con --proyecto, carga la talla de cada issue en los campos «Talla» y
    «Puntos» del tablero (si los campos no existen, los crea).

07_Estimacion.md es donde se cambian el responsable y la talla de un issue.
Cuando el responsable tiene usuario de GitHub (tabla «Usuarios de GitHub» de
ese archivo), el issue se le asigna y la línea «Responsable sugerido» sale del
cuerpo; si había alguien de apoyo, queda como «Apoyo». Los issues de quien
todavía no tiene usuario quedan sin asignar y conservan esa línea.

No toca los issues cerrados ni los que ya están al día.

Requisitos: los mismos que subir_issues.py (Python 3.8+ y gh con la sesión
iniciada). Para el tablero, gh necesita el permiso «project»:
    gh auth refresh -s project

Uso (desde cualquier carpeta):

  1. Ver qué cambiaría (no toca GitHub ni los archivos):
       python sincronizar_issues.py --proyecto 1

  2. Aplicar los cambios:
       python sincronizar_issues.py --proyecto 1 --aplicar

Al aplicar, también actualiza los archivos: los números de los issues nuevos
(índice, dependencias, TRAZABILIDAD.md y estimación), los cuerpos sin la línea
del responsable y la tabla de carga por persona de 07_Estimacion.md.
"""
import argparse
import json
import os
import re
import sys
import tempfile
import time

sys.dont_write_bytecode = True  # no dejar __pycache__ en la carpeta del backlog
from subir_issues import (AQUI, ARCHIVOS, INDICE, SECCION, COLORES, Error, leer, escribir,
                          leer_issues, enlaces_absolutos, gh)

TRAZABILIDAD = os.path.join('..', 'TRAZABILIDAD.md')
ESTIMACION = '07_Estimacion.md'
PENDIENTE = re.compile(r'#\?\(orden (\d+)\)')
CAMPO_TALLA, CAMPO_PUNTOS = 'Talla', 'Puntos'


# ---------------------------------------------------------------- índice

def leer_indice_completo():
    texto, _ = leer(INDICE)
    milestones, filas = {}, []
    for linea in texto.split('\n'):
        c = [x.strip() for x in linea.strip().strip('|').split('|')]
        if len(c) == 4 and re.fullmatch(r'Sprint \d', c[0]) and re.fullmatch(r'\d{4}-\d\d-\d\d', c[2]):
            milestones[c[0]] = {'fechas': c[1], 'cierre': c[2]}
        elif len(c) == 8 and c[0].isdigit():
            numero = re.fullmatch(r'#(\d+)', c[7])
            if c[7] and not numero:
                raise Error(f'Fila {c[0]} del índice: «{c[7]}» no es un número de issue (#N).')
            filas.append({'orden': int(c[0]), 'titulo': c[1], 'milestone': c[3],
                          'etiquetas': [e.strip() for e in c[4].split(',') if e.strip()],
                          'numero': int(numero.group(1)) if numero else None})
    filas.sort(key=lambda f: f['orden'])
    return milestones, filas


# ---------------------------------------------------------------- estimación

def tabla(texto, encabezado):
    """Filas (como listas de celdas) de la tabla que empieza con la línea `encabezado`."""
    lineas = texto.split('\n')
    for k, linea in enumerate(lineas):
        if linea.strip().startswith(encabezado):
            filas = []
            for l in lineas[k + 2:]:
                if not l.strip().startswith('|'):
                    break
                filas.append([x.strip() for x in l.strip().strip('|').split('|')])
            return filas
    raise Error(f'{ESTIMACION}: no se encontró la tabla que empieza con «{encabezado}».')


def leer_estimacion():
    texto, _ = leer(ESTIMACION)
    escala = {f[0]: int(f[2]) for f in tabla(texto, '| Talla |')}
    usuarios = {}
    for f in tabla(texto, '| Persona | Usuario de GitHub |'):
        usuarios[f[0]] = f[1] if re.fullmatch(r'[A-Za-z0-9-]+', f[1]) else None
    filas = {}
    for f in tabla(texto, '| Orden | Issue |'):
        orden, titulo, responsable, talla = int(f[0]), f[2], f[4], f[5]
        if talla not in escala:
            raise Error(f'{ESTIMACION}, fila {orden}: la talla «{talla}» no está en la escala.')
        personas = [p.strip() for p in responsable.split(' y ')]
        for p in personas:
            if p not in usuarios:
                raise Error(f'{ESTIMACION}, fila {orden}: «{p}» no está en la tabla de usuarios de GitHub.')
        filas[orden] = {'titulo': titulo, 'responsable': responsable, 'personas': personas, 'talla': talla}
    return escala, usuarios, filas


def asignados(est, usuarios):
    return sorted({usuarios[p] for p in est['personas'] if usuarios[p]})


def ajustar_cuerpo(cuerpo, est, usuarios):
    """Quita «Responsable sugerido» si todos los responsables tienen usuario; deja el apoyo."""
    def linea(m):
        texto = m.group(0)
        apoyo = None
        r = re.search(r' · \*\*Responsable sugerido:\*\* (.+)$', texto)
        a = re.search(r' · \*\*Apoyo:\*\* (.+)$', texto)
        if r:
            partes = r.group(1).split(', con ', 1)
            apoyo = partes[1] if len(partes) == 2 else None
            texto = texto[:r.start()]
        elif a:
            apoyo = a.group(1)
            texto = texto[:a.start()]
        if all(usuarios[p] for p in est['personas']):
            return texto + (f' · **Apoyo:** {apoyo}' if apoyo else '')
        return texto + f" · **Responsable sugerido:** {est['responsable']}" + (f', con {apoyo}' if apoyo else '')
    return re.sub(r'^\*\*Tecnología:\*\*.*$', linea, cuerpo, count=1, flags=re.M)


def carga(filas_indice, estimacion, escala):
    personas, valores = [], {}
    for f in filas_indice:
        e = estimacion[f['orden']]
        s = int(f['milestone'][-1])
        for p in e['personas']:
            if p not in personas:
                personas.append(p)
            valores[(p, s)] = valores.get((p, s), 0) + escala[e['talla']] / len(e['personas'])
    return personas, valores


def actualizar_estimacion(filas_indice, numeros, estimacion, escala):
    """Pone al día número, título y sprint de cada fila, y recalcula la carga por persona."""
    texto, crlf = leer(ESTIMACION)
    por_orden = {f['orden']: f for f in filas_indice}
    lineas = texto.split('\n')
    for k, linea in enumerate(lineas):
        c = linea.strip().strip('|').split('|')
        if len(c) == 7 and c[0].strip().isdigit():
            f = por_orden[int(c[0].strip())]
            n = numeros.get(f['orden'])
            c[1] = f" #{n} " if n else f" #?(orden {f['orden']}) "
            c[2] = f" {f['titulo']} "
            c[3] = f" {f['milestone'][-1]} "
            lineas[k] = '|' + '|'.join(c) + '|'
    texto = '\n'.join(lineas)

    personas, valores = carga(filas_indice, estimacion, escala)
    orden = ['Alex', 'Jorge', 'Antonio', 'Marcelo', 'Franz']
    personas = [p for p in orden if p in personas] + [p for p in personas if p not in orden]
    sprints = sorted({int(f['milestone'][-1]) for f in filas_indice})
    def num(x): return ('%g' % x).replace('.', ',')
    nueva = ['| Persona | ' + ' | '.join(f'S{s}' for s in sprints) + ' | Total |',
             '| --- |' + ' --- |' * (len(sprints) + 1)]
    for p in personas:
        v = [valores.get((p, s), 0) for s in sprints]
        nueva.append(f'| {p} | ' + ' | '.join(num(x) for x in v) + f' | {num(sum(v))} |')
    tot = [sum(valores.get((p, s), 0) for p in personas) for s in sprints]
    nueva.append('| **Sprint** | ' + ' | '.join(num(x) for x in tot) + f' | {num(sum(tot))} |')
    m = re.search(r'^\| Persona \| S1 .*?(?=\n(?!\|))', texto, re.M | re.S)
    if not m:
        raise Error(f'{ESTIMACION}: no se encontró la tabla de carga por persona.')
    texto = texto[:m.start()] + '\n'.join(nueva) + texto[m.end():]

    cuantos = [(t, sum(1 for e in estimacion.values() if e['talla'] == t)) for t in escala]
    partes = [f'{n} {t}' for t, n in cuantos if n]
    total = f'En total: {len(estimacion)} issues, ' + (', '.join(partes[:-1]) + ' y ' + partes[-1] if len(partes) > 1 else partes[0]) + '.'
    xl = dict(cuantos).get('XL', 0)
    total += ' No queda ningún XL.' if not xl else f' Hay {xl} XL, que conviene dividir.'
    texto = re.sub(r'^En total: \d+ issues,.*$', total, texto, count=1, flags=re.M)
    escribir(ESTIMACION, texto, crlf)


# ---------------------------------------------------------------- cuerpos y dependencias

def dependencias(cuerpo, filas, numeros, simulacion):
    """Pone «título #N» en la línea «Depende de» para cada issue del índice que aparezca."""
    por_largo = sorted(filas, key=lambda f: -len(f['titulo']))

    def linea(m):
        texto = m.group(0)
        marcas = {}
        for k, f in enumerate(por_largo):
            patron = re.escape(f['titulo']) + r'(?: #\d+| #\?\(orden \d+\))?'
            if not re.search(patron, texto):
                continue
            n = numeros.get(f['orden'])
            if n:
                valor = f"{f['titulo']} #{n}"
            elif simulacion:
                valor = f"{f['titulo']} #?(orden {f['orden']})"
            else:
                raise Error(f"Falta el número de GitHub de {f['titulo']}")
            marca = f'\x00{k}\x00'
            marcas[marca] = valor
            texto = re.sub(patron, marca, texto)
        for marca, valor in marcas.items():
            texto = texto.replace(marca, valor)
        return texto
    return re.sub(r'^\*\*Depende de:\*\*.*$', linea, cuerpo, flags=re.M)


def guardar_cuerpos(nuevos):
    """Escribe en los archivos los cuerpos que cambiaron (por ahora, sin la línea del responsable)."""
    por_archivo = {}
    for titulo, (archivo, cuerpo) in nuevos.items():
        por_archivo.setdefault(archivo, []).append((titulo, cuerpo))
    for archivo, lista in por_archivo.items():
        texto, crlf = leer(archivo)
        for titulo, cuerpo in lista:
            m = SECCION.search(texto, texto.index(f'### {titulo}\n'))
            crudo = m.group('cuerpo')
            antes = crudo[:len(crudo) - len(crudo.lstrip('\n'))]
            despues = crudo[len(crudo.rstrip('\n')):]
            texto = texto[:m.start('cuerpo')] + antes + cuerpo + despues + texto[m.end('cuerpo'):]
        escribir(archivo, texto, crlf)


def normal(texto):
    return (texto or '').replace('\r\n', '\n').strip()


def escribir_numeros(filas, numeros):
    """Anota los números nuevos en el índice, las dependencias y TRAZABILIDAD.md."""
    texto, crlf = leer(INDICE)
    lineas = texto.split('\n')
    for k, linea in enumerate(lineas):
        c = linea.strip().strip('|').split('|')
        if len(c) == 8 and c[0].strip().isdigit() and not c[7].strip():
            n = numeros.get(int(c[0].strip()))
            if n:
                c[7] = f' #{n} '
                lineas[k] = '|' + '|'.join(c) + '|'
    escribir(INDICE, '\n'.join(lineas), crlf)

    def reemplazar(m):
        return f'#{numeros[int(m.group(1))]}' if int(m.group(1)) in numeros else m.group(0)
    for archivo in ARCHIVOS:
        texto, crlf = leer(archivo)
        nuevo = re.sub(r'^\*\*Depende de:\*\*.*$', lambda m: dependencias(m.group(0), filas, numeros, True),
                       texto, flags=re.M)
        nuevo = PENDIENTE.sub(reemplazar, nuevo)
        if nuevo != texto:
            escribir(archivo, nuevo, crlf)
    for archivo in (TRAZABILIDAD, ESTIMACION):
        if os.path.exists(os.path.join(AQUI, archivo)):
            texto, crlf = leer(archivo)
            nuevo = PENDIENTE.sub(reemplazar, texto)
            if nuevo != texto:
                escribir(archivo, nuevo, crlf)


# ---------------------------------------------------------------- tablero (Project)

def clave(nombre):
    return re.sub(r'\s+', '', nombre).lower()


def valor_en_item(item, campo):
    for k, v in item.items():
        if clave(k) == clave(campo):
            return v
    return None


class Tablero:
    def __init__(self, numero, propietario, repo):
        self.numero, self.propietario, self.repo = str(numero), propietario, repo
        self.id = json.loads(gh('project', 'view', self.numero, '--owner', propietario, '--format', 'json'))['id']
        self.cargar()

    def cargar(self):
        campos = json.loads(gh('project', 'field-list', self.numero, '--owner', self.propietario,
                               '--format', 'json', '--limit', '100'))['fields']
        self.campos = {c['name']: c for c in campos}
        items = json.loads(gh('project', 'item-list', self.numero, '--owner', self.propietario,
                              '--format', 'json', '--limit', '2000'))['items']
        self.items = {}
        for it in items:
            c = it.get('content') or {}
            repo = c.get('repository') or it.get('repository') or ''
            if c.get('type') == 'Issue' and self.repo.lower() in str(repo).lower() and c.get('number'):
                self.items[int(c['number'])] = it

    def asegurar_campos(self, tallas, aplicar):
        faltan = [n for n in (CAMPO_TALLA, CAMPO_PUNTOS) if n not in self.campos]
        if faltan and not aplicar:
            print(f"Tablero: se crearán los campos {', '.join(faltan)}.")
            return
        if CAMPO_TALLA not in self.campos:
            gh('project', 'field-create', self.numero, '--owner', self.propietario, '--name', CAMPO_TALLA,
               '--data-type', 'SINGLE_SELECT', '--single-select-options', ','.join(tallas))
            print(f'Tablero: campo «{CAMPO_TALLA}» creado.')
        if CAMPO_PUNTOS not in self.campos:
            gh('project', 'field-create', self.numero, '--owner', self.propietario, '--name', CAMPO_PUNTOS,
               '--data-type', 'NUMBER')
            print(f'Tablero: campo «{CAMPO_PUNTOS}» creado.')
        if faltan:
            self.cargar()
        opciones = {o['name'] for o in self.campos[CAMPO_TALLA].get('options', [])}
        if set(tallas) - opciones:
            raise Error(f"El campo «{CAMPO_TALLA}» del tablero no tiene las opciones {sorted(set(tallas) - opciones)}. "
                        'Agrégalas en el tablero y vuelve a correr el script.')

    def cambios(self, numero, talla, puntos):
        it = self.items.get(numero)
        if it is None:
            return ['agregar al tablero', CAMPO_TALLA, CAMPO_PUNTOS]
        c = []
        if valor_en_item(it, CAMPO_TALLA) != talla:
            c.append(CAMPO_TALLA)
        v = valor_en_item(it, CAMPO_PUNTOS)
        if v is None or float(v) != float(puntos):
            c.append(CAMPO_PUNTOS)
        return c

    def aplicar(self, numero, talla, puntos, cambios):
        if numero not in self.items:
            url = f'https://github.com/{self.repo}/issues/{numero}'
            nuevo = json.loads(gh('project', 'item-add', self.numero, '--owner', self.propietario,
                                  '--url', url, '--format', 'json'))
            self.items[numero] = {'id': nuevo['id']}
        item = self.items[numero]['id']
        if CAMPO_TALLA in cambios:
            opcion = next(o['id'] for o in self.campos[CAMPO_TALLA]['options'] if o['name'] == talla)
            gh('project', 'item-edit', '--id', item, '--project-id', self.id,
               '--field-id', self.campos[CAMPO_TALLA]['id'], '--single-select-option-id', opcion)
        if CAMPO_PUNTOS in cambios:
            gh('project', 'item-edit', '--id', item, '--project-id', self.id,
               '--field-id', self.campos[CAMPO_PUNTOS]['id'], '--number', str(puntos))


# ---------------------------------------------------------------- GitHub

def asegurar_milestones_y_etiquetas(repo, filas, milestones):
    hay = {m['title'] for m in json.loads(gh('api', f'repos/{repo}/milestones?state=all&per_page=100'))}
    for nombre in sorted({f['milestone'] for f in filas} - hay):
        datos = milestones[nombre]
        gh('api', '-X', 'POST', f'repos/{repo}/milestones', '-f', f'title={nombre}',
           '-f', f"due_on={datos['cierre']}T12:00:00Z", '-f', f"description=Construcción, {datos['fechas']}")
        print(f'Milestone «{nombre}»: creado.')
    etiquetas = {e['name'] for e in json.loads(gh('label', 'list', '--repo', repo, '--limit', '500', '--json', 'name'))}
    for nombre in sorted({e for f in filas for e in f['etiquetas']} - etiquetas):
        color, desc = next((v for k, v in COLORES.items() if nombre.startswith(k)), ('BFDADC', ''))
        gh('label', 'create', nombre, '--repo', repo, '--color', color, '--description', desc)
        print(f'Etiqueta «{nombre}»: creada.')


def con_archivo(cuerpo, accion):
    with tempfile.NamedTemporaryFile('w', encoding='utf-8', suffix='.md', delete=False) as t:
        t.write(cuerpo + '\n')
        ruta = t.name
    try:
        return accion(ruta)
    finally:
        os.remove(ruta)


# ---------------------------------------------------------------- principal

def principal():
    ap = argparse.ArgumentParser(description='Lleva a GitHub los cambios del backlog de Construcción.')
    ap.add_argument('--repo', default='uniWASD/ChuroViajeInterno')
    ap.add_argument('--rama', default='develop', help='Rama a la que apuntan los enlaces a documentos')
    ap.add_argument('--aplicar', action='store_true', help='Hacer los cambios (sin esto, solo muestra el plan)')
    ap.add_argument('--proyecto', type=int, help='Número del tablero (Project): agrega los issues y carga Talla y Puntos')
    ap.add_argument('--propietario', default='uniWASD', help='Dueño del tablero, si se usa --proyecto')
    ap.add_argument('--pausa', type=float, default=1.0, help='Segundos entre cambio y cambio en GitHub')
    args = ap.parse_args()

    milestones, filas = leer_indice_completo()
    issues = leer_issues()
    escala, usuarios, estimacion = leer_estimacion()
    titulos = {f['titulo'] for f in filas}
    if set(issues) != titulos:
        raise Error('Los archivos y el índice no tienen los mismos issues.\n'
                    f'  Solo en archivos: {sorted(set(issues) - titulos)}\n  Solo en el índice: {sorted(titulos - set(issues))}')
    for f in filas:
        if f['milestone'] not in milestones:
            raise Error(f"El Milestone «{f['milestone']}» no está en la tabla de Milestones del índice.")
        if f['orden'] not in estimacion:
            raise Error(f"El issue {f['orden']} del índice no tiene fila en {ESTIMACION}.")
    sobran = set(estimacion) - {f['orden'] for f in filas}
    if sobran:
        raise Error(f'{ESTIMACION} tiene filas que no están en el índice: {sorted(sobran)}.')

    # Cuerpos con el responsable ajustado (sin la línea si ya tiene usuario de GitHub)
    cuerpos_nuevos = {}
    for f in filas:
        i = issues[f['titulo']]
        nuevo = ajustar_cuerpo(i['cuerpo'], estimacion[f['orden']], usuarios)
        if nuevo != i['cuerpo']:
            cuerpos_nuevos[f['titulo']] = (i['archivo'], nuevo)
            i['cuerpo'] = nuevo
        if re.search(r'T-XXX|TXX|CP-XX\b', f['titulo'] + i['cuerpo']):
            raise Error(f"{f['titulo']} todavía tiene T-XXX, TXX o CP-XX; ponle sus números antes de sincronizar.")

    gh('auth', 'status')
    en_github = {g['number']: g for g in json.loads(gh(
        'issue', 'list', '--repo', args.repo, '--state', 'all', '--limit', '2000',
        '--json', 'number,title,body,milestone,labels,state,assignees'))}
    numeros = {f['orden']: f['numero'] for f in filas if f['numero']}
    del_equipo = {u for u in usuarios.values() if u}

    crear, editar, iguales, cerrados = [], [], 0, []
    for f in filas:
        i = issues[f['titulo']]
        quiero = asignados(estimacion[f['orden']], usuarios)
        if f['numero'] is None:
            crear.append(f)
            continue
        g = en_github.get(f['numero'])
        if g is None:
            raise Error(f"El issue #{f['numero']} ({f['titulo']}) no está en GitHub.")
        cuerpo = enlaces_absolutos(dependencias(i['cuerpo'], filas, numeros, True), args.repo, args.rama)
        actuales_e = {e['name'] for e in g.get('labels', [])}
        actuales_a = {a['login'] for a in g.get('assignees', [])}
        poner_a = set(quiero) - actuales_a
        quitar_a = (actuales_a & del_equipo) - set(quiero)
        cambios = []
        if g['title'] != f['titulo']:
            cambios.append('título')
        if normal(g['body']) != normal(cuerpo):
            cambios.append('cuerpo')
        if ((g.get('milestone') or {}).get('title')) != f['milestone']:
            cambios.append('Milestone')
        if actuales_e != set(f['etiquetas']):
            cambios.append('etiquetas')
        if poner_a or quitar_a:
            cambios.append('asignado')
        if not cambios:
            iguales += 1
        elif g['state'] != 'OPEN':
            cerrados.append((f, cambios))
        else:
            editar.append((f, cambios, actuales_e, poner_a, quitar_a))

    tablero, en_tablero = None, []
    if args.proyecto:
        tablero = Tablero(args.proyecto, args.propietario, args.repo)
        tablero.asegurar_campos(list(escala), False)
        for f in filas:
            if f['numero']:
                e = estimacion[f['orden']]
                c = tablero.cambios(f['numero'], e['talla'], escala[e['talla']])
                if c:
                    en_tablero.append((f, c))

    print(f'Sin cambios: {iguales} issues.')
    if cuerpos_nuevos:
        print(f'Cambia la línea del responsable en {len(cuerpos_nuevos)} cuerpos (los archivos se actualizan al aplicar).')
    for f in crear:
        quien = ', '.join(asignados(estimacion[f['orden']], usuarios)) or 'sin asignar'
        print(f"Crear: {f['titulo']} ({f['milestone']}, {quien})")
    for f, cambios, _, poner_a, quitar_a in editar:
        detalle = ''
        if poner_a or quitar_a:
            detalle = ' [' + ', '.join([f'+{u}' for u in sorted(poner_a)] + [f'-{u}' for u in sorted(quitar_a)]) + ']'
        print(f"Actualizar #{f['numero']} ({', '.join(cambios)}){detalle}: {f['titulo']}")
    for f, cambios in cerrados:
        print(f"No se toca #{f['numero']} porque está cerrado ({', '.join(cambios)}): {f['titulo']}")
    if tablero:
        print(f'Tablero: {len(en_tablero)} issues con Talla o Puntos por cargar' +
              (f", más los {len(crear)} nuevos." if crear else '.'))
    else:
        print('Tablero: no se revisa (usa --proyecto N para cargar Talla y Puntos).')

    if not (crear or editar or en_tablero or cuerpos_nuevos):
        print('Todo está al día.')
        return
    if not args.aplicar:
        print('\nNo se cambió nada. Para aplicar el plan, repite el comando con --aplicar.')
        return

    if cuerpos_nuevos:
        guardar_cuerpos(cuerpos_nuevos)
    asegurar_milestones_y_etiquetas(args.repo, filas, milestones)
    if tablero:
        tablero.asegurar_campos(list(escala), True)

    for f in crear:
        i = issues[f['titulo']]
        cuerpo = enlaces_absolutos(dependencias(i['cuerpo'], filas, numeros, False), args.repo, args.rama)
        argumentos = ['issue', 'create', '--repo', args.repo, '--title', f['titulo'], '--milestone', f['milestone']]
        for e in f['etiquetas']:
            argumentos += ['--label', e]
        for u in asignados(estimacion[f['orden']], usuarios):
            argumentos += ['--assignee', u]
        url = con_archivo(cuerpo, lambda ruta: gh(*argumentos, '--body-file', ruta)).strip().splitlines()[-1]
        m = re.search(r'/issues/(\d+)$', url)
        if not m:
            raise Error(f'No se entendió la respuesta de gh: {url}')
        f['numero'] = numeros[f['orden']] = int(m.group(1))
        escribir_numeros(filas, numeros)
        print(f"Creado #{f['numero']}: {f['titulo']}")
        if tablero:
            en_tablero.append((f, tablero.cambios(f['numero'], None, None)))
        time.sleep(args.pausa)

    for f, cambios, actuales_e, poner_a, quitar_a in editar:
        i = issues[f['titulo']]
        cuerpo = enlaces_absolutos(dependencias(i['cuerpo'], filas, numeros, False), args.repo, args.rama)
        argumentos = ['issue', 'edit', str(f['numero']), '--repo', args.repo, '--title', f['titulo'],
                      '--milestone', f['milestone']]
        for e in sorted(set(f['etiquetas']) - actuales_e):
            argumentos += ['--add-label', e]
        for e in sorted(actuales_e - set(f['etiquetas'])):
            argumentos += ['--remove-label', e]
        for u in sorted(poner_a):
            argumentos += ['--add-assignee', u]
        for u in sorted(quitar_a):
            argumentos += ['--remove-assignee', u]
        con_archivo(cuerpo, lambda ruta: gh(*argumentos, '--body-file', ruta))
        print(f"Actualizado #{f['numero']}: {f['titulo']}")
        time.sleep(args.pausa)

    for f, cambios in en_tablero:
        e = estimacion[f['orden']]
        tablero.aplicar(f['numero'], e['talla'], escala[e['talla']], cambios)
        print(f"Tablero #{f['numero']}: {CAMPO_TALLA} {e['talla']}, {CAMPO_PUNTOS} {escala[e['talla']]}")
        time.sleep(args.pausa / 2)

    escribir_numeros(filas, numeros)
    actualizar_estimacion(filas, numeros, estimacion, escala)
    print('\nListo. Revisa los archivos (índice, cuerpos, TRAZABILIDAD.md y estimación) y haz commit.')


if __name__ == '__main__':
    try:
        principal()
    except Error as e:
        print(f'\nError: {e}', file=sys.stderr)
        sys.exit(1)
