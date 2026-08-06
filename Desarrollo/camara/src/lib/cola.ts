/* ============================================================
   Cola FIFO de órdenes en memoria.

   El contrato obliga a encolar las órdenes que lleguen mientras hay una captura en curso, en
   vez de descartarlas, y a ACUSAR como fallo toda orden que se descarte. Un descarte
   silencioso es peor que un fallo: el backend no se entera y la orden queda esperando su
   vencimiento en lugar de reintentarse enseguida.
   ============================================================ */

import type { Orden } from './contrato';

export class ColaOrdenes {
  private readonly items: Orden[] = [];

  constructor(private tope: number) {}

  setTope(tope: number) {
    this.tope = Math.max(1, tope);
  }

  get largo() {
    return this.items.length;
  }

  /**
   * Encola una orden. Si la cola está en su tope, descarta la MÁS ANTIGUA y la devuelve para
   * que el llamador la acuse con motivo `COLA_LLENA`.
   *
   * Se descarta la vieja y no la nueva porque el riel ya se movió: la captura recién pedida
   * corresponde a la posición actual, mientras que la vieja apunta a un lugar donde el
   * cabezal ya no está.
   */
  encolar(orden: Orden): { descartada?: Orden } {
    // Reentrega de una orden que ya teníamos (mismo ordenId, intento mayor): se reemplaza.
    const yaEsta = this.items.findIndex((o) => o.ordenId === orden.ordenId);
    if (yaEsta >= 0) {
      this.items[yaEsta] = orden;
      return {};
    }

    let descartada: Orden | undefined;
    if (this.items.length >= this.tope) {
      descartada = this.items.shift();
    }
    this.items.push(orden);
    return { descartada };
  }

  desencolar(): Orden | undefined {
    return this.items.shift();
  }

  vaciar(): Orden[] {
    return this.items.splice(0, this.items.length);
  }
}

/**
 * Espera exponencial con jitter para los reintentos de envío.
 *
 * El jitter no es cosmético: sin él, varios dispositivos que pierden la red a la vez
 * reintentan todos en el mismo instante y golpean el backend en sincronía justo cuando
 * vuelve.
 */
export function esperaBackoff(intento: number, baseMs = 1000, topeMs = 30_000): number {
  const exponencial = Math.min(topeMs, baseMs * 2 ** Math.max(0, intento - 1));
  const jitter = Math.random() * exponencial * 0.3;
  return Math.round(exponencial - exponencial * 0.15 + jitter);
}

export const dormir = (ms: number) => new Promise((r) => setTimeout(r, ms));
