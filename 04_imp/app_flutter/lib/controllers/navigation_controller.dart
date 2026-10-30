import 'package:flutter/foundation.dart';

enum AppTab { map, events, stations }

class NavigationController extends ChangeNotifier {
  int _selectedIndex = 0;

  int get selectedIndex => _selectedIndex;

  void selectTab(int index) {
    if (index < 0 || index >= AppTab.values.length || index == _selectedIndex) {
      return;
    }

    _selectedIndex = index;
    notifyListeners();
  }
}
