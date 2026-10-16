/* Tab bar for the simulator's 5 sections. Uses window.location.hash for persistence across
   reloads without adding react-router as a dependency. */
import { useEffect, useState } from 'react';

export type TabId = 'configuracion' | 'telemetria' | 'automatizacion' | 'camara' | 'demo-expo';

const TABS: { id: TabId; label: string }[] = [
  { id: 'configuracion', label: 'Configuración' },
  { id: 'telemetria', label: 'Telemetría y reglas' },
  { id: 'automatizacion', label: 'Automatización' },
  { id: 'camara', label: 'Cámara' },
  { id: 'demo-expo', label: 'Demo Expo' },
];

const VALID_IDS = new Set<string>(TABS.map((t) => t.id));

function hashToTab(): TabId {
  const hash = window.location.hash.replace('#', '');
  return VALID_IDS.has(hash) ? (hash as TabId) : 'configuracion';
}

interface Props {
  active: TabId;
  onChange: (tab: TabId) => void;
}

export function NavigationTabs({ active, onChange }: Props) {
  return (
    <nav className="navTabs" aria-label="Secciones del simulador">
      {TABS.map((t) => (
        <button
          key={t.id}
          type="button"
          className={`navTab${active === t.id ? ' navTabActive' : ''}`}
          aria-current={active === t.id ? 'page' : undefined}
          onClick={() => onChange(t.id)}
        >
          {t.label}
        </button>
      ))}
    </nav>
  );
}

/** Reads the initial tab from the URL hash and keeps it in sync. */
export function useActiveTab(): [TabId, (tab: TabId) => void] {
  const [active, setActive] = useState<TabId>(hashToTab);

  useEffect(() => {
    const handler = () => setActive(hashToTab());
    window.addEventListener('hashchange', handler);
    return () => window.removeEventListener('hashchange', handler);
  }, []);

  const setTab = (tab: TabId) => {
    window.location.hash = tab;
    setActive(tab);
  };

  return [active, setTab];
}
