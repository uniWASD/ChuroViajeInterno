import 'package:flutter/material.dart';

import '../../models/surtidor.dart';
import '../tema/tema.dart';

/// Cómo se ve el estado de un surtidor y el origen de sus datos (CU-05, Figma «08 · Surtidores» y «12 · Detalle de
/// surtidor»): una barra de color por estado y una etiqueta por origen, que se distinguen a simple vista (RNF-07).
abstract final class ColoresSurtidor {
  static const Color disponible = ColoresChuro.verde;
  static const Color agotado = ColoresChuro.rojo;

  /// Algunos combustibles hay y otros no: la barra amarilla del diseño.
  static const Color mixto = Color(0xFFF5C518);
  static const Color sinInformacion = Color(0xFFB0B0B0);

  static const Color whatsapp = Color(0xFF1FA855);
  static const Color conductores = ColoresChuro.azul;
  static const Color anh = Color(0xFF7E57C2);
}

Color colorDeEstado(EstadoGeneral estado) => switch (estado) {
  EstadoGeneral.disponible => ColoresSurtidor.disponible,
  EstadoGeneral.mixto => ColoresSurtidor.mixto,
  EstadoGeneral.agotado => ColoresSurtidor.agotado,
  EstadoGeneral.sinInformacion => ColoresSurtidor.sinInformacion,
};

Color colorDeOrigen(OrigenDato? origen) => switch (origen) {
  OrigenDato.whatsapp => ColoresSurtidor.whatsapp,
  OrigenDato.conductores => ColoresSurtidor.conductores,
  OrigenDato.anh => ColoresSurtidor.anh,
  null => ColoresSurtidor.sinInformacion,
};

IconData iconoDeOrigen(OrigenDato? origen) => switch (origen) {
  OrigenDato.whatsapp => Icons.chat_bubble_outline,
  OrigenDato.conductores => Icons.groups_outlined,
  OrigenDato.anh => Icons.account_balance_outlined,
  null => Icons.help_outline,
};

/// «ahora», «10 min» o «2 h».
String haceCuanto(DateTime fecha, DateTime ahora) {
  final minutos = ahora.difference(fecha).inMinutes;
  if (minutos < 1) return 'ahora';
  if (minutos < 60) return '$minutos min';
  return '${minutos ~/ 60} h';
}

/// La hora del dato en el teléfono, como «16:50».
String horaDe(DateTime fecha) {
  final local = fecha.toLocal();
  return '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
}

/// La barra vertical de color del diseño.
class BarraEstado extends StatelessWidget {
  final Color color;
  final double alto;

  const BarraEstado({super.key, required this.color, this.alto = 20});

  @override
  Widget build(BuildContext context) => Container(
    width: 3.5,
    height: alto,
    decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(2)),
  );
}

/// RNF-07: el origen de un dato, con su ícono, su color y su nombre.
class EtiquetaOrigen extends StatelessWidget {
  final OrigenDato? origen;
  final bool soloIcono;

  const EtiquetaOrigen({super.key, required this.origen, this.soloIcono = false});

  @override
  Widget build(BuildContext context) {
    final color = colorDeOrigen(origen);
    final icono = Icon(iconoDeOrigen(origen), size: 16, color: color);
    final nombre = origen?.nombre ?? 'Desconocido';
    if (soloIcono) return Tooltip(message: 'Dato de $nombre', child: icono);
    return Container(
      key: Key('origen-${origen?.name ?? 'desconocido'}'),
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.10),
        border: Border.all(color: color.withValues(alpha: 0.6)),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          icono,
          const SizedBox(width: 4),
          Text(nombre, style: TextStyle(fontSize: 12, color: color, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

/// Una línea por combustible: la barra (verde si hay, roja si no), el nombre, la fila si se conoce, el origen y
/// cuándo llegó el dato. Si el origen es ANH, también el saldo en litros y el estado de ANH (RN-05.4).
class LineaCombustible extends StatelessWidget {
  final EstadoCombustible dato;
  final DateTime ahora;

  const LineaCombustible({super.key, required this.dato, required this.ahora});

  @override
  Widget build(BuildContext context) {
    final detalles = [
      dato.disponible ? 'Disponible' : 'Agotado',
      ?dato.filaParaMostrar,
      if (dato.origen == OrigenDato.anh && dato.litros != null) '${_litros(dato.litros!)} L',
      if (dato.origen == OrigenDato.anh && dato.estadoAnhParaMostrar != null) 'estado ANH ${dato.estadoAnhParaMostrar}',
    ];
    return Padding(
      key: Key('combustible-${dato.tipo}'),
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          BarraEstado(color: dato.disponible ? ColoresSurtidor.disponible : ColoresSurtidor.agotado, alto: 36),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(dato.nombreParaMostrar, style: const TextStyle(fontSize: 15)),
                Text(
                  detalles.join(' · '),
                  style: const TextStyle(fontSize: 13, color: ColoresChuro.textoSecundario),
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              EtiquetaOrigen(origen: dato.origen),
              const SizedBox(height: 2),
              Text(
                '${horaDe(dato.fecha)} · hace ${haceCuanto(dato.fecha, ahora)}',
                style: const TextStyle(fontSize: 12, color: ColoresChuro.textoSecundario),
              ),
            ],
          ),
        ],
      ),
    );
  }

  static String _litros(double litros) {
    final texto = litros == litros.roundToDouble() ? litros.toStringAsFixed(0) : litros.toStringAsFixed(1);
    return texto.replaceAll('.', ',');
  }
}
