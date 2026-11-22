"""Un backend falso para probar el simulador sin el backend de verdad: responde lo mínimo de cada ruta de la API y
guarda lo que recibió."""

import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CREDENCIAL = "credencial-de-prueba-solo-para-las-pruebas-0123456789"


class BackendFalso:
    def __init__(self, sesiones_habilitadas: bool = True):
        self.pedidos: list[tuple[str, str, dict | None, str | None]] = []
        self.sesiones_habilitadas = sesiones_habilitadas
        self._candado = threading.Lock()
        falso = self

        class Manejador(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def _responder(self, estado: int, cuerpo: object):
                datos = json.dumps(cuerpo).encode("utf-8")
                self.send_response(estado)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(datos)))
                self.end_headers()
                self.wfile.write(datos)

            def do_POST(self):
                largo = int(self.headers.get("Content-Length", 0))
                cuerpo = json.loads(self.rfile.read(largo) or b"null")
                jwt = (self.headers.get("Authorization") or "").removeprefix("Bearer ") or None
                with falso._candado:
                    falso.pedidos.append(("POST", self.path, cuerpo, jwt))
                if self.path == "/sesion/prueba":
                    if not falso.sesiones_habilitadas:
                        return self._responder(403, {"codigo": "ACCESO_DENEGADO", "mensaje": "No habilitado."})
                    if cuerpo.get("credencial") != CREDENCIAL:
                        return self._responder(401, {"codigo": "CREDENCIAL_PRUEBA_INVALIDA", "mensaje": "No."})
                    return self._responder(200, {"jwt": f"jwt-{cuerpo['pruebaId']}-{cuerpo['usuario']}",
                                                 "expiraEn": "2030-01-01T00:00:00Z"})
                if self.path == "/simulacion/borrar":
                    if cuerpo.get("credencial") != CREDENCIAL:
                        return self._responder(401, {"codigo": "CREDENCIAL_PRUEBA_INVALIDA",
                                                     "mensaje": "La credencial de prueba no es válida."})
                    return self._responder(200, {"votos": 2, "eventos": 5, "conductores": 3})
                if not jwt or not jwt.startswith("jwt-"):
                    return self._responder(401, {"codigo": "NO_AUTENTICADO", "mensaje": "Inicia sesión."})
                if self.path == "/rutas":
                    o, d = cuerpo["origen"], cuerpo["destino"]
                    return self._responder(200, {"estado": "ENCONTRADA", "distanciaM": 700, "tiempoS": 90,
                                                 "trazado": [o, {"latitud": o["latitud"], "longitud": d["longitud"]}, d],
                                                 "tramos": [{"id": "osm:1:0:ida", "calle": "A"},
                                                            {"id": "osm:2:0:ida", "calle": "B"}]})
                if self.path == "/rutas/revision":
                    return self._responder(200, {"estado": "SIN_ALTERNATIVA", "eventoId": 9})
                if self.path == "/telemetria":
                    return self._responder(202, {"estado": "RECIBIDO", "lecturas": len(cuerpo["lecturas"])})
                if self.path == "/eventos":
                    return self._responder(201, {"id": 1})
                return self._responder(404, {"codigo": "NO_EXISTE", "mensaje": "No existe."})

        self.servidor = ThreadingHTTPServer(("127.0.0.1", 0), Manejador)
        self.direccion = f"http://127.0.0.1:{self.servidor.server_address[1]}"
        threading.Thread(target=self.servidor.serve_forever, daemon=True).start()

    def de(self, ruta: str) -> list:
        with self._candado:
            return [p for p in self.pedidos if p[1] == ruta]

    def cerrar(self):
        self.servidor.shutdown()
        self.servidor.server_close()
