"""T-089: ejecuta el cliente ORIGINAL de ANH y prepara revisión, sin generar V2.

Uso recomendado, desde 04_imp:
  python herramientas/ejecutar_extraccion_anh.py
  python herramientas/ejecutar_extraccion_anh.py --snapshot RUTA/datos_tarija.json

Las estaciones solo pasan al CSV URBANO cuando el límite tiene aprobación
integral. El CSV no se aprueba automáticamente y jamás se escribe una V2.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

from preparar_revision_anh import preparar
from validacion_anh_t089 import verificar_snapshot_anh, validar_cobertura_aprobada

RAIZ = Path(__file__).resolve().parents[1]
CLIENTE_ANH = RAIZ / 'integraciones' / 'ANH_T115' / 'anh_tarija.py'
COBERTURA_POR_DEFECTO = RAIZ / 'datos' / 'mancha_urbana_tarija.geojson'
SALIDA_POR_DEFECTO = RAIZ / 'integraciones' / 'ANH_T115' / 'salida_tarija'



def verificar_evidencias_cliente(snapshot: Path, datos: dict) -> int:
    """Corrobora salida, estado, trazas TLS y respuestas del cliente T-115.

    No es una firma digital de ANH: las trazas son artefactos de ejecución que
    deben conservarse con el snapshot para su auditoría.
    """
    carpeta = snapshot.parent
    resumen = json.loads((carpeta / 'ejecucion.json').read_text(encoding='utf-8'))
    traces = json.loads((carpeta / 'respuestas' / 'peticiones.json').read_text(encoding='utf-8'))
    if (not isinstance(resumen, dict) or not isinstance(traces, list)
            or resumen.get('estado') != 'completa' or resumen.get('exit_code') != 0
            or resumen.get('modo') != 'consulta'
            or resumen.get('version_cliente') != datos.get('version_cliente')
            or resumen.get('registros') != datos.get('registros_totales')
            or resumen.get('estaciones') != datos.get('estaciones_distintas')
            or resumen.get('peticiones_http_intentadas') != len(traces)
            or resumen.get('politica_tls') == 'inseguro_solo_pruebas'):
        raise ValueError('El resumen de ejecución ANH no concuerda con el inventario validado')
    obligatorias = {datos.get('fuente_catalogo')}
    obligatorias.update(q['fuente_url'] for q in datos['consultas_productos'])
    visitas_exitosas = set()
    origen = 'https://abastecimiento.prod.anh.gob.bo/api/'
    for registro in traces:
        if not isinstance(registro, dict):
            raise ValueError('Evidencia de petición ANH inválida')
        url = registro.get('url')
        if not isinstance(url, str) or not url.startswith(origen):
            raise ValueError('Se detectó una petición de origen ajeno al ANH')
        if registro.get('politica_tls') == 'inseguro_solo_pruebas':
            raise ValueError('TLS inseguro en la ejecución ANH')
        if (url in obligatorias and registro.get('http_status') == 200
                and registro.get('json_valido') is True
                and registro.get('tls_verificado') is True):
            archivo = registro.get('respuesta_filtrada')
            if (not isinstance(archivo, str) or '/' in archivo or '\\' in archivo
                    or not (carpeta / 'respuestas' / archivo).is_file()):
                raise ValueError('Falta cuerpo JSON filtrado que acredite una petición de catálogo')
            visitas_exitosas.add(url)
    if not obligatorias <= visitas_exitosas:
        raise ValueError('No hay peticiones ANH verificadas para ambos combustibles y su catálogo')

    # Reconstruir desde las respuestas JSON filtradas guardadas por T-115.
    # Esta comparación evita que se agreguen manualmente estaciones o se alteren
    # sus nombres/coordenadas en el snapshot sin una respuesta fuente que los respalde.
    if str(CLIENTE_ANH.parent) not in sys.path:
        sys.path.insert(0, str(CLIENTE_ANH.parent))
    import anh_tarija

    trazas_buenas = {p['url']: p for p in traces
                   if p.get('http_status') == 200 and p.get('json_valido') is True
                   and p.get('tls_verificado') is True}

    def respuesta_de(url: str) -> dict:
        traza = trazas_buenas.get(url)
        if not traza:
            raise ValueError(f'No hay respuesta acreditada para: {url}')
        nombre = traza.get('respuesta_filtrada')
        if (not isinstance(nombre, str) or Path(nombre).name != nombre
                or not (carpeta / 'respuestas' / nombre).is_file()):
            raise ValueError('No existe el JSON de respuesta de la API ANH')
        return json.loads((carpeta / 'respuestas' / nombre).read_text(encoding='utf-8'))

    catalogo = respuesta_de(datos['fuente_catalogo'])
    if anh_tarija.encontrar_tarija(catalogo) != datos['departamento_id']:
        raise ValueError('El ID de Tarija no coincide con el catálogo fuente ANH')

    mapa_snapshot = {(r['estacion_id'], r['producto_id']): r for r in datos['registros']}
    for consulta in datos['consultas_productos']:
        producto = consulta['producto_id']
        url = consulta['fuente_url']
        normalizados = anh_tarija.normalizar_estaciones(
            respuesta_de(url), datos['departamento_id'], producto,
            datos['observado_en_utc'], url)
        ids_originales = {r['estacion_id'] for r in normalizados}
        ids_snapshot = {sid for sid, p in mapa_snapshot if p == producto}
        if ids_originales != ids_snapshot or len(normalizados) != consulta['registros']:
            raise ValueError('Identidades de surtidores ANH no corresponden con el listado original')
        for base in normalizados:
            destino = mapa_snapshot[(base['estacion_id'], producto)]
            suplemento = destino.get('fuente_complemento_identidad')
            if not suplemento and any(destino[campo] != base[campo]
                                     for campo in ('nombre_estacion', 'latitud', 'longitud')):
                raise ValueError('Nombre o coordenada alterados sin detalle ANH acreditado')
            if suplemento:
                campos = set(suplemento['campos'])
                fuente_detalle = respuesta_de(suplemento['url'])
                detalle = anh_tarija.validar_detalle(
                    fuente_detalle, base['estacion_id'], datos['departamento_id'])
                equivalencias = {'nombre_estacion': 'nombre', 'latitud': 'lat', 'longitud': 'lng'}
                for campo, campo_anh in equivalencias.items():
                    if base[campo] is not None and destino[campo] != base[campo]:
                        raise ValueError('El detalle ANH no debe sobreescribir un dato completo del listado')
                    if base[campo] is None and destino[campo] is not None:
                        if campo not in campos or destino[campo] != detalle.get(campo_anh):
                            raise ValueError('El dato complementario no coincide con el detalle ANH')
    return len(visitas_exitosas)


def extraer_desde_api(destino: Path, *, detalles: int = 20) -> Path:
    """Invoca cliente ANH sin flags TLS inseguros; falla cerrado ante error."""
    if not CLIENTE_ANH.is_file():
        raise FileNotFoundError(f'Cliente ANH no está incluido: {CLIENTE_ANH}')
    if not (0 <= detalles <= 20):
        raise ValueError('La API limita las consultas de detalle a 20 por ejecución')
    destino.mkdir(parents=True, exist_ok=True)
    anteriores = {p.name for p in destino.iterdir()}
    comando = [sys.executable, str(CLIENTE_ANH), '--usar-certifi',
               '--productos', '0', '1', '--max-detalles', str(detalles),
               '--salida', str(destino)]
    resultado = subprocess.run(comando, text=True, capture_output=True, check=False)
    nuevos = sorted(p for p in destino.iterdir() if p.is_dir() and p.name not in anteriores)
    if resultado.returncode != 0:
        raise RuntimeError('Consulta ANH NO válida (código {}): {}. Revisar diagnósticos en {}'.format(
            resultado.returncode, resultado.stderr.strip()[-800:], destino))
    if len(nuevos) != 1 or not (nuevos[0] / 'datos_tarija.json').is_file():
        raise RuntimeError('La API devolvió éxito, pero no se halló un único JSON nuevo verificable')
    # Se deja el snapshot intacto y se valida la estructura antes de cualquier revisión urbana.
    datos, _, _, _ = verificar_snapshot_anh(nuevos[0] / 'datos_tarija.json')
    verificar_evidencias_cliente(nuevos[0] / 'datos_tarija.json', datos)
    return nuevos[0] / 'datos_tarija.json'


def evaluar_snapshot(snapshot: Path, geojson: Path) -> dict:
    """Audita Tarija departamental. Si no hay mapa aprobado, NO exporta candidatos."""
    datos, estaciones, sha, incidencias = verificar_snapshot_anh(snapshot)
    informe = {
        'tarea': 'T-089', 'origen': 'ANH', 'estado_snapshot': 'VERIFICADO_ESTRUCTURALMENTE',
        'nota_prueba': 'La estructura y los metadatos TLS se han comprobado; para atestiguar el TLS se requiere conservar la ejecución original del cliente.',
        'sha256_snapshot': sha, 'observado_en_utc': datos['observado_en_utc'],
        'alcance_api': 'DEPARTAMENTO_TARIJA_NO_CIUDAD',
        'estaciones_departamentales_reportadas': datos['estaciones_distintas'],
        'registros_estacion_producto': datos['registros_totales'],
        'estaciones_identidad_consistente': len(estaciones),
        'incidencias': incidencias,
        'cobertura_urbana_aprobada': False,
        'candidatas_urbanas': None,
        'v2_generada': False,
        'revision_franz': None,
        'aprobacion_marcelo': None,
    }
    if geojson.is_file():
        doc = json.loads(geojson.read_text(encoding='utf-8'))
        try:
            validar_cobertura_aprobada(doc)
        except ValueError as exc:
            informe['bloqueo'] = str(exc)
        else:
            csv_destino = snapshot.parent / 'surtidores_anh_revision_PENDIENTE.csv'
            audit_destino = snapshot.parent / 'revision_urbana_PENDIENTE.json'
            # No sobrescribir un CSV existente que podría incluir decisiones humanas.
            if csv_destino.exists() or audit_destino.exists():
                raise FileExistsError('Revisión preexistente: no sobrescribir decisiones humanas de otro proceso')
            detalle = preparar(snapshot, geojson, csv_destino, audit_destino)
            informe['cobertura_urbana_aprobada'] = True
            informe['candidatas_urbanas'] = detalle['candidatas_urbanas_sin_aprobar']
            informe['revisar_archivo'] = str(csv_destino)
            informe['auditoria_urbana'] = str(audit_destino)
    else:
        informe['bloqueo'] = f'No existe la cobertura GeoJSON: {geojson}'
    archivo_informe = snapshot.parent / 'evaluacion_T089.json'
    numero = 2
    while archivo_informe.exists():
        archivo_informe = snapshot.parent / f'evaluacion_T089_{numero:03d}.json'
        numero += 1
    archivo_informe.write_text(json.dumps(informe, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return informe


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshot', type=Path, help='Snapshot ya generado por el cliente ANH, omite consulta en red')
    parser.add_argument('--salida', type=Path, default=SALIDA_POR_DEFECTO, help='Destino de la consulta ANH')
    parser.add_argument('--geojson', type=Path, default=COBERTURA_POR_DEFECTO,
                        help='Mapa urbano, obligatoriamente APROBADO para producir CSV')
    parser.add_argument('--max-detalles', type=int, default=20)
    args = parser.parse_args(argv)
    try:
        ruta_snapshot = args.snapshot if args.snapshot else extraer_desde_api(args.salida, detalles=args.max_detalles)
        resultado = evaluar_snapshot(ruta_snapshot, args.geojson)
        print('Snapshot ANH validado. Estaciones departamentales: {}. Urbanas candidatas: {}.'.format(
            resultado['estaciones_departamentales_reportadas'],
            resultado['candidatas_urbanas'] if resultado['candidatas_urbanas'] is not None else 'NO DETERMINADAS'))
        print('Cobertura aprobada:', 'SÍ' if resultado['cobertura_urbana_aprobada'] else 'NO')
        if resultado.get('bloqueo'):
            print('PENDIENTE:', resultado['bloqueo'])
        print('No se aprobaron estaciones ni se generó V2.')
        return 0 if resultado['cobertura_urbana_aprobada'] else 3
    except (OSError, ValueError, RuntimeError, KeyError, TypeError) as exc:
        print(f'ERROR T-089: {exc}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
