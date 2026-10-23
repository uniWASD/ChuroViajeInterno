cmake_minimum_required(VERSION 3.16)
project(ChuroViajeNucleo VERSION 0.1.0 LANGUAGES CXX)

# Núcleo C++ del motor core (D-15). Lo arranca el wrapper Java MotorDeRutas como
# proceso hijo y le habla por pipe: una línea de JSON por petición y por respuesta.

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Release)
endif()

if(MSVC)
  add_compile_options(/utf-8 /W4)   # /utf-8: los fuentes llevan tildes en UTF-8
else()
  add_compile_options(-Wall -Wextra)   # sin -march=native: el mismo código sirve en x86 y en ARM
endif()

include(FetchContent)
set(FETCHCONTENT_QUIET OFF)

# --- nlohmann/json: se usa la instalada en el sistema si existe; si no, se descarga ---
find_package(nlohmann_json 3.11 QUIET)
if(NOT nlohmann_json_FOUND)
  FetchContent_Declare(nlohmann_json
    URL      https://github.com/nlohmann/json/releases/download/v3.11.3/json.tar.xz
    URL_HASH SHA256=d6c65aca6b1ed68e7a182f4757257b107ae403032760ed6ef121c9d55e81757d)
  FetchContent_MakeAvailable(nlohmann_json)
endif()

# --- Biblioteca del núcleo (la reutilizan el ejecutable y las pruebas) ---
add_library(nucleo STATIC
  src/nucleo.cpp
  src/bucle.cpp)
target_include_directories(nucleo PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_link_libraries(nucleo PUBLIC nlohmann_json::nlohmann_json)

# --- Ejecutable que arranca el wrapper ---
add_executable(motor_nucleo src/main.cpp)
target_link_libraries(motor_nucleo PRIVATE nucleo)

# --- Pruebas: GoogleTest (BUILD_TESTING=OFF en el servidor si no se quieren) ---
include(CTest)
if(BUILD_TESTING)
  find_package(GTest QUIET)
  if(NOT GTest_FOUND)
    FetchContent_Declare(googletest
      URL      https://github.com/google/googletest/archive/refs/tags/v1.14.0.tar.gz
      URL_HASH SHA256=8ad598c73ad796e0d8280b082cebd82a630d73e73cd3c70057938a6501bba5d7)
    set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)   # Windows: misma biblioteca de C++ que el resto
    set(INSTALL_GTEST OFF CACHE BOOL "" FORCE)
    FetchContent_MakeAvailable(googletest)
  endif()
  include(GoogleTest)

  add_executable(pruebas_nucleo tests/test_nucleo.cpp tests/test_bucle.cpp)
  target_link_libraries(pruebas_nucleo PRIVATE nucleo GTest::gtest_main)
  gtest_discover_tests(pruebas_nucleo)

  # Prueba de extremo a extremo: el script arranca el ejecutable real por pipe, como el wrapper.
  find_package(Python3 COMPONENTS Interpreter QUIET)
  if(Python3_Interpreter_FOUND)
    add_test(NAME pipe_extremo_a_extremo
      COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/scripts/enviar_mensajes.py
              --nucleo $<TARGET_FILE:motor_nucleo> --verificar)
  endif()

  # Un solo comando que compila y corre todo:  cmake --build build --target pruebas
  add_custom_target(pruebas
    COMMAND ${CMAKE_CTEST_COMMAND} --output-on-failure -C $<CONFIG>
    DEPENDS pruebas_nucleo motor_nucleo
    WORKING_DIRECTORY ${CMAKE_BINARY_DIR}
    USES_TERMINAL)
endif()
