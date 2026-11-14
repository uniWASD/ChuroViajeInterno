import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';

import '../models/coordenada.dart';
import '../models/evento_vial.dart';
import '../models/navegacion.dart';
import '../models/revision_ruta.dart';
import '../models/ruta.dart';
import '../services/rutas/servicio_rutas.dart';
import 'map_controller.dart';

/// Una ruta Activa se sigue hasta que termina; terminada, se borra del teléfono (RN-03.17, D-05).
enum EstadoNavegacion { activa, terminada }

/// Por qué terminó la navegación.
enum FinNavegacion { llegada, cancelada }

/// Un aviso de la revisión de la ruta (CU-04): una alternativa para aceptar o rechazar (RN-04.17), o un evento que
/// afecta la ruta sin un camino mejor (RN-04.16).
class AvisoRuta {
  final int eventoId;

  /// El evento, si la app lo conoce por `GET /eventos`; para mostrar su tipo.
  final EventoVial? evento;

  /// Solo si hay una ruta mejor.
  final RutaCalculada? alternativa;

  /// Segundos que le quedan al aviso antes de cerrarse solo (RN-04.21).
  final int segundosRestantes;

  const AvisoRuta({required this.eventoId, this.evento, this.alternativa, required this.segundosRestantes});

  bool get conAlternativa => alternativa != null;

  AvisoRuta _conSegundos(int s) =>
      AvisoRuta(eventoId: eventoId, evento: evento, alternativa: alternativa, segundosRestantes: s);
}

/// Controlador de navegación (CU-03, CU-04): sigue la posición del conductor sobre la ruta Activa, sabe la calle que
/// sigue y cuánto falta, y guarda los tramos y las paradas que faltan para revisar la ruta (D-12). La ruta vive solo
/// en memoria: nunca se guarda en el teléfono ni en el servidor (D-05).
class NavegacionController extends ChangeNotifier {
  /// A menos de 30 m del destino, la navegación termina (CP-155). Lo mismo marca una parada como pasada.
  static const double llegadaM = 30;

  /// Hacia atrás solo se busca un par de segmentos: el GPS salta, pero el conductor no retrocede la ruta.
  static const int _segmentosHaciaAtras = 2;

  /// RN-04.13: la revisión acompaña a la consulta de eventos de cada 5 segundos (RN-02.6).
  static const Duration cicloRevision = Duration(seconds: 5);

  /// RN-04.21 y P-26: el aviso espera 15 segundos una respuesta.
  static const int segundosAviso = 15;

  /// Un evento a más de 50 m del trazado no se puede ubicar sobre la ruta.
  static const double _cercaDeLaRutaM = 50;

  final MapController _mapa;
  final ServicioRutas? _rutas;
  final List<EventoVial> Function()? _eventos;
  Timer? _cicloRevision;
  Timer? _cuentaAviso;
  bool _revisando = false;
  final Set<int> _avisados = {};
  AvisoRuta? _aviso;

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
    ServicioRutas? rutas,
    /// Los eventos vigentes que conoce la app (`GET /eventos`), para mostrar el tipo del que avisa y saber si ya
    /// quedó atrás.
    this._eventos,
    Duration revisarCada = cicloRevision,
  }) : _rutas = rutas,
       _paradas = List.of(paradas) {
    _usarRuta(ruta);
    _mapa.addListener(_alMoverse);
    final p = _mapa.posicion;
    if (p != null) actualizar(p);
    // Sin servicio de rutas (pruebas de la sola navegación) no se revisa.
    if (rutas != null) _cicloRevision = Timer.periodic(revisarCada, (_) => revisar());
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

  /// El aviso a la vista, si hay uno.
  AvisoRuta? get aviso => _aviso;

  /// RN-04.19: los eventos que ya provocaron un aviso en esta navegación; ninguno vuelve a avisar.
  Set<int> get eventosAvisados => Set.unmodifiable(_avisados);

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

  /// Una revisión de la ruta (CU-04): manda lo que falta y atiende la respuesta. Una falla (sin red, 503) no se
  /// muestra: la ruta sigue igual y se vuelve a intentar en el siguiente ciclo. Es asíncrona y no bloquea la
  /// interfaz mientras espera.
  Future<void> revisar() async {
    final rutas = _rutas, p = _posicion, ruta = _ruta;
    // Con una alternativa a la vista se espera la respuesta del conductor.
    if (rutas == null || !activa || p == null || ruta == null || _revisando || _aviso?.conAlternativa == true) {
      return;
    }
    final tramos = tramosQueFaltan;
    if (tramos.isEmpty) return;
    _revisando = true;
    try {
      final r = await rutas.revisar(PedidoRevision(
        posicion: p,
        tramos: tramos,
        destino: _destino,
        paradas: paradasQueFaltan,
        eventosAvisados: _avisados.toList()..sort(),
      ));
      // Si mientras tanto la ruta cambió o terminó, la respuesta ya no corresponde.
      if (!activa || !identical(ruta, _ruta)) return;
      switch (r.estado) {
        case EstadoRevision.sinCambios:
          break;
        case EstadoRevision.alternativa:
        case EstadoRevision.sinAlternativa:
          _avisarEvento(r.eventoId!, r.alternativa);
        case EstadoRevision.rutaDesactualizada:
          await _recalcular(rutas);
      }
    } on Object catch (e) {
      debugPrint('No se pudo revisar la ruta: $e');
    } finally {
      _revisando = false;
    }
  }

  void _avisarEvento(int eventoId, RutaCalculada? alternativa) {
    // RN-04.19: un evento avisa una sola vez, se acepte, se rechace o se cierre solo.
    if (!_avisados.add(eventoId)) return;
    final evento = _buscarEvento(eventoId);
    // RN-04.14: un evento que ya quedó atrás no afecta lo que falta (la ruta guarda los tramos desde el comienzo de
    // la calle por la que va el conductor, y el backend puede ver uno que ya pasó).
    if (evento != null && _quedoAtras(evento)) return;
    _mostrarAviso(AvisoRuta(
      eventoId: eventoId,
      evento: evento,
      alternativa: alternativa,
      segundosRestantes: segundosAviso,
    ));
  }

  EventoVial? _buscarEvento(int id) {
    for (final e in _eventos?.call() ?? const <EventoVial>[]) {
      if (e.id == id) return e;
    }
    return null;
  }

  bool _quedoAtras(EventoVial e) {
    final t = _trazado;
    if (t == null) return false;
    final p = t.proyectar(e.ubicacion);
    return p.distanciaM <= _cercaDeLaRutaM && p.recorridoM < _recorridoM - llegadaM;
  }

  void _mostrarAviso(AvisoRuta aviso) {
    _cuentaAviso?.cancel();
    _aviso = aviso;
    // RN-04.21: sin respuesta en 15 segundos se cierra solo y sigue la ruta original.
    _cuentaAviso = Timer.periodic(const Duration(seconds: 1), (_) {
      final actual = _aviso;
      if (actual == null) return;
      if (actual.segundosRestantes <= 1) {
        _cerrarAviso();
      } else {
        _aviso = actual._conSegundos(actual.segundosRestantes - 1);
        _avisar();
      }
    });
    _avisar();
  }

  /// RN-04.18: la alternativa pasa a ser la ruta Activa.
  void aceptarAlternativa() {
    final nueva = _aviso?.alternativa;
    if (!activa || nueva == null) return;
    _usarRuta(nueva);
    final p = _posicion;
    if (p != null) {
      final proyeccion = _trazado!.proyectar(p);
      _segmento = proyeccion.segmento;
      _recorridoM = proyeccion.recorridoM;
    }
    _cerrarAviso();
  }

  /// RN-04.18: sigue la ruta original, con el evento a la vista en el mapa.
  void rechazarAlternativa() => _cerrarAviso();

  /// Cierra el aviso de un evento sin alternativa.
  void cerrarAviso() => _cerrarAviso();

  void _cerrarAviso() {
    _cuentaAviso?.cancel();
    _cuentaAviso = null;
    if (_aviso == null) return;
    _aviso = null;
    _avisar();
  }

  /// La red vial se recargó y los tramos ya no existen: se pide la ruta otra vez desde la posición actual, con el
  /// mismo destino y las paradas que faltan.
  Future<void> _recalcular(ServicioRutas rutas) async {
    final p = _posicion;
    if (p == null) return;
    final nueva = await rutas.calcular(p, _destino, paradasQueFaltan);
    if (!activa || !nueva.encontrada) return;
    _usarRuta(nueva);
    final proyeccion = _trazado!.proyectar(p);
    _segmento = proyeccion.segmento;
    _recorridoM = proyeccion.recorridoM;
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
    _cicloRevision?.cancel();
    _cicloRevision = null;
    _cuentaAviso?.cancel();
    _cuentaAviso = null;
    _aviso = null;
    _avisados.clear();
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
    _cicloRevision?.cancel();
    _cuentaAviso?.cancel();
    _mapa.removeListener(_alMoverse);
    super.dispose();
  }
}
