import 'dart:async';

import 'package:flutter/foundation.dart';

import '../models/lectura_ubicacion.dart';
import '../services/telemetria/servicio_telemetria.dart';
import '../services/ubicacion/servicio_ubicacion.dart';
import 'map_controller.dart';
import 'sesion_controller.dart';

/// Controlador de telemetría (CU-04, HU-13): con la app en pantalla, con sesión y con permiso de ubicación, toma una
/// lectura del GPS cada 3 segundos y las envía agrupadas cada 15 (P-13), haya o no una ruta activa (RN-04.2).
///
/// Sigue al controlador del mapa: este solo pide la ubicación con permiso y con la app en pantalla, y deja de
/// pedirla al pasar a segundo plano o si se pierde el permiso. Cuando eso pasa, la telemetría se detiene y lo que no
/// se envió se descarta (RN-04.1, RN-04.3, RNF-13).
class TelemetriaController {
  /// P-13: una lectura cada 3 segundos, enviadas en grupos cada 15.
  static const Duration cadaLectura = Duration(seconds: 3);
  static const Duration cadaEnvio = Duration(seconds: 15);

  final MapController _mapa;
  final SesionController _sesion;
  final ServicioUbicacion _ubicacion;
  final ServicioTelemetria _servicio;
  final Duration _cadaLectura;
  final Duration _cadaEnvio;

  StreamSubscription<LecturaUbicacion>? _lecturas;
  Timer? _muestreo;
  Timer? _envio;
  LecturaUbicacion? _ultima;
  DateTime? _ultimaTomada;
  final List<LecturaUbicacion> _grupo = [];
  bool _enviando = false;
  bool _disposed = false;

  /// Cuántos grupos se enviaron y cuántos se descartaron por una falla (para el registro y las pruebas).
  int enviados = 0;
  int descartados = 0;

  TelemetriaController(
    this._mapa,
    this._sesion,
    this._ubicacion,
    this._servicio, {
    Duration lecturaCada = cadaLectura,
    Duration envioCada = cadaEnvio,
  }) : _cadaLectura = lecturaCada,
       _cadaEnvio = envioCada {
    _mapa.addListener(_revisar);
    _sesion.addListener(_revisar);
    _revisar();
  }

  /// true mientras toma y envía lecturas.
  bool get activa => _lecturas != null;

  bool get _puede => !_disposed && _mapa.siguiendoUbicacion && _sesion.conSesion;

  void _revisar() {
    if (_puede && !activa) {
      _empezar();
    } else if (!_puede && activa) {
      _detener();
    }
  }

  void _empezar() {
    _lecturas = _ubicacion.lecturas().listen(
      (l) => _ultima = l,
      // Por ejemplo, se revocó el permiso: el controlador del mapa también se entera y se deja de enviar.
      onError: (Object e) => debugPrint('Telemetría: error de ubicación: $e'),
    );
    _muestreo = Timer.periodic(_cadaLectura, (_) => _tomar());
    _envio = Timer.periodic(_cadaEnvio, (_) => _enviar());
  }

  /// Cada 3 segundos se toma la última lectura del GPS, si es nueva: nunca se repite una lectura con otro momento.
  void _tomar() {
    final l = _ultima;
    if (l == null || l.momento == _ultimaTomada) return;
    _ultimaTomada = l.momento;
    _grupo.add(l);
  }

  Future<void> _enviar() async {
    if (_grupo.isEmpty || _enviando) return;
    final lecturas = List.of(_grupo);
    _grupo.clear();
    _enviando = true;
    try {
      await _servicio.enviar(lecturas);
      enviados++;
    } on Object catch (e) {
      // La telemetría no se reintenta: un grupo que no salió se descarta (#75).
      descartados++;
      debugPrint('Telemetría: no se pudo enviar un grupo: $e');
    } finally {
      _enviando = false;
    }
  }

  void _detener() {
    _lecturas?.cancel();
    _lecturas = null;
    _muestreo?.cancel();
    _muestreo = null;
    _envio?.cancel();
    _envio = null;
    _grupo.clear(); // al volver se empieza de cero: nada de lecturas viejas
    _ultima = null;
  }

  void dispose() {
    _disposed = true;
    _detener();
    _mapa.removeListener(_revisar);
    _sesion.removeListener(_revisar);
  }
}
