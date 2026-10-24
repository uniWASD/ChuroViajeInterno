import 'package:flutter/foundation.dart';

import '../services/api/api_client.dart';

class MapController extends ChangeNotifier {
final ApiClient _apiClient;

bool _isLoading = false;
String? _errorMessage;
bool _isConnected = false;

MapController(this._apiClient);

bool get isLoading => _isLoading;
String? get errorMessage => _errorMessage;
bool get isConnected => _isConnected;

Future<void> checkConnection() async {
_isLoading = true;
_errorMessage = null;
notifyListeners();

try {
  await _apiClient.get('/');
  _isConnected = true;
} on ApiException catch (error) {
  _isConnected = false;
  _errorMessage = error.mensaje;
} catch (_) {
  _isConnected = false;
  _errorMessage =
      'No se pudo conectar con el servidor. Verifica tu conexión.';
} finally {
  _isLoading = false;
  notifyListeners();
}

}
}