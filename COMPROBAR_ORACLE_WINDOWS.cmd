@echo off
setlocal EnableExtensions
set "T117_RAIZ=%~dp0"
set "T117_SCRIPT=%T117_RAIZ%05_pro\PruebaApiANH\MedicionANH\scripts\PRUEBA_CORTA_WINDOWS.ps1"
if not exist "%T117_SCRIPT%" (
  echo ERROR: falta scripts\PRUEBA_CORTA_WINDOWS.ps1. Extraiga el paquete completo.
  pause
  exit /b 2
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%T117_SCRIPT%" -PedirIP %*
set "T117_EXIT=%ERRORLEVEL%"
pause
exit /b %T117_EXIT%
