## ADDED Requirements

Reemplaza a la dosificación v1 (`SupplyRule`, `DailyDoseLimitRule`). Valores de fábrica salvo que el escenario
diga otra cosa: dosis 100 mL, intervalos 5 d (fertilizante) y 7 d (fitosanitario), ventanas 07:00–10:00 y
17:00–19:00, ≤ 30 °C, lluvia < 5 mm en 6 h, humedad ≥ 45 %.

### Requirement: Aplicación de un insumo
Toda aplicación SHALL pasar por un único servicio que verifique las precondiciones, publique la orden de bomba con el insumo y
el volumen en mililitros (el nodo lo convierte a tiempo con su calibración y corta solo), y registre sector, insumo, volumen, hora y
regla de origen.

#### Scenario: Aplicación normal
- **WHEN** se pide fertilizante para S-045 a las 08:00 con 22 °C, sin lluvia prevista, humedad 55 % y sin
  aplicaciones recientes
- **THEN** se publica `pump ON {ml:100, insumo:FERTILIZANTE}` y se registra "S-045, fertilizante, 100 mL, 08:00"

#### Scenario: El nodo corta solo
- **WHEN** el nodo recibe la orden de bomba con `ml`
- **THEN** convierte el volumen a tiempo, corta al cumplirlo y el ack informa si se aplicó el límite de dosis

#### Scenario: Dosis fuera del límite del nodo
- **WHEN** se intenta guardar una dosis mayor al límite máximo del contrato
- **THEN** el guardado se rechaza

### Requirement: P-D1 Diagnóstico vigente
Una aplicación disparada por un diagnóstico SHALL requerir que el diagnóstico sea el de la última captura del
sector, tenga a lo sumo 48 h y su confianza alcance el umbral; "No concluyente" NO SHALL contar.

#### Scenario: Diagnóstico viejo
- **WHEN** el último diagnóstico tiene 60 h
- **THEN** no es vigente y la aplicación se rechaza

#### Scenario: Confianza baja
- **WHEN** la confianza es 65 % y el umbral 70 %
- **THEN** no es vigente

### Requirement: P-D2 Intervalo mínimo y una aplicación por día
Una aplicación SHALL requerir que haya pasado el intervalo mínimo del insumo en ese sector y que no haya habido
otra aplicación ese día. Si el mismo día corresponden ambos insumos, SHALL aplicarse primero el fitosanitario y
diferir el fertilizante al día siguiente.

#### Scenario: Intervalo no cumplido
- **WHEN** el fitosanitario se aplicó hace 5 días en S-045
- **THEN** una nueva aplicación de fitosanitario se difiere (mínimo 7 días)

#### Scenario: Ambos el mismo día
- **WHEN** hoy corresponden fertilizante y fitosanitario para S-045
- **THEN** se aplica el fitosanitario y el fertilizante queda diferido para mañana

### Requirement: P-D3 Ventana de aplicación y temperatura
Una aplicación SHALL ocurrir sólo dentro de 07:00–10:00 o 17:00–19:00 y con temperatura (última lectura o
pronóstico de esa hora) de a lo sumo 30 °C. Si no se cumple, no se hace nada y se reevalúa en la próxima ventana.

#### Scenario: Fuera de ventana
- **WHEN** se pide una aplicación a las 12:00
- **THEN** queda diferida hasta las 17:00

#### Scenario: Demasiado calor
- **WHEN** son las 08:30 y hay 31 °C
- **THEN** se difiere a la próxima ventana apta

### Requirement: P-D4 Lluvia prevista
Una aplicación NO SHALL hacerse si el pronóstico da 5 mm o más de lluvia en las próximas 6 h.

#### Scenario: Lluvia en camino
- **WHEN** se prevén 6 mm en las próximas 4 h
- **THEN** la aplicación se difiere

#### Scenario: Sin pronóstico
- **WHEN** el pronóstico no está disponible
- **THEN** la condición no bloquea (se asume que no llueve)

### Requirement: P-D5 Humedad de sustrato mínima
Una aplicación SHALL requerir humedad de sustrato ≥ 45 %, o que el sector se haya regado después de la última
lectura. No hay tope superior.

#### Scenario: Sustrato seco
- **WHEN** la humedad es 40 % y el sector no se regó desde la lectura
- **THEN** la aplicación se difiere

#### Scenario: Regado después de la lectura
- **WHEN** la humedad registrada es 40 % pero el sector se regó 10 min después de esa lectura
- **THEN** la condición se cumple

### Requirement: Reingreso y pausa de riego
Tras un fitosanitario el sistema SHALL marcar el sector con reingreso restringido por 24 h (o lo que diga el
marbete configurado) y SHALL dejar registrada la aplicación para la pausa de riego R-06.

#### Scenario: Fitosanitario aplicado
- **WHEN** se aplica fitosanitario en S-045 a las 08:00
- **THEN** S-045 muestra reingreso restringido hasta las 08:00 del día siguiente y R-01 no lo riega por 6 h

### Requirement: Stock de tanques
El sistema SHALL llevar el stock de cada tanque, descontar cada aplicación, emitir `WARNING` "Reponer [insumo]"
por debajo del 20 % y, con el tanque vacío, NO aplicar y emitir `CRITICAL`.

#### Scenario: Stock bajo
- **WHEN** una aplicación deja el tanque de fertilizante en 18 %
- **THEN** queda un `WARNING` "Reponer fertilizante"

#### Scenario: Tanque vacío
- **WHEN** el tanque de fitosanitario está en 0 y se pide una aplicación
- **THEN** no se aplica y se emite `CRITICAL`

#### Scenario: Recarga
- **WHEN** un Operario registra la carga de 20 L en el tanque de fertilizante
- **THEN** el stock se actualiza y el evento queda en el historial con el usuario

### Requirement: Aplicaciones diferidas
Una aplicación que no cumple las precondiciones SHALL quedar pendiente y reintentarse en la próxima ventana apta,
sin perderse, hasta que su regla de origen la cancele o venza.

#### Scenario: Reintento al abrir la ventana
- **WHEN** a las 17:00 se abre la ventana y hay una aplicación pendiente que ahora cumple todo
- **THEN** se ejecuta

### Requirement: Retiro de la dosificación v1
El motor NO SHALL conservar la dosificación por estado crítico ni el límite de dosis cada 24 h.

#### Scenario: Catálogo
- **WHEN** se lista el catálogo de parámetros
- **THEN** no existe `insumo.max-dosis-24h`
