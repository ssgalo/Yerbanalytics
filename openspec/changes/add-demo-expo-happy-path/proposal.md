# Change: add-demo-expo-happy-path

> Rama: `feat/demo-expo-happy-path`. Cambio **sólo del simulador**: el dashboard productivo no se toca y el backend sólo recibe la matriz de permisos del rol Servicio (ver design D7).

## Why

Para la Demo Expo hace falta mostrar, con un solo gesto, el camino feliz del sistema: **riego** por déficit, **insumo**
según el diagnóstico de IA y **mediasombra** protegiéndose del UV. Hoy eso exige armar a mano varios escenarios en
*Automatización*, poner cooldowns en 0, saltear la ventana horaria de riego y registrar un diagnóstico, en el orden
correcto. Además, desde PR #53 el backend puede leer la telemetría del ESP32 real o del simulador, y el happy path tiene
que respetar esa elección: con el ESP32 real el simulador **no debe inyectar** nada.

## What Changes

- Botón **Happy path de la demo** en la pestaña *Demo Expo* (junto a la pasada del riel y las secuencias), con su tarjeta de resultado.
- **Fuente = Simulador**: combina los escenarios existentes R01 + SUPPLY + SHADING en una sola lectura coherente, pone
  los cooldowns en 0, saltea R-05, registra el diagnóstico antes del pulso y dispara **un** pulso con motor.
- **Fuente = ESP32 real**: no inyecta nada; quita un clima simulado vivo, avisa si el sector no tiene un diagnóstico
  utilizable (de una pasada del riel previa) y dispara un pulso.
- Tras el pulso lee `GET /api/rules/evaluaciones/{sectorId}` y muestra por regla si actuó y el motivo.

Fuera de alcance: backend, frontend productivo, nuevos escenarios, cambiar la fuente de telemetría desde el botón.

## Impact

- `Desarrollo/simulador/src/happyPath.ts` (+ test), `components/HappyPathPanel.tsx`, `DemoExpoSection.tsx`, `App.tsx`, `api.ts`, `types.ts`, README.
- Backend: `Permiso.java` (+`reglas.ver`, `pasadas.ver`, `pasadas.operar` para Servicio), `PermisoTest`, `migracion-permisos-servicio-simulador.sql`, CLAUDE.md §6 y README del backend.
- Sin endpoints nuevos: sólo API pública por el proxy `/backend/**` con la cuenta Servicio.
