import 'package:google_sign_in/google_sign_in.dart';

import '../../config/app_config.dart';

/// El conductor cerró el selector de cuentas o no dio los permisos (RN-08.8): no es un error.
class InicioCancelado implements Exception {
  const InicioCancelado();
}

/// Pide a Google el token de identidad del conductor. La app nunca ve su contraseña (D-34).
abstract class ProveedorGoogle {
  /// Abre el selector de cuentas y devuelve el token de identidad, o lanza [InicioCancelado].
  Future<String> pedirTokenDeIdentidad();

  /// Olvida la cuenta elegida, para que el próximo inicio vuelva a mostrar el selector.
  Future<void> cerrar();
}

/// El selector de cuentas de Google con `google_sign_in`. El token de identidad sale para el Client ID web del
/// proyecto (`GOOGLE_SERVER_CLIENT_ID`), que es el que valida el backend.
class ProveedorGoogleSignIn implements ProveedorGoogle {
  Future<void>? _inicializado;

  Future<void> _inicializar() => _inicializado ??= GoogleSignIn.instance
      .initialize(
        serverClientId: AppConfig.googleServerClientId.isEmpty
            ? null
            : AppConfig.googleServerClientId,
      );

  @override
  Future<String> pedirTokenDeIdentidad() async {
    await _inicializar();
    try {
      final cuenta = await GoogleSignIn.instance.authenticate();
      final token = cuenta.authentication.idToken;
      if (token == null || token.isEmpty) {
        throw const InicioCancelado();
      }
      return token;
    } on GoogleSignInException catch (e) {
      if (e.code == GoogleSignInExceptionCode.canceled ||
          e.code == GoogleSignInExceptionCode.interrupted) {
        throw const InicioCancelado();
      }
      rethrow;
    }
  }

  @override
  Future<void> cerrar() async {
    await _inicializar();
    await GoogleSignIn.instance.signOut();
  }
}
