/* Presentación de usuarios y auditoría: fechas, estados y el resumen legible de un detalle. */
import { ETIQUETA_TIPO_AUDITORIA, INACTIVIDAD_MAX, INACTIVIDAD_MIN, nombreRol } from '@/lib/catalogoSeguridad';
import type { ColorPair } from '@/types/domain';
import type { EstadoUsuario, FiltrosAuditoria, RegistroAuditoria, TipoAuditoria } from '@/types/seguridad';

const p2 = (n: number) => String(n).padStart(2, '0');

/** "10/10/2026 14:03" en hora local. */
export function fechaHora(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return `${p2(d.getDate())}/${p2(d.getMonth() + 1)}/${d.getFullYear()} ${p2(d.getHours())}:${p2(d.getMinutes())}`;
}

/** "10/10/2026 14:03:22.123": la auditoría tiene precisión de milisegundos y se muestra. */
export function fechaHoraExacta(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return `${fechaHora(iso)}:${p2(d.getSeconds())}.${String(d.getMilliseconds()).padStart(3, '0')}`;
}

export const ESTADO_USUARIO: Record<EstadoUsuario, { label: string } & ColorPair> = {
  ACTIVO: { label: 'Activo', soft: 'var(--ok-soft)', ink: 'var(--ok-ink)' },
  SUSPENDIDO: { label: 'Suspendido', soft: 'var(--warn-soft)', ink: 'var(--warn-ink)' },
  BAJA: { label: 'Baja', soft: 'var(--off-soft)', ink: 'var(--muted)' },
};

export function etiquetaTipo(tipo: string): string {
  return ETIQUETA_TIPO_AUDITORIA[tipo as keyof typeof ETIQUETA_TIPO_AUDITORIA] ?? tipo;
}

/** Un valor del detalle, legible: los roles por su nombre, las listas separadas por coma. */
function valor(clave: string, v: unknown): string {
  if (v === null || v === undefined) return '—';
  if (Array.isArray(v)) return v.length ? v.join(', ') : '—';
  if (clave === 'rol' && typeof v === 'string') return nombreRol(v);
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

const objeto = (v: unknown): Record<string, unknown> | null =>
  v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : null;

/**
 * Resumen de una línea del detalle de un registro:
 * - `{ agregados, quitados }` → "+ reglas.editar · − pasadas.operar"
 * - `{ anterior, nuevo }`     → "rol: Operario → Productor Viverista"
 * - sólo `nuevo`              → "username: jperez · rol: Operario"
 */
export function resumenDetalle(r: RegistroAuditoria): string {
  const d = r.detalle ?? {};
  if (Array.isArray(d.agregados) || Array.isArray(d.quitados)) {
    const partes: string[] = [];
    if (Array.isArray(d.agregados) && d.agregados.length) partes.push(`+ ${d.agregados.join(', ')}`);
    if (Array.isArray(d.quitados) && d.quitados.length) partes.push(`− ${d.quitados.join(', ')}`);
    return partes.join(' · ') || 'Sin cambios';
  }
  const anterior = objeto(d.anterior);
  const nuevo = objeto(d.nuevo);
  if (anterior && nuevo) {
    const claves = [...new Set([...Object.keys(anterior), ...Object.keys(nuevo)])];
    return claves.map((k) => `${k}: ${valor(k, anterior[k])} → ${valor(k, nuevo[k])}`).join(' · ');
  }
  if (nuevo) return Object.entries(nuevo).map(([k, v]) => `${k}: ${valor(k, v)}`).join(' · ');
  const resto = Object.entries(d);
  return resto.length ? resto.map(([k, v]) => `${k}: ${valor(k, v)}`).join(' · ') : '—';
}

/* ---- Filtros de la auditoría ---- */

export const TAMANIO_PAGINA_AUDITORIA = 25;

export interface FormFiltrosAuditoria {
  autor: string;
  objetivo: string;
  tipo: TipoAuditoria | '';
  /** yyyy-mm-dd del input de fecha, en hora local. */
  desde: string;
  hasta: string;
}

export const SIN_FILTROS_AUDITORIA: FormFiltrosAuditoria = { autor: '', objetivo: '', tipo: '', desde: '', hasta: '' };

/** Del formulario a la consulta: los días locales pasan a instantes (desde el inicio, hasta el final del día). */
export function aConsulta(f: FormFiltrosAuditoria, pagina: number): FiltrosAuditoria {
  const instante = (dia: string, hora: string) => (dia ? new Date(`${dia}T${hora}`).toISOString() : undefined);
  return {
    autor: f.autor.trim() || undefined,
    objetivo: f.objetivo.trim() || undefined,
    tipo: f.tipo || undefined,
    desde: instante(f.desde, '00:00:00.000'),
    hasta: instante(f.hasta, '23:59:59.999'),
    pagina,
    tamanio: TAMANIO_PAGINA_AUDITORIA,
  };
}

/* ---- Política de sesión ---- */

/** null = válido. */
export function errorInactividad(texto: string, minimo = INACTIVIDAD_MIN, maximo = INACTIVIDAD_MAX): string | null {
  const n = Number(texto);
  if (texto.trim() === '' || !Number.isInteger(n) || n < minimo || n > maximo) {
    return `Debe ser un número entero de minutos entre ${minimo} y ${maximo}.`;
  }
  return null;
}
