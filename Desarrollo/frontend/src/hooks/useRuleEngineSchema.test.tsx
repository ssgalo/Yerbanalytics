// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useRuleEngineSchema } from './useRuleEngineSchema';
import * as data from '@/data';
import type { DagSchema } from '@/types/domain';
import type { DataRepository } from '@/data';

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe('useRuleEngineSchema', () => {
  it('obtiene el esquema del repositorio y no hace ninguna llamada de red directa', async () => {
    const schema: DagSchema = { nodes: [], edges: [] };
    const fetchEspia = vi.fn();
    vi.stubGlobal('fetch', fetchEspia);
    vi.spyOn(data, 'getRepository').mockReturnValue({
      getRuleSchema: vi.fn().mockResolvedValue(schema),
    } as unknown as DataRepository);

    const { result } = renderHook(() => useRuleEngineSchema());

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.schema).toBe(schema);
    expect(result.current.error).toBeNull();
    expect(fetchEspia).not.toHaveBeenCalled();
  });

  it('expone el error del repositorio', async () => {
    vi.spyOn(data, 'getRepository').mockReturnValue({
      getRuleSchema: vi.fn().mockRejectedValue(new Error('Error 500 al obtener el esquema del motor')),
    } as unknown as DataRepository);

    const { result } = renderHook(() => useRuleEngineSchema());

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.schema).toBeNull();
    expect(result.current.error?.message).toMatch(/500/);
  });

  it('en modo mock (el Historial en la demo) trae el DAG sin backend', async () => {
    // El `.env` local puede apuntar al backend: el test fija el modo para no depender de él.
    vi.stubEnv('VITE_DATA_SOURCE', 'mock');
    const fetchEspia = vi.fn();
    vi.stubGlobal('fetch', fetchEspia);

    const { result } = renderHook(() => useRuleEngineSchema());

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.schema?.nodes.map((n) => n.id)).toContain('IrrigationRule');
    expect(fetchEspia).not.toHaveBeenCalled();
  });
});
