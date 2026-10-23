import { describe, expect, it } from 'vitest';
import {
  altaDispositivo,
  altaError,
  buildFleet,
  buildHardware,
  nextDeviceId,
  recambioDispositivo,
  repararDispositivo,
} from './hardware';
import type { NuevoDispositivo } from '@/types/domain';

const FIXED_NOW = 1_700_000_000_000;

describe('flota de hardware (HU-18 / HU-21)', () => {
  it('es determinística para un mismo now', () => {
    expect(buildFleet(FIXED_NOW)).toEqual(buildFleet(FIXED_NOW));
  });

  it('deriva los tres estados y la batería baja según el seed', () => {
    const hw = buildHardware(buildFleet(FIXED_NOW), FIXED_NOW);
    const byId = new Map(hw.dispositivos.map((d) => [d.id, d]));
    expect(byId.get('DEV-001')!.estado).toBe('operativo'); // reportó hace 20 s
    expect(byId.get('DEV-002')!.bateriaBaja).toBe(true); // batería 16%
    expect(byId.get('DEV-003')!.estado).toBe('intermitente'); // hace 3 h
    expect(byId.get('DEV-004')!.estado).toBe('fuera_de_servicio'); // hace 26 h
    expect(byId.get('DEV-110')!.estado).toBe('fuera_de_servicio'); // averiado
    expect(byId.get('DEV-110')!.falla).toBe('SIN_CAUDAL'); // el tipo de error del nodo (S-05)
    expect(byId.get('DEV-110')!.fallaDesde).toBe(FIXED_NOW - 2 * 3_600_000);
    expect(byId.get('DEV-101')!.fallaDesde).toBeNull();
  });

  it('cuenta los KPIs de la flota', () => {
    const hw = buildHardware(buildFleet(FIXED_NOW), FIXED_NOW);
    expect(hw.total).toBe(18);
    expect(hw.bateriaBaja).toBe(1);
    expect(hw.averiados).toBe(1);
    expect(hw.fueraDeServicio).toBeGreaterThanOrEqual(2); // nodo caído + averiado
  });

  it('detecta los sectores incompletos (HU-18 CA-04)', () => {
    const hw = buildHardware(buildFleet(FIXED_NOW), FIXED_NOW);
    const ids = hw.incompletos.map((s) => s.sectorId);
    expect(ids).toContain('MZ-1-003'); // falta la bomba
    expect(ids).toContain('MZ-3-010'); // faltan bomba y mediasombra
    expect(ids).not.toContain('MZ-1-001'); // completo
    const mz3 = hw.incompletos.find((s) => s.sectorId === 'MZ-3-010')!;
    expect(mz3.faltantes.length).toBe(2);
  });
});

describe('alta de dispositivos (HU-18 CA-02/03)', () => {
  it('rechaza un serial/MAC duplicado', () => {
    const fleet = buildFleet(FIXED_NOW);
    const dup: NuevoDispositivo = {
      serial: 'A4:CF:12:9A:00:01',
      tipo: 'electrovalvula',
      zonaId: null,
      sectorId: 'MZ-2-005',
    };
    expect(altaError(fleet, dup)).not.toBeNull();
  });

  it('rechaza un segundo nodo testigo en la misma macro-zona', () => {
    const fleet = buildFleet(FIXED_NOW);
    const dup: NuevoDispositivo = {
      serial: 'NEW-NODE-01',
      tipo: 'nodo_testigo',
      zonaId: 'MZ-1',
      sectorId: null,
    };
    expect(altaError(fleet, dup)).not.toBeNull();
  });

  it('rechaza un segundo actuador del mismo tipo en un sector', () => {
    const fleet = buildFleet(FIXED_NOW);
    const dup: NuevoDispositivo = {
      serial: 'NEW-EV-01',
      tipo: 'electrovalvula',
      zonaId: null,
      sectorId: 'MZ-1-001',
    };
    expect(altaError(fleet, dup)).not.toBeNull();
  });

  it('acepta un alta válida y la agrega con un id nuevo', () => {
    const fleet = buildFleet(FIXED_NOW);
    const nuevo: NuevoDispositivo = {
      serial: 'BP-1-003',
      tipo: 'bomba_peristaltica',
      zonaId: null,
      sectorId: 'MZ-1-003',
    };
    expect(altaError(fleet, nuevo)).toBeNull();
    const idEsperado = nextDeviceId(fleet);
    const raw = altaDispositivo(fleet, nuevo);
    expect(raw.id).toBe(idEsperado);
    // MZ-1-003 deja de estar incompleto al completarse la bomba.
    const hw = buildHardware(fleet, FIXED_NOW);
    expect(hw.incompletos.map((s) => s.sectorId)).not.toContain('MZ-1-003');
  });
});

describe('sector del nodo testigo (E-01)', () => {
  const nodo = (sectorId: string | null): NuevoDispositivo => ({
    serial: 'NODO-TESTIGO-1',
    tipo: 'nodo_testigo',
    zonaId: 'MZ-3',
    sectorId,
  });

  it('el sector es opcional y se guarda si se informa', () => {
    const fleet: ReturnType<typeof buildFleet> = [];
    expect(altaError(fleet, nodo(null))).toBeNull();
    expect(altaError(fleet, nodo('MZ-3-045'))).toBeNull();
    const raw = altaDispositivo(fleet, nodo('MZ-3-045'));
    expect(raw.zonaId).toBe('MZ-3');
    expect(raw.sectorId).toBe('MZ-3-045');
  });

  it('rechaza un sector de otra macro-zona', () => {
    expect(altaError([], nodo('MZ-1-045'))).toMatch(/no pertenece a la macro-zona/);
  });

  it('el sector del nodo no lo hace figurar como sector con hardware incompleto', () => {
    const fleet: ReturnType<typeof buildFleet> = [];
    altaDispositivo(fleet, nodo('MZ-3-045'));
    const hw = buildHardware(fleet, FIXED_NOW);
    expect(hw.incompletos).toEqual([]);
    expect(hw.dispositivos[0].ubicacion).toBe('MZ-3 · MZ-3-045');
  });
});

describe('recambio (HU-21 CA-05)', () => {
  it('limpia la avería y conserva la posición', () => {
    const fleet = buildFleet(FIXED_NOW);
    recambioDispositivo(fleet, 'DEV-110', {
      serial: 'EV-5-042-NEW',
      tipo: 'electrovalvula',
      zonaId: null,
      sectorId: 'MZ-5-042',
    });
    const hw = buildHardware(fleet, FIXED_NOW);
    const dev = hw.dispositivos.find((d) => d.id === 'DEV-110')!;
    expect(dev.falla).toBeNull();
    expect(dev.serial).toBe('EV-5-042-NEW');
    expect(dev.sectorId).toBe('MZ-5-042');
    expect(dev.estado).toBe('operativo');
  });
});

describe('reparación (S-05)', () => {
  it('levanta la falla y deja el dispositivo operativo, con la posición intacta', () => {
    const fleet = buildFleet(FIXED_NOW);

    repararDispositivo(fleet, 'DEV-110', '  Se destapó el emisor  ');

    const dev = buildHardware(fleet, FIXED_NOW).dispositivos.find((d) => d.id === 'DEV-110')!;
    expect(dev.falla).toBeNull();
    expect(dev.fallaDesde).toBeNull();
    expect(dev.serial).toBe('EV-5-042');
    expect(dev.estado).toBe('operativo');
    expect(buildHardware(fleet, FIXED_NOW).averiados).toBe(0);
  });

  it('exige el detalle, como el backend', () => {
    const fleet = buildFleet(FIXED_NOW);

    expect(() => repararDispositivo(fleet, 'DEV-110', '   ')).toThrow(/detalle/i);
    expect(fleet.find((d) => d.id === 'DEV-110')!.falla).toBe('SIN_CAUDAL');
  });

  it('un dispositivo que no existe, o que no tiene falla, se rechaza', () => {
    const fleet = buildFleet(FIXED_NOW);

    expect(() => repararDispositivo(fleet, 'DEV-999', 'x')).toThrow(/no existe/);
    expect(() => repararDispositivo(fleet, 'DEV-101', 'x')).toThrow(/no tiene una falla/);
  });
});
