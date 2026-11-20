import 'package:flutter/material.dart';

import '../../controllers/eventos_controller.dart';
import '../../models/evento_vial.dart';
import '../../services/eventos/servicio_eventos.dart';
import '../tema/tema.dart';

/// El detalle de un evento (#172, diseño «07 · Detalle de evento»): su tipo, hace cuánto ocurrió, los botones para
/// votar si es real o falso (HU-11) y cuántos conductores votaron cada cosa (D-46). Ocupa solo lo que necesita. Se abre al tocar un evento en la
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
        const SizedBox(height: 12),
        if (evento.votable)
          _Votos(evento: evento, eventos: eventos)
        else
          // D-20: la congestión del tráfico no se vota; se dice su nivel.
          Text(
            [
              if (evento.nivel != null) nombreNivel(evento.nivel!),
              'Detectado por el tráfico',
            ].join(' · '),
            style: const TextStyle(
              fontSize: 13,
              color: ColoresChuro.textoSecundario,
            ),
          ),
      ],
    );
  }
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

/// Los botones «Real» y «Falso», centrados, y a la derecha la marca de verificación con cuántos votaron cada cosa:
/// el número de «real» en verde y el de «falso» en rojo (D-46), por ejemplo «3/2».
class _Votos extends StatelessWidget {
  final EventoVial evento;
  final EventosController eventos;

  const _Votos({required this.evento, required this.eventos});

  @override
  Widget build(BuildContext context) {
    final puede = eventos.puedeVotar(evento);
    const numero = TextStyle(fontSize: 15, fontWeight: FontWeight.w600);
    return Row(
      children: [
        const Expanded(child: SizedBox()),
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
        Expanded(
          child: Align(
            alignment: Alignment.centerRight,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                MarcaVerificado(confirmado: evento.confirmado, tamano: 20),
                const SizedBox(width: 3),
                Text(
                  '${evento.votosReal}',
                  key: Key('votos-real-${evento.id}'),
                  semanticsLabel: '${evento.votosReal} votos real',
                  style: numero.copyWith(color: colorVotoReal),
                ),
                const Text(
                  '/',
                  style: TextStyle(
                    fontSize: 15,
                    color: ColoresChuro.textoSecundario,
                  ),
                ),
                Text(
                  '${evento.votosFalso}',
                  key: Key('votos-falso-${evento.id}'),
                  semanticsLabel: '${evento.votosFalso} votos falso',
                  style: numero.copyWith(color: colorVotoFalso),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// Los colores de los números del conteo, de la paleta de la app: el verde de los chips elegidos y el rojo de los
/// marcadores, un poco más oscuros que los fondos para que se lean sobre blanco.
const Color colorVotoReal = ColoresChuro.bordeVerde;
const Color colorVotoFalso = ColoresChuro.rojo;

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
