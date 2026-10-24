import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'controllers/navigation_controller.dart';
import 'views/main/main_screen.dart';

void main() {
  runApp(
    ChangeNotifierProvider(
      create: (_) => NavigationController(),
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
