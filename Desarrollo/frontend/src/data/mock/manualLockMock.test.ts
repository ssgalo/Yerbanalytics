import { describe, expect, it } from 'vitest';
import { MockRepository } from './mockRepository';
import { ManualLockError } from '@/data/manualLockError';

describe('MockRepository · bloqueo manual', () => {
  it('arranca sin bloqueos', async () => {
    expect(await new MockRepository(1).getManualLocks()).toEqual([]);
  });

  it('bloquea un sector, lo lista con su zona y lo reanuda', async () => {
    const repo = new MockRepository(1);

    const creado = await repo.activateManualLock({
      zonaId: 'MZ-1',
      sectorId: 'MZ-1-001',
      motivo: ' mantenimiento ',
    });

    expect(creado).toMatchObject({ zonaId: 'MZ-1', sectorId: 'MZ-1-001', motivo: 'mantenimiento' });
    expect(await repo.getManualLocks()).toEqual([creado]);

    await repo.releaseManualLock(creado.id);
    expect(await repo.getManualLocks()).toEqual([]);
  });

  it('bloquea una macro-zona entera (sin sector)', async () => {
    const repo = new MockRepository(1);
    const creado = await repo.activateManualLock({ zonaId: 'MZ-2', motivo: 'x' });
    expect(creado.sectorId).toBeNull();
  });

  it('exige motivo', async () => {
    const repo = new MockRepository(1);
    await expect(repo.activateManualLock({ zonaId: 'MZ-1', motivo: '   ' })).rejects.toBeInstanceOf(
      ManualLockError,
    );
  });

  it('rechaza bloquear dos veces lo mismo y reanudar lo que no está bloqueado', async () => {
    const repo = new MockRepository(1);
    await repo.activateManualLock({ zonaId: 'MZ-1', motivo: 'x' });

    await expect(repo.activateManualLock({ zonaId: 'MZ-1', motivo: 'y' })).rejects.toBeInstanceOf(
      ManualLockError,
    );
    await expect(repo.releaseManualLock(999)).rejects.toBeInstanceOf(ManualLockError);
  });

  it('dos bloqueos de sectores distintos conviven', async () => {
    const repo = new MockRepository(1);
    await repo.activateManualLock({ zonaId: 'MZ-1', sectorId: 'MZ-1-001', motivo: 'x' });
    await repo.activateManualLock({ zonaId: 'MZ-1', sectorId: 'MZ-1-002', motivo: 'x' });
    expect(await repo.getManualLocks()).toHaveLength(2);
  });
});
