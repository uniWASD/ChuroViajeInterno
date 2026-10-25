import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:provider/provider.dart';

import 'app_providers.dart';
import 'config/app_config.dart';
import 'views/main/main_screen.dart';

void main() {
  AppConfig.validar();

  runApp(
    MultiProvider(
      providers: createAppProviders(),
      child: const ChuroViajeApp(),
    ),
  );
}

class ChuroViajeApp extends StatelessWidget {
  const ChuroViajeApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      locale: const Locale('es'),
      supportedLocales: const [Locale('es')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      title: 'ChuroViaje',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF087E8B)),
        useMaterial3: true,
      ),
      home: const MainScreen(),
    );
  }
}
