# Design: add-umbral-alerta-metricas

## Modelo

Sobre la recta de una métrica, de izquierda a derecha:

```
critMin ·· warnMin ── alertMin ── idealMin ══ idealMax ── alertMax ── warnMax ·· critMax
  (escala)  │  rojo  │ amarillo+alerta │ amarillo │ verde │ amarillo │ amarillo+alerta │ rojo │
```

| Lectura | Estado (sin cambios) | Alerta en la campana |
|---|---|---|
| `< warnMin` o `> warnMax` | Crítico | `CRITICAL` |
| `[warnMin, alertMin)` o `(alertMax, warnMax]` | En observación | `WARNING` |
| `[alertMin, idealMin)` o `(idealMax, alertMax]` | En observación | — |
| `[idealMin, idealMax]` | Saludable | — |

En la UI, `warnMin`/`warnMax` se rotulan "Crítico debajo de" / "Crítico arriba de", porque eso es lo
que hacen hoy en `NurseryService.buildMetricsList`. No se renombran los campos: son contrato del DTO y
de la tabla.

## Decisiones

- **Columnas nulables, valor por defecto calculado.** `ddl-auto=update` agrega columnas pero no puede
  agregar una `NOT NULL` a una tabla con filas. Con `NULL` el backend usa el medio de la franja,
  redondeado a los decimales de la métrica; el mock usa la misma fórmula. Así no hay script manual.
- **Los puntos no viajan en `MetricSpec`.** `MetricSpec` es el contrato de la lectura y lo construyen
  muchos tests; los puntos sólo los necesitan la configuración y la generación de alertas, que los
  piden a `ConfiguracionService`.
- **Payload sin puntos se acepta.** Un cliente viejo que no los manda guarda `NULL` (= punto medio);
  no se rompe el `PUT`.
- **Alertas calculadas, no persistidas.** Igual que las de sector: salen de la lectura vigente en cada
  snapshot. La campana ordena por severidad (crítico primero) y muestra hasta 8.
- **Manijas**: `role="slider"` con `aria-valuemin/max/now`; flechas mueven un paso (`10^-dec`),
  Shift+flechas diez. Cada manija queda acotada por sus vecinas, así que el orden nunca se puede
  romper arrastrando; tipeando sí, y ahí aparece el error de coherencia.
