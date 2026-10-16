## MODIFIED Requirements

### Requirement: Interfaz propia del simulador
El simulador SHALL presentar su propia interfaz autocontenida, sin depender de los estilos ni de los componentes del dashboard productivo. La interfaz SHALL estructurarse en 5 apartados funcionales accesibles mediante navegación superior por pestañas:
1. **Configuración General**: topología del vivero, gestión de sensores simulados, opción de registro en hardware del backend y visualización de parámetros operativos.
2. **Telemetría y Reglas**: envío de telemetría manual por métrica o conjunta, emisión automática, control ON/OFF de reglas, ajuste de cooldowns y simulación de pronóstico climático.
3. **Automatización de Reglas**: panel de ejecución automática con checkboxes por regla y cálculo de telemetría basado en umbrales.
4. **Cámara**: dispositivo de captura enrolado, subida directa auto-simulada, órdenes de captura cenital y carga manual de diagnósticos.
5. **Demo Expo**: consola integrada para la pasada del riel y las secuencias guionadas directas a actuadores.

#### Scenario: Navegación organizada por apartados
- **WHEN** el usuario utiliza el simulador
- **THEN** navega de manera limpia entre los cinco apartados funcionales mediante la barra superior sin recargar la página

#### Scenario: La interfaz no depende del dashboard
- **WHEN** se auditan los estilos y componentes del simulador
- **THEN** no importa archivos de `Desarrollo/frontend/src/` ni comparte tokens del sistema de diseño, manteniendo estilos propios en `Desarrollo/simulador/src/styles.css`

#### Scenario: Todo el control en un solo lugar
- **WHEN** el usuario interactúa con el simulador
- **THEN** gestiona sensores, telemetría, reglas, clima, cámara y secuencias de demo expo dentro de la misma aplicación sin salir de `:5180`

## ADDED Requirements

### Requirement: Sincronización de hardware con el backend
Al dar de alta un sensor simulado en el simulador, la interfaz SHALL ofrecer una opción para registrar automáticamente el dispositivo en la flota de hardware del backend (`POST /backend/api/hardware`) con el mismo serial/MAC, tipo "Nodo testigo" y la macro-zona asignada, garantizando que el sistema reconozca su presencia y actualice su estado técnico sin necesidad de cargarlo manualmente en el dashboard productivo.

#### Scenario: Alta dual de sensor simulado y dispositivo de hardware
- **WHEN** el usuario crea un sensor simulado con la casilla "Registrar en hardware del backend" marcada
- **THEN** el sensor se agrega a la lista local del simulador
- **AND** se crea el dispositivo en la base de datos del backend a través de su endpoint público de hardware
