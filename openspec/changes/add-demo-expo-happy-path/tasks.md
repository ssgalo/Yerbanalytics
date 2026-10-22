# Tasks: add-demo-expo-happy-path

TDD estricto. Suites: en `Desarrollo/simulador`, `npm test && npm run typecheck`.

## 1. Lógica pura (`src/happyPath.ts`)
- [x] 1.1 Tests: elección del sector destino, plan del modo simulador (valores coherentes y desde el catálogo), bypass de R-05, resumen de la traza, pre-chequeo y pista de modo.
- [x] 1.2 Orquestación con dependencias inyectadas: orden de la corrida en cada modo, espera de 409, traza nueva vs. vieja, error sin sensor.

## 2. UI
- [x] 2.1 `api.getEvaluationTrace` y `api.getSectorDiagnosis`; tipos de la traza.
- [x] 2.2 `HappyPathPanel` en la Demo Expo (modo, sector, bitácora, resultado por regla).

## 2b. Revisión
- [x] 2b.1 Lectura fijada + emisión encendida antes del pulso, restauradas en `finally`; limpieza de pin en ESP32 real; sondeo de traza tolerante a errores; test de `humSus` entre crítico (incl.) y riego (excl.) con umbrales del catálogo.

## 2c. Una carga por vez (REEMPLAZADO por `add-cola-actuadores-nodo`: vuelve a un solo pulso)
- [x] 2c.1 (histórico, ya no rige) Tres pulsos con reglas ejecutoras encendidas de a una, espera de fin de carga (`waitUntilFinished`) + 5 s, restauración de flags en `finally`, estado por paso en la tarjeta.

## 3. Permisos del rol Servicio y mediasombra
- [x] 3.0 Test de la matriz por defecto + `Permiso.java` + migración idempotente + docs; aviso visible si el catálogo falla; redacción «cerrada/abierta» con el umbral 50 espejado.

## 4. Docs
- [x] 4.1 README del simulador.
- [ ] 4.2 Verificación en vivo con el backend en modo demo, en ambos modos (pendiente: no se pudo correr acá).
