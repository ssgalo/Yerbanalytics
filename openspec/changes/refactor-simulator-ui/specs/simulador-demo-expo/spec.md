## ADDED Requirements

### Requirement: Consola de Demo Expo en el simulador
El simulador SHALL hospedar la consola de Demo Expo dentro de su interfaz (`:5180`), integrando en una misma sección el control y monitoreo de la pasada del riel y las secuencias guionadas directas a los actuadores, comunicándose exclusivamente a través de su proxy `/backend/**`.

#### Scenario: Visualización de Demo Expo en el simulador
- **WHEN** el usuario accede a la pestaña "Demo Expo" en el simulador
- **THEN** se presenta el panel de la pasada del riel junto con las tarjetas de secuencias de actuadores

### Requirement: Ejecución y seguimiento de la pasada del riel
El simulador SHALL permitir iniciar una pasada del riel mediante `POST /backend/api/pasadas` y cancelarla mediante `POST /backend/api/pasadas/actual/cancelar`. Mientras la pasada esté en curso, el simulador SHALL consultar su estado cada 1 s; una vez finalizada, SHALL consultar cada 3 s mientras resten diagnósticos de IA pendientes (hasta un máximo de 5 minutos). SHALL mostrar el titular del estado, la barra de progreso proporcional a los pasos terminados y la lista ordenada de pasos con su fotografía y diagnóstico.

#### Scenario: Iniciar pasada del riel
- **WHEN** el usuario pulsa "Iniciar pasada" sin operaciones previas en curso
- **THEN** el simulador envía la orden de inicio al backend y comienza el sondeo de progreso

#### Scenario: Progreso paso a paso
- **WHEN** la pasada avanza por el movimiento del riel, captura cenital e inferencia de IA
- **THEN** la interfaz actualiza los pasos en tiempo real mostrando las miniaturas de las imágenes recibidas y el diagnóstico obtenido

#### Scenario: Cancelar pasada en curso
- **WHEN** el usuario pulsa "Cancelar" durante una pasada activa
- **THEN** el simulador solicita la cancelación al backend y refleja el estado de cancelando hasta que el riel retorna a home

### Requirement: Secuencias directas de actuadores con guardia de hardware
El simulador SHALL ofrecer tres tarjetas de comandos directos para demostración en stand:
1. **Riego**: Apertura de válvula con duración configurable (1 a 120 s), espera y cierre.
2. **Mediasombra**: Despliegue con espera configurable (0 a 600 s) y enrollado.
3. **Lectura de sensores**: Pedido de lectura al nodo testigo de la zona, con opción adicional de ejecutar el motor de reglas (`ejecutarMotor: true`).
Los controles SHALL deshabilitarse si hay una pasada o secuencia en curso, y el simulador SHALL manejar las respuestas HTTP 409 informando que el equipo está ocupado.

#### Scenario: Ejecutar secuencia de riego
- **WHEN** el usuario especifica 15 segundos y presiona "Regar"
- **THEN** el simulador envía `POST /backend/api/secuencias` con tipo `RIEGO` y `duracionSeg: 15`, y muestra el progreso del paso con cuenta regresiva

#### Scenario: Ejecutar lectura de sensores con motor
- **WHEN** el usuario presiona "Leer sensores y ejecutar motor ahora"
- **THEN** el simulador envía `POST /backend/api/secuencias` con tipo `LECTURA` y `ejecutarMotor: true`

#### Scenario: Concurrencia entre pasada y secuencia
- **WHEN** el usuario intenta iniciar una secuencia mientras una pasada está en curso
- **THEN** el backend responde con código 409 y el simulador muestra el mensaje indicando que hay una pasada en curso
