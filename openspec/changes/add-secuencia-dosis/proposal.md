## Why

En Demo Expo se puede regar a mano (la secuencia de riego: válvula N segundos), pero no dosificar. La dosificación sólo
sale del motor, que la condiciona a sus reglas y precondiciones (P-D1…P-D5): en la noche de prueba, por ejemplo, P-D5
la difirió hasta que terminara el riego. Para mostrar el hardware en vivo hace falta un botón que prenda la bomba de
insumo cuando el operador quiera.

## What Changes

- **Backend:** un tipo de secuencia nuevo, `DOSIS`, con el mismo guion que el riego sobre la bomba de insumo:
  `pump ON {ml}` → esperar su ACK final → `pump OFF` de seguro. Parámetro `ml` (1 a 50, el tope del firmware; 5 por
  defecto). Plazo del ACK: ml (1 s por ml) más el margen del riego.
- **Simulador:** una tarjeta «Dosificación» en las secuencias de Demo Expo, con el campo de ml.
- **Sin cambios** en el contrato MQTT, el firmware, el motor de reglas ni `ApplicationService`.

## Capabilities

### Modified Capabilities
- `secuencias-demo-expo`: suma la secuencia de dosis.

## Impact

- `SecuenciaService` y `ParametrosSecuencia` (campo `ml`), el panel de secuencias del simulador y sus textos.
- Como el riego manual, la dosis va directo al actuador: no evalúa P-D1…P-D5 ni descuenta el tanque. Es una herramienta de
  demostración, no un camino de aplicación agronómica.
