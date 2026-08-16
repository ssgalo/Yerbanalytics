## MODIFIED Requirements

### Requirement: Camino único de alta de diagnósticos
El sistema SHALL exponer un único endpoint público para dar de alta un diagnóstico, y ese
endpoint SHALL ser el mismo que use cualquier emisor, sea el servicio de inferencia o una carga
manual. NO SHALL existir un endpoint alternativo, un parámetro ni una variante de
comportamiento destinada a distinguir el origen del diagnóstico.

#### Scenario: Un solo camino de escritura
- **WHEN** se audita cómo se crean los diagnósticos en el sistema
- **THEN** existe un único endpoint de alta, sin variantes ni duplicados

#### Scenario: Las validaciones no dependen del emisor
- **WHEN** dos altas idénticas llegan desde emisores distintos
- **THEN** el sistema aplica exactamente las mismas validaciones y produce exactamente el mismo
  resultado

#### Scenario: El alta no depende de ningún estado global del backend
- **WHEN** se dan de alta dos diagnósticos idénticos en momentos distintos de la vida del
  sistema
- **THEN** el resultado es el mismo, porque el backend no tiene ningún modo de operación ni
  interruptor global que condicione el alta
