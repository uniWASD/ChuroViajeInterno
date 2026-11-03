@echo off
setlocal EnableExtensions
chcp 65001 >nul
rem Capturar una sola vez el directorio del BAT ANTES de cambiar la carpeta actual.
set "T117_BASE=%~dp0"
set "T117_SUITE=%T117_BASE%scripts\ejecutar_suite.py"
if not exist "%T117_SUITE%" (
  echo ERROR: No se encontro scripts\ejecutar_suite.py dentro de MedicionANH.
  pause
  exit /b 2
)
pushd "%T117_BASE%"
if errorlevel 1 (
  echo ERROR: No se puede acceder a la carpeta MedicionANH.
  pause
  exit /b 2
)
set "PYTHONIOENCODING=utf-8"
where py >nul 2>nul
if not errorlevel 1 (
  py -3 "%T117_SUITE%" %*
) else (
  python "%T117_SUITE%" %*
)
set "T117_EXIT=%ERRORLEVEL%"
popd
echo.
if "%T117_EXIT%"=="0" (
  echo Pruebas T-117 terminadas correctamente.
) else (
  echo Se detectaron fallos: revise la salida y el informe generado.
)
pause
exit /b %T117_EXIT%
