import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/cola_controller.dart';
import '../../controllers/eventos_controller.dart';
import '../../controllers/map_controller.dart';
import '../../controllers/navigation_controller.dart';
import '../../controllers/sesion_controller.dart';
import '../map/map_screen.dart';
import '../events/events_screen.dart';
import '../stations/stations_screen.dart';

/// Pantalla principal con sus tres pestañas. El gesto de atrás (#172) no saca de la app de golpe: primero cierra la
/// ficha o el punto que esté abierto en el Mapa, después vuelve a la pestaña Mapa y, ya en el Mapa, pide repetirlo
/// para salir. Usa [PopScope], así que funciona con el gesto predictivo de Android 14 o superior.
class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  /// Cuánto tiempo hay para repetir el gesto y salir.
  static const Duration ventanaParaSalir = Duration(seconds: 2);
  static const String avisoSalir = 'Presiona atrás otra vez para salir';

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  static const List<Widget> _screens = [
    MapScreen(),
    EventsScreen(),
    StationsScreen(),
  ];

  /// Durante [MainScreen.ventanaParaSalir] después del primer gesto en el Mapa, el siguiente cierra la app.
  bool _puedeSalir = false;
  Timer? _ventana;

  @override
  void dispose() {
    _ventana?.cancel();
    super.dispose();
  }

  /// Si en el Mapa hay algo abierto que el gesto debe cerrar primero.
  bool _hayAlgoAbierto(EventosController eventos, MapController mapa) =>
      eventos.seleccionado != null || mapa.puntoSeleccionado != null;

  void _alVolver(bool salio) {
    if (salio) return;
    final pestanas = context.read<NavigationController>();
    final eventos = context.read<EventosController>();
    final mapa = context.read<MapController>();
    // 1. Una ficha o un punto abierto se cierra.
    if (pestanas.selectedIndex == AppTab.map.index &&
        eventos.seleccionado != null) {
      eventos.cerrarFicha();
      return;
    }
    if (pestanas.selectedIndex == AppTab.map.index &&
        mapa.puntoSeleccionado != null) {
      mapa.limpiarPunto();
      return;
    }
    // 2. Desde otra pestaña, se vuelve al Mapa.
    if (pestanas.selectedIndex != AppTab.map.index) {
      pestanas.selectTab(AppTab.map.index);
      return;
    }
    // 3. En el Mapa: el aviso, y por 2 segundos el siguiente gesto sale.
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
    return Consumer<NavigationController>(
      builder: (context, controller, child) {
        final enElMapa = controller.selectedIndex == AppTab.map.index;
        return PopScope(
          // Solo se deja salir en el Mapa, sin nada abierto y dentro de la ventana: así el sistema muestra la
          // animación del gesto predictivo hacia el inicio solo cuando de verdad se va a salir.
          canPop: _puedeSalir && enElMapa && !_hayAlgoAbierto(eventos, mapa),
          onPopInvokedWithResult: (salio, _) => _alVolver(salio),
          child: Scaffold(
            appBar: AppBar(
              title: const Text('ChuroViaje'),
              centerTitle: true,
              actions: const [_MenuCuenta()],
            ),
            body: _AvisosCola(
              child: IndexedStack(
                index: controller.selectedIndex,
                children: _screens,
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
        );
      },
    );
  }
}

/// Menú de la cuenta del Mapa principal: «Cerrar sesión».
class _MenuCuenta extends StatelessWidget {
  const _MenuCuenta();

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<String>(
      key: const Key('menu-cuenta'),
      tooltip: 'Cuenta',
      icon: const Icon(Icons.account_circle_outlined),
      onSelected: (opcion) {
        if (opcion == 'cerrar') context.read<SesionController>().cerrarSesion();
      },
      itemBuilder: (_) => const [
        PopupMenuItem(
          value: 'cerrar',
          child: ListTile(
            leading: Icon(Icons.logout),
            title: Text('Cerrar sesión'),
            contentPadding: EdgeInsets.zero,
          ),
        ),
      ],
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
