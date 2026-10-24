@echo off
setlocal
cd /d "%~dp0"
where python >nul 2>&1
if errorlevel 1 goto no_python
python --version
python -m pip install -r requirements.txt
if errorlevel 1 goto error
python -m py_compile anh_tarija.py
if errorlevel 1 goto error
python anh_tarija.py --usar-certifi --mostrar-ubicacion
set "CODIGO=%ERRORLEVEL%"
echo.
echo Codigo de salida: %CODIGO%
pause
exit /b %CODIGO%
:no_python
echo No se encontro Python. Instale Python 3.10 o posterior.
pause
exit /b 1
:error
echo Error al instalar dependencias o verificar la sintaxis.
pause
exit /b 2
