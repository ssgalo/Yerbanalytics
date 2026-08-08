/* Regenerates the nursery grid (N macro-zones × M sectors) so it matches the hardware to be
   simulated. It uses the SAME public endpoint as the dashboard's topology panel: the
   simulator has none of its own. Regeneration leaves every sector offline, with no history,
   so it asks for confirmation when a grid already exists. */
import { useEffect, useState } from 'react';
import { Section } from './Section';
import { generateTopology } from '../api';
import type { NurseryTopology } from '../types';

interface Props {
  topology: NurseryTopology | null;
  onRegenerated: (next: NurseryTopology) => void;
}

export function Topology({ topology, onRegenerated }: Props) {
  const [macroZones, setMacroZones] = useState(6);
  const [sectors, setSectors] = useState(100);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  // Prefill with the current counts once the topology finishes loading.
  useEffect(() => {
    if (topology && topology.macroZonas > 0) {
      setMacroZones(topology.macroZonas);
      setSectors(topology.sectoresPorMacroZona);
    }
  }, [topology]);

  const regenerate = async () => {
    setError(null);
    setDone(null);
    if (macroZones < 1 || sectors < 1) {
      setError('Ingresá cantidades válidas (≥ 1) de macro-zonas y sectores.');
      return;
    }
    // Replacing an existing grid discards its data: confirm first.
    if (topology?.generada) {
      const total = macroZones * sectors;
      const confirmed = window.confirm(
        `Esto reemplaza la topología actual por ${macroZones} macro-zonas × ${sectors} sectores ` +
          `(${total} sectores) y deja todos los sectores sin lecturas ni históricos. ¿Continuar?`,
      );
      if (!confirmed) return;
    }
    setGenerating(true);
    try {
      onRegenerated(await generateTopology(macroZones, sectors));
      setDone(`Topología regenerada: ${macroZones} × ${sectors} sectores, sin históricos.`);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setGenerating(false);
    }
  };

  return (
    <Section
      title="Topología del vivero"
      hint="Genera la grilla para que coincida con el hardware a simular. Deja cada sector offline, sin valores históricos."
    >
      <div className="row">
        <label className="field">
          <span className="label">Macro-zonas</span>
          <input
            className="num"
            type="number"
            min={1}
            value={macroZones}
            onChange={(e) => setMacroZones(Number(e.target.value))}
          />
        </label>
        <label className="field">
          <span className="label">Sectores por macro-zona</span>
          <input
            className="num"
            type="number"
            min={1}
            value={sectors}
            onChange={(e) => setSectors(Number(e.target.value))}
          />
        </label>
        <button type="button" className="primary" disabled={generating} onClick={regenerate}>
          {generating ? 'Regenerando…' : 'Regenerar topología'}
        </button>
        {topology && (
          <span className="muted">
            {topology.generada
              ? `Vigente: ${topology.macroZonas} × ${topology.sectoresPorMacroZona} = ${topology.totalSectores} sectores`
              : 'No hay ninguna grilla cargada todavía.'}
          </span>
        )}
      </div>
      {error && <p className="errorText">{error}</p>}
      {done && <p className="okText">{done}</p>}
    </Section>
  );
}
