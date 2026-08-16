## ADDED Requirements

### Requirement: Heartbeat del nodo por coincidencia de serial/MAC
Al ingerir una lectura, el backend SHALL actualizar el estado técnico (heartbeat:
batería/señal/último-update) del dispositivo registrado **cuyo serial/MAC coincide** con el de
la telemetría. Si ningún dispositivo registrado tiene ese serial/MAC, NO SHALL actualizar el
heartbeat de ningún nodo. La actualización de los sectores de la macro-zona SHALL ocurrir por
`zonaId`, con independencia de que exista un dispositivo registrado.

#### Scenario: Hardware registrado con el mismo serial/MAC
- **WHEN** se ingiere una lectura y existe un dispositivo registrado con el mismo serial/MAC
  en esa macro-zona
- **THEN** ese dispositivo actualiza su heartbeat y los sectores de la macro-zona reflejan la
  lectura

#### Scenario: Sin hardware registrado con ese serial/MAC
- **WHEN** se ingiere una lectura cuyo serial/MAC no coincide con ningún dispositivo
  registrado
- **THEN** los sectores de la macro-zona reflejan la lectura, pero ningún nodo actualiza su
  heartbeat

#### Scenario: El heartbeat no depende del emisor
- **WHEN** la lectura proviene de un nodo físico o de cualquier otro emisor que publique en el
  topic de la macro-zona
- **THEN** la regla de actualización del heartbeat es exactamente la misma

## MODIFIED Requirements

### Requirement: El envío manual de telemetría cubre las diez métricas

Cualquier emisor de telemetría SHALL poder publicar cualquier subconjunto de las diez
métricas para una macro-zona, respetando la semántica de lectura parcial, y la ingesta SHALL
tratar ese subconjunto igual sin importar quién lo haya publicado.

#### Scenario: Envío de una métrica nueva

- **WHEN** se publica manualmente un valor de nitrógeno para MZ-2
- **THEN** la lectura de MZ-2 refleja ese nitrógeno y conserva el resto de sus valores

#### Scenario: La ingesta no distingue el emisor

- **WHEN** dos lecturas idénticas llegan al mismo topic desde emisores distintos
- **THEN** la ingesta produce exactamente el mismo resultado persistido
