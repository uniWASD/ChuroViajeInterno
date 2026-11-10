import 'package:flutter/foundation.dart';

import '../models/coordenada.dart';
import '../models/ruta.dart';
import '../services/api/api_client.dart';
import '../services/rutas/servicio_rutas.dart';

/// Qué fija el próximo toque en el mapa.
enum EleccionPunto { destino, parada, origen }

/// Estado de la vista Cómo llegar: origen, destino, paradas y la ruta que devolvió el backend.
class RutaController extends ChangeNotifier {
  /// RN-03.2: hasta 10 paradas intermedias.
  static const int maximoParadas = 10;

  static const String mensajeMotorNoDisponible =
      'El cálculo de rutas no está disponible en este momento. Inténtalo de nuevo.';
  static const String mensajeSinUbicacion =
      'No tenemos tu ubicación. Marca el origen en el mapa.';

  final ServicioRutas _servicio;

  Coordenada? _origenMarcado;
  Coordenada? _destino;
  final List<Coordenada> _paradas = [];
  EleccionPunto _eleccion = EleccionPunto.destino;

  bool _calculando = false;
  RutaCalculada? _ruta;
  String? _aviso;
  String? _error;
  bool _disposed = false;

  RutaController(this._servicio, {Coordenada? destinoInicial})
    : _destino = destinoInicial;

  bool get esDeEjemplo => _servicio.esDeEjemplo;

  /// El punto que marcó el conductor como origen; si es null, el origen es su posición actual.
  Coordenada? get origenMarcado => _origenMarcado;
  Coordenada? get destino => _destino;
  List<Coordenada> get paradas => List.unmodifiable(_paradas);
  EleccionPunto get eleccion => _eleccion;
  bool get puedeAgregarParada => _paradas.length < maximoParadas;

  bool get calculando => _calculando;

  /// La ruta encontrada, con su tiempo y su distancia (RN-03.13).
  RutaCalculada? get ruta => _ruta?.encontrada == true ? _ruta : null;

  /// «No se encontró una ruta válida» u «Origen o destino inválido»: es una respuesta, no un error.
  String? get aviso => _aviso;

  /// El backend no respondió o falló (por ejemplo, 503). Se puede reintentar (RN-03.15).
  String? get error => _error;

  void elegir(EleccionPunto eleccion) {
    _eleccion = eleccion;
    notifyListeners();
  }

  /// El conductor tocó el mapa: el punto va a lo que se esté eligiendo.
  void tocarMapa(Coordenada punto) {
    switch (_eleccion) {
      case EleccionPunto.destino:
        _destino = punto;
      case EleccionPunto.origen:
        _origenMarcado = punto;
        _eleccion = EleccionPunto.destino;
      case EleccionPunto.parada:
        if (!puedeAgregarParada) return;
        _paradas.add(punto);
        _eleccion = EleccionPunto.destino;
    }
    _olvidarResultado();
    notifyListeners();
  }

  void usarPosicionActual() {
    _origenMarcado = null;
    if (_eleccion == EleccionPunto.origen) _eleccion = EleccionPunto.destino;
    _olvidarResultado();
    notifyListeners();
  }

  void quitarParada(int indice) {
    _paradas.removeAt(indice);
    _olvidarResultado();
    notifyListeners();
  }

  /// Origen efectivo: el marcado o, si no hay, la posición actual.
  Coordenada? origen(Coordenada? posicionActual) =>
      _origenMarcado ?? posicionActual;

  /// Hace falta un destino. Si tampoco hay origen, [calcular] pide marcarlo en el mapa.
  bool get puedeCalcular => !_calculando && _destino != null;

  /// Pide la ruta al backend. La vista pasa la posición actual del conductor, que es el origen si no marcó otro.
  Future<void> calcular({Coordenada? posicionActual}) async {
    final desde = origen(posicionActual);
    final hasta = _destino;
    if (_calculando || hasta == null) return;
    if (desde == null) {
      _olvidarResultado();
      _aviso = mensajeSinUbicacion;
      _eleccion = EleccionPunto.origen;
      notifyListeners();
      return;
    }

    _calculando = true;
    _olvidarResultado();
    notifyListeners();
    try {
      final ruta = await _servicio.calcular(desde, hasta, List.of(_paradas));
      if (ruta.encontrada) {
        _ruta = ruta;
      } else {
        _aviso = ruta.mensaje;
      }
    } on ApiException catch (e) {
      _error = e.statusCode == 503 ? mensajeMotorNoDisponible : e.mensaje;
    } finally {
      _calculando = false;
      if (!_disposed) notifyListeners();
    }
  }

  void _olvidarResultado() {
    _ruta = null;
    _aviso = null;
    _error = null;
  }

  @override
  void notifyListeners() {
    if (!_disposed) super.notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
