# Change: add-full-demo-mode

## Why

Para demostraciones a clientes, agrónomos y exposiciones técnicas, Yerbanalytics necesita exhibir
su ciclo ciberfísico completo (sensado → detección → decisión agronómica → actuación en hardware ESP32)
sin esperar los intervalos reales de sensado (típicamente 4 horas) y sin que las reglas de protección
temporal (cooldowns de riego, ventana horaria de 06:00 a 18:00, límite de 12 horas en déficit crítico,
pausa tras fertilizante) bloqueen ejecuciones sucesivas durante la demo.

Actualmente:
- El motor de reglas evalúa todas las reglas con sus tiempos de enfriamiento (*cooldowns* / CDs)
  productivos estrictos: un sector regado debe esperar hasta el siguiente ciclo de 4 horas para volver a regar.
- El pronóstico climático depende de la API externa de Open-Meteo, imposibilitando simular lluvia inminente
  o días nublados bajo demanda.
- No existe mecanismo para encender, apagar o ajustar los cooldowns de reglas individuales en tiempo de ejecución.

Se busca habilitar un **Modo Simulado / Full Demo** configurable mediante **feature flags**
(`application.properties` y variables de entorno en Docker) con una premisa de diseño clara:
**cero clases duplicadas**. Las reglas de negocio mantienen la misma lógica en producción y en demo,
pero incorporan **flags de activación (ON/OFF)** y **configuración individual de cooldown (CD) por regla**:
- En **producción**: cada regla opera con su cooldown establecido por el usuario / configuración agronómica
  (ej. 4 horas en ciclo de lectura, 12 horas en déficit crítico, 4 horas en pausa tras insumos).
- En **modo demo**: cada regla puede tener un cooldown diferente o ser seteado a `0` (sin CD), permitiendo
  re-regar o re-actuar inmediatamente de manera selectiva, y el operador puede encender y apagar reglas individuales a merced.

## What

- **Misma Base de Código (Sin Clases Duplicadas)**:
  - Las reglas existentes (`CicloLecturaRiegoRule`, `DeficitCriticoRule`, `PausaTrasAplicacionRule`,
    `FueraDeVentanaRiegoRule`, etc.) se mantienen como única fuente de verdad.
  - Cada regla consulta su cooldown efectivo individual (`ctx.effectiveCooldownMinutes(ruleName, defaultMinutes)`):
    - En producción: aplica estrictamente el cooldown configurado por el usuario.
    - En modo demo: admite sobreescribir el cooldown de cada regla de forma independiente (pudiendo ser `0`
      para omitir esperas o un valor específico para pruebas de tiempo).

- **Flags de Encendido/Apagado y Cooldowns Individuales**:
  - `RuleOrchestrator` gestiona un mapa en memoria de configuración por regla (`enabled: boolean`, `cooldownMinutes: Integer`).
  - Si una regla se apaga (`enabled = false`), el orquestador la saltea y la marca en la traza como
    `OMITIDA_DESHABILITADA` sin interrumpir la evaluación de las restantes.
  - Endpoints REST bajo `/api/demo/rules`:
    - `GET /api/demo/rules`: lista de reglas con estado (ON/OFF) y cooldowns individuales.
    - `PUT /api/demo/rules/{ruleName}`: actualiza activación y/o cooldown de una regla específica.
    - `PUT /api/demo/rules/cooldowns/zero`: atajo para poner todos los cooldowns en 0.

- **Simulación de API Climática**:
  - `WeatherService` admite inyectar un pronóstico meteorológico ficticio en memoria vía `POST /api/demo/weather`.
  - En demo, devuelve los valores ficticios cargados (temperatura, lluvia, UV) sin llamadas a Open-Meteo.
  - En producción, consulta Open-Meteo con normalidad.

- **Botón "Leer Ahora" en el Simulador**:
  - Habilitado en la UI web del simulador (`:5180`) en modo demo.
  - Dispara un pulso manual con ejecución de motor (`POST /api/pulsos`).
  - El backend emite `LEER_AHORA` y el simulador responde con la telemetría ficticia.
  - El motor evalúa con las reglas encendidas y aplicando sus cooldowns configurados, **guarda la acción en el historial real**,
    y despacha la orden física hacia el ESP32 por MQTT.

- **Configuración por Feature Flags y Cero Rastro**:
  - Propiedades: `yerbanalytics.mode=REAL` (o `SIMULATED`) y `yerbanalytics.demo.enabled=false` (o `true`).
  - Override por Docker: `YERBANALYTICS_MODE=SIMULATED` / `YERBANALYTICS_DEMO_ENABLED=true`.
  - Cuando está en modo real, los controladores de demo no se cargan (`@ConditionalOnProperty`) y los
    cooldowns permanecen siempre activos como en producción.

## Fuera de alcance

- Duplicar clases de reglas o crear jerarquías de políticas redundantes.
- Crear tablas en base de datos de producción para almacenar flags temporales de la demo.
- Alterar los tópicos o contratos MQTT físicos.

## Capabilities

| Tipo | Capability | Qué cubre |
|---|---|---|
| Nueva | `modo-operacion` | Feature flags (`REAL`/`SIMULATED`), override en Docker por variables de entorno y conditional configuration sin rastro en producción. |
| Nueva | `bypass-reglas` | Flags de activación (ON/OFF) y configuración individual de cooldowns (CDs) por regla en el motor agronómico, traza `OMITIDA_DESHABILITADA` y API REST. |
| Nueva | `simulacion-clima` | Inyección en memoria de pronósticos meteorológicos ficticios en `WeatherService` y control desde el simulador. |
| Nueva | `simulador-lectura-interactiva` | Botón "Leer ahora" en la UI del simulador, ingreso de métricas, ciclo interactivo completo con afectación en historial y comando físico al ESP32. |

## Rollback

Configurar `YERBANALYTICS_MODE=REAL` o `YERBANALYTICS_DEMO_ENABLED=false` en el entorno. No se agregan
tablas ni migraciones SQL, por lo que la base de datos no sufre cambios.

## Criterios de éxito

- [ ] Las clases de reglas existentes son las únicas implementadas (sin clases `DemoRule*` duplicadas).
- [ ] En modo producción (`demo.enabled=false`):
  - Los endpoints `/api/demo/**` retornan 404.
  - Los cooldowns de riego individuales de cada regla se aplican estrictamente.
  - `./mvnw test` pasa en verde.
- [ ] En modo demo (`demo.enabled=true`):
  - Con `cooldownMinutes = 0` en `CicloLecturaRiegoRule`, es posible ejecutar riegos consecutivos en el mismo sector sin esperar 4 horas.
  - Cada regla admite un cooldown diferenciado (ej: Rule A con 0 min y Rule B con 60 min).
  - Es posible apagar una regla específica (ej. `ManualLockRule`) vía `PUT /api/demo/rules/{name}` y verificar
    que en la siguiente evaluación figura como `OMITIDA_DESHABILITADA`.
  - Es posible inyectar lluvia ficticia vía `POST /api/demo/weather` y comprobar que `PosponerPorLluviaRule` reacciona.
- [ ] Al presionar "Leer ahora" en el simulador:
  - Se genera la telemetría, se evalúan las reglas activas con sus cooldowns configurados y se registra el evento en `/api/historial`.
  - El backend emite la orden al ESP32 por MQTT.
