## 1. Backend

- [x] 1.1 Tests de la secuencia `DOSIS` en `SecuenciaServiceTest` (destino, ml por defecto, camino feliz, sin ACK, en_cola, cortada, cerrar sin ACK, cancelar, validación, topes, parámetros de cada tipo)
- [x] 1.2 `ParametrosSecuencia.ml` y `SecuenciaService`: validación, pasos ABRIR/ESPERAR/CERRAR sobre `pump`, plazo del ACK por ml, textos de error
- [x] 1.3 Suite completa del backend: sin regresiones. Fallan 36 tests de integración (Nursery, Nutrition, Biotic, Chlorosis, Diagnostico, OperationalRuleStates) que ya fallaban sin este cambio: leen la base de desarrollo real, que hoy tiene la topología recortada de la expo (MZ-1 con 2 sectores)

## 2. Simulador

- [x] 2.1 Tests de los textos de los pasos (`src/secuencias.test.ts`)
- [x] 2.2 Textos a un módulo puro (`src/secuencias.ts`) y tarjeta «Dosificación» en `SecuenciasSimulatorPanel`
- [x] 2.3 `npm test` y `npm run typecheck` en verde

## 3. Documentación

- [x] 3.1 README del backend («Secuencias de la Demo Expo») y `CLAUDE.md`

## 4. Prueba con hardware

> A tener en cuenta (revisión): `segundosDeDosis` usa `ContratoNodo.BOMBA_INSUMO_MS_POR_ML` (1000), espejo de la calibración
> `BOMBA_INSUMO_MS_POR_ML` del `config.h`. Si se recalibra la bomba, hay que actualizar los dos o el plazo del ACK queda corto.

- [ ] 4.1 Con el ESP32 por USB y la bomba conectada: «Dosificar» 5 ml desde el simulador, la bomba larga la cantidad y la secuencia queda `COMPLETADA`
