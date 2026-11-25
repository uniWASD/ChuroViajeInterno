import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../tema/tema.dart';

/// Los marcadores del mapa con el ícono de cada tipo de evento (#172), como en el diseño: una gota del color del
/// tipo (RN-02.7) con el ícono en blanco. Los confirmados llevan un borde oscuro grueso (RN-02.14).
///
/// Se dibujan una sola vez por tipo, color y estado, y se guardan: el mapa los vuelve a usar en cada actualización.
class Marcadores {
  /// Tamaño de la imagen, en píxeles lógicos (44 × 56 en el diseño).
  static const Size tamano = Size(44, 56);

  /// Píxeles de la imagen por píxel lógico: la densidad de la pantalla, para que el marcador se vea nítido.
  double escala;
  final Map<String, Uint8List> _hechos = {};

  Marcadores({this.escala = 3});

  /// Si cambia la densidad de la pantalla, se dibujan de nuevo.
  void usarEscala(double nueva) {
    if (nueva == escala) return;
    escala = nueva;
    _hechos.clear();
  }

  /// El nombre con que cada dibujo queda registrado en el estilo del mapa: el mismo dibujo (y la misma densidad), el
  /// mismo nombre. Así el mapa guarda una sola imagen por dibujo y los marcadores solo la nombran, en vez de mandar
  /// cada uno su propia copia.
  String idSurtidor(Color color) => _id(_claveSurtidor(color));
  String idPin(Color color) => _id(_clavePin(color));
  String idEvento({required String tipo, required Color color, required bool confirmado}) =>
      _id(_claveEvento(tipo, color, confirmado));

  String _id(String clave) => 'churo-$clave@$escala';
  static String _claveSurtidor(Color color) => 'surtidor|${color.toARGB32()}';
  static String _clavePin(Color color) => 'pin|${color.toARGB32()}';
  static String _claveEvento(String tipo, Color color, bool confirmado) => '$tipo|${color.toARGB32()}|$confirmado';

  /// CU-05: el marcador de un surtidor, con su ícono, del color de su estado.
  Future<Uint8List> deSurtidor(Color color) async {
    final clave = _claveSurtidor(color);
    final hecho = _hechos[clave];
    if (hecho != null) return hecho;
    final png = await dibujarMarcador(icono: Icons.local_gas_station, color: color, confirmado: false, escala: escala);
    _hechos[clave] = png;
    return png;
  }

  /// #191: el pin rojo del lugar elegido, como en el diseño: la gota con un punto blanco.
  Future<Uint8List> dePin(Color color) async {
    final clave = _clavePin(color);
    final hecho = _hechos[clave];
    if (hecho != null) return hecho;
    final png = await dibujarMarcador(
      icono: Icons.circle,
      color: color,
      confirmado: false,
      escala: escala,
      tamanoIcono: 13,
    );
    _hechos[clave] = png;
    return png;
  }

  Future<Uint8List> de({required String tipo, required Color color, required bool confirmado}) async {
    final clave = _claveEvento(tipo, color, confirmado);
    final hecho = _hechos[clave];
    if (hecho != null) return hecho;
    final png = await dibujarMarcador(icono: iconoDeEvento(tipo), color: color, confirmado: confirmado, escala: escala);
    _hechos[clave] = png;
    return png;
  }
}

/// La gota con el ícono, como PNG. Es una función aparte para poder probarla sin el SDK de Mapbox.
Future<Uint8List> dibujarMarcador({
  required IconData icono,
  required Color color,
  required bool confirmado,
  double escala = 3,
  double tamanoIcono = 22,
}) async {
  final grabadora = ui.PictureRecorder();
  final lienzo = Canvas(grabadora)..scale(escala);
  final w = Marcadores.tamano.width, h = Marcadores.tamano.height;
  final r = w / 2 - 3;
  final centro = Offset(w / 2, r + 3);

  // La gota: un círculo con una punta hacia abajo.
  final gota = Path()
    ..addOval(Rect.fromCircle(center: centro, radius: r))
    ..moveTo(centro.dx - r * 0.62, centro.dy + r * 0.78)
    ..lineTo(w / 2, h - 2)
    ..lineTo(centro.dx + r * 0.62, centro.dy + r * 0.78)
    ..close();
  lienzo.drawShadow(gota, Colors.black, 2, false);
  lienzo.drawPath(gota, Paint()..color = color);
  lienzo.drawPath(
    gota,
    Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = confirmado ? 3.5 : 2
      ..color = confirmado ? const Color(0xFF1B1B1B) : Colors.white,
  );

  // El ícono en blanco, con la fuente de íconos de Material.
  final texto = TextPainter(
    text: TextSpan(
      text: String.fromCharCode(icono.codePoint),
      style: TextStyle(fontSize: tamanoIcono, fontFamily: icono.fontFamily, package: icono.fontPackage, color: Colors.white),
    ),
    textDirection: TextDirection.ltr,
  )..layout();
  texto.paint(lienzo, centro - Offset(texto.width / 2, texto.height / 2));

  final imagen = await grabadora.endRecording().toImage((w * escala).round(), (h * escala).round());
  final datos = await imagen.toByteData(format: ui.ImageByteFormat.png);
  imagen.dispose();
  return datos!.buffer.asUint8List();
}
