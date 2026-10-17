# Design: add-plan-nutricion

## Estado verificado
- No existe regla ni servicio de nutrición. La CE llega como métrica de la zona (`ce`, µS/cm → persistida en dS/m).
- Verificado: no hay historial de lecturas. N-02 usa `HistorialLecturas.media(zona, "ce", 24 h, soloPulso=true)` de
  `add-historial-lecturas`, que es prerrequisito.

## Decisiones

### D-1 — Evaluación por MZ, aplicación por sector
Como la mediasombra, N-01 y N-02 son **de zona** (el plan es por MZ; la CE es de la MZ). Se evalúan en el cierre del
pulso y en la apertura de cada ventana de aplicación. Generan una `aplicacion_pendiente` por sector (100 sectores) ⇒
el trabajo real lo hace `add-dosificacion-base`. Usa el `ZoneRuleEngine` / `ZoneRule` de `add-mediasombra-v2` D-1 (verificado: hoy
el motor sólo evalúa por sector). Si este cambio se implementa antes que la mediasombra, **crea** el `ZoneRuleEngine` (la tarea 3.0 de
mediasombra describe la interfaz) y la mediasombra lo reutiliza; quien llegue primero lo crea.

### D-2 — "Última aplicación del plan" de la MZ
Campo `ultima_aplicacion_plan` en la zona, escrito cuando se **completa** la tanda (todos los sectores aplicados o
descartados con motivo), no al primer sector. Si el plan arranca y algunos sectores quedan diferidos, N-01 no se
vuelve a disparar para ellos: siguen pendientes (idempotencia por `ciclo_id`).

### D-3 — N-02
Media de las últimas **6 lecturas** (24 h con pulso de 4 h) de CE de la MZ. Si hay menos de 6 lecturas ⇒ `SIN_DATO`,
no actúa. "No hubo fertilización en 5 días" mira **cualquier** aplicación de fertilizante en la MZ (plan o correctiva),
con el intervalo de 5 días como parámetro. Adelantar = fijar `ultima_aplicacion_plan` de modo que N-01 venza ya
(`proximo_plan = ahora`), sin tocar el historial de aplicaciones.

### D-4 — Una alerta por evento
N-02 alerta al activarse y no vuelve a hacerlo mientras el plan adelantado no se haya ejecutado (principio 8).

### D-5 — Primera ejecución
Una MZ sin aplicaciones previas: se considera "vencida" ⇒ N-01 aplica en la próxima ventana. (La v2 no lo dice;
propuesta, confirmar.)

### D-6 — Parámetros
| Clave | Fábrica | Rango |
|---|---|---|
| `nutricion.intervalo-plan` (por MZ) | 7 d | 3–30 |
| `nutricion.ce-baja` | 0,5 dS/m | 0,3–1,0 |
| `nutricion.ce-lecturas` | 6 | 3–12 |
| `nutricion.sin-fertilizar-dias` | 5 d | 3–14 |
`intervalo-plan` es por zona (columna en la zona), no del catálogo global; el global es el valor inicial.
