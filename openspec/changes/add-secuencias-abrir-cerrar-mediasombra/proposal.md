## Por qué

En Demo Expo hace falta abrir o cerrar la mediasombra con un solo clic, sin la espera de la secuencia
`MEDIASOMBRA` (desplegar, esperar, enrollar). Un publish MQTT directo desde el simulador esquivaría el
seguimiento de comandos y dejaría desactualizado el estado del backend.

## Qué cambia

- Backend: dos tipos nuevos en `POST /api/secuencias`, `MEDIASOMBRA_ABRIR` (`shade SET {targetPct: 100}`, enrollada)
  y `MEDIASOMBRA_CERRAR` (`targetPct: 0`, desplegada). Un solo paso cada uno (`ENROLLAR` / `DESPLEGAR`), mismo
  destino que la secuencia de mediasombra.
- Espera el ACK final; los `EN_CURSO` (`en_cola` / `iniciado`) sólo estiran el plazo; `sin_cambio` es éxito.
- Misma guardia que el resto: `409` con otra secuencia o una pasada en curso; `400` si el tipo no existe.
- Simulador: dos botones en la tarjeta "Abrir o cerrar mediasombra" de la pestaña Demo Expo.

## Fuera de alcance

Sin cambios en el contrato MQTT, en permisos (`pasadas.operar` ya alcanza) ni en el dashboard productivo.
