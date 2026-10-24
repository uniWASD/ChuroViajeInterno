#!/usr/bin/env python3
"""Genera 08_Camino_critico.md a partir del índice y de la estimación.

Lee la columna «Depende de» de 00_Indice_de_subida.md y las tallas y
responsables de 07_Estimacion.md, y escribe:
  - el camino crítico: la cadena de dependencias que más puntos suma;
  - las cadenas largas dentro de un mismo sprint;
  - el mapa de dependencias por sprint, en Mermaid (GitHub lo dibuja solo).

08_Camino_critico.md no se edita a mano. Se cambia el índice o la
estimación y se vuelve a correr este script:
    python camino_critico.py
sincronizar_issues.py también lo regenera cuando aplica cambios.

No usa GitHub ni internet.
"""
import re
import sys
from functools import lru_cache

sys.dont_write_bytecode = True
from subir_issues import Error, INDICE, leer, escribir

SALIDA = '08_Camino_critico.md'
LIMITE_SPRINT = 8  # puntos de una cadena dentro de un sprint a partir de los que conviene revisarla
NOMBRE_COLOR = {'Alex': 'azul', 'Jorge': 'verde', 'Antonio': 'amarillo', 'Marcelo': 'lila', 'Franz': 'rosado'}
COLORES = {'Alex': '#cfe2ff', 'Jorge': '#d1e7dd', 'Antonio': '#fff3cd', 'Marcelo': '#e2d9f3', 'Franz': '#f8d7da'}


def leer_dependencias():
    """{orden: texto de la columna «Depende de»} del índice."""
    texto, _ = leer(INDICE)
    dep = {}
    for linea in texto.split('\n'):
        c = [x.strip() for x in linea.strip().strip('|').split('|')]
        if len(c) == 8 and c[0].isdigit():
            dep[int(c[0])] = c[5]
    return dep


def corto(titulo):
    """«[HU-01][CU-01][Java] Registrar…» → ('Java', 'Registrar…')."""
    etiquetas = re.findall(r'\[([^\]]+)\]', titulo)
    nombre = re.sub(r'^(\[[^\]]+\])+\s*', '', titulo)
    tec = next((e for e in reversed(etiquetas) if not re.fullmatch(r'(HU|CU|T)-\d+', e)), '')
    return tec, nombre


def calcular(filas, estimacion, escala):
    por_orden = {f['orden']: f for f in filas}
    sprint = {o: int(f['milestone'][-1]) for o, f in por_orden.items()}
    puntos = {o: escala[estimacion[o]['talla']] for o in por_orden}

    texto_dep = leer_dependencias()
    deps, todos = {}, {}
    for o in por_orden:
        d = texto_dep.get(o, '—')
        if d in ('—', '-', ''):
            deps[o] = []
        elif d == 'Todos los de Java':
            deps[o] = [k for k in por_orden if 'tec:java' in por_orden[k]['etiquetas'] and sprint[k] < sprint[o]]
            todos[o] = d
        elif d == 'Todos los de sprints anteriores':
            deps[o] = [k for k in por_orden if sprint[k] < sprint[o]]
            todos[o] = d
        else:
            try:
                deps[o] = [int(x) for x in d.split(',')]
            except ValueError:
                raise Error(f'{INDICE}, fila {o}: no se entiende «{d}» en «Depende de».')
        for k in deps[o]:
            if k not in por_orden:
                raise Error(f'{INDICE}, fila {o}: depende de {k}, que no está en el índice.')
            if sprint[k] > sprint[o]:
                raise Error(f'{INDICE}, fila {o} (sprint {sprint[o]}) depende de {k}, que es del sprint {sprint[k]}.')

    def cadena(final, permitido):
        camino, o = [], final
        while o:
            camino.append(o)
            o = max((k for k in deps[o] if permitido(k, o)), key=lambda k: (largo_en(k, permitido), -k), default=None)
        return camino[::-1]

    memo = {}
    def largo_en(o, permitido):
        clave = (o, permitido)
        if clave not in memo:
            previos = [k for k in deps[o] if permitido(k, o)]
            memo[clave] = puntos[o] + max((largo_en(k, permitido) for k in previos), default=0)
        return memo[clave]

    todo = lambda k, o: True
    final = max(por_orden, key=lambda o: (largo_en(o, todo), -o))
    critico = cadena(final, todo)

    mismo = lambda k, o: sprint[k] == sprint[o]
    por_sprint = {}
    for s in sorted(set(sprint.values())):
        del_sprint = [o for o in por_orden if sprint[o] == s]
        fin = max(del_sprint, key=lambda o: (largo_en(o, mismo), -o))
        por_sprint[s] = cadena(fin, mismo)

    # Reducción transitiva: se quita k → o si o ya se alcanza desde k por otro camino.
    @lru_cache(None)
    def alcanza(o):
        r = set()
        for k in deps[o]:
            r |= {k} | alcanza(k)
        return frozenset(r)
    flechas = []
    for o in sorted(por_orden, key=lambda x: (sprint[x], x)):
        if o in todos:
            continue
        for k in deps[o]:
            if not any(k in alcanza(j) for j in deps[o] if j != k):
                flechas.append((k, o))
    return {'por_orden': por_orden, 'sprint': sprint, 'puntos': puntos, 'critico': critico,
            'por_sprint': por_sprint, 'flechas': flechas, 'todos': todos}


def nodo(o, c, estimacion):
    f = c['por_orden'][o]
    tec, nombre = corto(f['titulo'])
    num = f"#{f['numero']}" if f['numero'] else f'orden {o}'
    e = estimacion[o]
    return f'    i{o}["{num} · {tec}<br/>{nombre}<br/>{e["responsable"]} · {e["talla"]}"]'


def por_sprints(ordenes, c, estimacion):
    """Un recuadro por sprint, uno debajo del otro, con sus issues de izquierda a derecha."""
    lineas = []
    for s in sorted({c['sprint'][o] for o in ordenes}):
        lineas += [f'    subgraph S{s}["Sprint {s}"]', '    direction LR']
        lineas += ['    ' + nodo(o, c, estimacion) for o in ordenes if c['sprint'][o] == s]
        lineas.append('    end')
    return lineas


def estilos(nodos, critico, estimacion):
    lineas = [f'    classDef {p.lower()} fill:{color},stroke:#666,color:#000' for p, color in COLORES.items()]
    for p in COLORES:
        ids = [f'i{o}' for o in nodos if estimacion[o]['personas'][0] == p]
        if ids:
            lineas.append(f'    class {",".join(ids)} {p.lower()}')
    for o in critico:
        lineas.append(f'    style i{o} stroke:#d73a49,stroke-width:3px')
    return lineas


def generar(filas, estimacion, escala):
    c = calcular(filas, estimacion, escala)
    critico, sprint, puntos = c['critico'], c['sprint'], c['puntos']
    por_orden = c['por_orden']
    def num(o):
        n = por_orden[o]['numero']
        return f'#{n}' if n else f'#?(orden {o})'

    total = sum(puntos[o] for o in critico)
    md = ['# Camino crítico del backlog de Construcción', '',
          '[Índice del backlog](README.md) · [Estimación](07_Estimacion.md)', '',
          'Este archivo lo genera [camino_critico.py](camino_critico.py) con la columna «Depende de» de '
          '[00_Indice_de_subida.md](00_Indice_de_subida.md) y las tallas y responsables de [07_Estimacion.md](07_Estimacion.md). '
          'No se edita a mano: se cambia el índice o la estimación y se vuelve a correr el script. '
          '`sincronizar_issues.py` también lo regenera cuando aplica cambios.', '',
          '## Camino crítico', '',
          f'Es la cadena de dependencias que más puntos suma: {len(critico)} issues y {total} puntos, '
          f'del sprint {sprint[critico[0]]} al {sprint[critico[-1]]}. Si uno de ellos se atrasa, se atrasan los que le siguen, '
          'así que sus Pull Requests tienen prioridad en las revisiones.', '',
          '| Paso | Issue | Título | Sprint | Responsable | Talla | Puntos acumulados |',
          '| --- | --- | --- | --- | --- | --- | --- |']
    acumulado = 0
    for k, o in enumerate(critico, 1):
        acumulado += puntos[o]
        e = estimacion[o]
        md.append(f"| {k} | {num(o)} | {por_orden[o]['titulo']} | {sprint[o]} | {e['responsable']} | {e['talla']} | {acumulado} |")
    md += ['', '```mermaid', 'flowchart TB']
    md += por_sprints(critico, c, estimacion)
    md += ['    ' + ' --> '.join(f'i{o}' for o in critico)]
    md += estilos(critico, [], estimacion)
    md += ['```', '']

    md += ['## Cadenas dentro de un mismo sprint', '',
           'Cuando un issue depende de otro del mismo sprint, avanza con datos falsos que siguen '
           '[05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md) y se conecta de verdad cuando el otro se fusiona. '
           f'Aun así, una cadena larga dentro de una semana es un riesgo: las de {LIMITE_SPRINT} puntos o más conviene '
           'empezarlas el primer día y acordar el contrato antes.', '',
           '| Sprint | Cadena más larga | Puntos | |', '| --- | --- | --- | --- |']
    for s, cad in c['por_sprint'].items():
        p = sum(puntos[o] for o in cad)
        texto = ' → '.join(f"{num(o)} ({estimacion[o]['responsable']}, {estimacion[o]['talla']})" for o in cad)
        md.append(f"| {s} | {texto} | {p} | {'Revisar' if p >= LIMITE_SPRINT and len(cad) > 1 else ''} |")
    md.append('')

    md += ['## Mapa de dependencias', '',
           'Cada caja es un issue, con su responsable y su talla; el color es el del responsable y el borde rojo marca el camino crítico. '
           'Se dibujan solo las dependencias directas: si A → B → C, no se repite A → C.']
    for o, d in c['todos'].items():
        md[-1] += f" {num(o)} depende de «{d[0].lower() + d[1:]}», así que no lleva flechas."
    md += ['', '```mermaid', 'flowchart TB']
    md += por_sprints(sorted(por_orden, key=lambda x: (por_orden[x]['numero'] or 999, x)), c, estimacion)
    en_camino = list(zip(critico, critico[1:]))
    flechas = c['flechas'] + [f for f in en_camino if f not in c['flechas']]  # las que llegan a un «Todos los…»
    rojas = [str(k) for k, f in enumerate(flechas) if f in en_camino]
    md += [f'    i{a} --> i{b}' for a, b in flechas]
    md += estilos(list(por_orden), critico, estimacion)
    if rojas:
        md.append(f"    linkStyle {','.join(rojas)} stroke:#d73a49,stroke-width:3px")
    md += ['```', '', 'Colores: ' + ', '.join(f'{NOMBRE_COLOR[p]} {p}' for p in COLORES) + '.', '']

    try:
        _, crlf = leer(SALIDA)
    except (FileNotFoundError, OSError):
        crlf = False
    escribir(SALIDA, '\n'.join(md), crlf)
    return c


def principal():
    from sincronizar_issues import leer_indice_completo, leer_estimacion
    _, filas = leer_indice_completo()
    escala, _, estimacion = leer_estimacion()
    c = generar(filas, estimacion, escala)
    print(f"{SALIDA} generado: camino crítico de {len(c['critico'])} issues y "
          f"{sum(c['puntos'][o] for o in c['critico'])} puntos.")


if __name__ == '__main__':
    try:
        principal()
    except Error as e:
        print(f'\nError: {e}', file=sys.stderr)
        sys.exit(1)
