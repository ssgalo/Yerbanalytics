/* ============================================================
   Borrador de edición del catálogo, indexado por COPIA: `(regla, clave)`.

   Cada pieza tiene su copia de cada parámetro que lee (design D-1): editar el umbral de riego en
   R-01 no cambia el de R-03. El borrador guarda una entrada por copia y sale un cambio por copia.

   Entrada `string` = valor editado (texto canónico); escribir el de fábrica lo restablece.
   Las funciones son puras: devuelven un borrador nuevo.
   ============================================================ */
import { parametroError } from '@/lib/parametrosValidation';
import type { CambioParametro, CatalogoReglas, ParametroRegla } from '@/types/domain';

export type Borrador = ReadonlyMap<string, string | null>;

/** Índice de una copia en el borrador (y en los errores): `regla|clave`. */
export const idCopia = (p: Pick<ParametroRegla, 'regla' | 'clave'>): string => `${p.regla}|${p.clave}`;

/** Dos valores canónicos del mismo parámetro: numérico para números ("42" = "42.0"), texto para el resto. */
function mismoValor(p: Pick<ParametroRegla, 'tipo'>, a: string, b: string): boolean {
  if (p.tipo === 'NUMERO' || p.tipo === 'ENTERO') {
    const na = Number(a);
    const nb = Number(b);
    return a.trim() !== '' && Number.isFinite(na) && na === nb;
  }
  return a === b;
}

/** Valor a mostrar de la copia. */
export function valorMostrado(p: ParametroRegla, borrador: Borrador): string {
  const id = idCopia(p);
  if (!borrador.has(id)) return p.valor;
  return borrador.get(id) ?? p.fabrica;
}

/** Registra una edición. Volver al valor vigente deja la copia sin cambios. */
export function editar(borrador: Borrador, p: ParametroRegla, texto: string): Borrador {
  const out = new Map(borrador);
  if (mismoValor(p, texto, p.valor)) out.delete(idCopia(p));
  else out.set(idCopia(p), texto);
  return out;
}

/** El borrador sin las ediciones de una pieza (p. ej. al eliminarla). */
export function sinRegla(borrador: Borrador, regla: string): Borrador {
  const out = new Map(borrador);
  for (const id of borrador.keys()) if (id.startsWith(`${regla}|`)) out.delete(id);
  return out.size === borrador.size ? borrador : out;
}

/** Los cambios a enviar: uno por copia editada, en el orden del catálogo. */
export function cambiosDelBorrador(catalogo: CatalogoReglas, borrador: Borrador): CambioParametro[] {
  const cambios: CambioParametro[] = [];
  for (const p of catalogo.parametros) {
    const id = idCopia(p);
    if (!borrador.has(id)) continue;
    const v = borrador.get(id) ?? null;
    // Volver al valor de fábrica es restablecer: el servidor borra el override.
    cambios.push({
      regla: p.regla,
      clave: p.clave,
      valor: v === null || mismoValor(p, v, p.fabrica) ? null : v,
    });
  }
  return cambios;
}

/**
 * El borrador que queda tras guardar lo `enviado`: se borran sólo las copias enviadas cuyo valor
 * no cambió desde el envío. Lo editado mientras el PUT estaba en vuelo se conserva.
 */
export function sinEnviados(actual: Borrador, enviado: Borrador): Borrador {
  const out = new Map(actual);
  for (const [id, valor] of enviado) {
    if (actual.has(id) && actual.get(id) === valor) out.delete(id);
  }
  return out;
}

/** Errores de cliente por copia (tipo, rango, decimales). Restablecer nunca es inválido. */
export function erroresDelBorrador(catalogo: CatalogoReglas, borrador: Borrador): Map<string, string> {
  const errores = new Map<string, string>();
  for (const p of catalogo.parametros) {
    const v = borrador.get(idCopia(p));
    if (typeof v !== 'string') continue;
    const e = parametroError(p, v);
    if (e) errores.set(idCopia(p), e);
  }
  return errores;
}
