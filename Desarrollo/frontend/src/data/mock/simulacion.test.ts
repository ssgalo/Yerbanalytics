import { describe, expect, it } from 'vitest';
import type { EnvioTelemetria } from '@/types/domain';
import { buildNursery } from './generators';
import { aplicarEnvioMock } from './simulacion';

const envio = (zonaId: string, metrics: EnvioTelemetria['metrics']): EnvioTelemetria => ({
  serial: 'A4:CF:12:9A:00:0X',
  zonaId,
  metrics,
});

describe('aplicarEnvioMock', () => {
  it('deja saludables los sectores de la zona con métricas ideales y recalcula agregados', () => {
    const nursery = buildNursery(20260613);
    const out = aplicarEnvioMock(nursery, envio('MZ-1', { humSus: 55, humAmb: 72, temp: 23, ce: 1.4, uv: 4 }));

    const zonaSectors = out.sectors.filter((s) => s.zona === 'MZ-1');
    expect(zonaSectors.length).toBeGreaterThan(0);
    expect(zonaSectors.every((s) => s.status === 'ok')).toBe(true);

    const zona = out.zonas.find((z) => z.id === 'MZ-1')!;
    expect(zona.sano).toBe(zonaSectors.length);
    expect(zona.alerta).toBe(0);
    expect(zona.off).toBe(0);

    // Las stats globales quedan consistentes.
    expect(out.stats.sano + out.stats.warning + out.stats.critical + out.stats.offline).toBe(out.stats.total);
  });

  it('pone en crítico los sectores cuando las métricas caen fuera de banda', () => {
    const nursery = buildNursery(20260613);
    const out = aplicarEnvioMock(nursery, envio('MZ-3', { humSus: 10, humAmb: 30, temp: 40, ce: 4, uv: 13 }));

    const zonaSectors = out.sectors.filter((s) => s.zona === 'MZ-3');
    expect(zonaSectors.every((s) => s.status === 'critical')).toBe(true);
  });

  it('un envío parcial sólo actualiza la métrica enviada y conserva las demás', () => {
    const nursery = buildNursery(20260613);
    // Primero un envío completo deja la zona en valores ideales conocidos.
    const base = aplicarEnvioMock(nursery, envio('MZ-1', { humSus: 55, humAmb: 72, temp: 23, ce: 1.4, uv: 4 }));
    // Luego se envía sólo la radiación (uv), en valor crítico.
    const out = aplicarEnvioMock(base, envio('MZ-1', { uv: 13 }));

    const sector = out.sectors.find((s) => s.zona === 'MZ-1')!;
    const uv = sector.metrics.find((m) => m.key === 'uv')!;
    const temp = sector.metrics.find((m) => m.key === 'temp')!;
    // La métrica enviada cambia; las no enviadas conservan el valor previo.
    expect(uv.raw).toBe(13);
    expect(temp.raw).toBe(23);
    // El estado se recomputa con la combinación: la radiación crítica lo pone en crítico.
    expect(sector.status).toBe('critical');
  });

  it('no muta el dataset original ni toca otras zonas', () => {
    const nursery = buildNursery(20260613);
    const otrasAntes = nursery.sectors.filter((s) => s.zona === 'MZ-6').map((s) => s.status);

    const out = aplicarEnvioMock(nursery, envio('MZ-1', { humSus: 55, humAmb: 72, temp: 23, ce: 1.4, uv: 4 }));

    expect(out).not.toBe(nursery);
    const otrasDespues = out.sectors.filter((s) => s.zona === 'MZ-6').map((s) => s.status);
    expect(otrasDespues).toEqual(otrasAntes);
  });
});
