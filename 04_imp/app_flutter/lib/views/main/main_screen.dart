import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../controllers/busqueda_controller.dart';
import '../../controllers/cola_controller.dart';
import '../../controllers/eventos_controller.dart';
import '../../controllers/map_controller.dart';
import '../../controllers/navigation_controller.dart';
import '../busqueda/barra_busqueda.dart';
import '../map/map_screen.dart';
import '../events/events_screen.dart';
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

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final eventos = context.read<EventosController>();
    if (!identical(eventos, _eventos)) {
      _eventos?.removeListener(_mostrarMensajes);
      _eventos = eventos..addListener(_mostrarMensajes);
    }
  }

  @override
  void dispose() {
    _ventana?.cancel();
    _eventos?.removeListener(_mostrarMensajes);
    super.dispose();
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

  /// Si en el Mapa hay algo abierto que el gesto debe cerrar primero.
  bool _hayAlgoAbierto(
    EventosController eventos,
    MapController mapa,
    BusquedaController busqueda,
  ) =>
      busqueda.abierta ||
      eventos.seleccionado != null ||
      mapa.puntoSeleccionado != null;

  void _alVolver(bool salio) {
    if (salio) return;
    final pestanas = context.read<NavigationController>();
    final eventos = context.read<EventosController>();
    final mapa = context.read<MapController>();
    final busqueda = context.read<BusquedaController>();
    // 1. La lista de la búsqueda (y el teclado) se cierra.
    if (busqueda.abierta) {
      busqueda.cerrar();
      return;
    }
    // 2. El detalle de un evento (en el Mapa o en Eventos) o un punto abierto se cierra.
    if (pestanas.selectedIndex != AppTab.stations.index &&
        eventos.seleccionado != null) {
      eventos.cerrarFicha();
      return;
    }
    if (pestanas.selectedIndex == AppTab.map.index &&
        mapa.puntoSeleccionado != null) {
      mapa.limpiarPunto();
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
    final eventos = context.watch<EventosController>();
    final mapa = context.watch<MapController>();
    final busqueda = context.watch<BusquedaController>();
    return Consumer<NavigationController>(
      builder: (context, controller, child) {
        final enElMapa = controller.selectedIndex == AppTab.map.index;
        return PopScope(
          // Solo se deja salir en el Mapa, sin nada abierto y dentro de la ventana: así el sistema muestra la
          // animación del gesto predictivo hacia el inicio solo cuando de verdad se va a salir.
          canPop:
              _puedeSalir &&
              enElMapa &&
              !_hayAlgoAbierto(eventos, mapa, busqueda),
          onPopInvokedWithResult: (salio, _) => _alVolver(salio),
          // Sin encabezado, el mapa llega hasta arriba: los íconos de la barra de estado van oscuros.
          child: AnnotatedRegion<SystemUiOverlayStyle>(
            value: SystemUiOverlayStyle.dark.copyWith(
              statusBarColor: Colors.transparent,
            ),
            child: Scaffold(
              // #172: el mapa queda siempre detrás; Eventos es una hoja sobre él y Surtidores lo tapa. La barra de
              // búsqueda (D-47) flota arriba en las tres pestañas.
              body: _AvisosCola(
                child: Stack(
                  children: [
                    const Positioned.fill(child: MapScreen()),
                    if (controller.selectedIndex == AppTab.events.index)
                      const EventsScreen(),
                    if (controller.selectedIndex == AppTab.stations.index)
                      Positioned.fill(
                        child: ColoredBox(
                          color: Colors.white,
                          child: Padding(
                            padding: EdgeInsets.only(
                              top:
                                  MediaQuery.paddingOf(context).top +
                                  BarraBusqueda.espacio,
                            ),
                            child: MediaQuery.removePadding(
                              context: context,
                              removeTop: true,
                              child: const StationsScreen(),
                            ),
                          ),
                        ),
                      ),
                    const Positioned(
                      top: 0,
                      left: 0,
                      right: 0,
                      child: SafeArea(
                        bottom: false,
                        child: Padding(
                          padding: EdgeInsets.fromLTRB(12, 8, 12, 0),
                          child: BarraBusqueda(),
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
