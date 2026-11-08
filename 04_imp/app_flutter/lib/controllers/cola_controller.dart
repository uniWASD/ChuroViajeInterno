import 'dart:async';

import 'package:flutter/foundation.dart';

import '../models/catalogos.dart';
import '../models/reporte_evento.dart';
import '../services/api/api_client.dart';
import '../services/conectividad/conectividad.dart';
import '../services/eventos/servicio_eventos.dart';
import '../storage/cola_reportes.dart';
import 'sesion_controller.dart';

/// Cola de reportes sin conexión (HU-02): guarda los reportes que no se pudieron enviar y los reenvía solos cuando
/// vuelve la red, comprobando antes que sigan vigentes.
class ColaController extends ChangeNotifier {
  /// RN-01.15: después de 3 reenvíos fallidos seguidos, el reporte se muestra como pendiente.
  static const int intentosParaMostrarPendiente = 3;

  /// Además de cuando vuelve la red, se reintenta cada cierto tiempo con la app abierta.
  static const Duration reintentoPeriodico = Duration(seconds: 30);

  static const String mensajeGuardado =
      'No hay conexión. Tu reporte se guardó y se enviará solo cuando vuelva la red.';

  final ColaReportes _cola;
  final ServicioEventos _servicio;
  final SesionController _sesion;
  final DateTime Function() _ahora;
  StreamSubscription<bool>? _suscripcion;
  Timer? _temporizador;

  List<ReportePendiente> _pendientes = const [];
  final List<String> _avisos = [];
  bool _reenviando = false;
  bool _disposed = false;

  ColaController(
    this._cola,
    this._servicio,
    this._sesion, {
    DateTime Function()? reloj,
    Conectividad? conectividad,
    Duration? cadaCuanto,
  }) : _ahora = reloj ?? DateTime.now {
    _sesion.registrarLimpieza(vaciar);
    _sesion.addListener(_alCambiarSesion);
    _suscripcion = conectividad?.cambios.listen((hayRed) {
      if (hayRed) reenviar(); // RN-01.13: sin que el conductor intervenga
    });
    if (cadaCuanto != Duration.zero) {
      _temporizador = Timer.periodic(cadaCuanto ?? reintentoPeriodico, (_) => reenviar());
    }
  }

  /// Los reportes que esperan red (RN-01.15: nunca se descartan en silencio).
  List<ReportePendiente> get pendientes => _pendientes;

  /// Los que ya fallaron varias veces seguidas: la app los muestra como pendientes.
  List<ReportePendiente> get atascados =>
      [for (final r in _pendientes) if (r.intentos >= intentosParaMostrarPendiente) r];

  /// Avisos para el conductor que todavía no se mostraron (enviado, perdió vigencia, rechazado).
  List<String> get avisos => List.unmodifiable(_avisos);

  /// La vista toma el aviso más antiguo para mostrarlo.
  String? tomarAviso() => _avisos.isEmpty ? null : _avisos.removeAt(0);

  /// Lee la cola guardada (por ejemplo, al abrir la app) e intenta enviarla.
  Future<void> cargar() async {
    await _refrescar();
    await reenviar();
  }

  /// RN-01.13: guarda el reporte con su hora de confirmación y sus minutos.
  Future<void> guardar(ReporteEvento reporte) async {
    await _cola.agregar(reporte);
    await _refrescar();
  }

  /// Reenvía los reportes en el orden en que se guardaron. Se detiene en el primero que no se pudo enviar por
  /// falta de red: los demás esperan al próximo intento.
  Future<void> reenviar() async {
    if (_reenviando || _disposed || !_sesion.conSesion) return;
    _reenviando = true;
    try {
      for (final pendiente in await _cola.pendientes()) {
        if (!await _reenviarUno(pendiente)) break;
      }
    } finally {
      _reenviando = false;
    }
    await _refrescar();
  }

  /// Devuelve false si hay que dejar de intentar por ahora.
  Future<bool> _reenviarUno(ReportePendiente pendiente) async {
    final reporte = pendiente.reporte;
    final tipo = _sesion.catalogos?.tipo(reporte.tipo);
    if (tipo != null && _perdioVigencia(reporte, tipo)) {
      await _cola.quitar(pendiente.id); // RN-01.14
      _avisar('Tu reporte de ${_nombre(tipo, reporte)} no se envió porque perdió vigencia.');
      return true;
    }
    try {
      await _servicio.reportar(reporte);
      await _cola.quitar(pendiente.id);
      _avisar('Se envió tu reporte de ${_nombre(tipo, reporte)} que estaba guardado sin conexión.');
      return true;
    } on ApiException catch (e) {
      if (_reintentable(e)) {
        await _cola.sumarIntento(pendiente.id); // RN-01.15: sigue en la cola
        return false;
      }
      if (e.codigo == 'SESION_VENCIDA') {
        return false; // se reintenta cuando el conductor vuelva a iniciar sesión
      }
      // El backend lo rechazó (duplicado, fuera de Tarija, vencido): no tiene sentido reintentarlo.
      await _cola.quitar(pendiente.id);
      _avisar('Tu reporte de ${_nombre(tipo, reporte)} no se envió: ${e.mensaje}');
      return true;
    }
  }

  /// RN-01.14: con los tiempos del catálogo guardado, el evento ocurrió [minutos] antes de confirmarlo y vale lo
  /// que dice su tipo.
  bool _perdioVigencia(ReporteEvento reporte, TipoEventoCatalogo tipo) {
    final ocurrio = reporte.horaConfirmacion.subtract(Duration(minutes: reporte.minutosElegidos));
    return !_ahora().isBefore(ocurrio.add(tipo.vigencia));
  }

  /// Sin red, el servidor caído o demasiados reportes seguidos: se vuelve a intentar más tarde.
  static bool _reintentable(ApiException e) =>
      e.codigo == 'ERROR_RED' ||
      e.codigo == 'TIEMPO_ESPERA' ||
      e.codigo == 'LIMITE_DE_REPORTES' ||
      (e.statusCode != null && e.statusCode! >= 500);

  static String _nombre(TipoEventoCatalogo? tipo, ReporteEvento reporte) =>
      (tipo?.nombre ?? reporte.tipo).toLowerCase();

  Future<void> vaciar() async {
    await _cola.vaciar();
    _avisos.clear();
    await _refrescar();
  }

  Future<void> _refrescar() async {
    _pendientes = await _cola.pendientes();
    _notificar();
  }

  void _avisar(String aviso) {
    _avisos.add(aviso);
    _notificar();
  }

  void _alCambiarSesion() {
    if (_sesion.conSesion) reenviar();
  }

  void _notificar() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _suscripcion?.cancel();
    _temporizador?.cancel();
    _sesion.removeListener(_alCambiarSesion);
    super.dispose();
  }
}
