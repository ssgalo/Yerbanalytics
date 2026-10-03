/* ============================================================
   Resúmenes en palabras del estado del vivero.

   Lógica pura, sin JSX: lo que el Panel general dice sobre cada macro-zona
   y sobre el vivero entero, pensado para quien no conoce el dominio.
   ============================================================ */
import type { Status, Zona } from '@/types/domain';
import { TUBETES_POR_SECTOR } from '@/features/sector/geometriaSector';

/** "1 sector" / "2 sectores". */
export function pluralizar(n: number, singular: string, plural: string): string {
  return `${n} ${n === 1 ? singular : plural}`;
}

const cuenta = (zona: Zona, status: Status) => zona.sectors.filter((s) => s.status === status).length;

/** Sectores que piden atención: en observación o críticos. */
function conAtencion(zona: Zona): number {
  return cuenta(zona, 'warning') + cuenta(zona, 'critical');
}

/**
 * Peor estado de la zona: manda sobre el color de su parcela y su badge.
 * Sin lectura vigente del nodo testigo (o con todos los sectores sin señal) no hay estado que
 * afirmar: `offline`.
 */
export function estadoZona(zona: Zona): Status {
  if (zona.lectura.stale) return 'offline';
  if (cuenta(zona, 'critical') > 0) return 'critical';
  if (cuenta(zona, 'warning') > 0) return 'warning';
  if (zona.sectors.length > 0 && cuenta(zona, 'offline') === zona.sectors.length) return 'offline';
  return 'ok';
}

/** Resumen de la zona en una sola frase. */
export function resumenZona(zona: Zona): string {
  if (zona.lectura.stale) return 'Sin datos del sensor';
  const n = conAtencion(zona);
  if (n === 0) return 'Todo bien';
  return `${pluralizar(n, 'sector', 'sectores')} ${n === 1 ? 'necesita' : 'necesitan'} atención`;
}

/** Resumen de todo el vivero, para el encabezado del plano. */
export function resumenVivero(zonas: Zona[]): { text: string; status: Status } {
  const total = zonas.reduce((acc, z) => acc + z.sectors.length, 0);
  const atencion = zonas.reduce((acc, z) => acc + conAtencion(z), 0);
  const sinDatos = zonas.filter((z) => z.lectura.stale).length;
  const hayCriticos = zonas.some((z) => cuenta(z, 'critical') > 0);

  const status: Status =
    atencion > 0 ? (hayCriticos ? 'critical' : 'warning') : sinDatos > 0 ? 'offline' : 'ok';

  if (atencion === 0 && sinDatos === 0) {
    return { text: `El vivero está saludable: los ${total} sectores están bien.`, status };
  }
  const partes: string[] = [];
  if (atencion > 0) {
    partes.push(
      `${atencion} de ${pluralizar(total, 'sector', 'sectores')} ${atencion === 1 ? 'necesita' : 'necesitan'} atención`,
    );
  }
  if (sinDatos > 0) partes.push(`${pluralizar(sinDatos, 'zona', 'zonas')} sin datos del sensor`);
  return { text: partes.join(' · ') + '.', status };
}

/** Contadores en palabras ("X saludables · Y en observación · Z sin señal"); críticos sólo si hay. */
export function contadoresZona(zona: Zona): string {
  const ok = cuenta(zona, 'ok');
  const warn = cuenta(zona, 'warning');
  const crit = cuenta(zona, 'critical');
  const off = cuenta(zona, 'offline');
  const partes = [
    pluralizar(ok, 'saludable', 'saludables'),
    `${warn} en observación`,
  ];
  if (crit > 0) partes.push(pluralizar(crit, 'crítico', 'críticos'));
  partes.push(`${off} sin señal`);
  return partes.join(' · ');
}

/** "N sectores · M plantines". */
export function textoPlantines(zona: Zona): string {
  /* No se usa `Sector.n`: su valor no es la cantidad de plantines. Un sector son 100 tubetes. */
  const plantines = zona.sectors.length * TUBETES_POR_SECTOR;
  return `${pluralizar(zona.sectors.length, 'sector', 'sectores')} · ${plantines} plantines`;
}

/** Columnas de la grilla de sectores: la disposición configurada, sin pasarse de la cantidad. */
export function columnasGrilla(totalSectores: number, sectoresPorFila: number): number {
  return Math.max(1, Math.min(sectoresPorFila, totalSectores));
}
