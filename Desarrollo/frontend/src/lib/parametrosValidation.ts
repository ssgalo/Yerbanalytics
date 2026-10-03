/* ============================================================
   Validación genérica de los parámetros del catálogo del motor de reglas.

   Trabaja SÓLO con lo que trae el DTO (tipo, min, max, decimales, unidad): no hay rangos
   ni nombres de parámetros escritos acá, así que una regla nueva en el backend se valida
   sin tocar el frontend. Las restricciones cruzadas entre parámetros no se validan en el
   cliente: las resuelve el servidor y el 400 se muestra junto al parámetro.

   Los mensajes espejan los del backend para que el mock falle igual que el sistema real.
   ============================================================ */
import type { ParametroRegla } from '@/types/domain';
import { unidadSegunValor } from './plural';

type DefinicionValidable = Pick<ParametroRegla, 'tipo' | 'min' | 'max' | 'decimales' | 'unidad'>;

const HORA = /^([01]\d|2[0-3]):[0-5]\d$/;

const fmt = (n: number) => String(n);

function rangoMensaje(def: DefinicionValidable, min: number | null, max: number | null): string {
  // La unidad concuerda con el último número de la frase ("entre 0 y 1 riego", "entre 2 y 10 riegos").
  const unidadDe = (n: number) => (def.unidad ? ` ${unidadSegunValor(def.unidad, n)}` : '');
  if (min !== null && max !== null) return `Debe estar entre ${fmt(min)} y ${fmt(max)}${unidadDe(max)}.`;
  if (min !== null) return `Debe ser como mínimo ${fmt(min)}${unidadDe(min)}.`;
  return `Debe ser como máximo ${fmt(max as number)}${unidadDe(max as number)}.`;
}

function decimalesDe(texto: string): number {
  const punto = texto.indexOf('.');
  return punto === -1 ? 0 : texto.length - punto - 1;
}

/** Error de un valor (texto canónico) para ese parámetro, o null si es válido. */
export function parametroError(def: DefinicionValidable, valor: string): string | null {
  const v = valor.trim();

  if (def.tipo === 'HORA') {
    return HORA.test(v) ? null : 'Hora inválida: usá el formato HH:mm.';
  }

  if (def.tipo === 'VENTANA_HORARIA') {
    const [desde, hasta, ...resto] = v.split('-');
    const ok = resto.length === 0 && HORA.test(desde ?? '') && HORA.test(hasta ?? '') && desde !== hasta;
    return ok ? null : 'Ventana horaria inválida: usá HH:mm-HH:mm, con desde distinto de hasta.';
  }

  // NUMERO y ENTERO. Number('') es 0: el vacío se rechaza antes de convertir.
  const n = v === '' ? NaN : Number(v);
  if (!Number.isFinite(n)) return 'Debe ser un número.';

  if (def.tipo === 'ENTERO' && !Number.isInteger(n)) return 'Debe ser un número entero.';
  if (def.tipo === 'NUMERO' && decimalesDe(v) > def.decimales) {
    return def.decimales === 0
      ? 'Debe ser un número entero.'
      : `Admite como máximo ${def.decimales} decimales.`;
  }

  if ((def.min !== null && n < def.min) || (def.max !== null && n > def.max)) {
    return rangoMensaje(def, def.min, def.max);
  }
  return null;
}

/** Texto para mostrar un valor canónico: "42 %", "06:00–18:00". */
export function formatearValor(def: Pick<ParametroRegla, 'tipo' | 'unidad'>, valor: string): string {
  if (def.tipo === 'VENTANA_HORARIA') return valor.replace('-', '–');
  if (def.tipo === 'HORA') return valor;
  return def.unidad ? `${valor} ${unidadSegunValor(def.unidad, valor)}` : valor;
}
