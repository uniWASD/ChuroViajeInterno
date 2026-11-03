# T-117: validación abreviada y reproducible

Este directorio contiene el código Java de medición, su JAR, pruebas de integración con un servidor HTTP local, herramientas de análisis de registros y ejecutores de Windows/Linux. Los tests funcionales **no generan tráfico hacia ANH**.

## Windows

Haz doble clic en `EJECUTAR_PRUEBAS_WINDOWS.cmd` o, desde esta carpeta, utiliza:

```powershell
python .\scripts\ejecutar_suite.py
```

No hace falta `Set-ExecutionPolicy`, copiar archivos individuales ni tener un `datos-local` previo. Si ya existe, se analiza como entrada adicional sin modificarlo. Los resultados se escriben por defecto en `%TEMP%\ChuroViaje_T117\resultados\ejecucion-*`.

Para probar la misma suite en Oracle desde Windows, utiliza el `COMPROBAR_ORACLE_WINDOWS.cmd` de la raíz del paquete. El script de Oracle ejecuta todas las pruebas contra loopback, sin contactar a ANH salvo que se active explícitamente `-ConsultaRealOracle`.

## Linux / Oracle

```bash
bash EJECUTAR_PRUEBAS_LINUX.sh
bash scripts/oracle_prueba_corta.sh --solo-simulacion
```

Para automatizar la conexión desde Windows:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\PRUEBA_CORTA_WINDOWS.ps1 -IPServidor DIRECCION_IP
```

Se necesita Python 3.10+, Java 17+, y para conexión remota `ssh` y `scp` con llave privada instalada localmente. Maven y Gradle no son necesarios.

## Validación de resultados

El informe `RESULTADO_T117.json` muestra las pruebas ejecutadas; `PRUEBAS_DETALLADAS.txt` contiene cada caso y su estado. `SIMULACION_IDENTIFICADA` conserva los 432 ciclos de calendario virtual inequívocamente etiquetados como artificiales. Los registros de una simulación no se usarán para confirmar parámetros ni se mezclarán con `datos-local` o `datos-oracle`.

El actualizador `scripts/actualizar_arquitectura.py` solo admite cuatro archivos Markdown en una rama basada en `develop`, exige evidencia y ofrece una vista previa antes de escribir. Nunca ejecutar sobre el ZIP T-84.

Lee `INICIAR_AQUI.md` en la raíz del paquete para la instalación y los límites de aceptación del issue #112.
