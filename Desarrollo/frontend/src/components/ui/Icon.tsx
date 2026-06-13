/* ============================================================
   Registro de íconos SVG — extraídos EXACTOS del diseño.
   Uso: <Icon name="bell" size={20} />
   Para paths dinámicos (acciones/actuadores) usar <Glyph path={...} />.
   ============================================================ */
import type { CSSProperties } from 'react';

export type IconName =
  | 'leaf'
  | 'leaf-simple'
  | 'dashboard'
  | 'map'
  | 'diagnostics'
  | 'history'
  | 'config'
  | 'hardware'
  | 'sun'
  | 'bell'
  | 'alert'
  | 'check'
  | 'chevron-left'
  | 'arrow-right'
  | 'close'
  | 'cube';

const PATHS: Record<IconName, JSX.Element> = {
  leaf: (
    <>
      <path d="M12 21c0-6 0-10 0-13" />
      <path d="M12 13c-1.6-3-4.5-4-7.5-3.6C4 13 6 16 12 13Z" />
      <path d="M12 9.5c1.3-3 4-4.2 6.8-3.8C19 9 17 12 12 9.5Z" />
    </>
  ),
  'leaf-simple': (
    <>
      <path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.5 19 2c1 4 .5 11-4.5 15" />
      <path d="M5 21c.5-4.5 2.5-8 4.5-10" />
    </>
  ),
  dashboard: (
    <>
      <rect x="3" y="3" width="7" height="9" rx="1.5" />
      <rect x="14" y="3" width="7" height="5" rx="1.5" />
      <rect x="14" y="12" width="7" height="9" rx="1.5" />
      <rect x="3" y="16" width="7" height="5" rx="1.5" />
    </>
  ),
  map: (
    <>
      <path d="M9 3 3.5 5.2A1 1 0 0 0 3 6.1v13.2a1 1 0 0 0 1.4.9L9 18l6 3 5.6-2.2a1 1 0 0 0 .6-.9V4.7a1 1 0 0 0-1.4-.9L15 6 9 3Z" />
      <path d="M9 3v15M15 6v15" />
    </>
  ),
  diagnostics: (
    <>
      <path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2" />
      <path d="M3 12h18" />
    </>
  ),
  history: (
    <>
      <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
      <path d="M3 4v4h4" />
      <path d="M12 8v4l3 2" />
    </>
  ),
  config: (
    <>
      <path d="M4 6h10M18 6h2M4 12h2M10 12h10M4 18h7M15 18h5" />
      <circle cx="16" cy="6" r="2" />
      <circle cx="8" cy="12" r="2" />
      <circle cx="13" cy="18" r="2" />
    </>
  ),
  hardware: (
    <>
      <rect x="4" y="4" width="16" height="16" rx="2" />
      <rect x="9" y="9" width="6" height="6" />
      <path d="M9 1v3M15 1v3M9 20v3M15 20v3M1 9h3M1 15h3M20 9h3M20 15h3" />
    </>
  ),
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5 19 19M5 19l1.5-1.5M17.5 6.5 19 5" />
    </>
  ),
  bell: (
    <>
      <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
      <path d="M10.3 21a2 2 0 0 0 3.4 0" />
    </>
  ),
  alert: (
    <>
      <path d="M12 9v4M12 17h.01" />
      <path d="M10.3 3.9 2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" />
    </>
  ),
  check: <path d="M20 6 9 17l-5-5" />,
  'chevron-left': <path d="M15 18l-6-6 6-6" />,
  'arrow-right': <path d="M5 12h14M13 6l6 6-6 6" />,
  close: <path d="M18 6 6 18M6 6l12 12" />,
  cube: (
    <>
      <path d="M12 2 2 7l10 5 10-5-10-5Z" />
      <path d="m2 17 10 5 10-5M2 12l10 5 10-5" />
    </>
  ),
};

interface IconProps {
  name: IconName;
  size?: number;
  stroke?: string;
  strokeWidth?: number;
  style?: CSSProperties;
}

export function Icon({ name, size = 18, stroke = 'currentColor', strokeWidth = 1.8, style }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke={stroke}
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      style={style}
      aria-hidden="true"
    >
      {PATHS[name]}
    </svg>
  );
}

interface GlyphProps {
  path: string;
  size?: number;
  stroke?: string;
  strokeWidth?: number;
  style?: CSSProperties;
}

/** Ícono de un solo path dinámico (acciones, actuadores). */
export function Glyph({ path, size = 18, stroke = 'currentColor', strokeWidth = 1.9, style }: GlyphProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke={stroke}
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      style={style}
      aria-hidden="true"
    >
      <path d={path} />
    </svg>
  );
}
