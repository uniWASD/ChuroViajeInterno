import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/reporte_controller.dart';
import '../map/mapa_base.dart';

/// Vista Reportar evento (CU-01). Desde el mapa son tres toques: «Reportar evento», el tipo y «Confirmar»
/// (RNF-06). «Hace 5 minutos» viene marcado.
class ReportarEventoScreen extends StatefulWidget {
  const ReportarEventoScreen({super.key});

  static Route<void> ruta() =>
      MaterialPageRoute(builder: (_) => const ReportarEventoScreen());

  @override
  State<ReportarEventoScreen> createState() => _ReportarEventoScreenState();
}

class _ReportarEventoScreenState extends State<ReportarEventoScreen> {
  late final ReporteController _controller;

  @override
  void initState() {
    super.initState();
    _controller = context.read<ReporteController>();
    _controller.addListener(_alCambiar);
  }

  @override
  void dispose() {
    _controller.removeListener(_alCambiar);
    super.dispose();
  }

  void _alCambiar() {
    final estado = _controller.estado;
    if ((estado == EstadoReporte.enviado || estado == EstadoReporte.guardadoEnCola) && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_controller.mensaje ?? ReporteController.mensajeEnviado)),
      );
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<ReporteController>();
    final tema = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Reportar evento')),
      // «Confirmar» queda siempre a la vista: es el tercer toque (RNF-06).
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: FilledButton.icon(
            key: const Key('confirmar-reporte'),
            onPressed: controller.puedeConfirmar ? controller.confirmar : null,
            icon: controller.enviando
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.check),
            label: const Text('Confirmar'),
          ),
        ),
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text('¿Qué pasó?', style: tema.textTheme.titleMedium),
            const SizedBox(height: 8),
            if (controller.tipos.isEmpty)
              const Text('Cargando los tipos de evento…')
            else
              GridView.count(
                crossAxisCount: 2,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                mainAxisSpacing: 8,
                crossAxisSpacing: 8,
                childAspectRatio: 2.6,
                children: [
                  for (final tipo in controller.tipos)
                    _BotonTipo(
                      nombre: tipo.nombre,
                      color: tipo.colorFlutter,
                      elegido: controller.tipo == tipo.codigo,
                      alTocar: () => controller.elegirTipo(tipo.codigo),
                    ),
                ],
              ),
            const SizedBox(height: 20),
            Text('¿Cuándo ocurrió?', style: tema.textTheme.titleMedium),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final tiempo in controller.tiempos)
                  ChoiceChip(
                    label: Text(tiempo.nombre),
                    selected: controller.minutos == tiempo.minutos,
                    onSelected: (_) => controller.elegirTiempo(tiempo.minutos),
                  ),
              ],
            ),
            if (controller.estado == EstadoReporte.necesitaPunto ||
                controller.puntoManual != null) ...[
              const SizedBox(height: 20),
              _AjustarPunto(controller: controller),
            ],
            if (controller.mensaje != null &&
                controller.estado != EstadoReporte.enviado) ...[
              const SizedBox(height: 16),
              _Mensaje(controller: controller),
            ],
          ],
        ),
      ),
    );
  }
}

class _BotonTipo extends StatelessWidget {
  final String nombre;
  final Color color;
  final bool elegido;
  final VoidCallback alTocar;

  const _BotonTipo({
    required this.nombre,
    required this.color,
    required this.elegido,
    required this.alTocar,
  });

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: alTocar,
      style: OutlinedButton.styleFrom(
        backgroundColor: elegido ? color.withValues(alpha: 0.18) : null,
        side: BorderSide(color: color, width: elegido ? 3 : 1),
      ),
      icon: Icon(Icons.circle, color: color, size: 16),
      label: Text(nombre),
    );
  }
}

/// RN-01.5: el GPS no respondió; el conductor toca el mapa donde ocurrió el evento.
class _AjustarPunto extends StatelessWidget {
  final ReporteController controller;

  const _AjustarPunto({required this.controller});

  @override
  Widget build(BuildContext context) {
    final construirMapa = context.read<ConstructorMapa>();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          controller.puntoManual == null
              ? 'Toca el mapa donde ocurrió el evento.'
              : 'Punto marcado. Puedes tocar otro lugar para cambiarlo.',
        ),
        const SizedBox(height: 8),
        SizedBox(
          height: 220,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: construirMapa(
              context,
              DatosMapa(
                puntoSeleccionado: controller.puntoManual,
                alTocar: controller.usarPunto,
                alFallar: (detalle) => debugPrint('Mapa del reporte: $detalle'),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _Mensaje extends StatelessWidget {
  final ReporteController controller;

  const _Mensaje({required this.controller});

  @override
  Widget build(BuildContext context) {
    final esError = controller.estado == EstadoReporte.rechazado ||
        controller.estado == EstadoReporte.sinRed;
    final color = esError
        ? Theme.of(context).colorScheme.error
        : Theme.of(context).colorScheme.onSurfaceVariant;
    return Row(
      children: [
        Icon(esError ? Icons.error_outline : Icons.info_outline, color: color),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            controller.mensaje!,
            key: const Key('mensaje-reporte'),
            style: TextStyle(color: color),
          ),
        ),
      ],
    );
  }
}
