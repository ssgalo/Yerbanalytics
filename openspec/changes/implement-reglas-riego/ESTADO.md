# Estado al cortar la sesión

- Último commit válido: `c5de65c` (rama `feat/implementar-nuevas-reglas`). Árbol limpio de cambios míos, compila, suite: 535 tests, sólo fallan las 9 del baseline (NurseryControllerTest 1, DiagnosticoControllerTest 8).
- Commits: `6be6e6a` §9 (reglas sin registrar), `3437a15` fix de la revisión (8 puntos), `c5de65c` §10 conmutación.
- §9 completo (9.1-9.8). §10 completo (10.1-10.7); 10.4 se hizo sin @SpringBootTest (ver design.md, "Desvíos de los bloques 9 y 10").
- Nada a medio editar. Sin verificar: arranque real de Spring con el despacho (no se levantó el backend contra el broker), `@Scheduled(scheduler="despachoScheduler")` en vivo.
- Siguiente paso: §11 (contrato 1200 s, firmware, simulador), §12 (baja de riegoVolMaxDiarioMl; agregar DROP COLUMN al script), §13 frontend, §14 docs.
