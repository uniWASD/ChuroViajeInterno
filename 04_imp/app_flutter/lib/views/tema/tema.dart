import 'package:flutter/material.dart';

/// El aspecto de la app según el diseño de pantallas (#172): Figma «ChuroViaje – Borrador de pantallas» y
/// `03_des/DisenoUI/DisenoUIv1.pdf`. Solo cambia cómo se ven las pantallas que ya existen; las funciones nuevas que
/// están dibujadas quedaron fuera (lista en el issue #172).
abstract final class ColoresChuro {
  /// Botones principales: «Ir», «Cómo llegar», «Iniciar», «Enviar».
  static const Color azul = Color(0xFF3F58FF);

  /// El indicador de la pestaña activa de la barra inferior.
  static const Color azulClaro = Color(0xFFBADBF6);

  /// La barra de navegación inferior.
  static const Color grisBarra = Color(0xFFEDEDED);

  /// Bordes de tarjetas y campos.
  static const Color borde = Color(0xFFD0D0D0);

  /// El asa de las hojas inferiores.
  static const Color asa = Color(0xFFCACACA);

  /// Textos secundarios: hace cuánto, distancias, etiquetas.
  static const Color textoSecundario = Color(0xFF8A8A8A);
  static const Color etiqueta = Color(0xFF9D9D9D);

  /// Chips sin elegir y elegidos.
  static const Color chip = Color(0xFFD2D2D2);
  static const Color bordeChip = Color(0xFFC4C4C4);
  static const Color textoChip = Color(0xFF7A7A7A);
  static const Color verde = Color(0xFF00C948);
  static const Color bordeVerde = Color(0xFF00B040);

  /// El rojo suave de «Notificar», «falso» y el botón de cerrar.
  static const Color rojoSuave = Color(0xFFFF8188);
  static const Color rojo = Color(0xFFD3202F);
}

const String fuenteChuro = 'Open Sans';

ThemeData temaChuroViaje() {
  final esquema = ColorScheme.fromSeed(
    seedColor: ColoresChuro.azul,
    primary: ColoresChuro.azul,
    onPrimary: Colors.white,
    secondaryContainer: ColoresChuro.azulClaro,
    surface: Colors.white,
    error: ColoresChuro.rojo,
  );
  final base = ThemeData(colorScheme: esquema, useMaterial3: true, fontFamily: fuenteChuro);
  const forma = RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(16)));
  return base.copyWith(
    scaffoldBackgroundColor: Colors.white,
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.white,
      foregroundColor: Colors.black,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 1,
      titleTextStyle: TextStyle(fontFamily: fuenteChuro, fontSize: 20, color: Colors.black),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: ColoresChuro.grisBarra,
      surfaceTintColor: Colors.transparent,
      indicatorColor: ColoresChuro.azulClaro,
      height: 69,
      labelTextStyle: WidgetStateProperty.resolveWith(
        (estados) => TextStyle(
          fontFamily: fuenteChuro,
          fontSize: 13.5,
          color: Colors.black,
          fontWeight: estados.contains(WidgetState.selected) ? FontWeight.w700 : FontWeight.w400,
        ),
      ),
      iconTheme: const WidgetStatePropertyAll(IconThemeData(color: Colors.black, size: 28)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: ColoresChuro.azul,
        foregroundColor: Colors.white,
        shape: forma,
        textStyle: const TextStyle(fontFamily: fuenteChuro, fontSize: 16, fontWeight: FontWeight.w600),
      ),
    ),
    floatingActionButtonTheme: const FloatingActionButtonThemeData(
      backgroundColor: ColoresChuro.azul,
      foregroundColor: Colors.white,
      shape: forma,
      extendedTextStyle: TextStyle(fontFamily: fuenteChuro, fontSize: 16),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: ColoresChuro.azul,
        side: const BorderSide(color: ColoresChuro.borde),
        shape: forma,
      ),
    ),
    textButtonTheme: TextButtonThemeData(style: TextButton.styleFrom(foregroundColor: ColoresChuro.azul)),
    chipTheme: base.chipTheme.copyWith(
      backgroundColor: ColoresChuro.chip,
      selectedColor: ColoresChuro.verde,
      disabledColor: ColoresChuro.chip,
      checkmarkColor: Colors.white,
      side: WidgetStateBorderSide.resolveWith(
        (estados) => BorderSide(
          color: estados.contains(WidgetState.selected) ? ColoresChuro.bordeVerde : ColoresChuro.bordeChip,
        ),
      ),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(8))),
      labelStyle: const TextStyle(fontFamily: fuenteChuro, fontSize: 14, color: ColoresChuro.textoChip),
      secondaryLabelStyle: const TextStyle(fontFamily: fuenteChuro, fontSize: 14, color: Colors.white),
      iconTheme: const IconThemeData(size: 16),
      showCheckmark: false,
    ),
    cardTheme: const CardThemeData(
      color: Colors.white,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(12)),
        side: BorderSide(color: ColoresChuro.borde),
      ),
    ),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      showDragHandle: true,
      dragHandleColor: ColoresChuro.asa,
      dragHandleSize: Size(53, 3),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
    ),
    inputDecorationTheme: const InputDecorationTheme(
      border: OutlineInputBorder(
        borderRadius: BorderRadius.all(Radius.circular(12)),
        borderSide: BorderSide(color: ColoresChuro.borde),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.all(Radius.circular(12)),
        borderSide: BorderSide(color: ColoresChuro.borde),
      ),
      hintStyle: TextStyle(color: Color(0xFFB4B4B4)),
    ),
    snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
  );
}

/// Una hoja inferior con asa, como en el diseño: blanca, con las esquinas de arriba redondeadas y una sombra suave.
/// Se usa para lo que se abre sobre el mapa (la ficha de un evento, el punto elegido, la ruta calculada).
class HojaInferior extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;

  const HojaInferior({super.key, required this.child, this.padding = const EdgeInsets.fromLTRB(12, 0, 12, 12)});

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        boxShadow: [BoxShadow(color: Color(0x1F000000), blurRadius: 14, offset: Offset(0, -2))],
      ),
      child: Padding(
        padding: padding,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Center(child: AsaHoja()),
            child,
          ],
        ),
      ),
    );
  }
}

/// El asa gris de 53 × 3 de las hojas inferiores.
class AsaHoja extends StatelessWidget {
  const AsaHoja({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('asa-hoja'),
      margin: const EdgeInsets.symmetric(vertical: 10),
      width: 53,
      height: 3,
      decoration: const BoxDecoration(
        color: ColoresChuro.asa,
        borderRadius: BorderRadius.all(Radius.circular(2)),
      ),
    );
  }
}

/// El ícono de cada tipo de evento, para la lista, la ficha, los chips del reporte y los marcadores del mapa.
IconData iconoDeEvento(String tipo) => switch (tipo) {
  'ACCIDENTE' => Icons.warning_amber_rounded,
  'CONTROL_POLICIAL' => Icons.local_police_outlined,
  'BLOQUEO' => Icons.remove_road,
  'CONGESTION' => Icons.traffic_outlined,
  _ => Icons.report_outlined,
};
