/* eslint-disable react-refresh/only-export-components -- provider + hook colocados a propósito */
/* ============================================================
   Sidebar colapsable a íconos. El estado vive en un contexto porque lo leen dos lugares que no
   se conocen: el botón de la topbar y la propia sidebar. Se recuerda por navegador (es una
   comodidad de quien mira, no un dato del vivero) y se conmuta también con Ctrl/⌘ + B.
   ============================================================ */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

interface SidebarValue {
  collapsed: boolean;
  setCollapsed: (collapsed: boolean) => void;
  toggle: () => void;
}

const STORAGE_KEY = 'yerbanalytics.sidebar.collapsed';
const SHORTCUT_KEY = 'b';

/* Sin provider (tests de la sidebar sola) queda siempre expandida. */
const SidebarContext = createContext<SidebarValue>({
  collapsed: false,
  setCollapsed: () => {},
  toggle: () => {},
});

const readStored = () => {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
};

export function SidebarProvider({ children }: { children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(readStored);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, String(collapsed));
    } catch {
      /* sin almacenamiento, sólo no se recuerda */
    }
  }, [collapsed]);

  const toggle = useCallback(() => setCollapsed((c) => !c), []);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (
        e.key.toLowerCase() === SHORTCUT_KEY &&
        (e.metaKey || e.ctrlKey) &&
        !e.altKey &&
        !e.shiftKey
      ) {
        e.preventDefault();
        toggle();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [toggle]);

  const value = useMemo(() => ({ collapsed, setCollapsed, toggle }), [collapsed, toggle]);
  return <SidebarContext.Provider value={value}>{children}</SidebarContext.Provider>;
}

export const useSidebar = () => useContext(SidebarContext);
