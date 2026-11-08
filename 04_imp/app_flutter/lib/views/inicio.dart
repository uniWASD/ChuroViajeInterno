import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../controllers/sesion_controller.dart';
import 'main/main_screen.dart';
import 'sesion/inicio_sesion_screen.dart';

/// Sin sesión solo se ve Inicio de sesión; con sesión, el mapa (RN-08.2).
class Inicio extends StatelessWidget {
  const Inicio({super.key});

  @override
  Widget build(BuildContext context) {
    final estado = context.select<SesionController, EstadoSesion>((s) => s.estado);
    if (estado == EstadoSesion.sinSesion) {
      // Si la sesión se perdió con otra vista abierta encima (por ejemplo, Reportar evento), se cierra.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (context.mounted) Navigator.of(context).popUntil((ruta) => ruta.isFirst);
      });
    }
    return switch (estado) {
      EstadoSesion.cargando => const CargandoSesion(),
      EstadoSesion.sinSesion => const InicioSesionScreen(),
      EstadoSesion.conSesion => const MainScreen(),
    };
  }
}
