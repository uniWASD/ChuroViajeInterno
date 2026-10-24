import 'package:flutter/foundation.dart';

class NavigationController extends ChangeNotifier {
  int _selectedIndex = 0;

  int get selectedIndex => _selectedIndex;

  void selectTab(int index) {
    if (index < 0 || index > 2 || index == _selectedIndex) {
      return;
    }

    _selectedIndex = index;
    notifyListeners();
  }
}
