import 'dart:async';

import 'package:flutter/foundation.dart';

import '../models/coordenada.dart';
import '../models/surtidor.dart';
import '../services/surtidores/servicio_surtidores.dart';
import 'map_controller.dart';
import 'sesion_controller.dart';

/// Controlador de combustible de la app (CU-05): pide `GET /surtidores` con la ubicación del conductor y lo repite
/// cada 60 segundos mientras el mapa o la vista Surtidores están en pantalla (RN-05.17, P-27), para la lista y para
/// los marcadores. Sin la ubicación, pide desde el centro de Tarija: los marcadores salen igual y la lista avisa que
/// no está ordenada por cercanía.
class CombustibleController extends ChangeNotifier {
  /// RN-05.17 y P-27: cada cuánto se actualizan la lista y los marcadores.
  static const Duration ciclo = Duration(seconds: 60);
  static const String mensajeSinActualizar = 'No se pudo actualizar los surtidores';

  final ServicioSurtidores _servicio;
  final SesionController _sesion;
  final MapController _mapa;
  final Duration _ciclo;

  List<Surtidor> _surtidores = const [];
  Timer? _temporizador;
  bool _consultando = false;
  bool _cargado = false;
  bool _sinActualizar = false;
  bool _ordenadosPorCercania = false;
  int? _seleccionado;
  int? _expandido;
  bool _disposed = false;

  CombustibleController(this._servicio, this._sesion, this._mapa, {Duration? cadaCuanto})
    : _ciclo = cadaCuanto ?? ciclo {
    _sesion.addListener(_alCambiarSesion);
    _mapa.addListener(_alCambiarMapa);
  }

  /// Del más cercano al más lejano (RN-05.2).
  List<Surtidor> get surtidores => _surtidores;

  /// true hasta que llega la primera respuesta.
  bool get cargando => !_cargado;

  /// La última consulta falló: se conserva la lista anterior y se reintenta en el siguiente ciclo.
  bool get sinActualizar => _sinActualizar;

  /// false si la lista se pidió sin la ubicación del conductor (desde el centro de Tarija).
  bool get ordenadosPorCercania => _ordenadosPorCercania;

  /// true mientras corre el ciclo de 60 segundos.
  bool get activo => _temporizador != null;

  /// El surtidor abierto en el detalle.
  Surtidor? get seleccionado => _porId(_seleccionado);

  /// El surtidor desplegado en la lista.
  int? get expandido => _expandido;

  Surtidor? porId(int id) => _porId(id);

  /// El mapa está en pantalla: consulta ya y repite cada 60 segundos.
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

  Future<void> actualizar() async {
    if (_consultando || !_sesion.conSesion || _disposed) return;
    _consultando = true;
    final posicion = _mapa.posicion;
    try {
      _surtidores = await _servicio.consultar(posicion ?? centroTarija);
      _ordenadosPorCercania = posicion != null;
      _sinActualizar = false;
      _cargado = true;
    } on Object catch (e) {
      debugPrint('No se pudieron actualizar los surtidores: $e');
      _sinActualizar = true;
    } finally {
      _consultando = false;
    }
    _avisar();
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

  /// Despliega o pliega un surtidor de la lista, para ver su estado por combustible.
  void alternar(int id) {
    _expandido = _expandido == id ? null : id;
    _avisar();
  }

  /// Pliega el surtidor desplegado en la lista, por ejemplo al salir de la pestaña Surtidores.
  void plegar() {
    if (_expandido == null) return;
    _expandido = null;
    _avisar();
  }

  Surtidor? _porId(int? id) {
    if (id == null) return null;
    for (final s in _surtidores) {
      if (s.id == id) return s;
    }
    return null;
  }

  /// Llegó la primera posición después de pedir sin ella: se vuelve a pedir ya, para ordenar por cercanía.
  void _alCambiarMapa() {
    if (_temporizador != null && _cargado && !_ordenadosPorCercania && _mapa.posicion != null) {
      actualizar();
    }
  }

  void _alCambiarSesion() {
    if (_sesion.conSesion) {
      if (_temporizador != null && !_cargado) actualizar();
      return;
    }
    pausar();
    _surtidores = const [];
    _cargado = false;
    _seleccionado = null;
    _expandido = null;
    _sinActualizar = false;
  }

  void _avisar() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    pausar();
    _sesion.removeListener(_alCambiarSesion);
    _mapa.removeListener(_alCambiarMapa);
    super.dispose();
  }
}
