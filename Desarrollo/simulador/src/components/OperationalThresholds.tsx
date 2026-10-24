/* Read-only panel showing the rule parameters currently active in the backend. */
import { useCallback, useEffect, useState } from 'react';
import { getParametrosCatalogo } from '../api';
import type { ParametroRegla } from '../types';

export function OperationalThresholds() {
  const [parametros, setParametros] = useState<ParametroRegla[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setParametros(await getParametrosCatalogo());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section className="card">
      <div className="demoCardHeader">
        <h2 className="sectionTitle">Umbrales vigentes del motor</h2>
        <button type="button" className="small" onClick={() => void load()} disabled={loading}>
          {loading ? 'Actualizando…' : 'Actualizar'}
        </button>
      </div>
      <p className="sectionHint">
        Parámetros actuales del catálogo de reglas del backend, una fila por copia: cada regla tiene la suya. La
        automatización usa la copia de la regla que ejercita cada escenario para calcular la telemetría idónea.
      </p>

      {error && <p className="errorMsg">{error}</p>}

      {!loading && parametros.length === 0 && !error && (
        <p className="muted">No se pudieron cargar los parámetros del backend.</p>
      )}

      {parametros.length > 0 && (
        <table className="thresholdsTable">
          <thead>
            <tr>
              <th>Regla</th>
              <th>Parámetro</th>
              <th>Valor actual</th>
              <th>Fábrica</th>
              <th>Unidad</th>
            </tr>
          </thead>
          <tbody>
            {parametros.map((p) => (
              <tr key={`${p.regla}|${p.clave}`} className={p.valor !== p.fabrica ? 'thresholdModified' : ''}>
                <td className="mono" style={{ fontSize: 12 }}>
                  {p.regla}
                </td>
                <td>
                  <span className="mono" style={{ fontSize: 12 }}>
                    {p.clave}
                  </span>
                  {p.etiqueta && (
                    <span className="muted" style={{ marginLeft: 8, fontSize: 12 }}>
                      {p.etiqueta}
                    </span>
                  )}
                </td>
                <td>
                  <strong>{p.valor}</strong>
                </td>
                <td className="muted">{p.fabrica}</td>
                <td className="muted">{p.unidad}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
