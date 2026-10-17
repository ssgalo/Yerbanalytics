# Tasks: align-reglas-existentes-v2

**TDD estricto**, un commit por bloque (ver convenciones en `reglas-v2-overview/README.md`).
Rama: `feat/reglas-v2-align`.

## 0. Decisiones previas
- [ ] 0.1 Confirmar con el equipo D-2 (tope de R-02), D-3 (guarda de ciclo) y D-6 (escalamiento por pulso).
      Sin respuesta se aplica el default del diseño.
- [x] 0.2 Leer `CicloLecturaRiegoRule` completa y `SensingCadence` (no verificado en la auditoría).

## 1. Confianza mínima 70 %
- [x] 1.1 Test: `CatalogoParametros` siembra 70 con rango 50–95; 97 se rechaza.
- [x] 1.2 Cambiar fábrica y rango en `ParametrosDiagnostico.CONFIANZA_MINIMA`.
- [x] 1.3 Test: una fila existente con valor distinto no se pisa.
- [x] 1.4 `resources/migracion-confianza-70.sql` y entrada en el README del backend (lista de migraciones).
- [x] 1.5 Frontend: el rango sale del catálogo; test de que Parámetros muestra 50–95. Sin valores sueltos.
- [x] 1.6 Actualizar `servicio-inferencia/README.md` y `env.example` si citan 85 %.

## 2. Parámetros de sensado en el catálogo
- [x] 2.1 Test: cuatro claves nuevas, fábrica y rango de la tabla del diseño.
- [x] 2.2 `ParametrosSensado` (familia nueva `SENSADO`); `PulsoProperties` toma el valor inicial del catálogo.
- [x] 2.3 Test: cambiar `sensado.reintentos` cambia los pedidos del pulso (`PulsoSensadoService`).
- [x] 2.4 Frontend: la familia aparece en Motor de reglas → Parámetros.

## 3. Estado seguro (S-02)
- [x] 3.1 Test: el 3.er `registrarSinRespuesta` pide el estado seguro una vez; el 4.º no.
- [x] 3.2 Test: con el nodo de vuelta no hay ningún comando.
- [x] 3.3 Implementar vía `ComandoActuadorPublisher` (válvulas, bombas, mediasombra cerrada).
- [x] 3.4 Test: `SeguimientoComandosService` vigila esos comandos.

## 4. Reintento con nodo caído
- [x] 4.1 Test en `SensingScheduler`: zona con cuenta ≥ 3 recibe `LEER_AHORA` cada 15 min; zona sana, no.
- [x] 4.2 Implementar con el reloj inyectado.

## 5. Guardas
- [x] 5.1 Test: con lectura vencida, `StaleSensorRule` aborta el riego y la mediasombra se evalúa.
- [x] 5.2 Test de D-3 (dos lecturas seguidas) y resolver según el resultado.
- [x] 5.3 Si D-2 = (a): anotar la desviación en la cabecera de `DeficitCriticoRule` y en el README.

## 6. Cierre
- [ ] 6.1 Textos con "85 %" o reglas v1 en trazas y comentarios.
- [ ] 6.2 `./mvnw test`, `npm test && npm run lint` verdes.
- [ ] 6.3 Actualizar CLAUDE.md §6 (migraciones manuales) y marcar el cambio en el índice.

## 7. Control desde el simulador (requiere `add-control-demo-reglas`)
- [ ] 7.1 `EscalamientoNodoService` implementa `DemoControllable` (apagado, bypass `safe-state`, cooldown `retry-offline`); test de cada uno.
- [ ] 7.2 Escenario `NODE_OFFLINE` en el simulador (pausa la emisión de una zona y pide 3 pulsos) con su test.
- [ ] 7.3 Simulador: `DEFAULT_THRESHOLDS['diagnostico.confianza-minima']` = 70 (hoy 85, cableado en `ruleConditions.ts`); ajustar `ruleConditions.test.ts`.
- [ ] 7.4 Test: las reglas ya existentes (`ManualLockRule`, `StaleSensorRule`, R-01…R-06, `CicloLecturaRiegoRule`) conservan sus claves de cooldown y bypass.
