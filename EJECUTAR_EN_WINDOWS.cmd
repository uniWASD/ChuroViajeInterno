@echo off
setlocal EnableExtensions
set "T117_RAIZ=%~dp0"
set "T117_LANZADOR=%T117_RAIZ%05_pro\PruebaApiANH\MedicionANH\EJECUTAR_PRUEBAS_WINDOWS.cmd"
if not exist "%T117_LANZADOR%" (
  echo ERROR: extraiga el paquete completo conservando 05_pro\PruebaApiANH\MedicionANH.
  pause
  exit /b 2
)
call "%T117_LANZADOR%" %*
exit /b %ERRORLEVEL%
