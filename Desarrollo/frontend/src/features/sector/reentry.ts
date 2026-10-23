/* ============================================================
   Reingreso restringido de un sector tras un fitosanitario (reglas_v2 §8.3). El backend manda hasta cuándo
   (`Sector.reentryUntil`); un instante pasado, o ninguno, significa que no hay restricción. Sólo informa: no
   bloquea ningún actuador.
   ============================================================ */

/** `true` si el sector sigue con el reingreso restringido en `now` (epoch ms). */
export function hasReentryRestriction(reentryUntil: number | null | undefined, now: number): boolean {
  return reentryUntil != null && reentryUntil > now;
}

/** Texto del aviso: hasta cuándo no se puede reingresar. */
export function reentryMessage(reentryUntil: number): string {
  const until = new Date(reentryUntil).toLocaleString('es-AR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
  return `Reingreso restringido hasta el ${until}: se le aplicó un fitosanitario.`;
}
