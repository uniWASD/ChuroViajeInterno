import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/sesion_controller.dart';

/// Vista Inicio de sesión: la única disponible sin sesión (RN-08.1, RN-08.2).
class InicioSesionScreen extends StatelessWidget {
  const InicioSesionScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final sesion = context.watch<SesionController>();
    final tema = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.alt_route, size: 72, color: tema.colorScheme.primary),
                const SizedBox(height: 16),
                Text('ChuroViaje', style: tema.textTheme.headlineMedium),
                const SizedBox(height: 8),
                const Text(
                  'Rutas, eventos viales y combustible en Tarija.',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 32),
                FilledButton.icon(
                  key: const Key('boton-google'),
                  onPressed: sesion.ocupado ? null : sesion.iniciarConGoogle,
                  icon: sesion.ocupado
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.login),
                  label: const Text('Iniciar sesión con Google'),
                ),
                if (sesion.error != null) ...[
                  const SizedBox(height: 16),
                  Text(
                    sesion.error!,
                    key: const Key('error-sesion'),
                    textAlign: TextAlign.center,
                    style: TextStyle(color: tema.colorScheme.error),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Lo que se ve mientras se lee la sesión guardada al abrir la app.
class CargandoSesion extends StatelessWidget {
  const CargandoSesion({super.key});

  @override
  Widget build(BuildContext context) =>
      const Scaffold(body: Center(child: CircularProgressIndicator()));
}
