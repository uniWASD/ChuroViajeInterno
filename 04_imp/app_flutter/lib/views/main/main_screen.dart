import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/cola_controller.dart';
import '../../controllers/navigation_controller.dart';
import '../../controllers/sesion_controller.dart';
import '../map/map_screen.dart';
import '../events/events_screen.dart';
import '../stations/stations_screen.dart';

class MainScreen extends StatelessWidget {
  const MainScreen({super.key});

  static const List<Widget> _screens = [
    MapScreen(),
    EventsScreen(),
    StationsScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Consumer<NavigationController>(
      builder: (context, controller, child) {
        return Scaffold(
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
          bottomNavigationBar: NavigationBar(
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
    for (var aviso = _cola!.tomarAviso(); aviso != null; aviso = _cola!.tomarAviso()) {
      mensajero.showSnackBar(SnackBar(content: Text(aviso)));
    }
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
