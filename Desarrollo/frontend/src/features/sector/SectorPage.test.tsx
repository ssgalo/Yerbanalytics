// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { NurseryProvider } from '@/hooks/NurseryContext';
import { routes } from '@/router';

beforeEach(() => vi.stubEnv('VITE_DATA_SOURCE', 'mock'));
afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
});

describe('SectorPage · enlace al motor (8.6)', () => {
  it('enlaza a la última evaluación del motor con el sector ya elegido', async () => {
    const router = createMemoryRouter(routes, {
      initialEntries: ['/sector/MZ-2-006'],
      future: { v7_relativeSplatPath: true },
    });
    render(
      <NurseryProvider>
        <RouterProvider router={router} future={{ v7_startTransition: true }} />
      </NurseryProvider>,
    );

    const enlace = await screen.findByRole('link', { name: /Ver última evaluación del motor/ });

    expect(enlace.getAttribute('href')).toBe('/reglas?tab=inspector&sector=MZ-2-006');
  });
});
