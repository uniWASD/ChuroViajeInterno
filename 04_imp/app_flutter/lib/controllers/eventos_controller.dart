import 'dart:async';

import 'package:flutter/foundation.dart';

import '../models/catalogos.dart';
import '../models/evento_vial.dart';
import '../services/api/api_client.dart';
import '../services/eventos/servicio_eventos.dart';
import 'sesion_controller.dart';

/// Controlador de mapa para los eventos (CU-02): consulta `GET /eventos` cada 5 segundos mientras el mapa está en
/// pantalla, pide solo los cambios y conserva lo último que mostró si el backend no responde.
class EventosController extends ChangeNotifier {
  /// RN-02.6: con el mapa abierto, la app consulta cada 5 segundos.
  static const Duration ciclo = Duration(seconds: 5);
  static const String mensajeSinActualizar = 'No se pudo actualizar eventos';

  final ServicioEventos _servicio;
  final SesionController _sesion;
  final DateTime Function() _ahora;
  final Duration _ciclo;

  final Map<int, EventoVial> _eventos = {};
  String? _marca;
  Timer? _temporizador;
  bool _consultando = false;
  bool _sinActualizar = false;
  int? _seleccionado;
  bool _disposed = false;

  /// Los eventos que este conductor ya votó en esta sesión, o cuyo voto el backend rechazó: sus botones quedan
  /// desactivados (RN-02.11, RN-02.12).
  final Set<int> _votados = {};
  final Set<int> _votando = {};
  final List<String> _mensajes = [];

  EventosController(
    this._servicio,
    this._sesion, {
    DateTime Function()? reloj,
    Duration? cadaCuanto,
  }) : _ahora = reloj ?? DateTime.now,
       _ciclo = cadaCuanto ?? ciclo {
    _sesion.addListener(_alCambiarSesion);
  }

  /// Los eventos vigentes que conoce la app, sin los que ya pasaron su vigencia.
  List<EventoVial> get eventos {
    final ahora = _ahora();
    return [
      for (final e in _eventos.values)
        if (!_vencido(e, ahora)) e,
    ];
  }

  Catalogos? get catalogos => _sesion.catalogos;

  /// true si la última consulta falló: el mapa avisa y conserva lo último (RN-02.8).
  bool get sinActualizar => _sinActualizar;

  /// true mientras corre el ciclo de 5 segundos.
  bool get activo => _temporizador != null;

  /// El evento que tocó el conductor, para la ficha de detalle (RN-02.7).
  EventoVial? get seleccionado {
    final id = _seleccionado;
    if (id == null) return null;
    final evento = _eventos[id];
    return evento == null || _vencido(evento, _ahora()) ? null : evento;
  }

  /// El mapa está en pantalla: consulta ya y repite cada 5 segundos.
  void activar() {
    if (_temporizador != null || _disposed) return;
    _temporizador = Timer.periodic(_ciclo, (_) => actualizar());
    actualizar();
  }

  /// La app pasó a segundo plano o se cerró la sesión: deja de consultar.
  void pausar() {
    _temporizador?.cancel();
    _temporizador = null;
  }

  /// Una consulta: la primera trae todos los vigentes; las siguientes, solo los cambios (RN-02.6).
  Future<void> actualizar() async {
    if (_consultando || !_sesion.conSesion || _disposed) return;
    _consultando = true;
    try {
      final respuesta = await _servicio.consultar(marca: _marca);
      if (respuesta.completa) _eventos.clear();
      for (final e in respuesta.eventos) {
        _eventos[e.id] = e;
      }
      for (final id in respuesta.quitados) {
        _eventos.remove(id); // RN-02.4 y RN-02.13: retirado o vencido
      }
      _marca = respuesta.marca;
      _sinActualizar = false;
    } on Object catch (e) {
      // ApiException (sin red, tiempo de espera, error del backend) o una respuesta que no se pudo leer.
      debugPrint('No se pudieron actualizar los eventos: $e');
      _sinActualizar = true; // RN-02.8: se conserva lo último y se reintenta en el siguiente ciclo
    } finally {
      _consultando = false;
    }
    _avisar();
  }

  /// true si los botones de voto de [e] se pueden tocar.
  bool puedeVotar(EventoVial e) => e.votable && !_votados.contains(e.id) && !_votando.contains(e.id);

  bool yaVoto(int id) => _votados.contains(id);

  /// El próximo mensaje para el conductor (por ejemplo, «ya votaste este evento»), o null.
  String? tomarMensaje() => _mensajes.isEmpty ? null : _mensajes.removeAt(0);

  /// Un toque: vota «real» o «falso» (HU-11). El backend decide; si lo rechaza, se muestra su mensaje y nada cambia.
  Future<void> votar(int id, ValorVoto valor) async {
    final evento = _eventos[id];
    if (evento == null || !puedeVotar(evento)) return;
    _votando.add(id);
    _avisar();
    try {
      final r = await _servicio.votar(id, valor);
      _votados.add(id);
      final actual = _eventos[id];
      if (r.estado == 'RETIRADO' || r.estado == 'VENCIDO') {
        _eventos.remove(id); // RN-02.13: con el tercer «falso» deja de mostrarse
      } else if (actual != null) {
        _eventos[id] = actual.conEstado(r.estado, r.confirmado); // RN-02.14
      }
      _mensajes.add(valor == ValorVoto.real ? 'Gracias: votaste que es real.' : 'Gracias: votaste que es falso.');
    } on ApiException catch (e) {
      // VOTO_REPETIDO, VOTO_PROPIO, EVENTO_NO_VIGENTE o VOTO_NO_PERMITIDO: el voto no se puede repetir.
      if ((e.statusCode ?? 0) >= 400 && (e.statusCode ?? 0) < 500 && e.statusCode != 401) _votados.add(id);
      _mensajes.add(e.mensaje);
    } finally {
      _votando.remove(id);
      _avisar();
    }
  }

  void seleccionar(int id) {
    _seleccionado = id;
    _avisar();
  }

  void cerrarFicha() {
    if (_seleccionado == null) return;
    _seleccionado = null;
    _avisar();
  }

  /// Por si el aviso de vencimiento todavía no llegó: con los tiempos del catálogo, un evento vencido no se dibuja.
  bool _vencido(EventoVial e, DateTime ahora) {
    final tipo = _sesion.catalogos?.tipo(e.tipo);
    if (tipo == null) return false;
    return !ahora.isBefore(e.momentoOcurrencia.add(tipo.vigencia));
  }

  void _alCambiarSesion() {
    if (_sesion.conSesion) {
      // La sesión terminó de cargar (o se volvió a iniciar) con el mapa abierto: no se espera al ciclo.
      if (_temporizador != null && _marca == null) actualizar();
      return;
    }
    pausar(); // sin sesión no se consulta y no queda nada de la sesión anterior
    _eventos.clear();
    _marca = null;
    _seleccionado = null;
    _sinActualizar = false;
    _votados.clear();
    _mensajes.clear();
  }

  void _avisar() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    pausar();
    _sesion.removeListener(_alCambiarSesion);
    super.dispose();
  }
}
