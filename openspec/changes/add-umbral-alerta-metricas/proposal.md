# Change: add-umbral-alerta-metricas

## Why

Hoy cada métrica tiene tres bandas: ideal (verde), observación (amarillo) y crítico (rojo). Toda la
franja amarilla pone la macro-zona "En observación", pero nadie se entera hasta que un sector llega a
crítico: la campana sólo lista sectores. El agrónomo quiere elegir **desde dónde, dentro de lo
amarillo, vale la pena avisar**: una humedad apenas debajo del ideal no merece una alerta; una que ya
se acerca al crítico, sí.

Además, la tabla de seis números por métrica no se lee: no se ve dónde cae cada banda ni cuánto mide.

## What

- **Punto de alerta por lado** (`alertMin`, `alertMax`) en cada umbral de métrica, dentro de la franja
  amarilla: `warnMin ≤ alertMin ≤ idealMin` y `idealMax ≤ alertMax ≤ warnMax`.
- **No cambia el estado.** Toda la franja amarilla sigue siendo "En observación"; el punto sólo decide
  si se emite una alerta.
- **Alertas por métrica en la campana**: por cada macro-zona y métrica que afecta el estado, una
  alerta `WARNING` si la lectura está entre el crítico y el punto de alerta, y `CRITICAL` si está en
  la franja roja. Conviven con las alertas por sector.
- **Persistencia**: dos columnas nuevas, nulables, en `umbral_metrica`. Sin valor, el punto es el
  medio de la franja amarilla (también es el valor de fábrica). No requiere migración manual.
- **Editor visual de umbrales** en la vista Umbrales: una barra por métrica con las zonas roja,
  amarilla y verde, manijas arrastrables (y operables con teclado) en los cuatro límites de banda y en
  los dos puntos de alerta, y los seis campos numéricos debajo. El rango fisiológico (`critMin`,
  `critMax`) deja de mostrarse como campo: es la escala de la barra y el tope de las manijas.

## Fuera de alcance

- La API de alertas persistidas (`/api/alertas`, marcado como leída) de `alertas-inteligentes`.
- Que el motor de reglas use los puntos de alerta.
- Alertas de las métricas informativas (`afectaEstado: false`): sus bandas no están validadas.
- Alertas por métrica en el modo `mock` (la demo conserva sus alertas ilustrativas).
