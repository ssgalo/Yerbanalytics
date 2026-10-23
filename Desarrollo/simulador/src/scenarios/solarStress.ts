import { conflictsWithAZoneThatCannotApply, conflictsWithEarlierDiagnoses, diagnosisPlan } from './shared';
import type { RuleScenario, ScenarioConflict } from './types';

/** Points over the `estres.luz-alta` threshold the zone reads, so it is clearly high light (88 % with the factory 80 %). */
const LIGHT_MARGIN = 8;

/** Points under the `estres.luz-baja` threshold the zone reads (40 % with the factory 60 %). */
const LOW_LIGHT_MARGIN = 20;

/** Degrees under the `estres.temp-baja` threshold the zone reads (24 °C with the factory 30 °C). */
const MILD_TEMP_MARGIN = 6;

/** How far under the replenishment threshold the substrate reads (50 % with the factory 55 %). */
const SUBSTRATE_MARGIN = 5;

/** The scenarios that want the substrate in another state than the one F-S1 needs. */
const OTHER_SUBSTRATE_STATE = ['R01', 'R02', 'R04', 'FUNGAL_CONDITION'];

function conflictsOverTheSubstrate(): ScenarioConflict[] {
  return OTHER_SUBSTRATE_STATE.map((other) => ({
    with: other,
    reason: () =>
      'Estrés solar necesita el sustrato apenas por debajo del nivel de reposición, y ese otro escenario lo quiere en otro estado. Ejecutalos por separado.',
  }));
}

/**
 * F-S1, solar stress with high radiation: a "Estrés solar" diagnosis with a high light reading and the substrate under the
 * replenishment threshold, so the pulse orders a replenishment irrigation of the sector and leaves a WARNING.
 *
 * The light and the substrate travel in the answer to the pulse (`pinnedReadings`). F-S1 looks at the last 48 h; the
 * `samples` bypass makes the last reading count as the whole window (otherwise the zone would need two days of history),
 * and the `window` bypass lets the order go out of the irrigation window, also in the dispatch. The substrate is set a
 * little over the irrigation threshold so R-01 does not fire and F-S1 is the only rule that waters.
 */
export const solarStress: RuleScenario = {
  key: 'SOLAR_STRESS',
  label: 'Estrés solar con radiación alta (F-S1)',
  description:
    'Diagnóstico Estrés solar con luz alta y el sustrato bajo el nivel de reposición: el pulso riega el sector y deja un WARNING',
  branch: 'RIEGO',
  backendRules: ['SolarStressRule'],
  needsCooldownReset: true,
  needsDiagnosis: true,
  applyOrder: 72,
  conflicts: [
    ...conflictsWithEarlierDiagnoses('SOLAR_STRESS'),
    ...conflictsWithAZoneThatCannotApply(),
    ...conflictsOverTheSubstrate(),
  ],
  apply: ({ thresholds }) => {
    const diagnosis = diagnosisPlan('Estrés solar', 'Media', thresholds.confianzaMinima);
    const light = Math.min(100, thresholds.luzAlta + LIGHT_MARGIN);
    const substrate = Math.max(thresholds.umbralCritico + 1, thresholds.humedadReposicion - SUBSTRATE_MARGIN);
    return {
      diagnosis,
      pinnedReadings: {
        uv: { value: light, note: `uv = ${light} % en cada lectura de la zona (luz alta: desde ${thresholds.luzAlta} %)` },
        humSus: {
          value: substrate,
          note: `humSus = ${substrate} % en cada lectura de la zona (bajo el nivel de reposición, ${thresholds.humedadReposicion} %)`,
        },
      },
      bypassRules: { SolarStressRule: ['window', 'samples'] },
      notes: [
        `diagnóstico IA previo: ${diagnosis.estado} · severidad ${diagnosis.sev} · conf. ${diagnosis.conf}% (mínimo ${thresholds.confianzaMinima}%)`,
        'los bypasses «samples» (la última lectura cuenta como las 48 h) y «window» (fuera de la ventana de riego también sale) de SolarStressRule se activan antes del pulso',
        'el cooldown del ciclo de lectura queda en 0: el despacho no descarta el riego por haber regado ya en este ciclo',
        'la emisión automática tiene que estar activa: las lecturas viajan en la respuesta al pulso; al terminar, soltá la zona en «Lecturas fijadas»',
      ],
    };
  },
};

/**
 * F-S3, solar stress with no high radiation: the same diagnosis but with low light and a mild temperature, so the sun does not
 * explain it. Nothing is watered and the mesh does not move: the pulse only leaves the WARNING «Síntoma no explicado por
 * radiación». The `samples` bypass makes the last reading count as the last 48 h.
 */
export const solarStressUnexplained: RuleScenario = {
  key: 'SOLAR_STRESS_UNEXPLAINED',
  label: 'Estrés solar sin radiación alta (F-S3)',
  description:
    'Diagnóstico Estrés solar con luz baja y temperatura templada (40 %, 24 °C): no se riega ni se cierra la malla, sólo queda el aviso',
  branch: 'SEGUIMIENTO',
  backendRules: ['SolarStressUnexplainedRule'],
  needsCooldownReset: false,
  needsDiagnosis: true,
  applyOrder: 73,
  conflicts: [...conflictsWithEarlierDiagnoses('SOLAR_STRESS_UNEXPLAINED'), ...conflictsWithAZoneThatCannotApply()],
  apply: ({ thresholds }) => {
    const diagnosis = diagnosisPlan('Estrés solar', 'Media', thresholds.confianzaMinima);
    const light = Math.max(0, thresholds.luzBaja - LOW_LIGHT_MARGIN);
    const temp = thresholds.tempBaja - MILD_TEMP_MARGIN;
    return {
      diagnosis,
      pinnedReadings: {
        uv: { value: light, note: `uv = ${light} % en cada lectura de la zona (luz baja: bajo ${thresholds.luzBaja} %)` },
        temp: { value: temp, note: `temp = ${temp} °C en cada lectura de la zona (templada: bajo ${thresholds.tempBaja} °C)` },
      },
      bypassRules: { SolarStressUnexplainedRule: ['samples'] },
      notes: [
        `diagnóstico IA previo: ${diagnosis.estado} · severidad ${diagnosis.sev} · conf. ${diagnosis.conf}% (mínimo ${thresholds.confianzaMinima}%)`,
        'el bypass «samples» de SolarStressUnexplainedRule se activa antes del pulso: la última lectura cuenta como las últimas 48 h',
        'la emisión automática tiene que estar activa: las lecturas viajan en la respuesta al pulso; al terminar, soltá la zona en «Lecturas fijadas»',
      ],
    };
  },
};
