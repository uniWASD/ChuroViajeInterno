import 'package:flutter/material.dart';

import '../../controllers/eventos_controller.dart';

/// RN-02.9: sin conexión, el mapa y la lista muestran los últimos eventos guardados con esta marca, que avisa que
/// pueden estar desactualizados. Desaparece cuando una consulta vuelve a funcionar.
class AvisoDesactualizados extends StatelessWidget {
  final EventosController eventos;

  const AvisoDesactualizados({super.key, required this.eventos});

  static const Color fondo = Color(0xFFFFF4D6);
  static const Color borde = Color(0xFFF5C518);

  @override
  Widget build(BuildContext context) {
    final guardados = eventos.guardadosEn?.toLocal();
    final hora = guardados == null
        ? null
        : '${guardados.hour.toString().padLeft(2, '0')}:${guardados.minute.toString().padLeft(2, '0')}';
    return Card(
      key: const Key('aviso-desactualizados'),
      color: fondo,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: borde),
      ),
      child: ListTile(
        dense: true,
        leading: const Icon(Icons.cloud_off_outlined),
        title: const Text('Sin conexión: eventos guardados'),
        subtitle: Text(
          hora == null
              ? 'Pueden estar desactualizados.'
              : 'Guardados a las $hora. Pueden estar desactualizados.',
        ),
      ),
    );
  }
}
