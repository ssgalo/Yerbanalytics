# Estado al cortar la sesión

- Último commit válido: ver `git log` (rama `feat/implementar-nuevas-reglas`).
- §0-§13 completos; §11.2 (compilar el firmware) queda sin tildar: no hay toolchain.
- Falta: §14 (documentación), §15 (verificación manual con simulador y broker) y compilar el firmware (11.2).
- Correcciones de la revisión de la conmutación (ver `design.md`, "Correcciones de la revisión de la conmutación"):
  C1 la ronda se completa (cancelación explícita), C2 el despacho revalida al abrir, C3 R-02 sin guarda de ciclo,
  C4 último riego en memoria — hechas (commit `fix(backend): completar la ronda de riego y revalidar al despachar`).
  C5 el pronóstico no traba el hilo MQTT, C6 índices de `historial_evento` — hechas (commit `fix(backend): que el motor no se trabe sin pronóstico ni con el historial grande`).
  C7 registro de inacción sólo ante un cambio (DA-13) — hecha (commit `perf(backend): registrar la inacción del motor sólo cuando cambia`).
  Frontend alineado (fixture del catálogo, traza mock de la regla de ciclo y agrupación de consumidores) — hecho (commit `fix(frontend): …`).
- Sin verificar: arranque real de Spring con el despacho contra el broker; `@Scheduled(scheduler="despachoScheduler")` en vivo;
  el despacho revalidando contra la base real (los tests usan repositorios falsos).
