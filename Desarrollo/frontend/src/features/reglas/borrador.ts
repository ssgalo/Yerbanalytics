/* ============================================================
   Borrador de edición del catálogo, indexado POR CLAVE (no por regla).

   Un parámetro compartido aparece bajo varias reglas pero es un solo valor: si el borrador
   se guardara por tarjeta, editarlo en una dejaría la otra mostrando el valor viejo y al
   guardar saldrían dos cambios para la misma clave. Indexado por clave, todas las
   apariciones leen la misma entrada y sale un único cambio.

   Entrada `string` = valor editado (texto canónico); `null` = restablecer a fábrica.
   Las funciones son puras: devuelven un borrador nuevo.
   ============================================================ */
import { parametroError } from '@/lib/parametrosValidation';
import type { CambioParametro, CatalogoReglas, ParametroRegla } from '@/types/domain';

export type Borrador = ReadonlyMap<string, string | null>;

/** Dos valores canónicos del mismo parámetro: numérico para números ("42" = "42.0"), texto para el resto. */
function mismoValor(p: Pick<ParametroRegla, 'tipo'>, a: string, b: string): boolean {
  if (p.tipo === 'NUMERO' || p.tipo === 'ENTERO') {
    const na = Number(a);
    const nb = Number(b);
    return a.trim() !== '' && Number.isFinite(na) && na === nb;
  }
  return a === b;
}

/** Valor a mostrar en cualquier aparición del parámetro. */
export function valorMostrado(p: ParametroRegla, borrador: Borrador): string {
  if (!borrador.has(p.clave)) return p.valor;
  return borrador.get(p.clave) ?? p.fabrica;
}

/** Registra una edición. Volver al valor vigente deja el parámetro sin cambios. */
export function editar(borrador: Borrador, p: ParametroRegla, texto: string): Borrador {
  const out = new Map(borrador);
  if (mismoValor(p, texto, p.valor)) out.delete(p.clave);
  else out.set(p.clave, texto);
  return out;
}

/** Restablece a fábrica; si ya estaba en fábrica sólo descarta la edición pendiente. */
export function restablecer(borrador: Borrador, p: ParametroRegla): Borrador {
  const out = new Map(borrador);
  if (p.modificado) out.set(p.clave, null);
  else out.delete(p.clave);
  return out;
}

/** Los cambios a enviar: uno por clave editada, en el orden del catálogo. */
export function cambiosDelBorrador(catalogo: CatalogoReglas, borrador: Borrador): CambioParametro[] {
  const cambios: CambioParametro[] = [];
  for (const p of catalogo.parametros) {
    if (!borrador.has(p.clave)) continue;
    const v = borrador.get(p.clave) ?? null;
    // Volver al valor de fábrica es restablecer: el servidor borra el override.
    cambios.push({ clave: p.clave, valor: v === null || mismoValor(p, v, p.fabrica) ? null : v });
  }
  return cambios;
}

/**
 * El borrador que queda tras guardar lo `enviado`: se borran sólo las claves enviadas cuyo valor
 * no cambió desde el envío. Lo editado mientras el PUT estaba en vuelo se conserva.
 */
export function sinEnviados(actual: Borrador, enviado: Borrador): Borrador {
  const out = new Map(actual);
  for (const [clave, valor] of enviado) {
    if (actual.has(clave) && actual.get(clave) === valor) out.delete(clave);
  }
  return out;
}

/** Errores de cliente por clave (tipo, rango, decimales). Restablecer nunca es inválido. */
export function erroresDelBorrador(catalogo: CatalogoReglas, borrador: Borrador): Map<string, string> {
  const errores = new Map<string, string>();
  for (const p of catalogo.parametros) {
    const v = borrador.get(p.clave);
    if (typeof v !== 'string') continue;
    const e = parametroError(p, v);
    if (e) errores.set(p.clave, e);
  }
  return errores;
}
