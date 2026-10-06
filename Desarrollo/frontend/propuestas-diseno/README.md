# Propuestas de diseño del Panel general

Cinco direcciones estéticas para rediseñar la vista principal del dashboard. Son mockups HTML
estáticos, sin funcionalidad: se abren directo en el navegador. Las cinco resuelven los hallazgos
de `reporte-ui-ux.md` y usan los mismos datos (600 sectores, 6 macro-zonas, nodo de MZ-6 sin
señal), así que se pueden comparar lado a lado.

Lo que tienen en común:

- Arriba va qué hacer ahora y dónde: una cola de trabajo agrupada por causa, con link para ver los 164.
- KPIs honestos: nodos testigo "5 de 6", no sectores contados como hardware.
- Un único mapa de las 6 macro-zonas que muestra la proporción de estados, no el peor estado.
- Cada estado lleva glifo además de color: círculo con tilde (Saludable), triángulo (En
  observación), rombo (Crítico) y círculo tachado (Fuera de servicio).
- Indicador de frescura ("Datos de hace 12 s") y buscador "Ir a sector".
- "UV pronóstico" separado de la "Luz relativa" del sensor.
- Acciones autónomas de hoy y diagnósticos de IA con "Confirmar / Corregir".
- Navegación que se apila o pasa a barra en ancho de teléfono.

| Archivo | Dirección | Idea |
|---|---|---|
| `1-tierra-colorada.html` | Tierra colorada | Papel de bolsa de yerba, verde yerba y una franja de tierra laterítica con trama de tubetes. El mapa es una grilla de 10×10 por zona y el carmín frío queda reservado para lo crítico. Bricolage Grotesque + IBM Plex Mono. |
| `2-cuaderno-de-campo.html` | Cuaderno de campo / plano técnico | El vivero dibujado como plano de agrimensor, con el riel encima, escala y norte. Los estados se distinguen por rayado de lápiz, y la cola de trabajo es el orden de recorrido numerado sobre el plano. Los diagnósticos son fichas de herbario. Archivo Narrow + Courier Prime. |
| `3-modo-campo.html` | Modo campo / alto contraste | Blanco y negro con estados sólidos y glifo, texto de 18 px o más y objetivos de 48 a 56 px. Está pensado para el celular al sol: "Ahora" muestra tres tareas y cada zona es una barra apilada gruesa. Atkinson Hyperlegible. |
| `4-sala-de-control-nocturna.html` | Sala de control nocturna | Modo oscuro operativo y denso. Las zonas forman una matriz con su mini heatmap de proporciones, las 10 métricas y el nodo; los valores fuera de rango llevan glifo y los vencidos van tachados. Chivo + Chivo Mono con cifras tabulares. |
| `5-mate-cocido.html` | Mate cocido | Tono suave y narrativo: "Buen día, Mariano. Hoy MZ-4 te necesita.", seguido de tres tareas en orden. El mapa es una ronda de seis anillos de proporción, como mates pasando. Young Serif + Figtree. |

Las versiones para el lienzo de diseño (`*.dc.html`) tienen el mismo contenido y se generaron
aparte.
