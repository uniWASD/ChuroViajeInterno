/// Configuración del estilo Mapbox Standard para que el mapa se parezca a Google Maps (#172). Sigue siendo Mapbox
/// (D-08): no hay claves nuevas ni costos, solo se cambian colores, la fuente y las capas que se ven.
///
/// - Tierra gris clara, calles blancas y las vías principales en amarillo suave.
/// - Agua celeste y parques verdes.
/// - Sin edificios ni árboles en 3D: el mapa se ve plano, como en Google Maps.
/// - Etiquetas en Roboto, la fuente de Android, con los lugares de interés a la vista.
///
/// Se aplica al importar «basemap», que es el nombre con el que el SDK carga el estilo Standard.
const String importacionBasemap = 'basemap';

const Map<String, Object> configuracionEstiloGoogle = {
  'lightPreset': 'day',
  'theme': 'default',
  'show3dObjects': false,
  'colorLand': '#ECECE8',
  'colorWater': '#9CD4F5',
  'colorGreenspace': '#C3E8CA',
  'colorRoads': '#FFFFFF',
  'colorTrunks': '#FDE9A6',
  'colorMotorways': '#F9D776',
  'colorRoadLabels': '#5F6368',
  'colorPlaceLabels': '#3C4043',
  'colorCommercial': '#FCEFE6',
  'colorMedical': '#F8E1E1',
  'colorEducation': '#E8EEF8',
  'colorIndustrial': '#ECEDF0',
  'colorBuildings': '#F6F6F4',
  'colorAdminBoundaries': '#9AA0A6',
  'showPedestrianRoads': true,
  'showPointOfInterestLabels': true,
  'densityPointOfInterestLabels': 3,
  'showTransitLabels': true,
  'showPlaceLabels': true,
  'showRoadLabels': true,
  'font': 'Roboto',
};
