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
      await _apiClient.get('/catalogos');
      _isConnected = true;
    } on ApiException catch (error) {
      // Una respuesta HTTP confirma que el servidor es accesible,
      // incluso si la petición requiere autenticación.
      if (error.statusCode != null) {
        _isConnected = true;
        _errorMessage = error.mensaje;
      } else {
        _isConnected = false;
        _errorMessage = error.mensaje;
      }
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
}
