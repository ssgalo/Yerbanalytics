/* eslint-disable react-refresh/only-export-components -- provider + hooks colocados a propósito */
/* ============================================================
   Metadata de página (título + subtítulo de la topbar).
   Cada vista declara su meta con usePageTitle(); la Topbar la lee.
   Desacopla la barra superior del conocimiento del routing.
   ============================================================ */
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

interface PageMeta {
  title: string;
  subtitle: string;
}

interface PageMetaCtx {
  meta: PageMeta;
  setMeta: (meta: PageMeta) => void;
}

const Ctx = createContext<PageMetaCtx | null>(null);

export function PageMetaProvider({ children }: { children: ReactNode }) {
  const [meta, setMeta] = useState<PageMeta>({ title: 'Panel general', subtitle: '' });
  return <Ctx.Provider value={{ meta, setMeta }}>{children}</Ctx.Provider>;
}

export function usePageMeta(): PageMeta {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('usePageMeta debe usarse dentro de <PageMetaProvider>');
  return ctx.meta;
}

/** Declara el título/subtítulo de la vista actual. */
export function usePageTitle(title: string, subtitle: string): void {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('usePageTitle debe usarse dentro de <PageMetaProvider>');
  const { setMeta } = ctx;
  useEffect(() => {
    setMeta({ title, subtitle });
  }, [title, subtitle, setMeta]);
}
