import 'package:flutter/foundation.dart';

import '../models/catalogos.dart';
import '../models/coordenada.dart';
import '../models/evento_vial.dart';
import '../models/reporte_evento.dart';
import '../services/api/api_client.dart';
import '../services/eventos/servicio_eventos.dart';
import 'cola_controller.dart';
import 'map_controller.dart';
import 'sesion_controller.dart';

enum EstadoReporte {
  /// El conductor elige el tipo y el tiempo.
  eligiendo,

  /// El GPS no dio la ubicación: el conductor toca el mapa donde ocurrió (RN-01.5).
  necesitaPunto,
  enviando,
  enviado,

  /// El backend no lo aceptó: duplicado, límite, fuera de Tarija o vencido. Se muestra su mensaje.
  rechazado,

  /// No hay red y no hay cola: se conserva lo elegido.
  sinRed,

  /// No hay red: el reporte quedó en la cola y se enviará solo (HU-02, RN-01.13).
  guardadoEnCola,

  /// La sesión venció y no se pudo renovar: al volver a iniciar sesión, el reporte se envía solo (RN-01.16).
  esperandoSesion,
}

/// Controlador de reporte (CU-01): lo que el conductor eligió, la ubicación y el envío al backend.
class ReporteController extends ChangeNotifier {
  static const String mensajeEnviado = 'Gracias, tu reporte se envió.';
  static const String mensajeSinUbicacion =
      'No pudimos obtener tu ubicación. Toca el mapa donde ocurrió el evento y confirma.';
  static const String mensajeEsperandoSesion =
      'Tu sesión venció. Inicia sesión y tu reporte se enviará solo.';

  final ServicioEventos _servicio;
  final SesionController _sesion;
  final MapController _mapa;
  final ColaController? _cola;
  final DateTime Function() _ahora;

  EstadoReporte _estado = EstadoReporte.eligiendo;
  String? _tipo;
  int? _minutos;
  Coordenada? _puntoManual;
  String? _mensaje;
  EventoVial? _ultimoEnviado;
  ReporteEvento? _pendienteTrasSesion;
  bool _abierto = false;
  bool _disposed = false;

  ReporteController(
    this._servicio,
    this._sesion,
    this._mapa, {
    this._cola,
    DateTime Function()? reloj,
  }) : _ahora = reloj ?? DateTime.now {
    _sesion.addListener(_alCambiarSesion);
  }

  EstadoReporte get estado => _estado;
  String? get tipo => _tipo;
  Coordenada? get puntoManual => _puntoManual;
  String? get mensaje => _mensaje;
  EventoVial? get ultimoEnviado => _ultimoEnviado;
  bool get enviando => _estado == EstadoReporte.enviando;

  /// La vista Reportar evento está abierta. Si la sesión vence mientras el conductor la llena, la vista no se
  /// cierra: al confirmar, el reporte queda guardado y se envía al volver a iniciar sesión (RN-01.16).
  bool get abierto => _abierto;

  /// Los tipos reportables del catálogo guardado; la congestión no aparece (RN-01.3).
  List<TipoEventoCatalogo> get tipos => _sesion.catalogos?.reportables ?? const [];

  /// Los tiempos del catálogo: ahora mismo, hace 5 (marcado), hace 10 y hace 30 minutos (RN-01.4, D-09).
  List<TiempoReporte> get tiempos => _sesion.catalogos?.tiemposReporte ?? const [];

  int get minutos => _minutos ?? _sesion.catalogos?.tiempoPorDefecto?.minutos ?? 5;

  /// Se puede confirmar con un tipo elegido y sin un envío en curso.
  bool get puedeConfirmar => _tipo != null && !enviando;

  /// Empieza un reporte nuevo: «Hace 5 minutos» marcado y nada elegido.
  void empezar() {
    _abierto = true;
    if (_estado == EstadoReporte.esperandoSesion) return; // el anterior todavía espera la sesión
    _estado = EstadoReporte.eligiendo;
    _tipo = null;
    _minutos = null;
    _puntoManual = null;
    _mensaje = null;
    _avisar();
  }

  /// Se cerró la vista Reportar evento.
  void cerrar() => _abierto = false;

  void elegirTipo(String codigo) {
    _tipo = codigo;
    _avisar();
  }

  void elegirTiempo(int minutos) {
    _minutos = minutos;
    _avisar();
  }

  /// El conductor tocó el mapa: ese punto reemplaza al del GPS (RN-01.5).
  void usarPunto(Coordenada punto) {
    _puntoManual = punto;
    _avisar();
  }

  /// Toma la ubicación del GPS, o la que marcó el conductor si el GPS no respondió, y envía el reporte.
  Future<void> confirmar() async {
    final tipo = _tipo;
    if (tipo == null || enviando) return;
    final ubicacion = _puntoManual ?? _mapa.posicion;
    if (ubicacion == null) {
      _estado = EstadoReporte.necesitaPunto;
      _mensaje = mensajeSinUbicacion;
      _avisar();
      return;
    }
    await _enviar(ReporteEvento(
      tipo: tipo,
      ubicacion: ubicacion,
      horaConfirmacion: _ahora(),
      minutosElegidos: minutos,
    ));
  }

  /// Envía un reporte ya armado. Lo usan la confirmación y el reintento tras volver a iniciar sesión.
  Future<void> _enviar(ReporteEvento reporte) async {
    _estado = EstadoReporte.enviando;
    _mensaje = null;
    _avisar();
    try {
      _ultimoEnviado = await _servicio.reportar(reporte);
      _pendienteTrasSesion = null;
      _estado = EstadoReporte.enviado;
      _mensaje = mensajeEnviado;
    } on ApiException catch (e) {
      await _alFallarEnvio(reporte, e);
    }
    _avisar();
  }

  /// Decide qué hacer cuando el envío falla.
  Future<void> _alFallarEnvio(ReporteEvento reporte, ApiException error) async {
    if (error.codigo == 'SESION_VENCIDA' && _cola != null) {
      if (await _guardarEnCola(reporte)) {
        _estado = EstadoReporte.esperandoSesion;
        _mensaje = mensajeEsperandoSesion;
      }
    } else if (error.codigo == 'SESION_VENCIDA') {
      _pendienteTrasSesion = reporte; // RN-01.16: se conserva y se envía al volver a iniciar sesión
      _estado = EstadoReporte.esperandoSesion;
      _mensaje = mensajeEsperandoSesion;
    } else if (esErrorTemporal(error) && _cola != null) {
      if (await _guardarEnCola(reporte)) {
        _estado = EstadoReporte.guardadoEnCola;
        _mensaje = ColaController.mensajeGuardado;
      }
    } else if (esErrorTemporal(error)) {
      _estado = EstadoReporte.sinRed;
      _mensaje = error.mensaje;
    } else {
      _estado = EstadoReporte.rechazado;
      _mensaje = error.mensaje;
    }
  }

  /// Un servidor que responde 5xx también es indisponibilidad temporal (CP-83).
  static bool esErrorTemporal(ApiException e) =>
      e.codigo == 'ERROR_RED' ||
      e.codigo == 'TIEMPO_ESPERA' ||
      (e.statusCode != null && e.statusCode! >= 500);

  /// Si SQLite falla, no se cierra la pantalla ni se afirma que el reporte quedó guardado.
  Future<bool> _guardarEnCola(ReporteEvento reporte) async {
    try {
      await _cola!.guardar(reporte);
      return true;
    } on Object catch (e) {
      debugPrint('No se pudo guardar el reporte pendiente: $e');
      _estado = EstadoReporte.sinRed;
      _mensaje = 'No se pudo guardar el reporte en el teléfono. '
          'Mantén esta pantalla abierta e inténtalo de nuevo.';
      return false;
    }
  }

  void _alCambiarSesion() {
    if (!_sesion.conSesion || _estado != EstadoReporte.esperandoSesion) return;
    final pendiente = _pendienteTrasSesion;
    if (pendiente != null) {
      _enviar(pendiente);
    } else {
      // El reporte quedó en la cola y la cola lo envía al volver la sesión: se puede empezar otro.
      _estado = EstadoReporte.eligiendo;
      _mensaje = null;
    }
  }

  void _avisar() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _sesion.removeListener(_alCambiarSesion);
    super.dispose();
  }
}
