import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

/*
 * Guardián de "Interfaz sin emojis" (openspec/changes/add-motor-reglas-operativo, D-12): ni el frontend, ni el mock, ni un texto que
 * mande el backend pueden traer un pictograma. Un ícono, un estado o una marca se dibuja con Icon, StateGlyph o Chip.
 * La regla se arma con la propiedad Unicode para que este archivo no tenga ningún emoji escrito.
 */

const SRC = join(__dirname, '..');
const PICTOGRAMA = /\p{Extended_Pictographic}/u;
const THIS = 'sinEmojis.test.ts';

function archivos(dir: string): string[] {
  return readdirSync(dir).flatMap((n) => {
    const p = join(dir, n);
    if (statSync(p).isDirectory()) return archivos(p);
    return /\.(tsx?|json)$/.test(n) && n !== THIS ? [p] : [];
  });
}

const usos = (re: RegExp) =>
  archivos(SRC).flatMap((p) =>
    readFileSync(p, 'utf-8')
      .split('\n')
      .flatMap((l, i) => (re.test(l) ? [`${relative(SRC, p)}:${i + 1}`] : [])),
  );

describe('interfaz sin emojis', () => {
  it('ningún .ts, .tsx ni .json de src/ tiene un carácter Extended_Pictographic', () => {
    expect(usos(PICTOGRAMA)).toEqual([]);
  });

  it('el detector detecta: un emoji de verdad cae en la regla', () => {
    expect(PICTOGRAMA.test(String.fromCodePoint(0x1f512))).toBe(true);
    expect(PICTOGRAMA.test('Bloqueo manual')).toBe(false);
  });

  it('el velo de los diálogos usa el token --scrim, no un rgba suelto', () => {
    const css = readFileSync(
      join(SRC, 'features/historial/components/ReasoningModal.module.css'),
      'utf-8',
    );
    const tokens = readFileSync(join(__dirname, 'tokens.css'), 'utf-8');
    expect(tokens).toMatch(/--scrim:\s*rgba\(/);
    expect(css).toMatch(/background:\s*var\(--scrim\)/);
    expect(css).not.toMatch(/rgba\(/);
  });
});
