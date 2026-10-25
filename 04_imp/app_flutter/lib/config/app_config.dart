enum AppEnvironment { development, testing, production }

class AppConfig {
  static const String _environment = String.fromEnvironment(
    'APP_ENV',
    defaultValue: 'development',
  );

  static const String _developmentUrl = String.fromEnvironment(
    'API_URL_DEV',
    defaultValue: 'http://10.0.2.2:8080',
  );

  static const String _testingUrl = String.fromEnvironment(
    'API_URL_TEST',
    defaultValue: 'http://10.0.2.2:8080',
  );

  static const String _productionUrl = String.fromEnvironment(
    'API_URL_PROD',
    defaultValue: '',
  );

  static AppEnvironment get environment {
    switch (_environment) {
      case 'testing':
        return AppEnvironment.testing;
      case 'production':
        return AppEnvironment.production;
      default:
        return AppEnvironment.development;
    }
  }

  static String get apiBaseUrl {
    switch (environment) {
      case AppEnvironment.development:
        return _developmentUrl;
      case AppEnvironment.testing:
        return _testingUrl;
      case AppEnvironment.production:
        return _productionUrl;
    }
  }

  static bool get isProduction => environment == AppEnvironment.production;
  static void validar() {
    final uri = Uri.tryParse(apiBaseUrl);

    if (apiBaseUrl.isEmpty || uri == null || !uri.hasAuthority) {
      throw StateError(
        'Falta una dirección válida del backend para el ambiente $_environment.',
      );
    }

    if (isProduction && uri.scheme != 'https') {
      throw StateError(
        'En producción la dirección del backend debe usar HTTPS.',
      );
    }
  }
}
