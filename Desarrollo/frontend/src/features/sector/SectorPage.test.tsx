// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen } from '@testing-library/react';
import { montarApp } from '@/test/app';
import { seguridadDemo } from '@/test/sesion';

beforeEach(async () => {
  vi.stubEnv('VITE_DATA_SOURCE', 'mock');
  await seguridadDemo('agronomo');
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

describe('SectorPage · enlace al motor (8.6)', () => {
  it('enlaza a la última evaluación del motor con el sector ya elegido', async () => {
    montarApp('/sector/MZ-2-006');

    const enlace = await screen.findByRole('link', { name: /Ver última evaluación del motor/ });

    expect(enlace.getAttribute('href')).toBe('/reglas/inspector?sector=MZ-2-006');
  });
});

describe('SectorPage · diagnósticos anteriores', () => {
  it('enlaza a Diagnósticos de IA filtrado por el sector', async () => {
    montarApp('/sector/MZ-2-006');

    const enlace = await screen.findByRole('link', { name: /Ver anteriores/ });

    expect(enlace.getAttribute('href')).toBe('/diagnosticos?sector=MZ-2-006');
  });
});
