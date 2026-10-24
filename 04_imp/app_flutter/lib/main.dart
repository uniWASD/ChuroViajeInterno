import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'controllers/map_controller.dart';
import 'controllers/navigation_controller.dart';
import 'services/api/api_client.dart';
import 'views/main/main_screen.dart';

void main() {
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(
          create: (_) => NavigationController(),
        ),
        Provider<ApiClient>(
          create: (_) => ApiClient(),
          dispose: (_, apiClient) => apiClient.dispose(),
        ),
        ChangeNotifierProxyProvider<ApiClient, MapController>(
  create: (context) => MapController(
    context.read<ApiClient>(),
  ),
  update: (_, apiClient, mapController) =>
      mapController ?? MapController(apiClient),
),
      ],
      child: const ChuroViajeApp(),
    ),
  );
}

class ChuroViajeApp extends StatelessWidget {
  const ChuroViajeApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ChuroViaje',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF087E8B),
        ),
        useMaterial3: true,
      ),
      home: const MainScreen(),
    );
  }
}