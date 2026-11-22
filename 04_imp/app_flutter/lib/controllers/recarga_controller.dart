import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';

import '../models/coordenada.dart';
import '../models/surtidor.dart';
import '../services/api/api_client.dart';
import '../services/surtidores/servicio_surtidores.dart';
import 'combustible_controller.dart';
import 'map_controller.dart';

/// Un tipo que el conductor puede elegir al confirmar una recarga (RN-05.9): nunca Desconocido.
class TipoRecarga {
  final String codigo;
  final String nombre;

  const TipoRecarga(this.codigo, this.nombre);
}

/// La pregunta abierta: a qué surtidor se refiere y dónde se detectó la parada.
class PreguntaRecarga {
  final Surtidor surtidor;
  final Coordenada parada;
  final DateTime abierta;

  const PreguntaRecarga(this.surtidor, this.parada, this.abierta);
}

/// La parte del controlador de combustible que pregunta por la recarga (CU-05, HU-12, D-11). Con la app abierta,
/// mira la ubicación que ya sigue el mapa: si el conductor pasa [tiempoDetenido] a menos de [distancia] de un
/// surtidor del catálogo, pregunta «¿Acabas de recargar combustible?» (RN-05.8, P-09). Elegir un tipo es un toque
/// y «No» también (RN-05.16). La respuesta se envía aunque el conductor ya haya arrancado, con la ubicación de la
/// parada.
class RecargaController extends ChangeNotifier {
  /// P-09: a menos de 50 m del surtidor y 3 minutos ahí.
  static const double distancia = 50;
  static const Duration tiempoDetenido = Duration(minutes: 3);

  /// RN-05.14 y P-14: después de confirmar no se vuelve a preguntar por ese surtidor en 60 minutos.
  static const Duration silencio = Duration(minutes: 60);

  /// Una pregunta sin responder se cierra sola a los 15 minutos, sin enviar nada (RN-05.10).
  static const Duration vidaPregunta = Duration(minutes: 15);

  /// Cada cuánto se revisa, aunque no lleguen posiciones nuevas: detenido, el GPS casi no informa.
  static const Duration revision = Duration(seconds: 10);

  /// RN-05.9: los cuatro tipos, en el orden del catálogo.
  static const List<TipoRecarga> tipos = [
    TipoRecarga('GASOLINA', 'Gasolina'),
    TipoRecarga('GASOLINA_PREMIUM', 'Gasolina Premium'),
    TipoRecarga('DIESEL', 'Diesel'),
    TipoRecarga('DIESEL_ULS', 'Diesel ULS'),
  ];

  final MapController _mapa;
  final CombustibleController _combustible;
  final ServicioSurtidores _servicio;
  final DateTime Function() _ahora;

  Timer? _temporizador;
  int? _cercano;
  DateTime? _cercaDesde;
  Coordenada? _paradaDetectada;

  /// El surtidor por el que el conductor dijo «No» o cerró la pregunta: no se vuelve a preguntar hasta que se aleje.
  int? _descartado;
  final Map<int, DateTime> _silenciadosHasta = {};
  PreguntaRecarga? _pregunta;
  final List<String> _mensajes = [];
  bool _disposed = false;

  RecargaController(this._mapa, this._combustible, this._servicio, {DateTime Function()? reloj})
    : _ahora = reloj ?? DateTime.now {
    _mapa.addListener(revisar);
  }

  /// La pregunta que se muestra, o null.
  PreguntaRecarga? get pregunta => _pregunta;

  /// true mientras se revisa la ubicación (el mapa en pantalla).
  bool get activo => _temporizador != null;

  /// El próximo mensaje para el conductor (confirmada, repetida o rechazada), o null.
  String? tomarMensaje() => _mensajes.isEmpty ? null : _mensajes.removeAt(0);

  void activar() {
    if (_temporizador != null || _disposed) return;
    _temporizador = Timer.periodic(revision, (_) => revisar());
    revisar();
  }

  void pausar() {
    _temporizador?.cancel();
    _temporizador = null;
  }

  /// Mira la posición actual: si sigue junto al mismo surtidor el tiempo suficiente, pregunta.
  void revisar() {
    if (_disposed) return;
    final ahora = _ahora();
    final pregunta = _pregunta;
    if (pregunta != null && ahora.difference(pregunta.abierta) >= vidaPregunta) {
      _pregunta = null; // nadie respondió: no se envía nada
      _avisar();
    }
    final posicion = _mapa.posicion;
    final cerca = posicion == null ? null : _surtidorCercano(posicion);
    if (cerca == null) {
      _cercano = null;
      _cercaDesde = null;
      _descartado = null; // se alejó: la próxima vez sí se puede preguntar
      return;
    }
    if (_cercano != cerca.id) {
      _cercano = cerca.id;
      _cercaDesde = ahora;
      _paradaDetectada = posicion;
      if (_descartado != cerca.id) _descartado = null;
      return;
    }
    _paradaDetectada = posicion;
    if (_pregunta != null || _descartado == cerca.id || _silenciado(cerca.id, ahora)) return;
    if (ahora.difference(_cercaDesde!) >= tiempoDetenido) {
      _pregunta = PreguntaRecarga(cerca, _paradaDetectada!, ahora); // RN-05.8
      _avisar();
    }
  }

  /// RN-05.10: «No» o cerrar la pregunta no envía nada, y no se vuelve a preguntar mientras siga ahí.
  void responderNo() {
    final pregunta = _pregunta;
    if (pregunta == null) return;
    _descartado = pregunta.surtidor.id;
    _pregunta = null;
    _avisar();
  }

  /// RN-05.9 y RN-05.11: el conductor eligió el tipo. Se envía con la ubicación de la parada.
  Future<void> responderSi(TipoRecarga tipo) async {
    final pregunta = _pregunta;
    if (pregunta == null) return;
    final surtidor = pregunta.surtidor;
    _pregunta = null;
    _descartado = surtidor.id;
    _avisar();
    try {
      await _servicio.confirmarRecarga(surtidor.id, tipo.codigo, pregunta.parada);
      _silenciadosHasta[surtidor.id] = _ahora().add(silencio); // RN-05.14
      _mensajes.add('Gracias: marcaste ${tipo.nombre} disponible en ${surtidor.nombre}.');
      unawaited(_combustible.actualizar()); // el conductor ve enseguida su dato
    } on ApiException catch (e) {
      if (e.statusCode == 409) {
        _silenciadosHasta[surtidor.id] = _ahora().add(silencio); // ya estaba registrada
      }
      _mensajes.add(e.mensaje);
    } on Object catch (e) {
      debugPrint('No se pudo enviar la confirmación de recarga: $e');
      _mensajes.add('No se pudo enviar la confirmación. Revisa tu conexión.');
    }
    _avisar();
  }

  bool _silenciado(int surtidorId, DateTime ahora) {
    final hasta = _silenciadosHasta[surtidorId];
    return hasta != null && ahora.isBefore(hasta);
  }

  Surtidor? _surtidorCercano(Coordenada p) {
    Surtidor? elegido;
    var menor = double.infinity;
    for (final s in _combustible.surtidores) {
      final d = distanciaEnMetros(p, s.ubicacion);
      if (d < distancia && d < menor) {
        menor = d;
        elegido = s;
      }
    }
    return elegido;
  }

  void _avisar() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    pausar();
    _mapa.removeListener(revisar);
    super.dispose();
  }
}

/// Distancia sobre la Tierra entre dos puntos, en metros (fórmula del haversine). Para unas decenas de metros el
/// error es despreciable.
double distanciaEnMetros(Coordenada a, Coordenada b) {
  const radio = 6371000.0;
  double rad(double g) => g * math.pi / 180;
  final dLat = rad(b.latitud - a.latitud);
  final dLon = rad(b.longitud - a.longitud);
  final h = math.pow(math.sin(dLat / 2), 2) +
      math.cos(rad(a.latitud)) * math.cos(rad(b.latitud)) * math.pow(math.sin(dLon / 2), 2);
  return 2 * radio * math.asin(math.sqrt(h));
}
