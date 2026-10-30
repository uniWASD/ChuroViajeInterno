import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';

import 'controllers/map_controller.dart';
import 'controllers/navigation_controller.dart';
import 'services/api/api_client.dart';

List<SingleChildWidget> createAppProviders({ApiClient? apiClient}) => [
  ChangeNotifierProvider(create: (_) => NavigationController()),
  Provider<ApiClient>(
    create: (_) => apiClient ?? ApiClient(),
    dispose: (_, client) => client.dispose(),
  ),
  ChangeNotifierProvider<MapController>(
    create: (context) => MapController(context.read<ApiClient>()),
  ),
];
