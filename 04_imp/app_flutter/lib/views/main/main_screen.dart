import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../controllers/busqueda_controller.dart';
import '../../controllers/cola_controller.dart';
import '../../controllers/combustible_controller.dart';
import '../../controllers/eventos_controller.dart';
import '../../controllers/map_controller.dart';
import '../../controllers/navigation_controller.dart';
import '../../controllers/recarga_controller.dart';
import '../../controllers/reporte_controller.dart';
import '../../controllers/ruta_controller.dart';
import '../busqueda/barra_busqueda.dart';
import '../map/map_screen.dart';
import '../events/events_screen.dart';
import '../stations/pregunta_recarga.dart';
import '../stations/stations_screen.dart';

/// Pantalla principal con sus tres pestañas y, arriba, la barra de búsqueda (D-47) en lugar del encabezado. El gesto
/// de atrás (#172) no saca de la app de golpe: primero cierra la búsqueda, la ficha o el punto que esté abierto,
/// después vuelve a la pestaña Mapa y, ya en el Mapa, pide repetirlo para salir. Usa [PopScope], así que funciona
/// con el gesto predictivo de Android 14 o superior.
class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  /// Cuánto tiempo hay para repetir el gesto y salir.
  static const Duration ventanaParaSalir = Duration(seconds: 2);
  static const String avisoSalir = 'Presiona atrás otra vez para salir';

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  /// Durante [MainScreen.ventanaParaSalir] después del primer gesto en el Mapa, el siguiente cierra la app.
  bool _puedeSalir = false;
  Timer? _ventana;
  EventosController? _eventos;
  RecargaController? _recarga;
  ReporteController? _reporte;
  NavigationController? _pestanas;
  int? _pestanaAnterior;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final pestanas = context.read<NavigationController>();
    if (!identical(pestanas, _pestanas)) {
      _pestanas?.removeListener(_alCambiarPestana);
      _pestanas = pestanas..addListener(_alCambiarPestana);
      _pestanaAnterior = pestanas.selectedIndex;
    }
    final eventos = context.read<EventosController>();
    if (!identical(eventos, _eventos)) {
      _eventos?.removeListener(_mostrarMensajes);
      _eventos = eventos..addListener(_mostrarMensajes);
    }
    final recarga = context.read<RecargaController>();
    if (!identical(recarga, _recarga)) {
      _recarga?.removeListener(_mostrarMensajesRecarga);
      _recarga = recarga..addListener(_mostrarMensajesRecarga);
    }
    final reporte = context.read<ReporteController>();
    if (!identical(reporte, _reporte)) {
      _reporte?.removeListener(_mostrarMensajesReporte);
      _reporte = reporte..addListener(_mostrarMensajesReporte);
    }
  }

  /// #191: la hoja «Notificar evento» se cierra sola al enviar; su mensaje se muestra aquí.
  void _mostrarMensajesReporte() {
    if (!mounted) return;
    final mensajero = ScaffoldMessenger.maybeOf(context);
    if (mensajero == null) return;
    for (var m = _reporte!.tomarAviso(); m != null; m = _reporte!.tomarAviso()) {
      mensajero.showSnackBar(SnackBar(content: Text(m)));
    }
  }

  /// Al cambiar de pestaña no queda nada abierto de la anterior: la búsqueda, el detalle de un evento o de un
  /// surtidor, el surtidor desplegado en la lista, «Notificar evento» y la elección de una parada. Antes quedaban
  /// abiertos sin verse y aparecían al volver (por ejemplo, el detalle de un surtidor abierto en Surtidores salía
  /// en el Mapa, o «Ir» desde Surtidores mostraba un evento en vez de la ruta). El lugar elegido y su ruta («Cómo
  /// llegar») sí se conservan, como en Google Maps.
  void _alCambiarPestana() {
    final actual = _pestanas!.selectedIndex;
    if (actual == _pestanaAnterior) return;
    _pestanaAnterior = actual;
    final busqueda = context.read<BusquedaController>();
    if (busqueda.abierta) busqueda.cerrar();
    context.read<EventosController>().cerrarFicha();
    context.read<CombustibleController>()
      ..cerrarFicha()
      ..plegar();
    final reporte = context.read<ReporteController>();
    // Si se está enviando, o se quedó sin red con lo elegido, la hoja se conserva para no perder el reporte.
    if (reporte.abierto && !reporte.enviando && reporte.estado != EstadoReporte.sinRed) reporte.cerrar();
    final ruta = context.read<RutaController>();
    if (ruta.eligiendoParada) ruta.cancelarParada();
  }

  @override
  void dispose() {
    _ventana?.cancel();
    _pestanas?.removeListener(_alCambiarPestana);
    _eventos?.removeListener(_mostrarMensajes);
    _recarga?.removeListener(_mostrarMensajesRecarga);
    _reporte?.removeListener(_mostrarMensajesReporte);
    super.dispose();
  }

  /// HU-12: si la confirmación de recarga quedó registrada, o el mensaje del backend si la rechazó.
  void _mostrarMensajesRecarga() {
    if (!mounted) return;
    final mensajero = ScaffoldMessenger.maybeOf(context);
    if (mensajero == null) return;
    for (var m = _recarga!.tomarMensaje(); m != null; m = _recarga!.tomarMensaje()) {
      mensajero.showSnackBar(SnackBar(content: Text(m)));
    }
  }

  /// RN-02.11 y RN-02.12: el resultado del voto, o el mensaje del backend si lo rechazó. Se vota desde el detalle,
  /// en el Mapa o en Eventos, así que los mensajes se muestran aquí.
  void _mostrarMensajes() {
    if (!mounted) return;
    final mensajero = ScaffoldMessenger.maybeOf(context);
    if (mensajero == null) return;
    for (
      var m = _eventos!.tomarMensaje();
      m != null;
      m = _eventos!.tomarMensaje()
    ) {
      mensajero.showSnackBar(SnackBar(content: Text(m)));
    }
  }

  void _alVolver(bool salio) {
    if (salio) return;
    final pestanas = context.read<NavigationController>();
    final eventos = context.read<EventosController>();
    final mapa = context.read<MapController>();
    final busqueda = context.read<BusquedaController>();
    final combustible = context.read<CombustibleController>();
    final reporte = context.read<ReporteController>();
    final ruta = context.read<RutaController>();
    // 1. La lista de la búsqueda (y el teclado) se cierra.
    if (busqueda.abierta) {
      busqueda.cerrar();
      return;
    }
    // #191: la hoja «Notificar evento» se cierra.
    if (pestanas.selectedIndex == AppTab.map.index && reporte.abierto) {
      reporte.cerrar();
      return;
    }
    // 2. El detalle de un evento (en el Mapa o en Eventos) o un punto abierto se cierra.
    if (pestanas.selectedIndex != AppTab.stations.index &&
        eventos.seleccionado != null) {
      eventos.cerrarFicha();
      return;
    }
    // El detalle de un surtidor (en el Mapa o en Surtidores), igual.
    if (pestanas.selectedIndex != AppTab.events.index &&
        combustible.seleccionado != null) {
      combustible.cerrarFicha();
      return;
    }
    if (pestanas.selectedIndex == AppTab.map.index &&
        mapa.puntoSeleccionado != null) {
      // #191: primero se deja de elegir la parada, después se cierra la ruta y al final el lugar.
      if (ruta.eligiendoParada) {
        ruta.cancelarParada();
      } else if (ruta.mostrando && ruta.destino == mapa.puntoSeleccionado) {
        ruta.ocultar(mapa.posicion);
      } else {
        ruta.limpiar();
        mapa.limpiarPunto();
      }
      return;
    }
    // 3. Desde otra pestaña, se vuelve al Mapa.
    if (pestanas.selectedIndex != AppTab.map.index) {
      pestanas.selectTab(AppTab.map.index);
      return;
    }
    // 4. En el Mapa: el aviso, y por 2 segundos el siguiente gesto sale.
    setState(() => _puedeSalir = true);
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(
          content: Text(MainScreen.avisoSalir),
          duration: MainScreen.ventanaParaSalir,
        ),
      );
    _ventana?.cancel();
    _ventana = Timer(MainScreen.ventanaParaSalir, () {
      if (mounted) setState(() => _puedeSalir = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    // Solo lo que esta pantalla usa: así no se reconstruye entera con cada posición del GPS ni con cada consulta de
    // eventos (eso hacía que la lista de Surtidores se trabara).
    final busquedaAbierta = context.select<BusquedaController, bool>((b) => b.abierta);
    final reporteAbierto = context.select<ReporteController, bool>((r) => r.abierto);
    final eventoAbierto = context.select<EventosController, bool>((e) => e.seleccionado != null);
    final avisoEventos = context.select<EventosController, bool>((e) => e.sinActualizar || e.desactualizados);
    final surtidorAbierto = context.select<CombustibleController, bool>((c) => c.seleccionado != null);
    final puntoAbierto = context.select<MapController, bool>((m) => m.puntoSeleccionado != null);
    final pregunta = context.select<RecargaController, Object?>((r) => r.pregunta);
    final recarga = context.read<RecargaController>();
    // Si en el Mapa hay algo abierto que el gesto debe cerrar primero.
    final hayAlgoAbierto = busquedaAbierta || reporteAbierto || eventoAbierto || surtidorAbierto || puntoAbierto;
    return Consumer<NavigationController>(
      builder: (context, controller, child) {
        final enElMapa = controller.selectedIndex == AppTab.map.index;
        return PopScope(
          // Solo se deja salir en el Mapa, sin nada abierto y dentro de la ventana: así el sistema muestra la
          // animación del gesto predictivo hacia el inicio solo cuando de verdad se va a salir.
          canPop: _puedeSalir && enElMapa && !hayAlgoAbierto,
          onPopInvokedWithResult: (salio, _) => _alVolver(salio),
          // Sin encabezado, el mapa llega hasta arriba: los íconos de la barra de estado van oscuros.
          child: AnnotatedRegion<SystemUiOverlayStyle>(
            value: SystemUiOverlayStyle.dark.copyWith(
              statusBarColor: Colors.transparent,
            ),
            child: Scaffold(
              // #172: el mapa queda siempre detrás; Eventos y Surtidores (#71) son hojas sobre él. La barra de
              // búsqueda (D-47) flota arriba en las tres pestañas.
              body: _AvisosCola(
                child: Stack(
                  children: [
                    const Positioned.fill(child: MapScreen()),
                    if (controller.selectedIndex == AppTab.events.index)
                      const EventsScreen(),
                    if (controller.selectedIndex == AppTab.stations.index)
                      const StationsScreen(),
                    Positioned(
                      top: 0,
                      left: 0,
                      right: 0,
                      child: SafeArea(
                        bottom: false,
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              const BarraBusqueda(),
                              // HU-12: la pregunta va debajo de la barra y no tapa la lista ni la navegación
                              // (RN-05.16). Con la búsqueda abierta se queda esperando. Si el mapa avisa que no
                              // pudo actualizar los eventos, va debajo de ese aviso.
                              if (pregunta != null && !busquedaAbierta)
                                Padding(
                                  padding: EdgeInsets.only(top: enElMapa && avisoEventos ? 80 : 8),
                                  child: PreguntaRecargaTarjeta(recarga: recarga),
                                ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              // #172: la barra inferior del diseño, gris con las esquinas de arriba redondeadas.
              bottomNavigationBar: ClipRRect(
                borderRadius: const BorderRadius.vertical(
                  top: Radius.circular(20),
                ),
                child: NavigationBar(
                  selectedIndex: controller.selectedIndex,
                  onDestinationSelected: controller.selectTab,
                  destinations: const [
                    NavigationDestination(
                      icon: Icon(Icons.map_outlined),
                      selectedIcon: Icon(Icons.map),
                      label: 'Mapa',
                    ),
                    NavigationDestination(
                      icon: Icon(Icons.event_note_outlined),
                      selectedIcon: Icon(Icons.event_note),
                      label: 'Eventos',
                    ),
                    NavigationDestination(
                      icon: Icon(Icons.local_gas_station_outlined),
                      selectedIcon: Icon(Icons.local_gas_station),
                      label: 'Surtidores',
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

/// Muestra los avisos de la cola sin conexión: enviado, perdió vigencia o rechazado (RN-01.13 a RN-01.15).
class _AvisosCola extends StatefulWidget {
  final Widget child;

  const _AvisosCola({required this.child});

  @override
  State<_AvisosCola> createState() => _AvisosColaState();
}

class _AvisosColaState extends State<_AvisosCola> {
  ColaController? _cola;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final cola = context.read<ColaController>();
    if (!identical(cola, _cola)) {
      _cola?.removeListener(_mostrar);
      _cola = cola..addListener(_mostrar);
      WidgetsBinding.instance.addPostFrameCallback((_) => _mostrar());
    }
  }

  @override
  void dispose() {
    _cola?.removeListener(_mostrar);
    super.dispose();
  }

  void _mostrar() {
    if (!mounted) return;
    final mensajero = ScaffoldMessenger.of(context);
    for (
      var aviso = _cola!.tomarAviso();
      aviso != null;
      aviso = _cola!.tomarAviso()
    ) {
      mensajero.showSnackBar(SnackBar(content: Text(aviso)));
    }
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
