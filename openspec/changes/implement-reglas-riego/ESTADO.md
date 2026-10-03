# Estado al cortar la sesión

- Último commit válido: ver `git log` (rama `feat/implementar-nuevas-reglas`).
- §0-§13 completos; §11.2 (compilar el firmware) queda sin tildar: no hay toolchain.
- §14 (documentación) hecho: `diferencias-motor-reglas-vs-reglas-v2.md`, `circuito-sensado-a-motor.md`, `conectar-esp32.md`, README del backend y `CLAUDE.md`.
- Falta: §15 (verificación manual con simulador y broker) y compilar el firmware (11.2).
- Correcciones de la revisión de la conmutación (ver `design.md`, "Correcciones de la revisión de la conmutación"):
  C1 la ronda se completa (cancelación explícita), C2 el despacho revalida al abrir, C3 R-02 sin guarda de ciclo,
  C4 último riego en memoria — hechas (commit `fix(backend): completar la ronda de riego y revalidar al despachar`).
  C5 el pronóstico no traba el hilo MQTT, C6 índices de `historial_evento` — hechas (commit `fix(backend): que el motor no se trabe sin pronóstico ni con el historial grande`).
  C7 registro de inacción sólo ante un cambio (DA-13) — hecha (commit `perf(backend): registrar la inacción del motor sólo cuando cambia`).
  Frontend alineado (fixture del catálogo, traza mock de la regla de ciclo y agrupación de consumidores) — hecho (commit `fix(frontend): …`).
- Correcciones C8-C11 (pausa en vez de descarte, vencimiento de solicitudes, revalidación de R-06/R-03/ciclo/tope en el
  despacho, R-04 antes de la guarda de ciclo) — hechas (commit `fix(backend): pausar la ronda de riego en vez de descartarla
  y revalidar las precondiciones de R-01`); detalle en `design.md`.
- Correcciones C12-C14 (timeouts del pronóstico, estado en memoria tras el commit, migración del umbral, dosis diaria, zona aislada
  en el despacho) — hechas (commit `fix(backend): timeouts del pronóstico, estado en memoria tras el commit y migración del umbral`).
- Frontend (verificación en modo http): `/historial` ya no cuenta como sector los eventos "—" (Configuración y alertas de zona), el
  dashboard usa una clave única por diagnóstico — hecho (commit `fix(frontend): …`). Fixture y traza mock de C8-C11 ya iban en el commit de C8-C11.
- Sin verificar: arranque real de Spring con el despacho contra el broker; `@Scheduled(scheduler="despachoScheduler")` en vivo;
  el despacho revalidando contra la base real (los tests usan repositorios falsos).
