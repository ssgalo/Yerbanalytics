/* Vista principal: Estado técnico del hardware (HU-18 / HU-21) */
import { useMemo, useState } from 'react';
import { usePageTitle } from '@/hooks/PageMeta';
import { useHardware } from '@/hooks/useHardware';
import type { Dispositivo, NuevoDispositivo } from '@/types/domain';
import { HardwareKpis } from './components/HardwareKpis';
import { HardwareFilters, type HardwareFilterState } from './components/HardwareFilters';
import { HardwareTable } from './components/HardwareTable';
import { SectoresIncompletos } from './components/SectoresIncompletos';
import { AltaHardwareForm } from './components/AltaHardwareForm';
import styles from './Hardware.module.css';

const INITIAL: HardwareFilterState = { tipo: 'Todos', estado: 'Todos', zona: 'Todas' };

type Panel = { mode: 'alta' } | { mode: 'recambio'; device: Dispositivo } | null;
type Feedback = { kind: 'ok'; msg: string } | null;

/** Macro-zona a la que pertenece un dispositivo (por zona o por prefijo del sector). */
function zonaDe(d: Dispositivo): string {
  if (d.zonaId) return d.zonaId;
  if (d.sectorId) {
    const last = d.sectorId.lastIndexOf('-');
    return last > 0 ? d.sectorId.slice(0, last) : d.sectorId;
  }
  return '';
}

export function HardwarePage() {
  const { data, loading, error, mutating, register, replace } = useHardware();
  const [filters, setFilters] = useState<HardwareFilterState>(INITIAL);
  const [panel, setPanel] = useState<Panel>(null);
  const [feedback, setFeedback] = useState<Feedback>(null);

  const patch = (p: Partial<HardwareFilterState>) => setFilters((f) => ({ ...f, ...p }));

  const filtered = useMemo(() => {
    if (!data) return [];
    return data.dispositivos.filter(
      (d) =>
        (filters.tipo === 'Todos' || d.tipo === filters.tipo) &&
        (filters.estado === 'Todos' || d.estado === filters.estado) &&
        (filters.zona === 'Todas' || zonaDe(d) === filters.zona),
    );
  }, [data, filters]);

  usePageTitle('Estado del hardware', data ? `${data.total} dispositivos registrados` : '');

  if (error) {
    return (
      <div className={styles.state} style={{ color: 'var(--crit)' }}>
        No se pudo cargar el hardware: {error.message}
      </div>
    );
  }

  if (loading || !data) {
    return <div className={styles.state}>Cargando hardware…</div>;
  }

  const submitAlta = async (nuevo: NuevoDispositivo) => {
    await register(nuevo);
    setPanel(null);
    setFeedback({ kind: 'ok', msg: `Dispositivo «${nuevo.serial}» registrado.` });
  };

  const submitRecambio = (device: Dispositivo) => async (nuevo: NuevoDispositivo) => {
    await replace(device.id, nuevo);
    setPanel(null);
    setFeedback({ kind: 'ok', msg: `${device.tipoLabel} recambiado en ${device.ubicacion}.` });
  };

  return (
    <div>
      <p className={styles.intro}>
        Monitoreá la flota de hardware del vivero: nodos sensores testigo y actuadores por sector.
        Detectá equipos con batería baja, señal intermitente, caídos o averiados para ejecutar el
        recambio preventivo, y registrá el equipamiento nuevo asociándolo a su posición.
      </p>

      <HardwareKpis data={data} />

      <div className={styles.toolbar}>
        <HardwareFilters value={filters} count={filtered.length} onChange={patch} />
        <span className={styles.toolbarSpacer} />
        {feedback && <span className={styles.feedbackOk}>{feedback.msg}</span>}
        <button
          type="button"
          className={styles.btnPrimary}
          onClick={() => {
            setFeedback(null);
            setPanel({ mode: 'alta' });
          }}
        >
          Registrar dispositivo
        </button>
      </div>

      {panel && (
        <AltaHardwareForm
          mode={panel.mode}
          device={panel.mode === 'recambio' ? panel.device : undefined}
          busy={mutating}
          onSubmit={panel.mode === 'recambio' ? submitRecambio(panel.device) : submitAlta}
          onCancel={() => setPanel(null)}
        />
      )}

      <SectoresIncompletos incompletos={data.incompletos} />

      <HardwareTable
        dispositivos={filtered}
        onRecambiar={(device) => {
          setFeedback(null);
          setPanel({ mode: 'recambio', device });
        }}
      />
    </div>
  );
}
