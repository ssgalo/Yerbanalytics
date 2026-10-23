import type { RuleScenario } from './types';

/**
 * Scenarios that change who answers the pulse or whether the zone can dose at all: with a silent node the engine does
 * not decide, and with the zone locked the plan is due but waits for the lock to be lifted.
 */
const NEED_THE_ZONE_ANSWERING_AND_FREE = ['NODE_OFFLINE', 'LOCK'];

/**
 * N-01, nutrition plan: the plan of the target zone is due right now, so the pulse asks for fertilizer in every sector
 * of the zone. The interval between plan applications is the `interval` cooldown of `NutritionPlanRule`, which
 * `needsCooldownReset` takes to zero, so the plan falls due at the next pulse whatever its last application was.
 *
 * Whether each sector is dosed in the same pulse or deferred to the next application window is up to the application
 * preconditions (`ApplicationService`), not to this rule: combine it with `APPLICATION_PRECONDITIONS` (and the `window`
 * bypass of `ApplicationPreconditions`) to see the sectors applied right away. The tanks of the zone get stock first
 * (`ensureTankStock`): an application against an unregistered or empty tank is rejected, and the whole batch would be
 * discarded with that reason.
 *
 * A batch already open (sectors waiting for their window) is not asked for again: let it finish first.
 */
export const nutritionPlan: RuleScenario = {
  key: 'NUTRITION_PLAN',
  label: 'Plan de nutrición (N-01)',
  description:
    'El plan de nutrición de la zona vence ya: el pulso pide fertilizante para todos sus sectores (cada uno se aplica en su próxima ventana apta)',
  branch: 'INSUMO',
  backendRules: ['NutritionPlanRule'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 55,
  conflicts: NEED_THE_ZONE_ANSWERING_AND_FREE.map((other) => ({
    with: other,
    reason: () =>
      'El plan de nutrición necesita una zona que conteste el pulso y no esté bloqueada: con el nodo mudo el motor no decide y con un bloqueo el plan queda vencido esperando. Ejecutalo por separado.',
  })),
  apply: () => ({
    ensureTankStock: true,
    notes: [
      'el intervalo del plan (cooldown «interval» de NutritionPlanRule) queda en 0: el plan vence en el próximo pulso',
      'los tanques de la zona quedan con stock; si no hay uno registrado, se registra',
      'cada sector se aplica en su próxima ventana apta: combinalo con «Precondiciones de aplicación» y activá el bypass «window» para verlo aplicado en el mismo pulso',
      'si la zona ya tiene una tanda abierta (sectores esperando su ventana) no se vuelve a pedir: mirá el estado en el detalle de la macro-zona',
    ],
  }),
};
