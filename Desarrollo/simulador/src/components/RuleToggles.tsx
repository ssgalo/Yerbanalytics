import { useCallback, useEffect, useState } from 'react';
import * as api from '../api';
import type { DemoRule } from '../types';
import { stateLegend } from '../ruleToggleModel';

export function RuleToggles() {
  const [rules, setRules] = useState<DemoRule[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await api.getDemoRules();
      setRules(data);
    } catch {
      setRules([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const setCooldown = async (rule: DemoRule, key: string, value: number) => {
    setSaving(`${rule.name}-cd-${key}`);
    setError(null);
    try {
      const updated = await api.updateDemoRule(rule.name, {
        cooldownMinutes: { ...rule.cooldownMinutes, [key]: value },
      });
      setRules((prev) => prev.map((r) => (r.name === rule.name ? updated : r)));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(null);
    }
  };

  const toggleBypass = async (rule: DemoRule, key: string) => {
    setSaving(`${rule.name}-bp-${key}`);
    setError(null);
    try {
      const current = new Set(rule.bypassed);
      if (current.has(key)) current.delete(key);
      else current.add(key);
      const updated = await api.updateDemoRule(rule.name, { bypassed: Array.from(current) });
      setRules((prev) => prev.map((r) => (r.name === rule.name ? updated : r)));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(null);
    }
  };

  const zeroAll = async () => {
    setSaving('all');
    setError(null);
    try {
      const updated = await api.zeroDemoCooldowns();
      setRules(updated);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(null);
    }
  };

  if (loading) return <p className="loading">Cargando reglas…</p>;

  return (
    <div className="card demoCard">
      <div className="demoCardHeader">
        <h3>Control de reglas</h3>
        <button
          onClick={() => void zeroAll()}
          disabled={saving !== null}
          className="btnSecondary"
          title="Pone todos los cooldowns en 0 para poder re-regar o re-actuar de inmediato"
        >
          Todos los CDs en 0
        </button>
      </div>
      <p className="hint">
        Reducí cooldowns o salteá guardas para demostrar el ciclo ciberfísico sin esperar los
        tiempos de producción. Prender o apagar una regla se hace sólo en Motor de reglas del dashboard: acá se
        ve su estado. Los cambios viven en memoria: un reinicio del backend los resetea.
      </p>

      {error && <p className="errorMsg">{error}</p>}

      <div className="ruleList">
        {rules.map((rule) => (
          <div key={rule.name} className={`ruleRow ${rule.enabled ? '' : 'ruleDisabled'}`}>
            <div className="ruleHeader">
              <div className="ruleToggle">
                <span className="ruleLabel">{rule.label}</span>
                <span className="ruleMeta" data-state={rule.enabled ? 'on' : 'off'}>{stateLegend(rule)}</span>
                <span className="ruleMeta">
                  {rule.branch} · prioridad {rule.priority}
                </span>
              </div>
            </div>

            {rule.cooldownKeys.length > 0 && (
              <div className="ruleCooldowns">
                {rule.cooldownKeys.map((key) => (
                  <div key={key} className="fieldRow fieldRowCompact">
                    <label>CD [{key}]</label>
                    <input
                      type="number"
                      min={0}
                      step={1}
                      value={rule.cooldownMinutes[key] ?? ''}
                      placeholder="producción"
                      disabled={saving !== null}
                      onChange={(e) => {
                        const v = e.target.value === '' ? 0 : Number(e.target.value);
                        void setCooldown(rule, key, v);
                      }}
                    />
                    <span className="unit">min</span>
                  </div>
                ))}
              </div>
            )}

            {rule.bypassKeys.length > 0 && (
              <div className="ruleBypass">
                {rule.bypassKeys.map((key) => (
                  <label key={key} className="bypassToggle">
                    <input
                      type="checkbox"
                      checked={rule.bypassed.includes(key)}
                      disabled={saving !== null}
                      onChange={() => void toggleBypass(rule, key)}
                    />
                    bypass [{key}]
                  </label>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
