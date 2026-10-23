/* ============================================================
   Bypasses que un escenario necesita activos antes del pulso (por ejemplo el `samples` de N-02, que hace que la última
   lectura de CE cuente como la media). Se SUMAN a los que el operador ya tenía activos en el panel de reglas: nunca los
   reemplazan, porque `PUT /api/demo/rules/{regla}` guarda el conjunto entero.

   Módulo sin React ni fetch (las llamadas se inyectan en `BypassDeps`), como `tankStock.ts`: se prueba sin backend.
   Usa la API del modo demo del backend (`/api/demo/rules`), la misma que el panel de reglas.
   ============================================================ */
import type { DemoRule, DemoRuleUpdate } from './types';

export interface BypassDeps {
  getRules(): Promise<DemoRule[]>;
  updateRule(name: string, update: DemoRuleUpdate): Promise<unknown>;
}

/** Lista de bypasses con `keys` agregadas, o `null` si ya estaban todas (no hace falta tocar nada). */
export function withBypasses(rule: DemoRule, keys: string[]): string[] | null {
  const missing = keys.filter((k) => !rule.bypassed.includes(k));
  return missing.length === 0 ? null : [...rule.bypassed, ...missing];
}

/**
 * Activa los bypasses pedidos y devuelve las líneas para el registro de la corrida. Una regla que el backend no declara,
 * o una clave que no declara, no frena la corrida: queda dicho en el registro, porque sin el bypass la regla puede no
 * decidir y el operador tiene que saber por qué.
 */
export async function applyBypassRules(wanted: Record<string, string[]>, deps: BypassDeps): Promise<string[]> {
  const entries = Object.entries(wanted);
  if (entries.length === 0) return [];
  const rules = await deps.getRules();
  const lines: string[] = [];
  for (const [name, keys] of entries) {
    const rule = rules.find((r) => r.name === name);
    if (!rule) {
      lines.push(`⚠ ${name} no figura entre las reglas del modo demo del backend: no se pudo activar ${keys.join(', ')}`);
      continue;
    }
    const undeclared = keys.filter((k) => !rule.bypassKeys.includes(k));
    if (undeclared.length > 0) {
      lines.push(`⚠ ${name} no declara el bypass ${undeclared.join(', ')}: ¿el backend es anterior a esta regla?`);
      continue;
    }
    const bypassed = withBypasses(rule, keys);
    if (!bypassed) {
      lines.push(`✓ Bypass ${keys.join(', ')} de ${name} ya estaba activo`);
      continue;
    }
    await deps.updateRule(name, { bypassed });
    lines.push(`✓ Bypass ${keys.join(', ')} de ${name} activado`);
  }
  return lines;
}
