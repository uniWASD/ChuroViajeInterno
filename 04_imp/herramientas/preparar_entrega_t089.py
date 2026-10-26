#!/usr/bin/env python3
"""Prepara entrega técnica no aprobada del issue #41 (Tarija) a partir de evidencias locales.
No requiere dependencias, no usa Git/red/PostgreSQL y nunca altera código, CSV originales o migraciones.
"""
from __future__ import annotations
import argparse,csv,hashlib,html,io,json,re,sys,zipfile
from collections import Counter
from datetime import datetime,timezone
from pathlib import Path

HASH_SNAPSHOT='0214fb4252301b8acaea4edb7783b165a8f6c411dbbe35fa53ba09a2a6a9c320'
HASH_REFERENCIA='b1089ac8d22a2e914f6886691cb696669f165b0263164bff6e2a6fe02cf4e36a'
HASH_OPERATIVA='da13387f7d14850caba4231d2c2dbdeee3a5c1048bb0c2bed79bad1f7c759082'
# Identidades propuestas de conformidad con la captura ANH registrada por el usuario.
INCLUIR={
 'Agrupa':'2390', 'Don Daniel':'2804','El Portillo':'2472',
 'Panamericano':'2388','San Jorge':'2688','Sointa':'2364',
 'Surtidor Tarija':'2386','Tacuarandi':'2339'
}
EXCLUIR_FUERA={'El Molle':'2977','Moto Mendez':'2627','San Martin':'2979'}
EXCLUIR_SIN={
 'Campesino','Coliseo Universitario','Domingo Savio','Ex Terminal','Las Vegas',
 'Pimentel','San Geronimo','Villanueva','YPFB Morros Blancos','YPFB Parada Chaco'
}
NOMBRES_21=set(INCLUIR)|set(EXCLUIR_FUERA)|EXCLUIR_SIN
EXTRAS_ESPERADOS={'2341','2642','2911','2974'}
URL_ANH='https://abastecimiento.prod.anh.gob.bo/api/estaciones/?departamento=6&producto=0'

def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as fh:
  for b in iter(lambda:fh.read(1048576),b''): h.update(b)
 return h.hexdigest()

def expected_hash(path,expected,label):
 actual=sha(path)
 if actual.lower()!=expected: raise ValueError(f'{label}: SHA256 NO coincide. Esperado {expected}, obtenido {actual}; se detuvo para evitar mezclar versiones.')
 return actual

def read_csv(path,delimiter=','):
 with path.open('r',encoding='utf-8-sig',newline='') as fh: return list(csv.DictReader(fh,delimiter=delimiter))

def last_file(folder,glob):
 candidates=list(folder.glob(glob))
 if not candidates: raise FileNotFoundError(f'No se encuentra {glob} en {folder}')
 return max(candidates,key=lambda p:p.stat().st_mtime)

def normalize(text):
 import unicodedata
 base=unicodedata.normalize('NFD',text)
 base=''.join(c for c in base if not unicodedata.combining(c))
 value=' '.join(re.sub(r'[^a-z0-9 ]',' ',base.lower()).split())
 if not value: raise ValueError(f'Alias vacio: {text!r}')
 return value

def on_seg(a,b,p):
 cross=(b[0]-a[0])*(p[1]-a[1])-(b[1]-a[1])*(p[0]-a[0])
 return abs(cross)<1e-12 and min(a[0],b[0])-1e-12<=p[0]<=max(a[0],b[0])+1e-12 and min(a[1],b[1])-1e-12<=p[1]<=max(a[1],b[1])+1e-12

def pos_ring(ring,p):
 inside=False
 for a,b in zip(ring,ring[1:]):
  if on_seg(a,b,p): return 'BORDE'
  if (a[1]>p[1]) != (b[1]>p[1]):
   x=a[0]+(b[0]-a[0])*(p[1]-a[1])/(b[1]-a[1])
   if p[0]<x:inside=not inside
 return 'DENTRO' if inside else 'FUERA'

def in_polygon(poly,p):
 outer=pos_ring(poly[0],p)
 if outer=='FUERA':return False
 if outer=='BORDE':return True
 for ring in poly[1:]:
  r=pos_ring(ring,p)
  if r=='BORDE':return True
  if r=='DENTRO':return False
 return True

def in_geo(doc,p):
 for f in doc.get('features',[doc]):
  g=f.get('geometry',f)
  typ=g.get('type'); c=g.get('coordinates',[])
  polys=[c] if typ=='Polygon' else c if typ=='MultiPolygon' else []
  for poly in polys:
   if in_polygon(poly,p):return True
 return False

def coords(row):
 try: lat=float(row['latitud_anh']);lon=float(row['longitud_anh'])
 except (ValueError,KeyError,TypeError) as e: raise ValueError('Coordenada invalida ANH: '+str(row)) from e
 if not (-90<=lat<=90 and -180<=lon<=180): raise ValueError('Coordenadas fuera de rango')
 return lat,lon

def sql_lit(text): return "'" + text.replace("'","''") + "'"

def csv_bytes(rows,fields):
 s=io.StringIO(newline='')
 w=csv.DictWriter(s,fieldnames=fields,delimiter=';',extrasaction='ignore',lineterminator='\n')
 w.writeheader(); w.writerows(rows)
 return ('\ufeff'+s.getvalue()).encode('utf-8')

def safe_name(s):return html.escape(str(s),quote=True)

def svg_geojson(doc, stations):
 polys=[]
 for f in doc.get('features',[doc]):
  g=f.get('geometry',f)
  c=g.get('coordinates',[])
  if g.get('type')=='Polygon':polys.append(c)
  elif g.get('type')=='MultiPolygon':polys+=c
 pts=[p for poly in polys for ring in poly for p in ring]
 if not pts: return '<p>Sin geometria graficable.</p>'
 minx,maxx=min(x for x,y in pts),max(x for x,y in pts)
 miny,maxy=min(y for x,y in pts),max(y for x,y in pts)
 dx=max(.02,maxx-minx);dy=max(.02,maxy-miny)
 minx-=dx*.08;maxx+=dx*.08;miny-=dy*.08;maxy+=dy*.08
 W,H=1000,620; pad=20
 def scale(p):return pad+(p[0]-minx)/(maxx-minx)*(W-2*pad),pad+(maxy-p[1])/(maxy-miny)*(H-2*pad)
 bits=[f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" role="img" aria-label="Perimetro provisional y surtidores candidatos de Tarija">',f'<rect width="{W}" height="{H}" fill="#f2f7fc"/>']
 for poly in polys:
  for i,ring in enumerate(poly):
   path=' '.join(('M' if j==0 else 'L')+f'{scale(p)[0]:.2f},{scale(p)[1]:.2f}' for j,p in enumerate(ring))+' Z'
   bits.append(f'<path d="{path}" stroke="#2563eb" stroke-width="2" fill="{("#93c5fd" if i==0 else "#f2f7fc")}" fill-opacity=".23"/>')
 for s in stations:
  lat,lon=coords(s); p=[lon,lat]
  # omit outliers distantes: no distorsionar la escala cartografica
  if not (minx<=lon<=maxx and miny<=lat<=maxy):continue
  x,y=scale(p)
  state=s['resultado_referencia_NO_APROBADO']; color='#12805c' if state=='DENTRO_REFERENCIA' else '#e07838'
  title=safe_name(f"ID {s['estacion_id_anh']} | {s['nombre_oficial_anh']} | {state} | lat {lat}, lon {lon}")
  bits.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="5.5" stroke="white" stroke-width="1.5" fill="{color}"><title>{title}</title></circle>')
 bits.append('</svg>')
 return ''.join(bits)

def main():
 ap=argparse.ArgumentParser(description='Prepara propuesta de entrega T089 sin modificar aprobaciones ni GitHub')
 ap.add_argument('--proyecto',type=Path,required=True)
 ap.add_argument('--snapshot',type=Path,required=True)
 args=ap.parse_args()
 root=args.proyecto.expanduser().resolve(strict=True); snap=args.snapshot.expanduser().resolve(strict=True)
 evid=root/'evidencias'; evid.mkdir(exist_ok=True)
 ref=root/'datos_pendientes/cartografia_leyes/limite_urbano_ley110_ley118_PENDIENTE.geojson'
 oper=root/'datos/mancha_urbana_tarija.geojson'
 for path,hs,label in [(snap,HASH_SNAPSHOT,'Snapshot original ANH 24-10-2026'),(ref,HASH_REFERENCIA,'Cartografia leyes 110 y 118'),(oper,HASH_OPERATIVA,'Cobertura operativa')]:expected_hash(path,hs,label)
 refdoc=json.loads(ref.read_text(encoding='utf-8-sig')); opdoc=json.loads(oper.read_text(encoding='utf-8-sig'))
 for p,name in [(refdoc,'Referencia'),(opdoc,'Operativa')]:
  if p.get('estado_validacion')!='PROVISIONAL':raise ValueError(f'{name}: estado inesperado; NO ejecutar sin volver a comprobar.')
  if any(f.get('properties',{}).get('revision_equipo')!='PENDIENTE' for f in p.get('features',[])):raise ValueError(f'{name}: revision equipo cambió; validar antes de continuar.')
 auditoria=last_file(evid,'T089_ANH_31_AUDITORIA_*.csv')
 suger=last_file(evid,'T089_PROTO_21_SUGERENCIAS_*.csv')
 anh=read_csv(auditoria,',');proto=read_csv(suger,',')
 if len(anh)!=31 or len(proto)!=21: raise ValueError(f'Cantidad inesperada ANH={len(anh)}, prototipo={len(proto)}')
 byid={s['estacion_id_anh'].strip():s for s in anh}
 if len(byid)!=31:raise ValueError('Ids ANH repetidos')
 byname={p['nombre_prototipo'].strip():p for p in proto}
 if len(byname)!=21 or set(byname)!=NOMBRES_21:raise ValueError('No concuerdan los 21 nombres del prototipo.')
 original=read_csv(root/'datos_pendientes/surtidores_revision.csv')
 originales={r['nombre']:r for r in original}
 if len(originales)!=21 or set(originales)!=NOMBRES_21:raise ValueError('Lista original de prototipo no coincide.')
 count=Counter(x['resultado_referencia_NO_APROBADO'] for x in anh)
 if count['DENTRO_REFERENCIA']!=12 or count['FUERA_REFERENCIA']!=19:raise ValueError(f'Reparto ANH inesperado: {count}')
 if any(s['sha256_snapshot'].lower()!=HASH_SNAPSHOT for s in anh):raise ValueError('CSV ANH mezcló otra huella de snapshot')
 # Validar que las categorías de la auditoría concuerdan con el polígono legal, punto por punto.
 for s in anh:
  lat,lon=coords(s)
  actual='DENTRO_REFERENCIA' if in_geo(refdoc,(lon,lat)) else 'FUERA_REFERENCIA'
  if s['resultado_referencia_NO_APROBADO']!=actual:
   raise ValueError(f"Geometria y CSV discrepan en ANH ID {s['estacion_id_anh']}: {actual} vs {s['resultado_referencia_NO_APROBADO']}")
 rows=[];included=[];aliases={}
 for item in original:
  name=item['nombre']; proposed=byname[name]['candidatos_id_por_nombre'].strip()
  if name in INCLUIR:
   id=INCLUIR[name];decision='INCLUIR'; justification='Coincidencia nominal ANH univoca y punto dentro de referencia legal y cobertura operativa; sujeto a revision final'; reason=''
  elif name in EXCLUIR_FUERA:
   id=EXCLUIR_FUERA[name];decision='EXCLUIR'; justification='Identidad nominal probable; coordenada ANH fuera del perimetro urbano de referencia';reason='Fuera del perimetro urbano de referencia para esta primera carga de Tarija'
  else:
   id='';decision='EXCLUIR';justification='Sin identidad ANH univoca respaldada en este snapshot; no afirmar que la estacion no exista';reason='Sin correspondencia ANH verificable en esta extraccion; pendiente revalidacion de identidad'
  candidates=[k.strip() for k in proposed.split('|') if k.strip()]
  if id and candidates!=[id]:raise ValueError(f'No coincide candidato ID ANH para {name}: {candidates}, esperado {id}')
  if not id and candidates:raise ValueError(f'Candidato inesperado para nombre no identificado {name}: {candidates}')
  s=byid[id] if id else None
  if s:
   lat,lon=coords(s)
   expected='DENTRO_REFERENCIA' if decision=='INCLUIR' else 'FUERA_REFERENCIA'
   if s['resultado_referencia_NO_APROBADO']!=expected:raise ValueError(f'Categoria incoherente {name}')
   if decision=='INCLUIR' and not in_geo(opdoc,(lon,lat)):
    raise ValueError(f'{name}: dentro de referencia pero fuera de la cobertura operativa: requiere resolver, sin generar SQL')
   lon_txt=s['longitud_anh'];lat_txt=s['latitud_anh']
   # Alias seguros: nombre prototipo y nombre oficial exactamente normalizados;
   # omitir abreviaturas/errores y palabras genericas no verificadas por el equipo.
   chosen=[normalize(name)]
   official=normalize(s['nombre_oficial_anh'])
   if official not in chosen:chosen.append(official)
  else:
   lon_txt='';lat_txt='';chosen=[]
  if decision=='INCLUIR':
   for al in chosen:
    owner=aliases.get(al)
    if owner is not None and owner!=name:raise ValueError(f'Alias ambiguo {al}: {owner} vs {name}')
    aliases[al]=name
   included.append((name,lon_txt,lat_txt,s,chosen))
  rows.append(dict(nombre=name,aliases='|'.join(chosen) if decision=='INCLUIR' else '',longitud=lon_txt if decision=='INCLUIR' else '',latitud=lat_txt if decision=='INCLUIR' else '',fuente_ubicacion=URL_ANH if decision=='INCLUIR' else '',revision_franz='NO',aprobado_equipo='NO',decision=decision,motivo_exclusion=reason,id_anh=id,nombre_oficial_anh=s['nombre_oficial_anh'] if s else '',clasificacion_referencia=s['resultado_referencia_NO_APROBADO'] if s else 'SIN_CANDIDATO',dictamen_tecnico=justification,aprobacion_pendiente='SI',sha256_snapshot=HASH_SNAPSHOT))
 if len(included)!=8 or sum(r['decision']=='EXCLUIR' for r in rows)!=13:raise AssertionError('La propuesta debe tener exactamente 8 y 13.')
 extras=[s for s in anh if s['resultado_referencia_NO_APROBADO']=='DENTRO_REFERENCIA' and s['estacion_id_anh'] not in set(INCLUIR.values())]
 if {s['estacion_id_anh'] for s in extras}!=EXTRAS_ESPERADOS:raise ValueError('Lista de adicionales interiores inesperada.')
 extras_rows=[dict(id_anh=s['estacion_id_anh'],nombre_oficial_anh=s['nombre_oficial_anh'],latitud=s['latitud_anh'],longitud=s['longitud_anh'],estado='NO_INCLUIR_EN_ESTA_ENTREGA',motivo='Fuera de los 21 nombres del prototipo; revisar alias/alcance y posible duplicidad antes de incluir',sha256_snapshot=HASH_SNAPSHOT) for s in extras]
 fields=list(rows[0]); ef=list(extras_rows[0])
 tag=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
 sql=['-- T-089 / SOLO PROPUESTA TECNICA / NO APLICAR A BASE DE DATOS', '-- Cobertura legal/operativa: PROVISIONAL, revision: PENDIENTE','-- Aprobacion Franz/Marcelo: NO. Archivo NO es la migracion V2 oficial.', '-- Las sentencias estan comentadas de manera intencional hasta aprobacion formal.','/*']
 for name,lon,lat,s,alias_l in included:
  sql.append(f"INSERT INTO surtidor (nombre, ubicacion) VALUES ({sql_lit(name)}, ST_SetSRID(ST_MakePoint({lon}, {lat}), 4326)::geography);")
 for alias,name in sorted(aliases.items()):
  sql.append(f"INSERT INTO surtidor_alias (surtidor_id, alias) SELECT id, {sql_lit(alias)} FROM surtidor WHERE nombre = {sql_lit(name)};")
 sql+=['*/','-- No ejecutar ni renombrar como V2__surtidores_revisados.sql sin aprobaciones.','']
 report=f'''# T-089 · Dictamen técnico de selección urbana Tarija · 26 de octubre de 2026

**Estado: LISTO PARA REVISIÓN / NO APROBADO PARA PRODUCCIÓN.**

- **Decisión técnica propuesta:** INCLUIR {len(included)} de los 21 del prototipo (coincidencia unívoca ANH y dentro de referencia urbana y cobertura operativa preliminar); EXCLUIR {len(rows)-len(included)} de la primera carga (3 fuera del perímetro legal preliminar; 10 sin identidad ANH demostrada).
- **Datos:** captura ANH 24-10-2026 · 31 estaciones del departamento de Tarija, de las que 12 dentro y 19 fuera de polígono *provisional*; por tanto no son "31 surtidores urbanos".
- **4 estaciones ANH urbanas fuera de prototipo:** La Terminal (2341), Av. Circunvalación (2642), Las Américas (2911), Presidente Arce (2974); ninguna agregada automáticamente. No inferir que Ex Terminal equivale a La Terminal.
- **Alias:** solamente nombres de prototipo y nombre legal ANH normalizados. Se excluyeron alias informales o ambiguos no comprobados como `daniel`, `molle`, `vegas`, `campesino`, `sointar`, `tacurandi`, etc.
- **Cobertura:** se cotejaron los 31 puntos con `limite_urbano_ley110_ley118_PENDIENTE.geojson` y los 8 incluidos contra ambos GeoJSON. Sus estados no se cambiaron: `PROVISIONAL` / `PENDIENTE`.
- **Snapshot ANH SHA-256:** `{HASH_SNAPSHOT}`.
- **Referencia legal SHA-256:** `{HASH_REFERENCIA}`.
- **Cobertura operativa SHA-256:** `{HASH_OPERATIVA}`.
- **Maven:** el usuario acreditó `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` y `BUILD SUCCESS` (se valida el log conservado, sin adjudicarnos nueva ejecución).
- **Spring Boot completo:** sigue bloqueado por la entidad temporal `EventoVial` de HU-01/T-088, cuya tabla `eventos_viales` (o `evento_vial` en rama corregida) no está en las migraciones. Prueba aislada de T-089 aprobada, aplicación global no iniciada.
- **V2 y CP-23 final:** NO ejecutados. Se incluye un PREVIEW de SQL íntegramente comentado, que no es migración Flyway.

## Decisiones propuestas

| Nombre | ID ANH | Acción inicial | Justificación |
|---|---|---|---|
'''
 for r in rows:report+=f"| {r['nombre']} | {r['id_anh'] or '—'} | **{r['decision']}** | {r['dictamen_tecnico'].replace('|',';')} |\n"
 report+='''
## Acciones para cierre formal

1. Franz comprueba campo a campo las coordenadas/alias y firma su revisión; la propuesta no la suplanta.
2. Marcelo/el equipo confirma por escrito las 21 decisiones e inclusión/exclusión, y la política de los alias dudosos. Los 10 sin identidad pueden reabrirse con evidencia nueva.
3. Responsable cartográfico revisa la delimitación real y marca `APROBADO` en el GeoJSON **solo tras validar** las leyes/perímetro y los anillos, no por script.
4. Tras firmas, actualizar el CSV canónico con `revision_franz=SI`, `aprobado_equipo=SI` y generar `V2__surtidores_revisados.sql` usando el generador original, conservando los checks. Ejecutar CP-22/CP-23 en PostGIS y documentar resultados; sin alterar migraciones ya aplicadas.
5. Coordinar la tabla de eventos viales con HU-01/T-088 y el paquete MVC con Jorge. No crear esquema ficticio para superar el arranque.

**No equivale a aceptación definitiva, informe legal municipal ni georreferenciación catastral.**
'''
 # Self-contained HTML: report/map/table; browser-safe strings; avoid global HTML injection
 head='''<!doctype html><html lang="es"><head><meta charset="utf-8"><title>T089 · Selección técnica Tarija</title><style>
 body{font:14px system-ui,-apple-system,Segoe UI,Arial;color:#162639;background:#f5f7fb;margin:0}main{max-width:1320px;margin:auto;padding:30px}h1{font-size:28px;margin-bottom:4px}h2{margin-top:26px}p{line-height:1.5}.muted{color:#64748b}.stats{display:flex;gap:12px;flex-wrap:wrap}.stat{background:white;border:1px solid #dce3eb;border-radius:12px;padding:16px 22px;min-width:155px}.stat b{display:block;font-size:26px}.warn{border-left:4px solid #d97706;background:#fffbeb;padding:12px 16px;margin:16px 0}.map{background:white;border:1px solid #dce3eb;padding:12px;border-radius:12px;max-height:650px}.map svg{width:100%;height:580px}table{border-collapse:collapse;width:100%;font-size:12px;background:white}th,td{text-align:left;padding:9px;border-bottom:1px solid #e2e8f0;vertical-align:top}th{position:sticky;top:0;background:#183658;color:#fff}tr:nth-child(even){background:#f5f8fc}.pill{border-radius:99px;padding:3px 8px;font-weight:600;font-size:11px}.inc{background:#d1fae5;color:#065f46}.exc{background:#fee2e2;color:#991b1b}.wrap{overflow-x:auto}.hash{word-break:break-all;font:12px monospace}</style></head><body><main>'''
 htm=head+'<h1>T-089 · Tarija: selección técnica de surtidores</h1><p class="muted">Issue #41 · Snapshot ANH 24/10/2026 · Ejecución '+safe_name(tag)+'</p><div class="warn"><b>PROPUESTA PARA REVISIÓN — NO APROBADA.</b> Cobertura PROVISIONAL. No es una migración Flyway aplicable.</div><div class="stats"><div class="stat">Prototipo<b>21</b></div><div class="stat">Incluir (propuesta)<b>8</b></div><div class="stat">Excluir de carga inicial<b>13</b></div><div class="stat">ANH dentro de referencia<b>12</b></div><div class="stat">ANH fuera de referencia<b>19</b></div></div><h2>Referencia urbana: mapa esquemático a partir del GeoJSON</h2><p>Verde = puntos dentro de referencia; naranja = fuera. El contorno es PROVISIONAL y no sustituye cartografía municipal aprobada.</p><div class="map">'+svg_geojson(refdoc,anh)+'</div><h2>Decisiones por los 21 nombres</h2><div class="wrap"><table><thead><tr><th>Prototipo</th><th>ANH ID</th><th>Estación ANH</th><th>Ubicación ANH (lat, lon)</th><th>Perímetro</th><th>Propuesta</th><th>Motivo</th></tr></thead><tbody>'
 for r in rows:
  coords_text=f"{r['latitud']}, {r['longitud']}" if r['latitud'] else 'No verificada / no cargada'
  tagcss='inc' if r['decision']=='INCLUIR' else 'exc'
  htm+='<tr>'+''.join(f'<td>{safe_name(v)}</td>' for v in [r['nombre'],r['id_anh'],r['nombre_oficial_anh'],coords_text,r['clasificacion_referencia']])+f'<td><span class="pill {tagcss}">{r["decision"]}</span></td><td>{safe_name(r["dictamen_tecnico"])}</td></tr>'
 htm+='</tbody></table></div><h2>4 estaciones ANH interiores fuera de la lista del prototipo</h2><p>2341 La Terminal · 2642 Avenida Circunvalación · 2911 Las Américas · 2974 Presidente Arce. No se añadieron a esta propuesta.</p><h2>Integridad</h2><p class="hash">Snapshot ANH SHA256: '+HASH_SNAPSHOT+'<br>GeoJSON legal SHA256: '+HASH_REFERENCIA+'<br>GeoJSON operativo SHA256: '+HASH_OPERATIVA+'</p><p><b>Aprobaciones:</b> Franz: NO · Equipo: NO · Cobertura: PROVISIONAL. Revisar acta y polígonos antes de V2/CP-23.</p></main></body></html>'
 # evidence for 16 tests
 log=root/'evidencias/T089_VALIDACION_16_PRUEBAS.txt'
 if not log.is_file():raise FileNotFoundError('Falta evidencia T089_VALIDACION_16_PRUEBAS.txt')
 logged=log.read_text(encoding='utf-8-sig',errors='replace')
 if not (re.search(r'Tests run: 16, Failures: 0, Errors: 0, Skipped: 0',logged) and '[INFO] BUILD SUCCESS' in logged):raise ValueError('No se verificó el estado previo de 16 tests.')
 evidence='EVIDENCIA DE PRUEBAS EXISTENTES (sin ejecutar de nuevo)\nTests run: 16, Failures: 0, Errors: 0, Skipped: 0\nBUILD SUCCESS\nArchivo: evidencias/T089_VALIDACION_16_PRUEBAS.txt\nSHA-256: '+sha(log)+'\nArranque: T089 aislado; no avala backend completo.\n'
 meta=dict(fecha_utc=tag,estado='PROPUESTA_NO_APROBADA',alcance='Primera carga urbana Tarija, no todas las estaciones del departamento',snapshot_sha256=HASH_SNAPSHOT,geojson_legal_sha256=HASH_REFERENCIA,geojson_operativo_sha256=HASH_OPERATIVA,csv_anh_sha256=sha(auditoria),csv_prototipo_sha256=sha(suger),maven_log_sha256=sha(log),estaciones_anh=31,estaciones_dentro_referencia=12,estaciones_fuera_referencia=19,prototipo=21,incluir_propuesto=8,excluir_propuesto=13,identidades_no_verificadas=10,extras_anh_no_incluidos=4,revisado_franz=False,aprobado_equipo=False,cobertura_aprobada=False,v2_aplicada=False,cp23_final=False)
 base=f'T089_TARIJA_ENTREGA_{tag}'
 files={
 '01_DECISION_TECNICA_21.csv':csv_bytes(rows,fields),
 '02_ANH_12_DENTRO_REFERENCIA.csv':csv_bytes([s for s in anh if s['resultado_referencia_NO_APROBADO']=='DENTRO_REFERENCIA'], list(anh[0])),
 '03_EXTRAS_4_NO_INCLUIDOS.csv':csv_bytes(extras_rows,ef),
 '04_V2_PROPUESTA_NO_EJECUTAR.sql.txt':'\n'.join(sql).encode('utf-8'),
 '05_INFORME_PARA_REVISION.md':report.encode('utf-8'),
 '06_MAPA_Y_MATRIZ.html':htm.encode('utf-8'),
 '07_EVIDENCIA_16_PRUEBAS.txt':evidence.encode('utf-8'),
 '08_METADATOS_TRAZABILIDAD.json':json.dumps(meta,ensure_ascii=False,indent=2).encode('utf-8'),
 '09_SNAPSHOT_ANH_ORIGINAL.json':snap.read_bytes(),
 '10_POLIGONO_LEGAL_PROVISIONAL.geojson':ref.read_bytes(),
 '11_COBERTURA_OPERATIVA_PROVISIONAL.geojson':oper.read_bytes(),
 }
 outzip=evid/f'{base}.zip'
 if outzip.exists():raise FileExistsError(outzip)
 with zipfile.ZipFile(outzip,'x',compression=zipfile.ZIP_DEFLATED) as z:
  for name,data in files.items():z.writestr(name,data)
 # save only the HTML in short windows TEMP path for browsing without broken old Excel path
 import tempfile
 short=Path(tempfile.gettempdir())/'T089_ENTREGA_TARIJA_REVISION.html'
 short.write_bytes(files['06_MAPA_Y_MATRIZ.html'])
 print(json.dumps(dict(resultado='ENTREGA_PREPARADA_NO_APROBADA',destino=str(outzip),archivo_html_corto=str(short),prototipo=21,incluir_propuesto=8,excluir_propuesto=13,extra_anh_no_incluidos=4,geojson_provisional=True,aprobaciones='PENDIENTES',comprobacion='16 pruebas existentes: BUILD SUCCESS verificado',zip_sha256=sha(outzip)),ensure_ascii=False,indent=2))

if __name__=='__main__':
 try:main()
 except Exception as e:
  print(f'ERROR DE SEGURIDAD/DATOS: {e}',file=sys.stderr)
  sys.exit(2)
