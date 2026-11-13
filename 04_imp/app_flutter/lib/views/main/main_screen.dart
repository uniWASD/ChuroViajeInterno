import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

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
          body: IndexedStack(
            index: controller.selectedIndex,
            children: _screens,
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
