# Datos compartidos

Archivos que usan varias partes del sistema. La red vial real de Tarija **no** está aquí (D-33): se genera con [preparacion](../motor_cpp/preparacion/README.md) y se guarda fuera del repositorio. El `.gitignore` de `04_imp` ignora `datos/red_vial_*.json` para que no se suba por error.

| Archivo | Para qué sirve |
|---|---|
| `mancha_urbana_tarija.geojson` | Cobertura operativa: Tarija, Tomatitas y el corredor de El Rancho. La usan el backend y la preparación de la red vial. |
| `rutas_referencia.json` | Viajes de referencia de Tarija (T-120): origen, destino, paradas, nodo de inicio y de fin en la red preparada, calles por las que debe pasar la ruta (en orden), calles que no debe usar y distancia aproximada. Los usan las pruebas del núcleo y del backend con la red real. |
| `ruta_ejemplo_post_rutas.json` | Respuesta de `POST /rutas` para el primer viaje de referencia (plaza Luis de Fuentes a la ex terminal), en el formato del [contrato](../../03_des/02_Arquitectura/05_contratos.md#rutas-post-rutas). La app la puede usar como respuesta falsa mientras no se conecte al backend. |

## Viajes de referencia

Hay seis viajes: dos desde la plaza hacia el sur (ex terminal y UAJMS), uno por la calle Bolívar en su sentido y el mismo al revés (que no puede usarla), uno que cruza el Guadalquivir hacia Tomatitas y uno de sur a norte con una parada en la ex terminal.

Las calles esperadas salieron de la primera corrida del núcleo con el extracto del 2026-11-02, quitando los tramos sin nombre, las rotondas y las calles de menos de 150 m. El 04/11 se volvieron a comprobar con el extracto del 2026-11-03: los nodos, las calles en orden, los sentidos y las distancias (todas dentro del 15 %).

Los viajes no los revisó alguien que maneje en Tarija: Marcelo aceptó que la revisión con OpenStreetMap la reemplace para T-120 (#129). Por eso `revisado` sigue en `false` y lo aceptado queda anotado en `revision.criterio_sustituto_aceptado`. Ten en cuenta que OpenStreetMap puede no coincidir con la señalización de la calle. Si más adelante alguien que maneja los revisa, corrige las calles que haga falta y anota quién lo hizo y cuándo en `revision`.

Si se prepara un extracto nuevo, puede que cambien los nodos de inicio y de fin o alguna calle: las pruebas avisan qué viaje cambió y por qué calles pasa ahora.

Para correr las pruebas con la red real:

```bash
# Núcleo (CP-167 y CP-168)
RED_VIAL_REAL=/ruta/a/red_vial_tarija.json ./build/pruebas_nucleo --gtest_filter="RutasReferencia*"

# Backend con el núcleo real
MOTOR_PATH=/ruta/a/motor_nucleo RED_VIAL_REAL=/ruta/a/red_vial_tarija.json mvn test -Dtest=RutasReferenciaNucleoRealTest
```

Sin esas variables, o si el archivo de la red no existe, las dos pruebas se saltan. La corrida en Windows del 04/11 está en [VERIFICACION_T120_WINDOWS_2026-11-04.md](../motor_cpp/preparacion/VERIFICACION_T120_WINDOWS_2026-11-04.md).
