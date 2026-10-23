/* ============================================================
   Registro de escenarios: una entrada por regla que el simulador sabe accionar.

   Para agregar una regla: crear `scenarios/<regla>.ts` con su `RuleScenario`, sumarla acá y
   agregar su test. Sin escenario, la regla se puede apagar desde el panel pero el simulador
   no sabe armarle las condiciones, y el panel la muestra en gris ("sin escenario").
   ============================================================ */
import type { DemoRule } from '../types';
import { applicationPreconditions } from './applicationPreconditions';
import { chlorosisNonNutritional, chlorosisNutritional } from './chlorosis';
import { irrigationNoEffect, treatmentNoEffect } from './effectiveness';
import { faultNoFlow, faultOverflow, faultShadeLimit } from './faults';
import { foliarPest } from './foliarPest';
import { fungalCondition } from './fungalCondition';
import { fungalDamage } from './fungalDamage';
import { fungicideCap } from './fungicideCap';
import { incompleteHardware } from './incompleteHardware';
import { invalidReading } from './invalidReading';
import { lock } from './lock';
import { lowEc } from './lowEc';
import { nodeOffline } from './nodeOffline';
import { nutritionPlan } from './nutritionPlan';
import { outbreak } from './outbreak';
import { r01 } from './r01';
import { r02 } from './r02';
import { r03 } from './r03';
import { r04 } from './r04';
import { repair } from './repair';
import { shading } from './shading';
import { solarStress, solarStressUnexplained } from './solarStress';
import { supply } from './supply';
import type { RuleScenario } from './types';

export const SCENARIOS: RuleScenario[] = [
  r01, r02, r04, r03, shading, supply, nodeOffline, lock, invalidReading, incompleteHardware,
  faultNoFlow, faultOverflow, faultShadeLimit, repair, applicationPreconditions, nutritionPlan, lowEc,
  fungalDamage, foliarPest, fungalCondition, outbreak, fungicideCap,
  chlorosisNutritional, chlorosisNonNutritional, solarStress, solarStressUnexplained,
  irrigationNoEffect, treatmentNoEffect,
];

/** Backend pieces (`GET /api/demo/rules`) that no scenario exercises. */
export function rulesWithoutScenario(rules: DemoRule[]): DemoRule[] {
  const covered = new Set(SCENARIOS.flatMap((s) => s.backendRules));
  return rules.filter((r) => !covered.has(r.name));
}
