#include "tiempo.hpp"

#include <chrono>
#include <string>

namespace churoviaje {
namespace {

bool digitos(std::string_view t, std::size_t desde, std::size_t cuantos, int& valor) {
  if (desde + cuantos > t.size()) return false;
  valor = 0;
  for (std::size_t i = desde; i < desde + cuantos; ++i) {
    if (t[i] < '0' || t[i] > '9') return false;
    valor = valor * 10 + (t[i] - '0');
  }
  return true;
}

// Días desde 1970-01-01 para una fecha del calendario gregoriano (algoritmo de Howard Hinnant).
std::int64_t dias_desde_civil(std::int64_t y, unsigned m, unsigned d) {
  y -= m <= 2;
  const std::int64_t era = (y >= 0 ? y : y - 399) / 400;
  const unsigned yoe = static_cast<unsigned>(y - era * 400);
  const unsigned doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
  const unsigned doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
  return era * 146097 + static_cast<std::int64_t>(doe) - 719468;
}

bool bisiesto(int y) { return (y % 4 == 0 && y % 100 != 0) || y % 400 == 0; }

}  // namespace

std::optional<std::int64_t> segundos_desde_iso(std::string_view t) {
  int anio, mes, dia, hora, minuto, segundo;
  if (t.size() < 20 || !digitos(t, 0, 4, anio) || t[4] != '-' || !digitos(t, 5, 2, mes) || t[7] != '-' ||
      !digitos(t, 8, 2, dia) || (t[10] != 'T' && t[10] != 't') || !digitos(t, 11, 2, hora) || t[13] != ':' ||
      !digitos(t, 14, 2, minuto) || t[16] != ':' || !digitos(t, 17, 2, segundo)) {
    return std::nullopt;
  }
  static constexpr int kDiasPorMes[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
  if (mes < 1 || mes > 12 || dia < 1 || hora > 23 || minuto > 59 || segundo > 60) return std::nullopt;
  if (dia > kDiasPorMes[mes - 1] + (mes == 2 && bisiesto(anio) ? 1 : 0)) return std::nullopt;

  std::size_t i = 19;
  if (i < t.size() && t[i] == '.') {
    ++i;
    const std::size_t inicio = i;
    while (i < t.size() && t[i] >= '0' && t[i] <= '9') ++i;
    if (i == inicio) return std::nullopt;
  }
  int desfase = 0;  // segundos que la hora local va adelantada respecto de UTC
  if (i < t.size() && (t[i] == 'Z' || t[i] == 'z')) {
    ++i;
  } else if (i < t.size() && (t[i] == '+' || t[i] == '-')) {
    int hh, mm;
    if (!digitos(t, i + 1, 2, hh) || i + 3 >= t.size() || t[i + 3] != ':' || !digitos(t, i + 4, 2, mm) || hh > 23 ||
        mm > 59) {
      return std::nullopt;
    }
    desfase = (hh * 3600 + mm * 60) * (t[i] == '-' ? -1 : 1);
    i += 6;
  } else {
    return std::nullopt;  // sin zona horaria no se sabe a qué instante se refiere
  }
  if (i != t.size()) return std::nullopt;

  const std::int64_t dias = dias_desde_civil(anio, static_cast<unsigned>(mes), static_cast<unsigned>(dia));
  return dias * 86400 + hora * 3600 + minuto * 60 + segundo - desfase;
}

std::int64_t ahora_en_segundos() {
  return std::chrono::duration_cast<std::chrono::seconds>(std::chrono::system_clock::now().time_since_epoch()).count();
}

}  // namespace churoviaje
