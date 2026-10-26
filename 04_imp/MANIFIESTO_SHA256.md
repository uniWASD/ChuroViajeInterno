# T-089 — manifiesto de procedencia SHA-256

Estos hashes permiten comprobar que las fuentes indicadas no se sustituyeron por datos generados. Los ZIP originales **no se duplican** dentro de la entrega.

## Fuentes ZIP revisadas

| Archivo original | SHA-256 |
|---|---|
| `uniWASD ChuroViajeInterno feature-T89-java-base-de-datos 04_imp.zip` | `722a0fd7ac2f4c42295ff6d10a1e2240652dfc1b0cc039d7f46d0a3a4aaf33ba` |
| `uniWASD ChuroViajeInterno feature-T115-investigacion-api-anh 05_pro_PruebaApiANH.zip` | `258bd78f86ae593b89fba30773e56a61708f3f13672fb64bf36c5ba8fa9bfcf3` |
| `uniWASD ChuroViajeInterno feature-T84-arquitectura-md 02_req_pdf.zip` | `e8f098c985ea148b06d28066d26d84f7e004f8d1b56c7d4820f699f07f23f0b2` |
| `uniWASD ChuroViajeInterno feature-T84-arquitectura-md 03_des.zip` | `59827ae604a7272a2bbff20db2d1d3f8f38103fdc9730552a001fe28f6a34416` |
| `uniWASD ChuroViajeInterno feature-HU08-CU06-AnalisisLLM 05_pro_PruebaClasificacion.zip` | `80091787654197cbecd3b1e98291416a007b975c895e28b3c234a8275c3618ca` |
| `uniWASD ChuroViajeInterno main 06_proyect.zip` | `b8048b21439a010198896612525c203eb3b9b1d8f85ddad51cd3dbe019e84e0f` |

## Archivos íntegros dentro del ZIP corregido

| Archivo incorporado | SHA-256 |
|---|---|
| `integraciones/ANH_T115/anh_tarija.py` | `7bba307fdb4eafced99ac871f07d1aaa79500b3b46e2f92880315f900557d0a4` |
| `integraciones/ANH_T115/requirements.txt` | `fa72933e8764d9e64f3d4e74f138fbe4424db2443d0e280511fe18f4b3f0bf00` |
| `backend_java/src/main/resources/db/migration/V1__postgis_y_catalogos.sql` | `06fd693039a5ca0bbc2c69e2a1d1ba2392398bdbc5f707b8a159363cbc30ce3b` |
| `referencias_t089/06_base_de_datos.md` | `5dd4e7708b3ebb34e9cedbf31ab92447dc07dc80403060c30681f1b8d3758136` |
| `referencias_t089/03_modelo_dominio.md` | `416503cf129f474b3c17925bd1dad3f7fd40c34e4d0e9192602b235ed74bbe6d` |
| `referencias_t089/CONTRIBUTING.md` | `c2d57286a8466c13c7ac4a100c6bb4f6b5cc728894e555b03359cea9d0380cd4` |
| `referencias_t089/TRAZABILIDAD.md` | `aece8da05f2d82398631891be5a3e05a935846d0d18d9e80c6e13e5ba108173a` |

## Advertencias de interpretación

- El hash de un JSON obtenido por red comprueba **integridad posterior** del archivo, no equivale a una firma digital de ANH. La validación inicial debe incluir las trazas HTTPS y los archivos crudos.
- La V1 se conserva idéntica a la original; los cambios posteriores van en V1.1.
- No hay ninguna extracción real exitosa en este paquete ni SQL V2 para surtidores.
- No se copiaron conversaciones de WhatsApp ni muestras con nombres de prueba a tablas productivas.
