import { describe, expect, it } from 'vitest';
import type { EnvioTelemetria } from '@/types/domain';
import { buildNursery } from './generators';
import { aplicarEnvioMock } from './simulacion';

const envio = (zonaId: string, metrics: EnvioTelemetria['metrics']): EnvioTelemetria => ({
  serial: 'A4:CF:12:9A:00:0X',
  zonaId,
  metrics,
});

/** Lectura completa en valores ideales de las 10 métricas. */
const IDEAL = {
  humSus: 55,
  humAmb: 72,
  temp: 23,
  tempSuelo: 20,
  uv: 50,
  ce: 1.4,
  phSuelo: 5.5,
  n: 150,
  p: 45,
  k: 180,
};

describe('aplicarEnvioMock', () => {
  it('aplica la lectura UNA sola vez, en la macro-zona', () => {
    const nursery = buildNursery(20260613);
    const out = aplicarEnvioMock(nursery, envio('MZ-1', IDEAL));

    const zona = out.zonas.find((z) => z.id === 'MZ-1')!;
    expect(zona.lectura.metrics).toHaveLength(10);
    expect(zona.lectura.metrics.every((m) => m.status === 'ok')).toBe(true);
    expect(zona.lectura.stale).toBe(false);
    expect(zona.nodo.mac).toBe('A4:CF:12:9A:00:0X');

    // Con el ambiente en rango, lo único que puede dejar a un sector en alerta es el
    // diagnóstico de su plantín.
    const enAlerta = out.sectors.filter(
      (s) => s.zona === 'MZ-1' && (s.status === 'warning' || s.status === 'critical'),
    );
    expect(enAlerta.every((s) => s.diagnosis.sev === 'Alta' || s.diagnosis.sev === 'Media')).toBe(
      true,
    );

    // Las stats globales quedan consistentes.
    expect(out.stats.sano + out.stats.warning + out.stats.critical + out.stats.offline).toBe(
      out.stats.total,
    );
  });

  it('recalcula los agregados de la zona a partir de sus sectores', () => {
    const nursery = buildNursery(20260613);
    const out = aplicarEnvioMock(nursery, envio('MZ-1', IDEAL));

    const zona = out.zonas.find((z) => z.id === 'MZ-1')!;
    const suyos = out.sectors.filter((s) => s.zona === 'MZ-1');
    expect(zona.sano + zona.alerta + zona.off).toBe(suyos.length);
    expect(zona.sano).toBe(suyos.filter((s) => s.status === 'ok').length);
  });

  it('una lectura fuera de banda degrada a TODOS los sectores de la zona', () => {
    const nursery = buildNursery(20260613);
    const out = aplicarEnvioMock(nursery, envio('MZ-3', { ...IDEAL, humSus: 10, temp: 40 }));

    // Los sectores con su propio nodo caído siguen offline: la lectura de la zona no los revive.
    const zonaSectors = out.sectors.filter((s) => s.zona === 'MZ-3' && s.status !== 'offline');
    expect(zonaSectors.length).toBeGreaterThan(0);
    expect(zonaSectors.every((s) => s.status === 'critical')).toBe(true);
  });

  it('una métrica informativa fuera de banda se colorea pero no cambia el estado', () => {
    const nursery = buildNursery(20260613);
    // pH 4,2 cae en banda crítica, pero phSuelo es informativa (afectaEstado: false).
    const out = aplicarEnvioMock(nursery, envio('MZ-2', { ...IDEAL, phSuelo: 4.2 }));

    const zona = out.zonas.find((z) => z.id === 'MZ-2')!;
    const ph = zona.lectura.metrics.find((m) => m.key === 'phSuelo')!;
    expect(ph.status).toBe('critical');

    // Ningún sector se degrada por causa ambiental: los que quedan en alerta es por diagnóstico.
    const enAlerta = out.sectors.filter(
      (s) => s.zona === 'MZ-2' && (s.status === 'warning' || s.status === 'critical'),
    );
    expect(enAlerta.every((s) => s.diagnosis.sev === 'Alta' || s.diagnosis.sev === 'Media')).toBe(
      true,
    );
  });

  it('un envío parcial sólo actualiza la métrica enviada y conserva las demás', () => {
    const nursery = buildNursery(20260613);
    // Primero un envío completo deja la zona en valores ideales conocidos.
    const base = aplicarEnvioMock(nursery, envio('MZ-1', IDEAL));
    // Luego se envía sólo la luminosidad, en valor crítico.
    const out = aplicarEnvioMock(base, envio('MZ-1', { uv: 13 }));

    const zona = out.zonas.find((z) => z.id === 'MZ-1')!;
    const uv = zona.lectura.metrics.find((m) => m.key === 'uv')!;
    const temp = zona.lectura.metrics.find((m) => m.key === 'temp')!;
    // La métrica enviada cambia; las no enviadas conservan el valor previo.
    expect(uv.raw).toBe(13);
    expect(temp.raw).toBe(23);
    // La luminosidad crítica arrastra a los sectores que sí están reportando.
    const vivos = out.sectors.filter((s) => s.zona === 'MZ-1' && s.status !== 'offline');
    expect(vivos.every((s) => s.status === 'critical')).toBe(true);
  });

  it('no muta el dataset original ni toca otras zonas', () => {
    const nursery = buildNursery(20260613);
    const otrasAntes = nursery.sectors.filter((s) => s.zona === 'MZ-6').map((s) => s.status);
    const lecturaAntes = nursery.zonas.find((z) => z.id === 'MZ-6')!.lectura;

    const out = aplicarEnvioMock(nursery, envio('MZ-1', IDEAL));

    expect(out).not.toBe(nursery);
    const otrasDespues = out.sectors.filter((s) => s.zona === 'MZ-6').map((s) => s.status);
    expect(otrasDespues).toEqual(otrasAntes);
    expect(out.zonas.find((z) => z.id === 'MZ-6')!.lectura).toBe(lecturaAntes);
  });

  it('ignora un envío a una macro-zona inexistente', () => {
    const nursery = buildNursery(20260613);
    expect(aplicarEnvioMock(nursery, envio('MZ-99', IDEAL))).toBe(nursery);
  });
});
