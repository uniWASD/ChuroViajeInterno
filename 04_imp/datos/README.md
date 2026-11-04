# Datos compartidos

Esta carpeta contiene archivos de intercambio entre el núcleo C++ y el backend Java. La red vial real `red_vial_tarija.json` **no se versiona**: se genera localmente con las herramientas de `../motor_cpp/preparacion/` y se conserva fuera de Git conforme a D-33.

| Archivo | Utilidad |
| --- | --- |
| `mancha_urbana_tarija.geojson` | Delimitación de cobertura utilizada por la preparación y el backend. Ya existe en el repositorio. |
| `rutas_referencia.json` | Seis casos de referencia T-120: coordenadas, nodos OSM, paradas, calles esperadas y distancias aproximadas, consumidos por C++ y Java. |
| `ruta_ejemplo_post_rutas.json` | Ejemplo de respuesta de `POST /rutas` para que la aplicación pueda usar una respuesta simulada mientras se integran los componentes. |

## Viajes T-120 (issue #129)

Se documentan seis itinerarios: plaza–ex terminal, plaza–UAJMS, Mercado Bolívar–Caja Nacional de Salud y retorno por calles alternativas, plaza–Tomatitas (declarado como cruce del Guadalquivir) y San Luis–San Bernardo con parada en ex terminal. Las distancias y calles fueron contrastadas contra la red OSM del **03/11/2026**. La tolerancia registrada es del **15 %**.

### Revisión de circulación

El solicitante **aceptó expresamente la revisión digital con OpenStreetMap como sustituto de la revisión presencial por un conductor** para esta entrega T-120. Se tiene por cumplido el criterio de circulación **bajo esa sustitución acordada**: se comprobó la continuidad de arcos, los sentidos presentes en el grafo dirigido y la exclusión de Calle Bolívar en la ruta de retorno. `revision.revisado=false` sigue indicando correctamente que **no** hubo revisión humana presencial. La aceptación y sus límites constan en `revision.criterio_sustituto_aceptado`.

La red no contiene una capa hidrográfica independiente; el indicador de cruce del río se toma como caso documentado, no como ensayo geométrico externo. Las restricciones OSM pueden diferir de la señalización vigente en la calle.

## Ejecución de pruebas

Con el grafo disponible localmente, definir `RED_VIAL_REAL` apuntando a la ruta absoluta del JSON y, para Java, `MOTOR_PATH` apuntando al ejecutable `motor_nucleo` compilado. Las pruebas del núcleo `RutasReferencia.*` validan CP-167 y CP-168; `RutasReferenciaNucleoRealTest` usa `MotorDeRutas` con ese ejecutable. Si falta el grafo, las pruebas dependientes de él se omiten conforme al issue.

El informe de la ejecución Windows del 04/11/2026 figura en [`../motor_cpp/preparacion/VERIFICACION_T120_WINDOWS_2026-11-04.md`](../motor_cpp/preparacion/VERIFICACION_T120_WINDOWS_2026-11-04.md). La base documental del contrato está en `../../03_des/02_Arquitectura/05_contratos.md`.
