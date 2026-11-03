// Fechas del contrato: ISO 8601 con zona horaria, por ejemplo 2026-10-06T15:02:00-04:00.
#pragma once

#include <cstdint>
#include <optional>
#include <string_view>

namespace churoviaje {

// Segundos desde 1970-01-01T00:00:00Z, o std::nullopt si el texto no es una fecha ISO 8601 con zona horaria
// (Z o ±HH:MM). Las fracciones de segundo se aceptan y se descartan.
std::optional<std::int64_t> segundos_desde_iso(std::string_view texto);

// Segundos desde 1970 según el reloj del sistema.
std::int64_t ahora_en_segundos();

}  // namespace churoviaje
