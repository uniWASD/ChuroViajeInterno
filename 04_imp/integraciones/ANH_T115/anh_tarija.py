from __future__ import annotations

import argparse
import csv
import hashlib
import http.client
import json
import math
import re
import socket
import ssl
import sys
import warnings
import time
import unicodedata
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, HTTPSHandler, Request, build_opener, getproxies

VERSION = "2.2.0"
BASE = "https://abastecimiento.prod.anh.gob.bo/api/"
PRODUCTOS = {0: "Gasolina", 1: "Diésel", 2: "Gasolina Premium", 3: "Diésel ULS"}
MAX_BYTES = 10 * 1024 * 1024
CAMPOS_VENTA_EXCLUIDOS = {"fechaultimaventa", "fechaultimaventaraw", "ultimaventa"}
CAMPOS_CSV = [
    "estado_consulta", "tls_verificado", "uso_datos", "estacion_id", "nombre_estacion", "departamento", "departamento_id",
    "direccion", "zona", "latitud", "longitud", "url_mapa", "producto_id",
    "tipo_combustible", "origen_nombre_combustible", "cantidad_litros",
    "disponibilidad_reportada", "saldo_positivo_reportado", "venta_reportada",
    "fecha_actualizacion_reportada", "fecha_ultima_carga_confirmada",
    "observado_en_utc", "fuente_url", "verificacion_departamento", "advertencias",
]


AVISO_TLS = (
    "SOLO PRUEBAS: se desactivó la verificación del certificado y del nombre del servidor. "
    "La conexión puede ser interceptada y sus datos alterados. No usar estos resultados en producción. "
    "Esta opción NO renueva ni corrige el certificado remoto."
)


class ErrorANH(RuntimeError):
    def __init__(self, mensaje: str, categoria: str = "contrato", http_status: int | None = None):
        super().__init__(mensaje)
        self.categoria = categoria
        self.http_status = http_status


def ahora_utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def guardar_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + ".tmp")
    temp.write_text(json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False), encoding="utf-8")
    temp.replace(path)


def normalizar_texto(value: Any) -> str:
    if not isinstance(value, str):
        return ""
    return "".join(c for c in unicodedata.normalize("NFD", value.strip().casefold())
                   if unicodedata.category(c) != "Mn")


def excluir_fecha_venta(value: Any) -> Any:
    if isinstance(value, dict):
        return {k: excluir_fecha_venta(v) for k, v in value.items()
                if re.sub(r"[^a-z]", "", normalizar_texto(k)) not in CAMPOS_VENTA_EXCLUIDOS}
    if isinstance(value, list):
        return [excluir_fecha_venta(v) for v in value]
    return value


def identificador(value: Any, campo: str, minimo: int = 0) -> int:
    if type(value) is int:
        number = value
    elif isinstance(value, str) and re.fullmatch(r"[0-9]+", value.strip()):
        number = int(value.strip())
    else:
        raise ErrorANH(f"{campo}: identificador ausente o no entero: {value!r}")
    if number < minimo:
        raise ErrorANH(f"{campo}: identificador fuera de rango: {number}")
    return number


def resultado(payload: Any, tipo: type) -> Any:
    if not isinstance(payload, dict):
        raise ErrorANH("La respuesta no es un objeto JSON.")
    if type(payload.get("decCodigo")) is not int or payload["decCodigo"] != 1:
        raise ErrorANH(f"Respuesta no exitosa: decCodigo={payload.get('decCodigo')!r}; "
                       f"strMensaje={payload.get('strMensaje')!r}", "negocio")
    data = payload.get("oResultado")
    if not isinstance(data, tipo):
        raise ErrorANH(f"oResultado no es {tipo.__name__}; revisar el contrato, no asumir cero registros.")
    if isinstance(data, list) and any(not isinstance(row, dict) for row in data):
        raise ErrorANH("oResultado contiene elementos que no son objetos.")

    for holder in (payload, payload.get("pagination"), payload.get("paginacion")):
        if isinstance(holder, dict) and any(holder.get(k) for k in ("next", "next_page", "siguiente")):
            raise ErrorANH("Se detectó paginación no soportada; no se publicará una extracción truncada.")
    return data


def encontrar_tarija(payload: Any) -> int:
    rows = resultado(payload, list)
    matches = [r for r in rows if normalizar_texto(r.get("nombre")) == "tarija"]
    if len(matches) != 1:
        raise ErrorANH("El catálogo no contiene una única entrada nombre='Tarija'. No se asumirá id=6.")
    return identificador(matches[0].get("id"), "id de Tarija", 1)


def numero(value: Any, campo: str, avisos: list[str]) -> int | float | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, (str, int, float)):
        avisos.append(f"{campo}: tipo no numérico")
        return None
    try:
        number = float(value)
    except (ValueError, OverflowError):
        avisos.append(f"{campo}: número no interpretable; no se supone separador decimal")
        return None
    if not math.isfinite(number):
        avisos.append(f"{campo}: número no finito")
        return None
    return int(number) if number.is_integer() else number


def texto(value: Any, campo: str, avisos: list[str]) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        avisos.append(f"{campo}: se esperaba texto")
        return None
    return value.strip() or None


def fecha_raw(value: Any, avisos: list[str]) -> str | None:
    date = texto(value, "updated_at", avisos)
    if date is not None:
        try:
            dt = datetime.fromisoformat(date.replace("Z", "+00:00"))
            if dt.tzinfo is None:
                avisos.append("updated_at: zona horaria no especificada; no se interpreta como UTC")
        except ValueError:
            avisos.append("updated_at: formato no ISO; se conserva sin interpretar")
    return date


def normalizar_estaciones(payload: Any, departamento: int, producto: int,
                          observado: str, fuente: str) -> list[dict[str, Any]]:
    rows = resultado(payload, list)
    salida, claves = [], set()
    for original in rows:
        station_id = identificador(original.get("id"), "id de estación", 1)
        avisos: list[str] = []
        dep = original.get("departamento_id")
        if dep is not None and identificador(dep, "departamento_id", 1) != departamento:
            raise ErrorANH(f"Estación {station_id}: el servidor devolvió otro departamento.")
        if dep is None:
            avisos.append("departamento_id ausente: el alcance depende del filtro de la API")
        row = dict(original)
        if "productos" in original:
            products = original["productos"]
            if not isinstance(products, list) or any(not isinstance(p, dict) for p in products):
                raise ErrorANH(f"Estación {station_id}: productos no es una lista de objetos.")
            selected = [p for p in products
                        if identificador(p.get("producto_id"), "producto_id") == producto]
            if len(selected) != 1:
                raise ErrorANH(f"Estación {station_id}: no existe un único producto {producto}.")

            for key in ("producto_id", "producto_nombre", "saldo_litros", "saldo_estado", "con_venta", "updated_at"):
                row[key] = selected[0].get(key)
        reported_id = row.get("producto_id")
        if reported_id is not None and identificador(reported_id, "producto_id") != producto:
            raise ErrorANH(f"Estación {station_id}: producto diferente del solicitado.")
        if reported_id is None:
            avisos.append("producto_id ausente: se utiliza el código del filtro consultado")
        clave = (station_id, producto)
        if clave in claves:
            raise ErrorANH(f"Clave estación-producto duplicada: {clave}.")
        claves.add(clave)
        name = texto(original.get("nombre"), "nombre", avisos)
        if name is None:
            avisos.append("nombre de estación no reportado")
        lat = numero(original.get("lat"), "lat", avisos)
        lng = numero(original.get("lng"), "lng", avisos)
        if lat is not None and not -90 <= lat <= 90:
            avisos.append("lat fuera del rango geográfico")
            lat = None
        if lng is not None and not -180 <= lng <= 180:
            avisos.append("lng fuera del rango geográfico")
            lng = None
        if lat == 0 and lng == 0:
            avisos.append("coordenadas 0,0 incompatibles con la ubicación solicitada")
            lat = lng = None
        if lat is None or lng is None:
            avisos.append("ubicación incompleta: no se genera enlace ni punto de mapa")
        litros = numero(row.get("saldo_litros"), "saldo_litros", avisos)
        if litros is not None and litros < 0:
            avisos.append("saldo_litros negativo: cantidad invalidada, no convertida a cero")
            litros = None
        venta = row.get("con_venta")
        if venta is not None and type(venta) is not bool:
            avisos.append("con_venta no booleano: indicador desconocido")
            venta = None
        if litros == 0 and venta is True:
            avisos.append("saldo cero con indicador de venta: comprobar antigüedad y consistencia")
        fuel_name = texto(row.get("producto_nombre"), "producto_nombre", avisos)
        salida.append({
            "estacion_id": station_id, "nombre_estacion": name,
            "departamento": "Tarija", "departamento_id": departamento,
            "alcance": "departamento_de_tarija_no_solo_ciudad",
            "verificacion_departamento": "campo_y_filtro" if dep is not None else "solo_filtro_api",
            "direccion": texto(original.get("direccion"), "direccion", avisos),
            "zona": texto(original.get("zona"), "zona", avisos),
            "latitud": lat, "longitud": lng,
            "url_mapa": enlace_mapa(lat, lng),
            "producto_id": producto, "producto_id_reportado": reported_id,
            "tipo_combustible": fuel_name or PRODUCTOS[producto],
            "origen_nombre_combustible": "api" if fuel_name else "codigo_del_antecedente",
            "cantidad_litros": litros,
            "disponibilidad_reportada": texto(row.get("saldo_estado"), "saldo_estado", avisos),
            "saldo_positivo_reportado": None if litros is None else litros > 0,
            "venta_reportada": venta,
            "atencion_al_publico_verificada": False,
            "fecha_actualizacion_reportada": fecha_raw(row.get("updated_at"), avisos),
            "fecha_ultima_carga_confirmada": None,
            "motivo_fecha_carga_nula": "El contrato examinado no acredita una recepción física.",
            "observado_en_utc": observado, "fuente_url": fuente,
            "advertencias": avisos,
        })
    return salida


def enlace_mapa(lat: Any, lng: Any) -> str | None:
    if lat is None or lng is None:
        return None
    return "https://www.google.com/maps/search/?" + urlencode({"api": 1, "query": f"{lat},{lng}"})


class RedireccionMismoOrigen(HTTPRedirectHandler):
    def __init__(self, base_url: str, *, permitir: bool = True):
        super().__init__()
        self.permitir = permitir
        u = urlsplit(base_url)
        self.origen = (u.scheme, u.netloc)

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not self.permitir:
            raise ErrorANH("Redirección bloqueada en modo TLS inseguro de pruebas.", "redireccion")
        target = urlsplit(newurl)
        if (target.scheme, target.netloc) != self.origen:
            raise ErrorANH("Redirección fuera del origen permitido o a HTTP; detenida.", "redireccion")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def segundos_retry_after(value: str | None) -> float | None:
    if value is None:
        return None
    try:
        if value.strip().isdigit():
            return float(value.strip())
        dt = parsedate_to_datetime(value)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return max(0.0, (dt - datetime.now(timezone.utc)).total_seconds())
    except (ValueError, OverflowError, TypeError):
        return None


def clasificar_red(exc: BaseException) -> str:
    cause = exc.reason if isinstance(exc, URLError) else exc
    if isinstance(cause, socket.gaierror):
        return "dns"
    if isinstance(cause, (ssl.SSLError, ssl.CertificateError)):
        return "tls"
    if isinstance(cause, (TimeoutError, socket.timeout)):
        return "timeout"
    return "red"


def informacion_error_tls(exc: BaseException) -> dict[str, Any]:
    cause = exc.reason if isinstance(exc, URLError) else exc
    code = getattr(cause, "verify_code", None)
    message = getattr(cause, "verify_message", None)
    text_error = str(message or cause).lower()
    if code == 10 or "certificate has expired" in text_error:
        motivo = "certificado_vencido"
    elif code == 9 or "not yet valid" in text_error:
        motivo = "certificado_aun_no_vigente"
    elif code in (62, 64) or "hostname mismatch" in text_error or "ip address mismatch" in text_error:
        motivo = "nombre_del_servidor_no_coincide"
    elif code in (18, 19, 20, 21):
        motivo = "cadena_no_confiable_o_incompleta"
    else:
        motivo = "otro_error_tls"
    return {"motivo": motivo, "verify_code": code, "verify_message": message,
            "hora_del_equipo_utc": ahora_utc()}


def datos_bundle_certifi() -> dict[str, Any]:
    try:
        import certifi
    except ImportError as exc:
        raise ValueError(
            "Falta certifi. Instálelo con el MISMO intérprete: "
            "python -m pip install --upgrade certifi. No se desactivará TLS."
        ) from exc
    path = Path(certifi.where()).resolve(strict=True)
    if not path.is_file():
        raise ValueError("certifi.where() no devolvió un archivo PEM válido.")
    return {"origen": "certifi_exclusivo", "archivo": str(path),
            "version_certifi": getattr(certifi, "__version__", "no_reportada"),
            "sha256_bundle": hashlib.sha256(path.read_bytes()).hexdigest()}


def crear_contexto_tls(*, inseguro: bool = False, archivo_ca: Path | None = None,
                       usar_certifi: bool = False) -> ssl.SSLContext:
    if type(inseguro) is not bool or type(usar_certifi) is not bool:
        raise ValueError("inseguro y usar_certifi deben ser booleanos explícitos.")
    if inseguro and (archivo_ca is not None or usar_certifi):
        raise ValueError("No se puede combinar TLS inseguro con un almacén de CA.")
    if usar_certifi and archivo_ca is not None:
        raise ValueError("--usar-certifi y --archivo-ca son mutuamente excluyentes.")
    if inseguro:

        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
    elif usar_certifi:
        bundle = datos_bundle_certifi()


        context = ssl.create_default_context(cafile=bundle["archivo"])
    else:
        context = ssl.create_default_context()
        if archivo_ca is not None:
            context.load_verify_locations(cafile=str(archivo_ca))
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    return context


class HTTPSOrigenRestringido(HTTPSHandler):
    def __init__(self, base_url: str, context: ssl.SSLContext):
        super().__init__(context=context)
        u = urlsplit(base_url)
        self.origen = (u.scheme, u.hostname, u.port or 443)

    def https_open(self, req):
        u = urlsplit(req.full_url)
        if (u.scheme, u.hostname, u.port or 443) != self.origen or u.username or u.password:
            raise ErrorANH("Solicitud HTTPS fuera del origen permitido.", "origen")
        if req.get_method() != "GET" or req.data is not None:
            raise ErrorANH("Este cliente solo admite GET sin cuerpo.", "metodo")
        return super().https_open(req)


class ClienteANH:
    def __init__(self, evidencias: Path | None = None, timeout: float = 12.0,
                 reintentos: int = 1, pausa: float = 1.0, *, _base_url: str = BASE,
                 tls_inseguro_solo_pruebas: bool = False, archivo_ca: Path | None = None,
                 usar_certifi: bool = False):
        if not math.isfinite(timeout) or timeout <= 0 or reintentos not in (0, 1, 2):
            raise ValueError("timeout debe ser finito y positivo; reintentos debe estar entre 0 y 2.")
        if not math.isfinite(pausa) or pausa < 0:
            raise ValueError("pausa debe ser finita y no negativa.")
        u = urlsplit(_base_url)
        if _base_url != BASE and not (u.scheme in ("http", "https") and u.hostname in ("127.0.0.1", "localhost")
                                          and not u.username and not u.password and not u.query and not u.fragment):
            raise ValueError("Solo se admite el origen oficial, o loopback para pruebas locales.")
        self.base_url = _base_url.rstrip("/") + "/"
        self.evidencias = Path(evidencias) if evidencias is not None else None
        self.timeout, self.reintentos, self.pausa = timeout, reintentos, pausa
        self.tls_inseguro = tls_inseguro_solo_pruebas
        self.archivo_ca = Path(archivo_ca) if archivo_ca is not None else None
        self.contexto_tls = crear_contexto_tls(inseguro=tls_inseguro_solo_pruebas,
                                              archivo_ca=self.archivo_ca, usar_certifi=usar_certifi)
        self.almacen_ca = (datos_bundle_certifi() if usar_certifi else
                           {"origen": "sin_validacion" if self.tls_inseguro else
                             "sistema_mas_ca_adicional" if self.archivo_ca else "sistema",
                            "archivo_adicional": str(self.archivo_ca) if self.archivo_ca else None})
        self.politica_tls = "inseguro_solo_pruebas" if self.tls_inseguro else "verificacion_estricta"
        if self.tls_inseguro:
            warnings.warn(AVISO_TLS, RuntimeWarning, stacklevel=2)
        self.opener = build_opener(
            RedireccionMismoOrigen(self.base_url, permitir=not self.tls_inseguro),
            HTTPSOrigenRestringido(self.base_url, self.contexto_tls))
        self.peticiones: list[dict[str, Any]] = []
        self._ultima_fin = 0.0
        self._bloqueo_hasta = 0.0

    def _registrar(self, rec: dict[str, Any]) -> None:
        rec["fin_utc"] = ahora_utc()
        self._ultima_fin = time.monotonic()
        self.peticiones.append(rec)
        if self.evidencias:
            guardar_json(self.evidencias / "peticiones.json", self.peticiones)

    def get(self, ruta: str, params: dict[str, Any] | None = None) -> tuple[dict[str, Any], dict[str, Any]]:
        if not isinstance(ruta, str) or not ruta or ruta.startswith("/") or "://" in ruta or ".." in ruta:
            raise ValueError("Ruta relativa inválida.")
        url = self.base_url + ruta
        if params:
            url += "?" + urlencode(params)
        for intento in range(self.reintentos + 1):
            bloqueo = self._bloqueo_hasta - time.monotonic()
            if bloqueo > 0:
                raise ErrorANH(f"Consultas suspendidas por Retry-After; espera restante: {bloqueo:.0f} s.", "limite")
            time.sleep(max(0.0, self.pausa - (time.monotonic() - self._ultima_fin)))
            rec: dict[str, Any] = {"url": url, "metodo": "GET", "intento": intento + 1,
                                   "inicio_utc": ahora_utc(), "http_status": None,
                                   "json_valido": False, "error": None,
                                   "politica_tls": self.politica_tls, "tls_verificado": None,
                                   "almacen_ca": self.almacen_ca}
            start = time.monotonic()
            error: ErrorANH | None = None
            retry, delay = False, float(2 ** intento)
            try:
                req = Request(url, headers={"Accept": "application/json", "Accept-Encoding": "identity",
                                            "User-Agent": "TarijaFuelReadOnly/2.1"}, method="GET")
                with self.opener.open(req, timeout=self.timeout) as response:
                    rec["tls_verificado"] = (not self.tls_inseguro) if urlsplit(url).scheme == "https" else False
                    rec["http_status"] = response.status
                    rec["cabeceras"] = {k.lower(): v for k, v in response.headers.items()
                        if k.lower() in ("content-type", "date", "etag", "last-modified", "cache-control",
                                         "access-control-allow-origin", "retry-after")}
                    raw = response.read(MAX_BYTES + 1)
                    if len(raw) > MAX_BYTES:
                        raise ErrorANH("Respuesta mayor al límite de 10 MiB.", "tamano")
                    rec["bytes"] = len(raw)
                    rec["sha256_cuerpo_original"] = hashlib.sha256(raw).hexdigest()
                    if response.status != 200:
                        raise ErrorANH(f"HTTP inesperado: {response.status}", "http", response.status)
                    if "json" not in response.headers.get("Content-Type", "").lower():
                        raise ErrorANH("HTTP 200 sin Content-Type JSON; no son datos utilizables.", "contenido")
                    try:
                        def rechazar_constante(value):
                            raise ValueError(f"Constante JSON no estándar: {value}")
                        data = json.loads(raw.decode("utf-8-sig"), parse_constant=rechazar_constante)
                    except (ValueError, UnicodeDecodeError, RecursionError) as exc:
                        raise ErrorANH("Cuerpo JSON inválido.", "contenido") from exc
                    if not isinstance(data, dict):
                        raise ErrorANH("Se esperaba objeto JSON de ANH.", "contrato")
                    data = excluir_fecha_venta(data)
                    rec["json_valido"] = True
                    if self.evidencias:
                        filename = f"respuesta_{len(self.peticiones) + 1:03d}_sin_fecha_venta.json"
                        guardar_json(self.evidencias / filename, data)
                        rec["respuesta_filtrada"] = filename
            except HTTPError as exc:
                rec["tls_verificado"] = (not self.tls_inseguro) if urlsplit(url).scheme == "https" else False
                rec["http_status"] = exc.code
                after = exc.headers.get("Retry-After") if exc.headers else None
                rec["retry_after"] = after
                retry = exc.code in (429, 502, 503, 504)
                requested_delay = segundos_retry_after(after)
                if requested_delay is not None:
                    delay = max(delay, requested_delay)
                error = ErrorANH(f"HTTP {exc.code}: {exc.reason}", "http", exc.code)

                if retry and delay > 30:
                    self._bloqueo_hasta = time.monotonic() + delay
                    retry = False
                    error = ErrorANH(f"HTTP {exc.code}: Retry-After={after}; consultas suspendidas.", "limite", exc.code)
                exc.close()
            except (URLError, OSError, http.client.HTTPException) as exc:
                category = clasificar_red(exc)
                retry = category in ("dns", "timeout", "red")
                if category == "tls":
                    rec["diagnostico_tls"] = informacion_error_tls(exc)
                    info = rec["diagnostico_tls"]
                    error = ErrorANH(f"TLS: {info['motivo']} (verify_code={info['verify_code']}). "
                                     f"{info['verify_message'] or str(exc)}", category)
                else:
                    error = ErrorANH(str(exc), category)
            except ErrorANH as exc:
                error = exc
            finally:
                rec["duracion_segundos"] = round(time.monotonic() - start, 4)
                if error:
                    rec["error"] = str(error)
                    rec["categoria_error"] = error.categoria
                self._registrar(rec)
            if error is None:
                return data, rec
            if not retry or intento >= self.reintentos:

                if rec.get("http_status") == 429 or (rec.get("http_status") == 503 and rec.get("retry_after")):
                    self._bloqueo_hasta = max(self._bloqueo_hasta, time.monotonic() + max(delay, 1.0))
                raise error
            time.sleep(max(delay, self.pausa))
        raise ErrorANH("Estado de reintentos inesperado.")


def validar_detalle(payload: Any, station_id: int, departamento: int) -> dict[str, Any]:
    detail = resultado(payload, dict)
    if identificador(detail.get("id"), "id de detalle", 1) != station_id:
        raise ErrorANH("El detalle pertenece a otra estación.")
    if identificador(detail.get("departamento_id"), "departamento de detalle", 1) != departamento:
        raise ErrorANH("El detalle pertenece a otro departamento.")
    return detail


def completar_identidad(client: ClienteANH, records: list[dict[str, Any]], departamento: int,
                        max_detalles: int) -> list[dict[str, Any]]:
    incidencias = []
    pending = sorted({r["estacion_id"] for r in records
                      if r["nombre_estacion"] is None or r["direccion"] is None or r["url_mapa"] is None})
    for sid in pending[:max_detalles]:
        try:
            payload, trace = client.get(f"estaciones/{sid}")
            detail = validar_detalle(payload, sid, departamento)

            identidad = {k: detail.get(k) for k in ("id", "nombre", "departamento_id", "direccion", "zona", "lat", "lng")}
            wrapped = {"decCodigo": 1, "oResultado": [identidad]}
            clean = normalizar_estaciones(wrapped, departamento, 0, trace["fin_utc"], trace["url"])[0]
            for r in (r for r in records if r["estacion_id"] == sid):
                campos = []
                for campo in ("nombre_estacion", "direccion", "zona"):
                    if r[campo] is None and clean[campo] is not None:
                        r[campo] = clean[campo]
                        campos.append(campo)
                if r["url_mapa"] is None and clean["url_mapa"] is not None:
                    for campo in ("latitud", "longitud", "url_mapa"):
                        r[campo] = clean[campo]
                        campos.append(campo)
                if campos:
                    r["advertencias"] = ["listado: " + aviso for aviso in r["advertencias"]]
                    r["fuente_complemento_identidad"] = {"url": trace["url"], "campos": campos,
                                                          "observado_en_utc": trace["fin_utc"],
                                                          "tls_verificado": trace.get("tls_verificado")}
                    if trace.get("tls_verificado") is not True:
                        r["tls_verificado"] = trace.get("tls_verificado")
                    r["advertencias"].append("Identidad/ubicación completada desde detalle; el saldo procede del listado.")
        except ErrorANH as exc:
            incidencias.append({"estacion_id": sid, "error": str(exc), "categoria": exc.categoria})
    if len(pending) > max_detalles:
        incidencias.append({"nota": "Se alcanzó el límite de consultas de detalle.",
                            "estaciones_no_consultadas": len(pending) - max_detalles})
    return incidencias


def obtener_datos_tarija(productos: tuple[int, ...] | list[int] = (0, 1), *,
                         cliente: ClienteANH | None = None, max_detalles: int = 5) -> dict[str, Any]:
    if not productos or any(type(p) is not int or p not in PRODUCTOS for p in productos):
        raise ValueError("productos debe contener códigos 0, 1, 2 o 3.")
    if type(max_detalles) is not int or not 0 <= max_detalles <= 20:
        raise ValueError("max_detalles debe ser un entero entre 0 y 20.")
    client = cliente or ClienteANH()
    fallos_catalogo = []
    dep = None
    for code in (0, 1):
        try:
            payload, trace = client.get("departamentos/", {"producto": code})
            dep = encontrar_tarija(payload)
            fuente_catalogo = trace["url"]
            tls_catalogo = trace.get("tls_verificado")
            break
        except ErrorANH as exc:
            if exc.categoria == "tls":
                raise ErrorANH("No se recibió el catálogo: la conexión HTTPS fue rechazada. " + str(exc), "tls") from exc
            fallos_catalogo.append({"producto": code, "error": str(exc), "categoria": exc.categoria})
    if dep is None:
        raise ErrorANH("No se pudo validar Tarija en ninguno de los dos catálogos: " +
                       "; ".join(e["error"] for e in fallos_catalogo),
                       fallos_catalogo[-1]["categoria"])
    records, consultas = [], []
    for code in dict.fromkeys(productos):
        try:
            payload, trace = client.get("estaciones/", {"departamento": dep, "producto": code})
            normalized = normalizar_estaciones(payload, dep, code, trace["fin_utc"], trace["url"])
            for row in normalized:
                row["tls_verificado"] = trace.get("tls_verificado")
                row["uso_datos"] = ("solo_pruebas_origen_no_verificado" if getattr(client, "tls_inseguro", False)
                                     else "fuente_reportada" if row["tls_verificado"] is True
                                     else "verificacion_no_acreditada")
                if getattr(client, "tls_inseguro", False):
                    row["advertencias"].append(AVISO_TLS)
            records.extend(normalized)
            consultas.append({"producto_id": code, "estado": "correcta", "registros": len(normalized),
                               "fuente_url": trace["url"], "tls_verificado": trace.get("tls_verificado"),
                               "server_time_reportado": payload.get("server_time")})
        except ErrorANH as exc:
            consultas.append({"producto_id": code, "estado": "fallida", "registros": None,
                               "error": str(exc), "categoria": exc.categoria})
    exitosas = sum(c["estado"] == "correcta" for c in consultas)
    if exitosas == 0:
        raise ErrorANH("Fallaron todas las consultas de productos: " +
                       "; ".join(c["error"] for c in consultas), "consulta_total_fallida")
    incidencias = completar_identidad(client, records, dep, max_detalles) if max_detalles else []
    records.sort(key=lambda r: (normalizar_texto(r["nombre_estacion"]), r["estacion_id"], r["producto_id"]))
    tls_total = tls_catalogo is True and all(c.get("tls_verificado") is True for c in consultas if c["estado"] == "correcta")
    tls_total = tls_total and all(r.get("tls_verificado") is True for r in records)
    inseguro = getattr(client, "tls_inseguro", False)
    return {"estado": "completa" if exitosas == len(consultas) else "parcial",
            "version_cliente": VERSION, "tls_verificado": bool(tls_total),
            "almacen_ca": getattr(client, "almacen_ca", None),
            "politica_tls": getattr(client, "politica_tls", "no_acreditada"),
            "uso_datos": "solo_pruebas_origen_no_verificado" if inseguro else "fuente_reportada" if tls_total else "verificacion_no_acreditada",
            "advertencia_tls": AVISO_TLS if inseguro else None,
            "observado_en_utc": ahora_utc(), "departamento": "Tarija", "departamento_id": dep,
            "alcance": "departamento_de_tarija_no_solo_ciudad", "fuente_catalogo": fuente_catalogo,
            "aviso": "Saldos y estados reportados; no garantizan atención ni recepción física de combustible.",
            "errores_catalogo_previo": fallos_catalogo, "consultas_productos": consultas,
            "incidencias_detalles": incidencias, "estaciones_distintas": len({r["estacion_id"] for r in records}),
            "registros_totales": len(records), "registros": records}


def probar_apis(client: ClienteANH, rondas: int = 2) -> dict[str, Any]:
    tests: list[dict[str, Any]] = []
    ids_departamento, muestras = [], {}

    def probar(nombre, ruta, params=None, validador=None):
        entry = {"prueba": nombre, "ruta": ruta, "parametros": params, "estado": "fallida"}
        try:
            data, trace = client.get(ruta, params)
            entry.update(http_status=trace["http_status"], fuente_url=trace["url"], tls_verificado=trace.get("tls_verificado"))
            value = validador(data, trace) if validador else data
            entry["estado"] = "correcta"
            tests.append(entry)
            return value
        except ErrorANH as exc:
            entry.update(error=str(exc), categoria=exc.categoria, http_status=exc.http_status)
            tests.append(entry)
            return None

    for p in (0, 1):
        dep = probar(f"catalogo_p{p}", "departamentos/", {"producto": p}, lambda d, t: encontrar_tarija(d))
        if dep is not None:
            ids_departamento.append(dep)
    unicos = set(ids_departamento)
    if len(unicos) > 1:
        return {"estado": "fallida", "error": "Los catálogos discrepan sobre el ID de Tarija.", "pruebas": tests}
    dep = next(iter(unicos)) if unicos else 6

    for ronda in range(1, rondas + 1):
        for p in (0, 1):
            rows = probar(f"listado_p{p}_ronda{ronda}", "estaciones/", {"departamento": dep, "producto": p},
                           lambda d, t, p=p: normalizar_estaciones(d, dep, p, t["fin_utc"], t["url"]))
            if rows is not None:
                tests[-1]["registros"] = len(rows)
                tests[-1]["estaciones_con_nombre"] = sum(r["nombre_estacion"] is not None for r in rows)
                tests[-1]["estaciones_con_coordenadas"] = sum(r["url_mapa"] is not None for r in rows)
                muestras[(ronda, p)] = rows
    if unicos:
        station_ids = sorted({r["estacion_id"] for rows in muestras.values() for r in rows})[:2]
    else:
        station_ids = []
    for sid in station_ids:
        probar(f"detalle_{sid}", f"estaciones/{sid}", validador=lambda d, t, sid=sid: validar_detalle(d, sid, dep))

        def estado(d, t):
            if not isinstance(d, dict) or type(d.get("decCodigo")) is not int or d["decCodigo"] != 1:
                raise ErrorANH("El endpoint estado no devolvió un resultado exitoso.")
            if not isinstance(d.get("oResultado"), (dict, list)):
                raise ErrorANH("El endpoint estado no devolvió un objeto/lista de productos.")
            return d
        probar(f"estado_{sid}", f"estaciones/{sid}/estado", validador=estado)
        tests[-1]["alcance_validacion"] = "solo_envoltura; contrato_interno_no_confirmado"
    for name, route in (("advertencia", "Disclaimer"), ("precios", "precios/1")):
        def envoltura(d, t):
            if type(d.get("decCodigo")) is not int or d["decCodigo"] != 1 or d.get("oResultado") is None:
                raise ErrorANH("No se reconoció la envoltura exitosa del servicio auxiliar.")
            return d
        probar(name, route, validador=envoltura)
        tests[-1]["uso"] = "auxiliar; no incorporado al extractor de combustible"
    comparisons = []
    for p in (0, 1):
        if (1, p) in muestras and (rondas, p) in muestras and rondas > 1:
            first = {r["estacion_id"] for r in muestras[(1, p)]}
            last = {r["estacion_id"] for r in muestras[(rondas, p)]}
            comparisons.append({"producto_id": p, "ids_agregados": sorted(last - first),
                                 "ids_ausentes_ultima_ronda": sorted(first - last),
                                 "nota": "La igualdad de litros entre consultas no es un requisito: son datos variables."})
    return {"estado": "completa" if all(t["estado"] == "correcta" for t in tests) else "con_fallos",
            "generado_en_utc": ahora_utc(), "politica_tls": getattr(client, "politica_tls", "no_acreditada"),
            "advertencia_tls": AVISO_TLS if getattr(client, "tls_inseguro", False) else None, "departamento_consultado": dep,
            "catalogo_tarija_validado": bool(unicos), "rondas": rondas,
            "pruebas": tests, "comparaciones": comparisons,
            "pruebas_detalle_estado_omitidas": not bool(station_ids),
            "motivo_omision": None if station_ids else "Sin IDs de Tarija obtenidos de listados y catálogo válidos.",
            "sse_y_cisternas": "No probados: innecesarios para el extractor o sin contrato/identificadores verificados."}


def celda_csv(value: Any) -> Any:
    if value is None:
        return ""
    if isinstance(value, list):
        value = " | ".join(str(v) for v in value)
    if isinstance(value, str) and value.lstrip().startswith(("=", "+", "-", "@")):
        return "'" + value
    return value


def exportar(data: dict[str, Any], carpeta: Path) -> None:
    guardar_json(carpeta / "datos_tarija.json", data)
    with (carpeta / "estaciones_tarija.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=CAMPOS_CSV, extrasaction="ignore")
        writer.writeheader()
        for row in data["registros"]:
            writer.writerow({k: celda_csv(data["estado"] if k == "estado_consulta" else row.get(k)) for k in CAMPOS_CSV})
    features = [{"type": "Feature", "geometry": {"type": "Point", "coordinates": [r["longitud"], r["latitud"]]},
                 "properties": r} for r in data["registros"] if r["url_mapa"] is not None]
    guardar_json(carpeta / "estaciones_tarija.geojson", {"type": "FeatureCollection", "features": features,
                 "estado_consulta": data["estado"], "observado_en_utc": data["observado_en_utc"],
                 "tls_verificado": data.get("tls_verificado"), "uso_datos": data.get("uso_datos")})


def metadatos_certificado(der: bytes, carpeta: Path, indice: int = 0) -> dict[str, Any]:
    carpeta.mkdir(parents=True, exist_ok=True)
    ruta = carpeta / f"certificado_observado_{indice:02d}.pem"
    ruta.write_text(ssl.DER_cert_to_PEM_cert(der), encoding="ascii")
    info: dict[str, Any] = {"archivo": ruta.name, "sha256": hashlib.sha256(der).hexdigest(),
                            "autenticidad_acreditada": False}


    decoder = getattr(getattr(ssl, "_ssl", None), "_test_decode_cert", None)
    if decoder is None:
        info["nota"] = "Decodificador opcional no disponible; inspeccionar el PEM público."
        return info
    try:
        cert = decoder(str(ruta))
        info.update(sujeto=cert.get("subject"), emisor=cert.get("issuer"),
                    nombres_alternativos=cert.get("subjectAltName"),
                    not_before=cert.get("notBefore"), not_after=cert.get("notAfter"))
        ahora = datetime.now(timezone.utc).timestamp()
        for source, target in (("notBefore", "valido_desde_utc"), ("notAfter", "valido_hasta_utc")):
            if cert.get(source):
                epoch = ssl.cert_time_to_seconds(cert[source])
                info[target] = datetime.fromtimestamp(epoch, timezone.utc).isoformat()
                if source == "notAfter":
                    info["vencido_segun_reloj_equipo"] = ahora > epoch
                else:
                    info["aun_no_vigente_segun_reloj_equipo"] = ahora < epoch
    except Exception as exc:
        info["error_decodificacion"] = str(exc)
    return info


def diagnosticar_tls(client: ClienteANH, carpeta: Path) -> dict[str, Any]:
    if client.tls_inseguro:
        raise ValueError("El diagnóstico TLS debe usar un cliente con verificación estricta.")
    destino = urlsplit(client.base_url)
    if destino.scheme != "https":
        raise ValueError("El diagnóstico TLS requiere HTTPS.")
    report: dict[str, Any] = {
        "version_cliente": VERSION, "hora_equipo_utc": ahora_utc(),
        "hora_equipo_local": datetime.now().astimezone().isoformat(),
        "python": sys.version, "openssl": ssl.OPENSSL_VERSION, "host": destino.hostname,
        "almacen_ca": getattr(client, "almacen_ca", None),
        "proxy_configurado_para_https": bool(getproxies().get("https") or getproxies().get("all")),
        "aviso": "El reloj no se contrastó con una fuente externa. La inspección directa puede seguir otra ruta que un proxy.",
        "consulta_verificada": {}, "inspeccion_certificado": {}}
    try:
        payload, trace = client.get("departamentos/", {"producto": 0})
        report["consulta_verificada"] = {"estado": "correcta", "tls_verificado": trace.get("tls_verificado"),
                                          "http_status": trace["http_status"], "url": trace["url"]}
        try:
            report["consulta_verificada"]["tarija_id"] = encontrar_tarija(payload)
        except ErrorANH as exc:
            report["consulta_verificada"]["error_contrato"] = str(exc)
    except ErrorANH as exc:
        ultima = client.peticiones[-1] if client.peticiones else {}
        report["consulta_verificada"] = {"estado": "fallida", "error": str(exc), "categoria": exc.categoria,
                                          "http_status": exc.http_status, "tls_verificado": ultima.get("tls_verificado"),
                                          "detalle_tls": ultima.get("diagnostico_tls")}
    inspeccion = {"conexion": "directa_sin_proxy_explicito", "envia_peticion_http": False,
                  "autenticidad_acreditada": False,
                  "aviso": "Metadatos observados, no verificados. La cadena enviada puede omitir la raíz o usar otra ruta de confianza."}
    try:
        contexto = crear_contexto_tls(inseguro=True)
        with socket.create_connection((destino.hostname, destino.port or 443), timeout=client.timeout) as sock:
            with contexto.wrap_socket(sock, server_hostname=destino.hostname) as tls:
                inspeccion["version_tls"] = tls.version()
                getter = getattr(tls, "get_unverified_chain", None)
                cadena = getter() if callable(getter) else [tls.getpeercert(binary_form=True)]
                if not cadena or any(not isinstance(c, bytes) for c in cadena):
                    cadena = [tls.getpeercert(binary_form=True)]
                if not cadena or not all(cadena):
                    raise ValueError("No se recibió un certificado.")
                inspeccion["certificados"] = [metadatos_certificado(c, carpeta / "certificados", i)
                                               for i, c in enumerate(cadena)]
                inspeccion["estado"] = "observado_no_verificado"
    except (OSError, ValueError, http.client.HTTPException) as exc:
        inspeccion.update(estado="fallida", error=str(exc), categoria=clasificar_red(exc))
    report["inspeccion_certificado"] = inspeccion
    return report


def imprimir_resumen(data: dict[str, Any], *, mostrar_ubicacion: bool = False) -> None:
    source = (data.get("almacen_ca") or {}).get("origen", "no_reportado")
    tls = "SI" if data.get("tls_verificado") is True else "NO"
    print(f"TLS verificado: {tls} | Almacén: {source}")
    print(f"Consulta {data['estado']}: {data['estaciones_distintas']} estaciones; "
          f"{data['registros_totales']} registros.")
    for r in data["registros"]:
        litros = "desconocido" if r["cantidad_litros"] is None else f"{r['cantidad_litros']:g} L"
        print(f"{r['nombre_estacion'] or 'NOMBRE NO REPORTADO'} | {r['tipo_combustible']} | "
              f"{litros} | {r['disponibilidad_reportada'] or 'estado no reportado'}")
        if mostrar_ubicacion:
            print(f"  Dirección: {r.get('direccion') or 'NO REPORTADA'}")
            if r.get("zona"):
                print(f"  Zona: {r['zona']}")
            if r.get("latitud") is not None and r.get("longitud") is not None:
                print(f"  Coordenadas: {r['latitud']}, {r['longitud']}")
            else:
                print("  Coordenadas: NO REPORTADAS O NO VÁLIDAS")
            if r.get("url_mapa"):
                print(f"  Mapa: {r['url_mapa']}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Consulta pública de combustibles del departamento de Tarija", formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--version", action="version", version=f"%(prog)s {VERSION}")
    parser.add_argument("--productos", type=int, nargs="+", choices=sorted(PRODUCTOS), default=[0, 1])
    parser.add_argument("--salida", type=Path, default=Path("salida_tarija"))
    parser.add_argument("--timeout", type=float, default=12.0, help="Timeout de operaciones de socket; DNS depende del sistema.")
    parser.add_argument("--reintentos", type=int, choices=(0, 1, 2), default=1)
    parser.add_argument("--max-detalles", type=int, default=5, help="Completar identidad/ubicación faltante en hasta 5 estaciones (0 a 20).")
    modos = parser.add_mutually_exclusive_group()
    modos.add_argument("--diagnosticar-tls", action="store_true", help="Verifica HTTPS e inspecciona fechas del certificado; no extrae inventario.")
    modos.add_argument("--probar-apis", action="store_true", help="Ejecuta pruebas de lectura; no genera un inventario utilizable.")
    parser.add_argument("--rondas", type=int, choices=(1, 2, 3), default=2, help="Rondas del diagnóstico, por defecto 2.")
    parser.add_argument("--json", action="store_true", help="Imprime además el resultado JSON, en vez del resumen.")
    parser.add_argument("--tls-inseguro-solo-pruebas", action="store_true",
                        help="OMITE validación TLS y de nombre: riesgo de interceptación. Solo consultas públicas de prueba, nunca producción.")
    parser.add_argument("--archivo-ca", type=Path, help="CA PEM adicional proporcionada por una fuente confiable. No corrige certificados vencidos.")
    parser.add_argument("--usar-certifi", action="store_true",
                        help="Verifica TLS usando SOLO el bundle de certifi, sin CA/ROOT del sistema. Requiere certifi instalado.")
    parser.add_argument("--mostrar-ubicacion", action="store_true",
                        help="Muestra dirección, zona, coordenadas y enlace de mapa por registro, cuando existen.")
    args = parser.parse_args(argv)
    if args.tls_inseguro_solo_pruebas and (args.diagnosticar_tls or args.archivo_ca or args.usar_certifi):
        parser.error("TLS inseguro no se combina con --diagnosticar-tls, --archivo-ca ni --usar-certifi.")
    if args.usar_certifi and args.archivo_ca:
        parser.error("--usar-certifi y --archivo-ca son mutuamente excluyentes.")
    if not math.isfinite(args.timeout) or args.timeout <= 0 or not 0 <= args.max_detalles <= 20:
        parser.error("timeout debe ser finito y positivo; max-detalles debe estar entre 0 y 20.")
    root = args.salida / "NO_VERIFICADO_TLS" if args.tls_inseguro_solo_pruebas else args.salida
    folder = root / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S_%fZ")
    try:
        client = ClienteANH(folder / "respuestas", args.timeout, args.reintentos,
                            tls_inseguro_solo_pruebas=args.tls_inseguro_solo_pruebas,
                            archivo_ca=args.archivo_ca, usar_certifi=args.usar_certifi)
    except (OSError, ValueError) as exc:
        print(f"ERROR de configuración TLS: {exc}", file=sys.stderr)
        return 2
    report = {"inicio_utc": ahora_utc(), "estado": "en_proceso", "version_cliente": VERSION,
              "politica_tls": client.politica_tls, "almacen_ca": client.almacen_ca,
              "modo": "diagnostico_tls" if args.diagnosticar_tls else "diagnostico_apis" if args.probar_apis else "consulta"}
    exit_code = 2
    try:
        folder.mkdir(parents=True, exist_ok=False)
        if args.diagnosticar_tls:
            data = diagnosticar_tls(client, folder)
            guardar_json(folder / "diagnostico_tls.json", data)
            exit_code = 0 if data["consulta_verificada"].get("tls_verificado") is True else 2
            report["estado"] = "correcta" if exit_code == 0 else "con_fallos"
            print(json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False))
        elif args.probar_apis:
            data = probar_apis(client, args.rondas)
            guardar_json(folder / "diagnostico_apis.json", data)
            exit_code = (4 if args.tls_inseguro_solo_pruebas else 0) if data["estado"] == "completa" else 2
            report.update(estado=data["estado"], pruebas_correctas=sum(t["estado"] == "correcta" for t in data["pruebas"]),
                          pruebas_totales=len(data["pruebas"]))
            if args.json:
                print(json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False))
            else:
                for t in data["pruebas"]:
                    print(f"{t['estado'].upper():8} {t['prueba']}: {t.get('error', 'respuesta validada dentro del alcance indicado')}")
        else:
            data = obtener_datos_tarija(args.productos, cliente=client, max_detalles=args.max_detalles)
            exportar(data, folder)
            exit_code = 4 if args.tls_inseguro_solo_pruebas else (0 if data["estado"] == "completa" else 3)
            report.update(estado=data["estado"], registros=data["registros_totales"],
                          estaciones=data["estaciones_distintas"])
            if args.json:
                print(json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False))
            else:
                if args.tls_inseguro_solo_pruebas:
                    print("SOLO PRUEBAS - ORIGEN NO VERIFICADO POR TLS")
                imprimir_resumen(data, mostrar_ubicacion=args.mostrar_ubicacion)
    except (ErrorANH, OSError) as exc:
        report.update(estado="fallida", error=str(exc), categoria=getattr(exc, "categoria", "archivo"))
        print(f"ERROR: {exc}\nNo se publica un inventario vacío para sustituir este error.", file=sys.stderr)
        if getattr(exc, "categoria", None) == "tls" and not client.tls_inseguro:
            print("Compruebe fecha/hora del equipo y ejecute: python anh_tarija.py --diagnosticar-tls\n"
                  "Un certificado vencido puede pertenecer al servidor, a la cadena o a un intermediario. "
                  "No se ha desactivado la validación ni se ha reintentado en modo inseguro.", file=sys.stderr)
    finally:
        report.update(fin_utc=ahora_utc(), exit_code=exit_code, peticiones_http_intentadas=len(client.peticiones))
        try:
            guardar_json(folder / "ejecucion.json", report)
        except OSError as exc:
            print(f"No se pudo guardar el diagnóstico: {exc}", file=sys.stderr)
            exit_code = 2
        print(f"Archivos: {folder.resolve()}", file=sys.stderr)
    return exit_code


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(errors="replace")
        sys.stderr.reconfigure(errors="replace")
    raise SystemExit(main())
