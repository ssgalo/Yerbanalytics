import { describe, expect, it } from 'vitest';
import { hasReentryRestriction, reentryMessage } from './reentry';

describe('reingreso restringido', () => {
  const now = Date.parse('2026-10-22T12:00:00Z');

  it('rige mientras el instante no pasó', () => {
    expect(hasReentryRestriction(now + 3_600_000, now)).toBe(true);
  });

  it('en el instante exacto o después ya no rige', () => {
    expect(hasReentryRestriction(now, now)).toBe(false);
    expect(hasReentryRestriction(now - 1, now)).toBe(false);
  });

  it('sin fitosanitario aplicado no hay restricción', () => {
    expect(hasReentryRestriction(null, now)).toBe(false);
    expect(hasReentryRestriction(undefined, now)).toBe(false);
  });

  it('el aviso dice hasta cuándo y por qué', () => {
    expect(reentryMessage(now)).toMatch(/Reingreso restringido hasta el .+ fitosanitario/);
  });
});
