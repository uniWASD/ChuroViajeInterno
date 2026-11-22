import 'package:flutter/material.dart';

import '../../controllers/recarga_controller.dart';
import '../tema/tema.dart';

/// Vista Pregunta de recarga (HU-12), como en el diseño «05 · Mapa · ¿Recargaste combustible?»: una tarjeta debajo
/// de la barra de búsqueda que no tapa la lista de surtidores ni la navegación (RN-05.16). Elegir el tipo es un solo
/// toque y equivale a «Sí» (RN-05.9); «No» o la X no envían nada (RN-05.10).
class PreguntaRecargaTarjeta extends StatelessWidget {
  final RecargaController recarga;

  const PreguntaRecargaTarjeta({super.key, required this.recarga});

  @override
  Widget build(BuildContext context) {
    final pregunta = recarga.pregunta;
    if (pregunta == null) return const SizedBox.shrink();
    return Material(
      key: const Key('pregunta-recarga'),
      color: Colors.white,
      elevation: 3,
      shadowColor: const Color(0x55000000),
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 8, 4, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                const Icon(Icons.local_gas_station, size: 20),
                const SizedBox(width: 6),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('¿Acabas de recargar combustible?', style: TextStyle(fontSize: 15)),
                      Text(
                        pregunta.surtidor.nombre,
                        style: const TextStyle(fontSize: 13, color: ColoresChuro.textoSecundario),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  key: const Key('cerrar-pregunta-recarga'),
                  tooltip: 'Cerrar',
                  visualDensity: VisualDensity.compact,
                  icon: const Icon(Icons.close, size: 20),
                  onPressed: recarga.responderNo,
                ),
              ],
            ),
            const Text('Sí, recargué:', style: TextStyle(fontSize: 13, color: ColoresChuro.etiqueta)),
            const SizedBox(height: 4),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                for (final tipo in RecargaController.tipos)
                  ActionChip(
                    key: Key('recarga-${tipo.codigo}'),
                    label: Text(tipo.nombre),
                    visualDensity: VisualDensity.compact,
                    onPressed: () => recarga.responderSi(tipo),
                  ),
              ],
            ),
            TextButton(
              key: const Key('recarga-no'),
              style: TextButton.styleFrom(
                foregroundColor: ColoresChuro.textoSecundario,
                padding: EdgeInsets.zero,
                minimumSize: const Size(40, 32),
              ),
              onPressed: recarga.responderNo,
              child: const Text('No, no recargué'),
            ),
          ],
        ),
      ),
    );
  }
}
