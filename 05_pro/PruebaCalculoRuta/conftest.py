"""Configuración de pytest: permite que tests/ importe los módulos del prototipo."""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
