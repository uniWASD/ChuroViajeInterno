# Issue #75 — protocolo de cierre y evidencias reproducibles

## CP-122: cuatro POST en 60 s con app abierta, cero después de minimizar

1. Preparar Java #74 en red aislada, Google OAuth, sesión autorizada y GPS concedido; confirmar `/actuator/health` y OpenAPI de `/telemetria`.
2. Identificar el commit Flutter, APK firmada, versión de Android, hora local y hora Java (zona horaria UTC−04:00 en el registro remitido).
3. Mantener la app visible con seguimiento de ubicación y grabación de pantalla; registrar marca de INICIO y contar POST aceptados por Java durante 60 s.
4. Minimizar mientras se graba; registrar marca MINIMIZADA y mantenerla 60 s. Verificar cero POST nuevos originados después de minimizar. Identificar por separado solicitudes ya iniciadas y aún en tránsito.
5. Registrar marca FIN, conservar conteo y archivo de vídeo con privacidad protegida.
6. Repetir con ruta activa y sin ruta activa. Un lote HTTP puede contener menos de cinco lecturas si el GPS no generó nuevas posiciones; no falsificar timestamps.

## CP-123: revocación real de ubicación

1. Confirmar envíos con permiso y sesión activos.
2. Revocar Ubicación desde los Ajustes del sistema Android, regresar al mapa, desplazarlo, ampliar/reducir y seleccionar una ubicación.
3. Registrar ausencia de POST posteriores a la detección de revocación. El vídeo debe demostrar el cambio de permiso y que el mapa funciona sin GPS.
4. Comprobar cierre de sesión y regreso a primer plano en pruebas adicionales.

## RNF-18: autonomía y fluidez

1. Mismo teléfono, sin cable ni cargador, brillo fijo, mismo tipo de red, GPS activo y condiciones ambientales comparables.
2. ChuroViaje abierta 30 min, sesión activa, telemetría real funcionando. Registrar batería inicial/final, hora y bloqueos de mapa.
3. Google Maps durante 30 min con **navegación activa**, con condiciones comparables; registrar batería inicial/final.
4. Cumple si (inicioChuro-finalChuro) es **estrictamente menor** que (inicioMaps-finalMaps) **y** la interfaz ChuroViaje no se congela.
5. Mantener registros primarios; una diferencia de un punto porcentual aconseja repetición. No comparar ensayos con USB alimentando uno de ellos.

## Estado en la consolidación

Se informó un ensayo con 7 pp ChuroViaje y 8 pp Google Maps, 30 min c/u, sin carga y mapa fluido. También hubo recepción Java con cuatro POST consecutivos en intervalos de 15 s y declaración de cese al minimizar/revocar. En la documentación pública aún faltan vídeos y los marcadores temporales que vinculen exactamente cada acción con los registros del backend.

La detección efectiva de congestión en C++ no forma parte del criterio de aceptación de la app Flutter que se está validando aquí. El backend de pruebas tenía motor deshabilitado; no inferir que procesó posiciones.
