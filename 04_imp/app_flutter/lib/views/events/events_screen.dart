import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/eventos_controller.dart';
import '../../models/evento_vial.dart';
import '../../services/eventos/servicio_eventos.dart';

/// Vista Lista de eventos (CU-02, HU-11): los eventos vigentes con su tipo y hace cuánto ocurrieron, con los mismos
/// datos que el mapa (comparten el controlador de eventos). Cada uno se vota «real» o «falso» con un toque; las
/// congestiones de la telemetría no se votan (D-20).
class EventsScreen extends StatefulWidget {
  const EventsScreen({super.key});

  @override
  State<EventsScreen> createState() => _EventsScreenState();
}

class _EventsScreenState extends State<EventsScreen> {
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
    _eventos?.removeListener(_mostrarMensajes);
    super.dispose();
  }

  /// RN-02.11 y RN-02.12: el resultado del voto, o el mensaje del backend si lo rechazó.
  void _mostrarMensajes() {
    if (!mounted) return;
    final mensajero = ScaffoldMessenger.maybeOf(context);
    if (mensajero == null) return;
    for (var m = _eventos!.tomarMensaje(); m != null; m = _eventos!.tomarMensaje()) {
      mensajero.showSnackBar(SnackBar(content: Text(m)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final eventos = context.watch<EventosController>();
    final lista = eventos.eventos..sort((a, b) => b.momentoOcurrencia.compareTo(a.momentoOcurrencia));
    return Column(
      children: [
        if (eventos.sinActualizar)
          Material(
            color: Theme.of(context).colorScheme.errorContainer,
            child: const ListTile(
              dense: true,
              leading: Icon(Icons.sync_problem),
              title: Text(EventosController.mensajeSinActualizar),
            ),
          ),
        Expanded(
          child: lista.isEmpty
              ? const _SinEventos()
              : ListView.separated(
                  key: const Key('lista-eventos'),
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  itemCount: lista.length,
                  separatorBuilder: (_, _) => const Divider(height: 1),
                  itemBuilder: (_, i) => _FilaEvento(evento: lista[i], eventos: eventos),
                ),
        ),
      ],
    );
  }
}

class _SinEventos extends StatelessWidget {
  const _SinEventos();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.event_available, size: 64),
            SizedBox(height: 12),
            Text('No hay eventos vigentes por ahora.', textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}

class _FilaEvento extends StatelessWidget {
  final EventoVial evento;
  final EventosController eventos;

  const _FilaEvento({required this.evento, required this.eventos});

  @override
  Widget build(BuildContext context) {
    final tipo = eventos.catalogos?.tipo(evento.tipo);
    final detalle = [
      evento.haceCuanto(DateTime.now()),
      if (evento.nivel != null) _nivel(evento.nivel!),
      if (!evento.votable) 'Detectado por el tráfico',
    ].join(' · ');
    final puede = eventos.puedeVotar(evento);
    return ListTile(
      key: Key('evento-${evento.id}'),
      leading: Icon(Icons.warning_amber_rounded, color: tipo?.colorFlutter ?? Colors.grey, size: 32),
      title: Row(
        children: [
          Flexible(child: Text(tipo?.nombre ?? evento.tipo)),
          if (evento.confirmado) ...[
            const SizedBox(width: 6),
            // RN-02.14: la marca de confirmado.
            const Icon(Icons.verified, size: 18, color: Colors.green, semanticLabel: 'Confirmado'),
          ],
        ],
      ),
      subtitle: Text(evento.confirmado ? '$detalle · Confirmado por otros conductores' : detalle),
      // D-20: la congestión de la telemetría aparece sin botones.
      trailing: !evento.votable
          ? null
          : Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  key: Key('real-${evento.id}'),
                  tooltip: 'Es real',
                  icon: const Icon(Icons.thumb_up_alt_outlined),
                  onPressed: puede ? () => eventos.votar(evento.id, ValorVoto.real) : null,
                ),
                IconButton(
                  key: Key('falso-${evento.id}'),
                  tooltip: 'Es falso',
                  icon: const Icon(Icons.thumb_down_alt_outlined),
                  onPressed: puede ? () => eventos.votar(evento.id, ValorVoto.falso) : null,
                ),
              ],
            ),
    );
  }

  static String _nivel(String nivel) => switch (nivel) {
    'MUY_CONGESTIONADO' => 'Muy congestionado',
    'CONGESTIONADO' => 'Congestionado',
    _ => nivel,
  };
}
