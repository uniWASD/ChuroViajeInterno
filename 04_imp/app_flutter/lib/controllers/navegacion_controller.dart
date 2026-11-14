import 'dart:math' as math;

import 'package:flutter/foundation.dart';

import '../models/coordenada.dart';
import '../models/navegacion.dart';
import '../models/ruta.dart';
import 'map_controller.dart';

/// Una ruta Activa se sigue hasta que termina; terminada, se borra del teléfono (RN-03.17, D-05).
enum EstadoNavegacion { activa, terminada }

/// Por qué terminó la navegación.
enum FinNavegacion { llegada, cancelada }

/// Controlador de navegación (CU-03, CU-04): sigue la posición del conductor sobre la ruta Activa, sabe la calle que
/// sigue y cuánto falta, y guarda los tramos y las paradas que faltan para revisar la ruta (D-12). La ruta vive solo
/// en memoria: nunca se guarda en el teléfono ni en el servidor (D-05).
class NavegacionController extends ChangeNotifier {
  /// A menos de 30 m del destino, la navegación termina (CP-155). Lo mismo marca una parada como pasada.
  static const double llegadaM = 30;

  /// Hacia atrás solo se busca un par de segmentos: el GPS salta, pero el conductor no retrocede la ruta.
  static const int _segmentosHaciaAtras = 2;

  final MapController _mapa;

  RutaCalculada? _ruta;
  Trazado? _trazado;
  List<Maniobra> _maniobras = const [];
  final Coordenada _destino;
  final List<Coordenada> _paradas;
  EstadoNavegacion _estado = EstadoNavegacion.activa;
  FinNavegacion? _fin;
  Coordenada? _posicion;
  int _segmento = 0;
  double _recorridoM = 0;
  bool _disposed = false;

  NavegacionController({
    required RutaCalculada ruta,
    required this._destino,
    List<Coordenada> paradas = const [],
    required this._mapa,
  }) : _paradas = List.of(paradas) {
    _usarRuta(ruta);
    _mapa.addListener(_alMoverse);
    final p = _mapa.posicion;
    if (p != null) actualizar(p);
  }

  void _usarRuta(RutaCalculada ruta) {
    _ruta = ruta;
    _trazado = Trazado(ruta.trazado);
    _maniobras = planificarManiobras(_trazado!, ruta.tramos);
    _segmento = 0;
    _recorridoM = 0;
  }

  EstadoNavegacion get estado => _estado;
  bool get activa => _estado == EstadoNavegacion.activa;
  FinNavegacion? get fin => _fin;

  /// La ruta Activa, o null cuando ya terminó (se borró).
  RutaCalculada? get ruta => _ruta;
  Coordenada get destino => _destino;

  /// La última posición del conductor sobre la ruta, o null si todavía no llegó ninguna.
  Coordenada? get posicion => _posicion;

  /// Metros recorridos sobre el trazado.
  double get recorridoM => _recorridoM;

  /// Las paradas que el conductor todavía no pasó, en orden (para revisar la ruta, D-12).
  List<Coordenada> get paradasQueFaltan => List.unmodifiable(_paradas);

  double get distanciaRestanteM {
    final t = _trazado;
    return t == null ? 0 : math.max(0.0, t.longitudM - _recorridoM);
  }

  int get tiempoRestanteS {
    final ruta = _ruta, t = _trazado;
    if (ruta == null || t == null) return 0;
    return tiempoProporcional(ruta.tiempoS, math.max(0.0, t.longitudM - _recorridoM), t.longitudM);
  }

  /// La próxima esquina donde se cambia de calle, o null si ya no hay más cambios hasta el destino.
  Maniobra? get siguienteManiobra {
    for (final m in _maniobras) {
      if (m.recorridoM > _recorridoM) return m;
    }
    return null;
  }

  /// Metros hasta la próxima esquina.
  double? get distanciaASiguienteM {
    final m = siguienteManiobra;
    return m == null ? null : m.recorridoM - _recorridoM;
  }

  /// La calle por la que va el conductor.
  String? get calleActual {
    final ruta = _ruta;
    if (ruta == null || ruta.tramos.isEmpty) return null;
    String calle = ruta.tramos.first.calle;
    for (final m in _maniobras) {
      if (m.recorridoM <= _recorridoM) calle = m.calle;
    }
    return calle;
  }

  /// D-12: los id de los tramos que faltan, en orden. Se cuenta desde el primer tramo de la calle por la que va el
  /// conductor, porque la ruta no dice dónde empieza cada tramo dentro de una misma calle.
  List<String> get tramosQueFaltan {
    final ruta = _ruta;
    if (ruta == null) return const [];
    var desde = 0;
    for (final m in _maniobras) {
      if (m.recorridoM <= _recorridoM) desde = m.primerTramo;
    }
    return [for (final t in ruta.tramos.skip(desde)) t.id];
  }

  /// Llega una posición nueva del conductor.
  void actualizar(Coordenada p) {
    final t = _trazado;
    if (!activa || t == null) return;
    _posicion = p;
    final proyeccion = t.proyectar(p, desdeSegmento: _segmento - _segmentosHaciaAtras);
    _segmento = math.max(_segmento, proyeccion.segmento);
    _recorridoM = math.max(_recorridoM, proyeccion.recorridoM);
    while (_paradas.isNotEmpty && distanciaM(p, _paradas.first) < llegadaM) {
      _paradas.removeAt(0);
    }
    if (distanciaM(p, _destino) < llegadaM) {
      _terminar(FinNavegacion.llegada);
      return;
    }
    _avisar();
  }

  /// «Cancelar»: la ruta pasa a Terminada y se borra (D-05).
  void cancelar() => _terminar(FinNavegacion.cancelada);

  void _terminar(FinNavegacion fin) {
    if (!activa) return;
    _estado = EstadoNavegacion.terminada;
    _fin = fin;
    _ruta = null;
    _trazado = null;
    _maniobras = const [];
    _paradas.clear();
    _avisar();
  }

  void _alMoverse() {
    final p = _mapa.posicion;
    if (p != null && p != _posicion) actualizar(p);
  }

  void _avisar() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _mapa.removeListener(_alMoverse);
    super.dispose();
  }
}
