## ADDED Requirements

### Requirement: Control del bloqueo desde el simulador
`ManualLockRule` SHALL poder apagarse y conservar el bypass `lock` desde el simulador, y el simulador SHALL tener un escenario
"Bloqueo manual" que crea un bloqueo por sector o macro-zona por la API de bloqueos y lo levanta.

#### Scenario: Escenario de bloqueo
- **WHEN** el operador elige "Bloqueo manual" para MZ-1 y pulsa Disparar con la humedad en 40 %
- **THEN** se crea el bloqueo, el pulso no ordena ningún riego en MZ-1 y la traza muestra el bloqueo como causa

#### Scenario: Bypass del bloqueo
- **WHEN** se activa el bypass `lock` con un bloqueo vigente
- **THEN** la regla ya no corta la cadena y el sector se evalúa

#### Scenario: Regla apagada
- **WHEN** se apaga `ManualLockRule`
- **THEN** el bloqueo no corta la evaluación y la traza dice "deshabilitada"
