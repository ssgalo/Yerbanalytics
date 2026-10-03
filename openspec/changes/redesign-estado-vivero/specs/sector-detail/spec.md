## ADDED Requirements

### Requirement: Dibujo físico del sector
El detalle de sector SHALL mostrar un breadcrumb Vivero / macro-zona / sector, un explicador de la
jerarquía y un dibujo del sector físico: 4 bandejas de 25 tubetes (5×5), un microaspersor compartido
con su área de riego y el riel con la cámara. El nodo testigo SHALL mostrarse fuera de la caja del
sector, indicando que es de la macro-zona. Los 100 tubetes SHALL pintarse de forma uniforme con el
color de estado del sector. NO SHALL señalarse cuál plantín fue fotografiado.

#### Scenario: Estructura del dibujo
- **WHEN** se abre un sector
- **THEN** el dibujo contiene 4 bandejas y 100 tubetes dentro de la caja del sector

#### Scenario: Nodo testigo aparte
- **WHEN** se abre un sector
- **THEN** el nodo testigo aparece fuera de la caja del sector, rotulado con su macro-zona

#### Scenario: Diagnóstico a nivel sector
- **WHEN** se abre un sector con diagnóstico
- **THEN** el panel de diagnóstico se muestra directamente, y el texto aclara que surge de una foto
  del sector y se aplica al sector completo, con la etiqueta "a futuro" para más fotos por sector

#### Scenario: Sector sin señal
- **WHEN** el sector está sin señal
- **THEN** los tubetes se pintan con el color de sin señal y se avisa que no hay lectura vigente
