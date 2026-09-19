## 1. Backend Data & API
- [ ] 1.1 Add `intervaloSensadoMinutos` and `intervaloInferenciaMinutos` (integer, default 240) to `ConfiguracionOperativaEntity.java`.
- [ ] 1.2 Add the fields to `ConfiguracionOperativa.java` (Domain model / DTO).
- [ ] 1.3 Add Liquibase migration or ensure Hibernate creates the columns properly (since it uses H2 auto-ddl, check `application.yml`).

## 2. Frontend UI
- [ ] 2.1 Update `src/types/domain.ts` to include the new fields in `ConfiguracionOperativa`.
- [ ] 2.2 Create a reusable `TimeInput` component or inline logic in `ConfiguracionPage.tsx` to handle value + unit (Horas/Minutos) mapping to/from minutes.
- [ ] 2.3 Add the "Intervalo de Inferencia (IA)" field to the form in `ConfiguracionPage.tsx`.
- [ ] 2.4 Add the "Intervalo de Sensado (IoT)" field to the form in `ConfiguracionPage.tsx`.

## 3. Simulator & Inference integration
- [ ] 3.1 Update `servicio-inferencia/src/main.py` to fetch configuration via GET `/api/configuracion` at the end of each cycle and sleep dynamically.
- [ ] 3.2 Update `simulador/server/emission.ts` to dynamically fetch the configuration and adjust its `setInterval` timer when the configured interval changes.
