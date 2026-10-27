cmake_minimum_required(VERSION 3.16)
project(ChuroViajeRuteo LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Release)
endif()

if(MSVC)
  add_compile_options(/utf-8 /W4)   # /utf-8: los fuentes llevan tildes en UTF-8
else()
  add_compile_options(-Wall -Wextra)
endif()

# Motor (se reutilizará desde el motor core).
add_library(motor_ruteo STATIC red_de_calles.cpp redes_de_prueba.cpp)
target_include_directories(motor_ruteo PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

# Consola: coordenadas por pantalla -> mejor ruta por pantalla.
add_executable(ruteo main.cpp)
target_link_libraries(ruteo PRIVATE motor_ruteo)

# Pruebas (CP-09 a CP-19 del prototipo Python).
add_executable(test_ruteo test_ruteo.cpp)
target_link_libraries(test_ruteo PRIVATE motor_ruteo)

enable_testing()
add_test(NAME ruteo COMMAND test_ruteo)
