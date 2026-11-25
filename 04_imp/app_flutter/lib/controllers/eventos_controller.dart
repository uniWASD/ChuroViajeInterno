import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';

import '../models/catalogos.dart';
import '../models/evento_vial.dart';
import '../models/trafico.dart';
import '../services/api/api_client.dart';
import '../services/eventos/servicio_eventos.dart';
import '../services/sesion/almacen_local.dart';
import 'sesion_controller.dart';

/// Controlador de mapa para los eventos (CU-02): consulta `GET /eventos` cada 5 segundos mientras el mapa está en
/// pantalla, pide solo los cambios y conserva lo último que mostró si el backend no responde. Cada consulta exitosa
/// guarda los eventos en el teléfono; sin conexión, al abrir la app se muestran esos con la marca de desactualizados
/// hasta que una consulta vuelva a funcionar (HU-04, RN-02.9).
class EventosController extends ChangeNotifier {
  /// Donde quedan guardados los últimos eventos (HU-04). Cerrar sesión borra todo lo guardado (RN-08.14).
  static const String claveGuardados = 'eventos_guardados';

  /// RN-02.6: con el mapa abierto, la app consulta cada 5 segundos.
  static const Duration ciclo = Duration(seconds: 5);
  static const String mensajeSinActualizar = 'No se pudo actualizar eventos';

  final ServicioEventos _servicio;
  final SesionController _sesion;
  final DateTime Function() _ahora;
  final Duration _ciclo;
  final AlmacenLocal? _telefono;
  bool _desactualizados = false;
  DateTime? _guardadosEn;
  bool _yaGuardo = false;

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

  /// Los vigentes ya calculados: mientras no cambie nada, [eventos] devuelve la misma lista. Así el mapa y la
  /// navegación no rehacen sus cálculos (las congestiones unidas, los marcadores) en cada reconstrucción.
  List<EventoVial>? _vigentes;
  List<CongestionUnida>? _congestiones;
  Catalogos? _vigentesCatalogos;
  DateTime? _vigentesDesde;

  /// El primer vencimiento de los eventos que entraron en [_vigentes]: desde ahí hay que volver a calcular.
  DateTime? _vigentesHasta;

  EventosController(
    this._servicio,
    this._sesion, {
    DateTime Function()? reloj,
    Duration? cadaCuanto,
    AlmacenLocal? almacen,
  }) : _ahora = reloj ?? DateTime.now,
       _ciclo = cadaCuanto ?? ciclo,
       _telefono = almacen {
    _sesion.addListener(_alCambiarSesion);
  }

  /// Los eventos vigentes que conoce la app, sin los que ya pasaron su vigencia. Mientras no llegue ni venza nada
  /// es la misma lista (no se puede modificar), para que las vistas puedan reconocer que no cambió.
  List<EventoVial> get eventos {
    final ahora = _ahora();
    final catalogos = _sesion.catalogos;
    final guardada = _vigentes;
    if (guardada != null &&
        identical(catalogos, _vigentesCatalogos) &&
        !ahora.isBefore(_vigentesDesde!) &&
        (_vigentesHasta == null || ahora.isBefore(_vigentesHasta!))) {
      return guardada;
    }
    DateTime? hasta;
    final lista = <EventoVial>[];
    for (final e in _eventos.values) {
      final tipo = catalogos?.tipo(e.tipo);
      if (tipo == null) {
        lista.add(e);
        continue;
      }
      final vence = e.momentoOcurrencia.add(tipo.vigencia);
      if (!ahora.isBefore(vence)) continue;
      lista.add(e);
      if (hasta == null || vence.isBefore(hasta)) hasta = vence;
    }
    _vigentes = List.unmodifiable(lista);
    _congestiones = null;
    _vigentesCatalogos = catalogos;
    _vigentesDesde = ahora;
    _vigentesHasta = hasta;
    return _vigentes!;
  }

  /// #191: las congestiones de [eventos] unidas de donde empiezan a donde acaban. Se calculan una vez por cada lista
  /// de vigentes, no en cada reconstrucción del mapa.
  List<CongestionUnida> get congestiones {
    final vigentes = eventos;
    return _congestiones ??= List.unmodifiable(unirCongestiones(vigentes));
  }

  /// Algo cambió en los eventos que conoce la app: [eventos] se vuelve a calcular.
  void _cambiaronEventos() {
    _vigentes = null;
    _congestiones = null;
  }

  Catalogos? get catalogos => _sesion.catalogos;

  /// true si la última consulta falló: el mapa avisa y conserva lo último (RN-02.8).
  bool get sinActualizar => _sinActualizar;

  /// RN-02.9: true mientras se muestran los eventos guardados en el teléfono porque no hubo conexión; la vista
  /// avisa que pueden estar desactualizados.
  bool get desactualizados => _desactualizados;

  /// Cuándo se guardaron los eventos que se muestran sin conexión.
  DateTime? get guardadosEn => _guardadosEn;

  /// Al abrir la app: si todavía no llegó ninguna consulta, muestra los eventos guardados con la marca de
  /// desactualizados (RN-02.9). Los que ya vencieron según el catálogo no se dibujan, igual que siempre.
  Future<void> cargarGuardados() async {
    final almacen = _telefono;
    if (almacen == null) return;
    String? texto;
    try {
      texto = await almacen.leer(claveGuardados);
    } on Object catch (e) {
      debugPrint('No se pudieron leer los eventos guardados: $e');
      return;
    }
    // Si una consulta ya respondió mientras se leía, mandan los datos frescos.
    if (texto == null || _disposed || _marca != null || _eventos.isNotEmpty) return;
    try {
      final json = jsonDecode(texto) as Map<String, dynamic>;
      for (final e in (json['eventos'] as List? ?? const [])) {
        final evento = EventoVial.fromJson(e as Map<String, dynamic>);
        _eventos[evento.id] = evento;
      }
      _cambiaronEventos();
      _guardadosEn = DateTime.tryParse(json['guardadoEn'] as String? ?? '');
      _desactualizados = true;
    } on Object catch (e) {
      debugPrint('Los eventos guardados no se pudieron leer: $e');
      return;
    }
    _avisar();
  }

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
      if (respuesta.completa || respuesta.eventos.isNotEmpty || respuesta.quitados.isNotEmpty) _cambiaronEventos();
      _marca = respuesta.marca;
      _sinActualizar = false;
      _desactualizados = false; // RN-02.9: volvió la red y lo guardado se reemplaza por lo vigente
      _guardadosEn = null;
      if (respuesta.completa || respuesta.eventos.isNotEmpty || respuesta.quitados.isNotEmpty || !_yaGuardo) {
        unawaited(_guardar());
      }
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
        // RN-02.14 y D-46: el estado y el conteo que dejó el voto.
        _eventos[id] = actual.conEstado(r.estado, r.confirmado, votosReal: r.votosReal, votosFalso: r.votosFalso);
      }
      _cambiaronEventos();
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

  /// HU-04: cada consulta que trae algo guarda los eventos que conoce la app.
  Future<void> _guardar() async {
    final almacen = _telefono;
    if (almacen == null) return;
    _yaGuardo = true;
    final texto = jsonEncode({
      'guardadoEn': _ahora().toUtc().toIso8601String(),
      'eventos': [for (final e in _eventos.values) e.toJson()],
    });
    try {
      await almacen.guardar(claveGuardados, texto);
    } on Object catch (e) {
      debugPrint('No se pudieron guardar los eventos: $e');
    }
  }

  void _alCambiarSesion() {
    if (_sesion.conSesion) {
      // La sesión terminó de cargar (o se volvió a iniciar) con el mapa abierto: no se espera al ciclo.
      if (_temporizador != null && _marca == null) actualizar();
      return;
    }
    pausar(); // sin sesión no se consulta y no queda nada de la sesión anterior
    _eventos.clear();
    _cambiaronEventos();
    _marca = null;
    _seleccionado = null;
    _sinActualizar = false;
    _desactualizados = false;
    _guardadosEn = null;
    _yaGuardo = false;
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
