import { NORMAL_EC, NORMAL_PH, NORMAL_SUBSTRATE_HUMIDITY } from './chlorosis';
import { conflictsWithAZoneThatCannotApply, conflictsWithEarlierDiagnoses, diagnosisPlan, humidityDueForIrrigation } from './shared';
import type { RuleScenario } from './types';

/**
 * E-01 y S-06, riego sin efecto: la humedad de sustrato queda fijada en el valor que dispara R-01. El pulso riega, y la lectura
 * de verificación (que el backend pide 30 minutos después del cierre, o enseguida con el cooldown `verification` en 0) lee
 * exactamente lo mismo: la humedad no subió los 8 puntos, el riego queda «Sin efectividad» y R-01 se bloquea en toda la
 * macro-zona hasta que se registre la revisión (o se active el bypass `block` de IneffectiveActionRule).
 *
 * Con los cooldowns en 0 la verificación sale en el siguiente ciclo del servicio de efectividad (30 s). El escenario del riego
 * efectivo (una subida de 15 puntos) todavía no existe: pide que la lectura fijada cambie después del riego.
 */
export const irrigationNoEffect: RuleScenario = {
  key: 'IRRIGATION_NO_EFFECT',
  label: 'Riego sin efecto (E-01 · S-06)',
  description:
    'Humedad fijada en el valor de riego: el pulso riega, la verificación no ve subida y R-01 queda bloqueado en la macro-zona',
  branch: 'RIEGO',
  backendRules: ['RiegoPorDeficitRule', 'IneffectiveActionRule', 'EffectivenessService'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 80,
  apply: (ctx) => {
    const humidity = humidityDueForIrrigation(ctx);
    return {
      telemetry: { humSus: humidity },
      pinnedReadings: {
        humSus: { value: humidity.value, note: `humSus = ${humidity.value} % en cada lectura de la zona, también en la de verificación (sin subida)` },
      },
      notes: [
        'los cooldowns de demo quedan en 0: la lectura de verificación se pide en el siguiente ciclo del servicio de efectividad (≈ 30 s)',
        'la verificación lee lo mismo que antes del riego: delta 0, por debajo de los 8 puntos requeridos',
        'después, R-01 no riega ningún sector de la macro-zona hasta registrar la revisión (Historial → «Registrar revisión») o activar el bypass «block»',
        'la emisión automática tiene que estar activa; al terminar, soltá la zona en «Lecturas fijadas»',
      ],
    };
  },
};

/**
 * E-02, fertilizante correctivo sin efecto: una clorosis de severidad Alta (no puede bajar un nivel) sobre sustrato, CE y pH
 * normales, así F-C2 la fertiliza. Con los cooldowns en 0 (incluida la espera de 14 días de E-02) el servicio compara en su
 * siguiente ciclo: el diagnóstico sigue igual, el tratamiento queda «Sin efectividad» y F-C2 no vuelve a aplicar en el sector
 * hasta que se registre la revisión. El plan de nutrición no se bloquea.
 */
export const treatmentNoEffect: RuleScenario = {
  key: 'TREATMENT_NO_EFFECT',
  label: 'Tratamiento sin efecto (E-02 · S-06)',
  description:
    'Clorosis de severidad Alta que se fertiliza y no mejora: el tratamiento queda sin efectividad y F-C2 se bloquea en el sector',
  branch: 'INSUMO',
  backendRules: ['ChlorosisNutritionalRule', 'ApplicationPreconditions', 'EffectivenessService'],
  needsCooldownReset: true,
  needsDiagnosis: true,
  applyOrder: 81,
  conflicts: [...conflictsWithEarlierDiagnoses('TREATMENT_NO_EFFECT'), ...conflictsWithAZoneThatCannotApply()],
  apply: ({ thresholds }) => {
    const diagnosis = diagnosisPlan('Clorosis', 'Alta', thresholds.confianzaMinima);
    return {
      diagnosis,
      pinnedReadings: {
        humSus: { value: NORMAL_SUBSTRATE_HUMIDITY, note: `humSus = ${NORMAL_SUBSTRATE_HUMIDITY} % en cada lectura de la zona (sin asfixia radicular)` },
        ce: { value: NORMAL_EC, note: `ce = ${NORMAL_EC} dS/m en cada lectura de la zona (sin salinidad)` },
        phSuelo: { value: NORMAL_PH, note: `phSuelo = ${NORMAL_PH} en cada lectura de la zona (sin clorosis férrica)` },
      },
      ensureTankStock: true,
      bypassRules: { ApplicationPreconditions: ['window'] },
      notes: [
        `diagnóstico IA previo: ${diagnosis.estado} · severidad ${diagnosis.sev} · conf. ${diagnosis.conf}% (mínimo ${thresholds.confianzaMinima}%)`,
        'el bypass «window» de ApplicationPreconditions se activa antes del pulso: el fertilizante sale en el mismo pulso',
        'con el cooldown «fertilizer-latency» en 0 el servicio de efectividad compara en su siguiente ciclo: la severidad sigue Alta, sin mejora',
        'después F-C2 no vuelve a aplicar en el sector hasta registrar la revisión (Historial → «Registrar revisión»)',
        'la emisión automática tiene que estar activa; al terminar, soltá la zona en «Lecturas fijadas»',
      ],
    };
  },
};
