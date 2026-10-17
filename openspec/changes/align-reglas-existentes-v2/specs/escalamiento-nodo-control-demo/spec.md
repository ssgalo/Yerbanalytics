## ADDED Requirements

### Requirement: Control del escalamiento del nodo desde el simulador
El escalamiento del nodo (`EscalamientoNodo`) SHALL ser un `DemoControllable` apagable, con el bypass `safe-state` (no emite las órdenes de
estado seguro) y el cooldown `retry-offline` (minutos del reintento con el nodo caído, 0 lo elimina), y el simulador SHALL tener un
escenario "Nodo sin respuesta" que deja de publicar la telemetría de una zona y pide los pulsos necesarios.

#### Scenario: Apagar el escalamiento
- **WHEN** se apaga `EscalamientoNodo` y una zona no responde a 3 pulsos
- **THEN** no se emite ninguna alerta ni orden de estado seguro

#### Scenario: Reintento inmediato
- **WHEN** se fija `retry-offline` en 0 con una zona fuera de servicio
- **THEN** el siguiente tick pide lectura a esa zona

#### Scenario: Escenario del simulador
- **WHEN** el operador elige "Nodo sin respuesta" y pulsa Disparar
- **THEN** el simulador deja de publicar la telemetría de la zona elegida y encadena tres pulsos con motor
