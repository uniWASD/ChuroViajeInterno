## 1. Documento de Referencia de Variables Viales (Contexto: Tarija)

Para que el algoritmo clasifique correctamente la congestión y cumpla con los criterios de aceptación, se definen las siguientes variables base adaptadas a la mancha urbana de la ciudad:

### 1.1 Velocidades promedio esperadas por tipo de zona
*   **Vías Rápidas:** 50 km/h - 60 km/h.
*   **Avenidas Principales:** 35 km/h - 45 km/h.
*   **Zonas Residenciales:** 15 km/h - 25 km/h.

### 1.2 Tiempo estimado promedio de liberación de tráfico
*   **Tiempo de liberación estimado: 5 minutos**. 
*   *Justificación:* En el centro de la ciudad, una trancadera típica por semáforos saturados en hora pico o embotellamientos menores tarda un promedio de 5 minutos en despejarse para que el flujo vuelva a velocidad normal.

### 1.3 Frecuencia de solicitud de datos GPS
*   **Frecuencia establecida: Cada 3 segundos**.
*   *Justificación:* Un intervalo menor saturaría la batería del dispositivo y el backend. Un intervalo mayor impediría detectar a tiempo cambios bruscos de trayectoria, generando falsos positivos de tráfico.

---

## 2. Fundamento Matemático (Fórmulas y Datos de Salida)

Para transformar los datos crudos del GPS en inteligencia de tráfico utilizable, el motor core del sistema ejecuta en cadena las siguientes tres operaciones matemáticas:

### 2.1 Fórmula de Haversine (Cálculo de Distancia)
Se utiliza para calcular la distancia más corta sobre la curvatura de la Tierra entre dos puntos GPS.

`Distancia = 2 * R * asen( raíz( sen²(ΔLat / 2) + cos(Lat1) * cos(Lat2) * sen²(ΔLon / 2) ) )`

*   **R:** Radio de la Tierra (6371 kilómetros).
*   **Lat1, Lat2:** Latitud anterior y latitud actual.
*   **ΔLat, ΔLon:** La resta o diferencia entre las latitudes y longitudes.
*   **Dato de Salida:** La **distancia física real** en metros o kilómetros que el vehículo avanzó. Al dividir esta distancia entre los 3 segundos transcurridos, el sistema obtiene la **Velocidad Real** del conductor.

### 2.2 Fórmula de Azimut (Cálculo de Ángulo y Giro)
Calcula el ángulo de dirección del vehículo utilizando trigonometría básica.

`Ángulo = atan2( sen(ΔLon) * cos(Lat2), cos(Lat1) * sen(Lat2) - sen(Lat1) * cos(Lat2) * cos(ΔLon) )`

*   **atan2:** Función trigonométrica estándar para obtener los grados de orientación.
*   **Dato de Salida:** El **ángulo de dirección** en grados (de 0° a 360°). Al calcular este ángulo en dos lecturas seguidas y restarlos, se obtienen los **grados exactos de giro**. Esto permite descartar falsos positivos de congestión cuando el usuario simplemente frenó para doblar una esquina.

### 2.3 Retraso Vehicular (Cálculo de Tiempo de Congestión)
Calcula el impacto real del tráfico comparando el rendimiento del usuario con las variables teóricas de la vía.

`Tiempo Perdido = (Distancia / Velocidad Actual) - (Distancia / Velocidad Ideal)`

*   **Dato de Salida:** El **tiempo de retraso** (en minutos o segundos). Es la resta exacta entre el tiempo que realmente tardó el auto en cruzar ese tramo y el tiempo que debería haber tardado si la calle estuviera vacía. Este valor se usa para recalcular automáticamente las rutas de otros conductores.

### 2.4 Fórmula Física de Velocidad (Cálculo de Velocidad Real)
Toma la distancia obtenida por Haversine y la cruza con la telemetría temporal para obtener la velocidad del conductor.

`Velocidad (km/h) = (Distancia / Tiempo transcurrido) * 3.6`

*   **Distancia:** El resultado en metros arrojado por Haversine.
*   **Tiempo transcurrido:** La diferencia en segundos entre la lectura anterior y la actual (ej. 3 segundos).
*   **3.6:** Constante matemática para convertir metros por segundo (m/s) a kilómetros por hora (km/h).
*   **Dato de Salida:** La **Velocidad Real** a la que se desplaza el usuario.

---

## 3. Definición del Algoritmo (Clasificación de Congestión)

El procesamiento lógico se realiza mediante una máquina de estados que evalúa las salidas de las fórmulas anteriores junto con la calidad del hardware:

1.  **Filtro de Precisión (Accuracy):** Se evalúa la calidad de la señal GPS. Se descartan automáticamente lecturas con un error mayor a 50 metros para evitar saltos de ubicación irreales (Efecto Multipath causado por rebotes de señal en edificios).
2.  **Validación de Hardware:** Si la velocidad calculada supera límites físicos urbanos (ej. > 150 km/h), se clasifica como error del sensor y se descarta el punto.
3.  **Detección de Giros (Filtro de Falsos Positivos):** Si la velocidad es baja (<= 25 km/h) pero la diferencia del Azimut detecta un cambio de ángulo > 40°, se clasifica como un giro en esquina y se evita catalogar el tramo como congestionado.
4.  **Clasificación Final:** Si se superan los filtros anteriores, el sistema compara la velocidad actual y asigna el estado de la vía: **Velocidad normal, Congestionado, o Muy congestionado**.
