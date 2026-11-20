import 'package:flutter/material.dart';

import '../../controllers/eventos_controller.dart';
import '../../models/evento_vial.dart';
import '../../services/eventos/servicio_eventos.dart';
import '../tema/tema.dart';

/// El detalle de un evento (#172, diseño «07 · Detalle de evento»): su tipo, hace cuánto ocurrió, los botones para
/// votar si es real o falso (HU-11) y cuántos conductores votaron cada cosa (D-46). Se abre al tocar un evento en la
/// lista o en el mapa. Las congestiones que detecta el tráfico no se votan (D-20).
class DetalleEvento extends StatelessWidget {
  final EventoVial evento;
  final EventosController eventos;

  /// En la pestaña Eventos lleva la flecha para volver a la lista; en el Mapa, la X para cerrar.
  final bool conVolver;

  const DetalleEvento({
    super.key,
    required this.evento,
    required this.eventos,
    this.conVolver = false,
  });

  @override
  Widget build(BuildContext context) {
    final tipo = eventos.catalogos?.tipo(evento.tipo);
    return Column(
      key: const Key('ficha-evento'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            if (conVolver) ...[
              _BotonVolver(alTocar: eventos.cerrarFicha),
              const SizedBox(width: 8),
            ],
            Icon(
              iconoDeEvento(evento.tipo),
              color: tipo?.colorFlutter ?? Colors.grey,
              size: 24,
            ),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                tipo?.nombre ?? evento.tipo,
                style: const TextStyle(fontSize: 20),
              ),
            ),
            Text(
              haceCuantoCorto(evento, DateTime.now()),
              style: const TextStyle(
                fontSize: 13,
                color: ColoresChuro.textoSecundario,
              ),
            ),
            if (!conVolver) ...[
              const SizedBox(width: 8),
              BotonCerrarHoja(alTocar: eventos.cerrarFicha),
            ],
          ],
        ),
        const SizedBox(height: 10),
        Container(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 10),
          decoration: BoxDecoration(
            color: Colors.white,
            border: Border.all(color: const Color(0xFFD6D6D6)),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(_descripcion(evento), style: const TextStyle(fontSize: 16)),
              const SizedBox(height: 12),
              if (evento.votable)
                _Votos(evento: evento, eventos: eventos)
              else
                const Text(
                  'La congestión que detecta el tráfico no se vota.',
                  style: TextStyle(
                    fontSize: 13,
                    color: ColoresChuro.textoSecundario,
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }

  static String _descripcion(EventoVial e) => [
    e.haceCuanto(DateTime.now()),
    if (e.nivel != null) nombreNivel(e.nivel!),
    e.votable ? 'Reportado por un conductor' : 'Detectado por el tráfico',
    if (e.confirmado) 'Confirmado por otros conductores',
  ].join(' · ');
}

/// «Muy congestionado» o «Congestionado».
String nombreNivel(String nivel) => switch (nivel) {
  'MUY_CONGESTIONADO' => 'Muy congestionado',
  'CONGESTIONADO' => 'Congestionado',
  _ => nivel,
};

/// Hace cuánto, corto, como en la lista del diseño: «ahora», «10 min», «2 h».
String haceCuantoCorto(EventoVial e, DateTime ahora) {
  final minutos = ahora.difference(e.momentoOcurrencia).inMinutes;
  if (minutos < 1) return 'ahora';
  if (minutos < 60) return '$minutos min';
  return '${minutos ~/ 60} h';
}

/// La marca de verificación de un evento: verde si otros conductores lo confirmaron (3 votos «real», RN-02.14) y gris
/// si todavía no.
class MarcaVerificado extends StatelessWidget {
  final bool confirmado;
  final double tamano;

  const MarcaVerificado({
    super.key,
    required this.confirmado,
    this.tamano = 24,
  });

  static const Color gris = Color(0xFF7A7A7A);

  @override
  Widget build(BuildContext context) => Icon(
    Icons.verified,
    size: tamano,
    color: confirmado ? ColoresChuro.verde : gris,
    semanticLabel: confirmado ? 'Confirmado' : 'Sin confirmar',
  );
}

/// Los botones «Real» y «Falso», y al lado cuántos votaron cada cosa sobre los 3 que confirman o retiran el evento.
class _Votos extends StatelessWidget {
  final EventoVial evento;
  final EventosController eventos;

  const _Votos({required this.evento, required this.eventos});

  @override
  Widget build(BuildContext context) {
    final puede = eventos.puedeVotar(evento);
    return Row(
      children: [
        const Spacer(),
        _BotonVoto(
          clave: Key('real-${evento.id}'),
          texto: 'Real',
          fondo: const Color(0xFFE0E0E0),
          letra: const Color(0xFF333333),
          alTocar: puede
              ? () => eventos.votar(evento.id, ValorVoto.real)
              : null,
        ),
        const SizedBox(width: 14),
        _BotonVoto(
          clave: Key('falso-${evento.id}'),
          texto: 'Falso',
          fondo: const Color(0xFFFF9393),
          letra: Colors.white,
          alTocar: puede
              ? () => eventos.votar(evento.id, ValorVoto.falso)
              : null,
        ),
        const Spacer(),
        // D-46: cuántos votaron «real» y cuántos «falso», sobre los 3 que deciden (P-01, P-11).
        MarcaVerificado(confirmado: evento.confirmado, tamano: 20),
        Text(
          '${evento.votosReal}/3',
          key: Key('votos-real-${evento.id}'),
          style: const TextStyle(
            fontSize: 13,
            color: ColoresChuro.textoSecundario,
          ),
        ),
        const SizedBox(width: 8),
        const Icon(
          Icons.cancel,
          size: 20,
          color: ColoresChuro.rojoSuave,
          semanticLabel: 'Votos falso',
        ),
        Text(
          '${evento.votosFalso}/3',
          key: Key('votos-falso-${evento.id}'),
          style: const TextStyle(
            fontSize: 13,
            color: ColoresChuro.textoSecundario,
          ),
        ),
      ],
    );
  }
}

class _BotonVoto extends StatelessWidget {
  final Key clave;
  final String texto;
  final Color fondo;
  final Color letra;
  final VoidCallback? alTocar;

  const _BotonVoto({
    required this.clave,
    required this.texto,
    required this.fondo,
    required this.letra,
    this.alTocar,
  });

  @override
  Widget build(BuildContext context) => FilledButton(
    key: clave,
    onPressed: alTocar,
    style: FilledButton.styleFrom(
      backgroundColor: fondo,
      foregroundColor: letra,
      disabledBackgroundColor: fondo.withValues(alpha: 0.45),
      disabledForegroundColor: letra.withValues(alpha: 0.7),
      minimumSize: const Size(72, 36),
      shape: const StadiumBorder(),
      textStyle: const TextStyle(fontFamily: fuenteChuro, fontSize: 16),
    ),
    child: Text(texto),
  );
}

class _BotonVolver extends StatelessWidget {
  final VoidCallback alTocar;

  const _BotonVolver({required this.alTocar});

  @override
  Widget build(BuildContext context) => IconButton(
    tooltip: 'Volver a la lista',
    onPressed: alTocar,
    style: IconButton.styleFrom(
      backgroundColor: const Color(0xFFE8E8E8),
      side: const BorderSide(color: Color(0xFFBDBDBD)),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      fixedSize: const Size(36, 32),
      minimumSize: const Size(36, 32),
      padding: EdgeInsets.zero,
    ),
    icon: const Icon(Icons.arrow_back, size: 20, color: Colors.black),
  );
}

/// El botón de cerrar del diseño: un círculo rojo suave con una X blanca.
class BotonCerrarHoja extends StatelessWidget {
  final VoidCallback alTocar;
  final String tooltip;

  const BotonCerrarHoja({
    super.key,
    required this.alTocar,
    this.tooltip = 'Cerrar',
  });

  @override
  Widget build(BuildContext context) {
    return IconButton.filled(
      tooltip: tooltip,
      style: IconButton.styleFrom(
        backgroundColor: ColoresChuro.rojoSuave,
        foregroundColor: Colors.white,
        minimumSize: const Size(29, 29),
        fixedSize: const Size(32, 32),
        padding: EdgeInsets.zero,
      ),
      icon: const Icon(Icons.close, size: 18),
      onPressed: alTocar,
    );
  }
}
