# T-117 / PR #123 — Paquete corregido

Este paquete reúne `02_req`, `03_des` y `05_pro/PruebaApiANH` de la rama `feature/T117-java-medir-api-anh` recibida, con las correcciones de `correcciones-T117-PR123.md`. La campaña real de 72 horas está excluida por instrucción del usuario y **no** se presenta como cumplida.

## Cambios de la revisión

- P-29, P-30 y P-32 permanecen como **Propuesta**.
- `03_modelo_dominio.md` conserva la regla general de **6 horas sin dato nuevo** y distingue la propuesta específica de ANH.
- `05_contratos.md` separa el Disclaimer del tercer entorno, marca 703/972/894 ms como no comparables y documenta el indicio horario de `updated_at`.
- CU-09 conserva una sola copia de la evidencia puntual de laptop/Oracle y no atribuye una aprobación anónima.
- `medicion-anh.jar` no se distribuye ni se versiona; se genera localmente desde `java/MedicionANH.java`.
- Los dos CSV Oracle se conservan con excepciones explícitas en `.gitignore`.
- Se incluyen los lanzadores de raíz que necesitaba `PRUEBA_CORTA_WINDOWS.ps1`.

## Pruebas offline

Se requieren Python 3.10+ y **JDK 17+** (`java`, `javac` y `jar` en PATH). En Windows ejecute `EJECUTAR_EN_WINDOWS.cmd`; en Linux:

```bash
cd 05_pro/PruebaApiANH/MedicionANH
bash EJECUTAR_PRUEBAS_LINUX.sh
```

La suite genera el JAR local y prueba contra HTTP de loopback. No consulta ANH. El JAR generado queda ignorado por Git.

## Estado del issue

El PR #123 ya existe. Con la evidencia disponible, no deben marcarse como cumplidos los checkboxes de 72 horas, P-29/P-30/P-32 confirmados, CP-157 ni CP-158. Si el equipo cambia formalmente esos criterios en el issue #112, debe quedar el responsable y la fecha.

No incluya claves privadas, tokens, `.env`, IP privadas ni `estado.properties` al hacer commit. Revise `git diff --check` y `git diff` antes de enviar.
