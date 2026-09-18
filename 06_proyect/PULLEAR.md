# Guía de Commits y Cierre de Issues en GitHub

Esta guía establece el flujo de trabajo estándar para relacionar nuestros commits con los *issues* del proyecto en GitHub, utilizando la convención de **Conventional Commits**.

## 1. Convención de Nomenclatura (Conventional Commits)

Nuestros commits deben seguir la siguiente estructura:

`<tipo>(<ámbito>): <descripción corta> (HU-XX)`

*   **`tipo`**: Qué clase de cambio estás haciendo. (Ej: `feat` para nueva funcionalidad, `fix` para corregir un bug, `docs` para documentación).
*   **`ámbito`**: El componente, módulo o área afectada. (Ej: `ruteo`, `auth`, `ui`).
*   **`descripción`**: Resumen breve del cambio.
*   **`HU/CU`**: Opcional, referencia a la Historia de Usuario o Caso de Uso.

Ejemplo: `feat(ruteo): Calculo de la ruta (HU-01, CU-03)`

---

## 2. Flujo para REFERENCIAR un Issue (Sin cerrarlo)

Utiliza este flujo cuando estés guardando avances en tu rama, pero la tarea (issue) **aún no está finalizada**. Esto creará un enlace visual en el issue para saber en qué commits se trabajó.

**Pasos en la terminal:**

```bash
# 1. Agrega los archivos modificados
git add .

# 2. Crea el commit usando dos parámetros -m
# El primer -m es el título. El segundo -m incluye la referencia (Refs).
git commit -m "feat(ruteo): iniciar estructura del grafo vial (HU-01)" -m "Refs #2"

# 3. Sube los cambios a tu rama de trabajo
git push origin feature/nombre-de-tu-rama
```

## 3. Flujo para CERRAR un Issue Automáticamente
Utiliza este flujo únicamente en tu commit final para esa tarea, cuando el trabajo esté 100% completado y listo para revisión mediante un Pull Request.

Pasos en la terminal:

```Bash
# 1. Agrega los archivos finales
git add .

# 2. Crea el commit usando dos parámetros -m
# El segundo -m debe usar una palabra clave de cierre (Closes, Fixes, Resolves).
git commit -m "feat(ruteo): completar algoritmo A* y calculo de ruta" -m "Closes #2"

# 3. Sube los cambios a tu rama de trabajo
git push origin feature/nombre-de-tu-rama
```