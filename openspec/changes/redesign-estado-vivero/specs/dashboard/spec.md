## MODIFIED Requirements

### Requirement: Vista general del vivero
El Panel general SHALL mostrar un plano del vivero con cada macro-zona como parcela —badge "MZ-N",
ícono de nodo testigo, color según su peor estado y resumen en una frase— y debajo un bloque por
macro-zona con su grilla de sectores. SHALL incluir un resumen del vivero en una frase y la tira
"Cómo leer esta pantalla" (Vivero → Macro-zona → Sector → Bandeja). Las macro-zonas y los sectores
por fila SHALL respetar la disposición configurada en la topología.

#### Scenario: Resumen en palabras
- **WHEN** una zona tiene 3 sectores en observación o críticos
- **THEN** su resumen dice "3 sectores necesitan atención"; con 1, "1 sector necesita atención";
  con 0, "Todo bien"; con el nodo sin datos, "Sin datos del sensor"

#### Scenario: Contadores en palabras
- **WHEN** se muestra el bloque de una zona
- **THEN** los contadores dicen "X saludables · Y en observación · Z sin señal" (más "críticos" si
  hay) y "N sectores · M plantines", sin abreviaturas

#### Scenario: Color por peor estado
- **WHEN** una zona tiene algún sector crítico
- **THEN** su parcela y su badge usan el color crítico; sin datos del nodo, el de sin señal

#### Scenario: Tocar una parcela
- **WHEN** el usuario toca una parcela del plano
- **THEN** la pantalla se desplaza hasta el bloque de esa zona y lo destaca

#### Scenario: Nodo testigo en la zona
- **WHEN** se muestra el encabezado de una zona
- **THEN** incluye un chip del nodo testigo con batería y señal, o "Sin datos" si no reportó

#### Scenario: Celdas de tamaño acotado
- **WHEN** una zona tiene 1 sector, o 100
- **THEN** cada celda mide entre 10 y 22 px y la grilla queda centrada; nunca ocupa todo el ancho

#### Scenario: Abrir sector y zona
- **WHEN** el usuario hace clic en una celda
- **THEN** navega al detalle del sector
- **AND** "Ver detalle de la zona" navega a `/mapa?zona=` de esa zona
