import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';
import { theme } from './theme';

/*
 * Guardián del sistema de diseño (openspec/changes/add-sistema-diseno): si alguien vuelve a usar
 * un token retirado o un color de la paleta anterior, este test dice dónde.
 */

const SRC = join(__dirname, '..');
const RETIRADOS = /--(g900|g800|g700|orange|orange-soft|cream|cdim|cfaint)\b/;
const PALETA_VIEJA = /#(3fa06a|e0972c|dd5238|a9b2ab|3b82f6)\b/i;

function archivos(dir: string): string[] {
  return readdirSync(dir).flatMap((n) => {
    const p = join(dir, n);
    if (statSync(p).isDirectory()) return archivos(p);
    return /\.(css|tsx?)$/.test(n) && !n.endsWith('tokens.test.ts') ? [p] : [];
  });
}

const usos = (re: RegExp) =>
  archivos(SRC).flatMap((p) =>
    readFileSync(p, 'utf-8')
      .split('\n')
      .flatMap((l, i) => (re.test(l) ? [`${relative(SRC, p)}:${i + 1}`] : [])),
  );

describe('sistema de diseño', () => {
  it('ningún módulo usa un token retirado', () => {
    expect(usos(RETIRADOS)).toEqual([]);
  });

  it('ningún módulo usa un hex de la paleta anterior', () => {
    expect(usos(PALETA_VIEJA)).toEqual([]);
  });

  it('theme.ts espeja los valores de tokens.css', () => {
    const css = readFileSync(join(__dirname, 'tokens.css'), 'utf-8');
    const valor = (token: string) => css.match(new RegExp(`${token}:\\s*(#[0-9a-f]{6})`, 'i'))?.[1]?.toUpperCase();
    const pares: [string, string][] = [
      ['--bg', theme.bg],
      ['--ink', theme.ink],
      ['--muted', theme.muted],
      ['--brand', theme.brand],
      ['--ok', theme.ok],
      ['--warn', theme.warn],
      ['--crit', theme.crit],
      ['--off', theme.off],
      ['--info', theme.info],
      ['--tray', theme.tray],
      ['--det-sano', theme.detSano],
      ['--det-clorosis', theme.detClorosis],
      ['--det-estres-solar', theme.detEstresSolar],
      ['--det-plaga-foliar', theme.detPlagaFoliar],
      ['--det-dano-fungico', theme.detDanoFungico],
    ];
    for (const [token, hex] of pares) expect(valor(token), token).toBe(hex);
  });

  it('los colores de las cajas de detección sólo se usan sobre la fotografía (y en el catálogo)', () => {
    // Su uso está restringido a superposiciones sobre una foto: si aparecen en otro módulo, es
    // tinta saturada fuera de lugar.
    const permitidos = [
      'styles/tokens.css',
      'features/sector/components/detectionView.ts',
      'features/sistema-diseno/SistemaDisenoPage.tsx',
    ];
    const fuera = usos(/--det-/).filter((u) => !permitidos.some((p) => u.split('\\').join('/').startsWith(p)));
    expect(fuera).toEqual([]);
  });
});
