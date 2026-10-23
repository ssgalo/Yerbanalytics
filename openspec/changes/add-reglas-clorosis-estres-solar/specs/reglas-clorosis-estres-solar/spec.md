## ADDED Requirements

> **F-S2 (Estrés solar extendido) no está implementada**: depende de `add-mediasombra-v2` (cancelado), ver el `design.md` del cambio. Los demás requisitos sí.

### Requirement: F-C1 Clorosis con causa no nutricional
El motor SHALL NO aplicar fertilizante correctivo en el sector y SHALL emitir `WARNING` con la causa probable ante un
diagnóstico Clorosis vigente cuando se cumpla alguna de estas causas: humedad de sustrato media de las últimas 48 h
> 80 %; CE ≥ 2,0 dS/m; pH ≥ 6,5.

#### Scenario: Asfixia radicular
- **WHEN** hay Clorosis vigente y el sustrato promedió 84 % en 48 h
- **THEN** no se aplica fertilizante y el `WARNING` dice "asfixia radicular"

#### Scenario: Salinidad
- **WHEN** hay Clorosis vigente y CE 2,3 dS/m
- **THEN** no se aplica y el aviso menciona salinidad

#### Scenario: pH alto
- **WHEN** hay Clorosis vigente y pH 6,8
- **THEN** no se aplica y el aviso menciona clorosis férrica

#### Scenario: Valores en el borde
- **WHEN** CE vale 2,0
- **THEN** la causa se cumple (≥)

### Requirement: F-C2 Clorosis nutricional
Con un diagnóstico Clorosis vigente sin ninguna causa de F-C1, el motor SHALL pedir 100 mL de fertilizante para el
sector en la próxima ventana apta, repetible respetando el intervalo mínimo, con `WARNING` (`CRITICAL` si la severidad
es alta). Durante la rustificación SHALL considerar sólo severidad moderada o alta.

#### Scenario: Clorosis nutricional
- **WHEN** hay Clorosis vigente, sustrato 60 %, CE 0,8, pH 5,5
- **THEN** se pide fertilizante y queda un `WARNING`

#### Scenario: Leve en rustificación
- **WHEN** la severidad es Baja y la MZ tiene un plan en curso
- **THEN** no se aplica

#### Scenario: Intervalo mínimo
- **WHEN** el sector recibió fertilizante hace 3 días
- **THEN** la nueva aplicación queda diferida hasta cumplirse 5 días

### Requirement: F-S1 Estrés solar con radiación alta
El motor SHALL ordenar un riego de reposición del sector si su humedad es menor a 55 %, dentro de la ventana de
riego y respetando R-04 y R-06, con `WARNING` (`CRITICAL` si la severidad es alta), ante un diagnóstico Estrés solar
vigente cuando en las últimas 48 h la luz fue ≥ 80 % en alguna lectura entre las 10 y las 16 h o la temperatura llegó a ≥ 32 °C.

#### Scenario: Reposición
- **WHEN** hay Estrés solar vigente, la luz llegó a 88 % ayer a las 13:00 y el sustrato está en 50 %, a las 09:00
- **THEN** se riega el sector con el volumen de la fórmula y queda un `WARNING`

#### Scenario: Sustrato suficiente
- **WHEN** el sustrato está en 58 %
- **THEN** no se riega

#### Scenario: Sustrato saturado
- **WHEN** R-04 bloquea el riego
- **THEN** F-S1 no riega

#### Scenario: Fuera de ventana
- **WHEN** son las 20:00
- **THEN** F-S1 no riega

### Requirement: F-S2 Estrés solar extendido
Cuando el 10 % o más de los sectores de una MZ cumplen F-S1 en la última captura, el motor SHALL cerrar la
mediasombra de la MZ de 11:00 a 16:00 durante 3 días, en cualquier perfil o etapa, sin mover el calendario del plan,
y emitir `WARNING` "Estrés solar en el N % de la MZ: evaluar cancelar el plan de rustificación".

#### Scenario: Umbral alcanzado
- **WHEN** 12 de 100 sectores de MZ-2 cumplen F-S1
- **THEN** la malla de MZ-2 queda cerrada 11–16 h los próximos 3 días y se emite el aviso con "12 %"

#### Scenario: Por debajo
- **WHEN** 9 de 100 cumplen F-S1
- **THEN** F-S2 no actúa

#### Scenario: Calendario del plan
- **WHEN** F-S2 cierra la malla
- **THEN** el día del plan sigue avanzando

### Requirement: F-S3 Estrés solar sin radiación alta
Con un diagnóstico Estrés solar vigente y, en las últimas 48 h, luz < 60 % y temperatura < 30 °C, el motor SHALL NO
actuar y SHALL emitir `WARNING` "Síntoma no explicado por radiación: posible estrés hídrico o problema de raíz.
Inspeccionar".

#### Scenario: Sin radiación
- **WHEN** hay Estrés solar vigente, luz máxima 55 % y temperatura máxima 27 °C
- **THEN** no se riega, la malla no cambia y queda el `WARNING`

#### Scenario: Zona gris
- **WHEN** la luz máxima fue 70 % y la temperatura 31 °C
- **THEN** ni F-S1 ni F-S3 actúan (comportamiento definido por la v2)
