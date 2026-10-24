import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/navigation_controller.dart';
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
