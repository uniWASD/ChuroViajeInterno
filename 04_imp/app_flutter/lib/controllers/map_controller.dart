import 'dart:async';

import 'package:flutter/foundation.dart';

import '../models/coordenada.dart';
import '../services/ubicacion/servicio_ubicacion.dart';

/// Estado de la vista Mapa principal: la ubicación del conductor, el punto que tocó y si el mapa base cargó.
class MapController extends ChangeNotifier {
  static const String mensajeErrorMapa =
      'No se pudo cargar el mapa. Revisa tu conexión e inténtalo de nuevo.';

  final ServicioUbicacion _ubicacion;

  PermisoUbicacion? _permiso;
  Coordenada? _posicion;
  StreamSubscription<Coordenada>? _suscripcion;
  bool _enPantalla = true;
  bool _pidiendoPermiso = false;

  Coordenada? _puntoSeleccionado;
  String? _errorMapa;
  int _intentoMapa = 0;
  bool _disposed = false;

  MapController(this._ubicacion);

  PermisoUbicacion? get permiso => _permiso;
  bool get tienePermiso => _permiso == PermisoUbicacion.concedido;

  /// Última posición conocida del conductor, o null si no hay permiso o todavía no llegó ninguna.
  Coordenada? get posicion => _posicion;

  /// true mientras se está pidiendo la ubicación (con permiso y con la app en pantalla).
  bool get siguiendoUbicacion => _suscripcion != null;

  Coordenada? get puntoSeleccionado => _puntoSeleccionado;

  /// Mensaje para el conductor si el mapa base no cargó (RN-03.18).
  String? get errorMapa => _errorMapa;

  /// Cambia en cada reintento, para que la vista vuelva a crear el mapa.
  int get intentoMapa => _intentoMapa;

  /// Pide el permiso de ubicación y, si se concede, empieza a seguir la posición. Sin permiso, el mapa sigue
  /// funcionando igual, solo que sin la posición del conductor (RN-04.3).
  Future<void> activarUbicacion() async {
    if (_pidiendoPermiso) return;
    _pidiendoPermiso = true;
    try {
      _permiso = await _ubicacion.pedirPermiso();
    } finally {
      _pidiendoPermiso = false;
    }
    if (_disposed) return;
    if (tienePermiso) {
      _seguir();
    } else {
      _dejarDeSeguir();
      // Nunca se debe reportar usando una posición antigua cuando el GPS está apagado
      // o el conductor retiró el permiso (CP-58, RN-01.5).
      _posicion = null;
    }
    notifyListeners();
  }

  /// La app pasó a segundo plano: se deja de pedir la ubicación (RNF-13).
  void alSalirDePantalla() {
    _enPantalla = false;
    // Al volver debe obtenerse una posición nueva: la última podría haberse quedado obsoleta.
    _posicion = null;
    if (_suscripcion != null) {
      _dejarDeSeguir();
    }
    notifyListeners();
  }

  /// La app volvió a estar en pantalla: si había permiso, se vuelve a seguir la posición.
  void alVolverAPantalla() {
    _enPantalla = true;
    if (tienePermiso && _suscripcion == null) {
      _seguir();
      notifyListeners();
    }
  }

  Future<void> abrirAjustesDeUbicacion() => _ubicacion.abrirAjustes();

  /// El conductor tocó un punto del mapa (para el ajuste del reporte o para elegir un destino).
  void seleccionarPunto(Coordenada punto) {
    _puntoSeleccionado = punto;
    notifyListeners();
  }

  void limpiarPunto() {
    if (_puntoSeleccionado == null) return;
    _puntoSeleccionado = null;
    notifyListeners();
  }

  /// El mapa base no cargó. El detalle es para el registro; al conductor se le muestra un mensaje fijo.
  void informarErrorMapa(String detalle) {
    debugPrint('El mapa base no cargó: $detalle');
    _errorMapa = mensajeErrorMapa;
    notifyListeners();
  }

  void reintentarMapa() {
    _errorMapa = null;
    _intentoMapa++;
    notifyListeners();
  }

  void _seguir() {
    if (!_enPantalla || _suscripcion != null) return;
    _suscripcion = _ubicacion.posiciones().listen(
      (p) {
        _posicion = p;
        notifyListeners();
      },
      onError: (Object error) {
        // Por ejemplo, el conductor apagó el GPS: se descarta la última lectura.
        debugPrint('Error de ubicación: $error');
        _posicion = null; // el último punto ya no es una lectura GPS fiable
        _dejarDeSeguir();
        notifyListeners();
      },
    );
  }

  void _dejarDeSeguir() {
    _suscripcion?.cancel();
    _suscripcion = null;
  }

  @override
  void dispose() {
    _disposed = true;
    _dejarDeSeguir();
    super.dispose();
  }
}
