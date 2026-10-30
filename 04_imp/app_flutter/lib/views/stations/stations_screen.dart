import 'package:flutter/material.dart';

class StationsScreen extends StatelessWidget {
  const StationsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.local_gas_station_outlined, size: 72),
          SizedBox(height: 16),
          Text(
            'Surtidores',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          ),
          SizedBox(height: 8),
          Text('Aquí se mostrarán los surtidores disponibles.'),
        ],
      ),
    );
  }
}
