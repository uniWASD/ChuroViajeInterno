@echo off
setlocal
cd /d "%~dp0"
where python >nul 2>&1
if errorlevel 1 (
  echo ERROR: Instale Python 3.10 o posterior y habilitelo en PATH.
  pause
  exit /b 2
)
python -m pip install -r "integraciones\ANH_T115\requirements.txt"
if errorlevel 1 (
  echo ERROR: No fue posible instalar los requisitos del cliente ANH.
  pause
  exit /b 2
)
python "herramientas\ejecutar_extraccion_anh.py"
set "CODIGO=%ERRORLEVEL%"
echo.
if "%CODIGO%"=="3" echo INFO: Snapshot ANH validado pero pendiente aprobacion del mapa urbano.
if "%CODIGO%"=="2" echo ERROR: Extraccion/evidencia no validada. No se cargaron estaciones.
echo Codigo de salida T-089: %CODIGO%
pause
exit /b %CODIGO%
