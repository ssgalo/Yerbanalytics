# Spec: sensado-macrozona

## ADDED Requirements

### Requirement: Panel de valores sensados de la macro-zona

La vista de macro-zona SHALL mostrar un panel "Valores sensados · nodo testigo" con la
lectura del único nodo sensor de esa macro-zona. El panel SHALL dejar explícito que la
lectura corresponde a la macro-zona completa y no a un sector individual.

#### Scenario: Lectura visible al seleccionar una macro-zona

- **WHEN** el usuario selecciona una macro-zona
- **THEN** el panel muestra la lectura vigente de esa macro-zona
- **AND** el encabezado identifica el nodo testigo como origen del dato

#### Scenario: Cambio de macro-zona

- **WHEN** el usuario cambia a otra macro-zona
- **THEN** el panel se actualiza con la lectura de la nueva macro-zona

### Requirement: Set de diez métricas monitoreadas

El panel SHALL mostrar las diez métricas que publica el nodo: humedad de sustrato
(`humSus`), humedad ambiental (`humAmb`), temperatura del aire (`temp`), temperatura del
sustrato (`tempSuelo`), nutrientes/CE (`ce`), pH del sustrato (`phSuelo`), nitrógeno
(`n`), fósforo (`p`), potasio (`k`) y luminosidad (`uv`). Cada métrica SHALL mostrar
valor, unidad, rango óptimo y color según su estado (ok / warning / critical) evaluado
contra sus umbrales configurados.

#### Scenario: Métrica dentro del rango óptimo

- **WHEN** el valor de una métrica cae dentro de su banda ideal
- **THEN** se muestra con el color de estado saludable

#### Scenario: Métrica fuera de banda

- **WHEN** el valor de una métrica cae fuera de su banda de advertencia
- **THEN** se muestra con el color crítico y su rango óptimo queda visible como referencia

#### Scenario: Métrica sin dato

- **WHEN** el nodo no reportó una métrica (sensor con falla o payload parcial)
- **THEN** esa métrica se muestra como "—" sin color de estado, sin afectar al resto

### Requirement: Histórico navegable por métrica, en un inspector

El panel SHALL permitir seleccionar cualquiera de las diez métricas y ver su serie
histórica, con selector de rango temporal 24h / 7d / 30d. El histórico SHALL presentarse
como un inspector que entra deslizándose desde el borde del panel y cubre la grilla de
sectores, SIN tapar los botones de las métricas: el usuario tiene que poder saltar de una
métrica a otra sin cerrarlo. El inspector SHALL empezar cerrado.

#### Scenario: Apertura del inspector

- **WHEN** el usuario hace clic en una métrica del panel
- **THEN** el inspector entra con su serie histórica y la métrica queda destacada en el panel
- **AND** los botones de las demás métricas siguen accesibles

#### Scenario: Cierre del inspector

- **WHEN** el usuario cierra el inspector, o vuelve a hacer clic en la métrica abierta
- **THEN** el inspector sale con la misma animación de la entrada, conservando su contenido
  visible durante todo el recorrido

#### Scenario: Cambio de rango temporal

- **WHEN** el usuario cambia el rango del gráfico
- **THEN** la serie de la métrica seleccionada se recalcula para ese rango

### Requirement: Estado del nodo testigo

El panel SHALL mostrar el estado del nodo que produjo la lectura: nivel de batería,
calidad de señal y antigüedad de la última lectura. SHALL señalar de forma visible cuando
la batería está baja o cuando el nodo lleva demasiado tiempo sin reportar.

#### Scenario: Nodo operativo

- **WHEN** el nodo reportó recientemente
- **THEN** se muestran batería, señal y el tiempo transcurrido desde la última lectura

#### Scenario: Nodo sin reportar

- **WHEN** el nodo superó el umbral de silencio
- **THEN** el panel marca la lectura como desactualizada y advierte que los valores
  mostrados no son vigentes

#### Scenario: Batería baja

- **WHEN** el nivel de batería del nodo cae bajo el umbral definido
- **THEN** el panel lo señala visualmente

### Requirement: Métricas nuevas informativas hasta validar sus umbrales

El sistema SHALL marcar como informativas las cinco métricas incorporadas en este cambio
—temperatura del sustrato, pH del sustrato, nitrógeno, fósforo y potasio—: se muestran y se
evalúan contra sus umbrales para colorearse, pero NO SHALL participar del cálculo del
estado de salud de los sectores hasta que sus rangos sean validados con el vivero. Las
cinco métricas originales conservan ese rol.

#### Scenario: Métrica informativa fuera de banda

- **WHEN** el pH del sustrato cae fuera de su banda ideal y todas las métricas originales
  están dentro de rango
- **THEN** la métrica se muestra con color de advertencia en el panel
- **AND** los sectores de la macro-zona permanecen en estado saludable

#### Scenario: Rangos señalados como provisionales

- **WHEN** el usuario consulta los umbrales de una métrica informativa
- **THEN** el sistema indica que su rango es provisional y está pendiente de validación

### Requirement: Vuelta del sector a su macro-zona

El detalle de un sector SHALL ofrecer una vuelta atrás a la macro-zona a la que pertenece,
donde están sus valores sensados. La navegación SHALL identificar la macro-zona, sin
repetir sus lecturas dentro del sector.

#### Scenario: Navegación a la zona correcta

- **WHEN** el usuario vuelve atrás desde el detalle de un sector
- **THEN** llega a la vista de macro-zona con la zona de ese sector ya seleccionada

### Requirement: Acceso exclusivo desde el panel general

La vista de macro-zona SHALL alcanzarse únicamente eligiendo una macro-zona en el panel
general, y NO SHALL ofrecer un selector de zonas adentro: una vez elegida, sólo se vuelve.
Una ruta sin macro-zona SHALL redirigir al panel general, porque sin esa elección no hay
nada que mostrar.

#### Scenario: Entrada desde el panel general

- **WHEN** el usuario elige una macro-zona en el panel general
- **THEN** entra a su detalle, y desde adentro sólo puede volver

#### Scenario: Ruta sin macro-zona

- **WHEN** se abre la vista sin una macro-zona indicada, o con una que no existe
- **THEN** el sistema redirige al panel general
