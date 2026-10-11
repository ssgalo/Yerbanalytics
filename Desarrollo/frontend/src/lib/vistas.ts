/* ============================================================
   Tabla vista → permiso. ÚNICA: la usan el router (`<RequirePermiso>`), el sidebar (qué
   ítems mostrar) y el login (a dónde volver). Si cada uno tuviera su copia, tarde o temprano un
   ítem del menú llevaría a una pantalla de "sin permiso", o al revés.

   El primer permiso de cada vista es su permiso de lectura (el que nombra la spec). Los demás
   son de los datos que la vista consume de otra superficie: el Panel, Diagnósticos, el
   Inspector del motor y Demo Expo leen el snapshot o la preferencia del vivero
   (`GET /api/nursery`, `GET /api/configuracion/demo-expo`, ambos `vivero.ver`). Sin ellos la
   vista no podría cargar, así que tampoco se ofrece.

   El dashboard es sólo presentación: la autoridad es el backend.
   ============================================================ */
import type { Permiso } from '@/types/seguridad';

export interface Vista {
  /** Ruta de la vista (el primer segmento identifica a la vista). */
  ruta: string;
  /** Todos requeridos. El primero es el de lectura de la vista. */
  permisos: readonly Permiso[];
}

export const VISTAS = {
  panel: { ruta: '/', permisos: ['vivero.ver'] },
  mapa: { ruta: '/mapa', permisos: ['vivero.ver'] },
  sector: { ruta: '/sector', permisos: ['vivero.ver'] },
  diagnosticos: { ruta: '/diagnosticos', permisos: ['diagnosticos.ver', 'vivero.ver'] },
  demoExpo: { ruta: '/demo-expo', permisos: ['pasadas.ver', 'vivero.ver'] },
  historial: { ruta: '/historial', permisos: ['historial.ver'] },
  configuracion: { ruta: '/configuracion', permisos: ['configuracion.ver'] },
  reglas: { ruta: '/reglas', permisos: ['reglas.ver', 'vivero.ver'] },
  hardware: { ruta: '/hardware', permisos: ['hardware.ver'] },
  topologia: { ruta: '/topologia', permisos: ['topologia.ver'] },
  usuarios: { ruta: '/usuarios', permisos: ['usuarios.gestionar'] },
} as const satisfies Record<string, Vista>;

export type ClaveVista = keyof typeof VISTAS;

/** Orden en que se elige la primera vista habilitada (el del menú). */
const ORDEN: readonly ClaveVista[] = [
  'panel',
  'diagnosticos',
  'historial',
  'configuracion',
  'reglas',
  'hardware',
  'topologia',
  'usuarios',
];

type Puede = (permiso: Permiso) => boolean;

export function puedeVer(vista: Vista, puede: Puede): boolean {
  return vista.permisos.every(puede);
}

/** La vista a la que corresponde una ruta (`/sector/MZ-1-001` → sector); null si no es de ninguna. */
export function vistaDeRuta(pathname: string): Vista | null {
  const primero = `/${pathname.split('/').filter(Boolean)[0] ?? ''}`;
  return Object.values(VISTAS).find((v) => v.ruta === primero) ?? null;
}

/** Primera vista del menú que el usuario puede ver; null si su rol no tiene ninguna. */
export function primeraVistaHabilitada(puede: Puede): Vista | null {
  return ORDEN.map((c) => VISTAS[c] as Vista).find((v) => puedeVer(v, puede)) ?? null;
}

/**
 * A dónde ir después de iniciar sesión: la ruta pedida si el rol todavía puede verla; si no, la
 * primera habilitada.
 */
export function destinoTrasLogin(pedida: string | null | undefined, puede: Puede): string | null {
  if (pedida) {
    const vista = vistaDeRuta(pedida.split('?')[0]);
    if (vista && puedeVer(vista, puede)) return pedida;
  }
  return primeraVistaHabilitada(puede)?.ruta ?? null;
}

/** Ruta que se había pedido antes de pasar por el login (la deja `RequireAuth` en el state). */
export function rutaPedida(state: unknown): string | null {
  const desde = (state as { desde?: unknown } | null)?.desde;
  return typeof desde === 'string' ? desde : null;
}
