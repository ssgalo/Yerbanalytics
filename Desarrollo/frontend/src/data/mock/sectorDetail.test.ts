import { describe, expect, it } from 'vitest';
import { buildNursery } from './generators';
import { buildSectorDetail } from './sectorDetail';
import { latestCaptureDiagnosis, selectSectorDetail } from '@/data/selectors';

const SEED = 20260613;

const filaValvula = (valve: string) => {
  const { zonas } = buildNursery(SEED);
  const zona = zonas[0];
  const sector = { ...zona.sectors[0], actuadores: { ...zona.sectors[0].actuadores, valve } };
  return buildSectorDetail(sector, zona.lectura).actsRows[0];
};

describe('buildSectorDetail · estado de la válvula (13.5)', () => {
  it('"En cola" se muestra tal cual, sin pasar por abierta ni por cerrada', () => {
    const fila = filaValvula('En cola');

    expect(fila.state).toBe('En cola');
    expect(fila.active).toBe(false);
    // Un estilo propio (ámbar): ni el verde de "Regando" ni el gris de "Cerrada".
    expect(fila.dot).not.toBe(filaValvula('Regando').dot);
    expect(fila.dot).not.toBe(filaValvula('Cerrada').dot);
  });

  it('"Regando" es la única que queda activa', () => {
    expect(filaValvula('Regando')).toMatchObject({ state: 'Regando', active: true });
    expect(filaValvula('Cerrada')).toMatchObject({ state: 'Cerrada', active: false });
  });
});

describe('detalle de sector · diagnóstico con cajas', () => {
  it('toma el diagnóstico DX- más reciente del sector para pedir sus cajas', () => {
    const nursery = buildNursery(SEED);
    const card = nursery.diagnoses.find((d) => d.detectionCount > 0)!;

    const detalle = selectSectorDetail(nursery, card.sectorId)!;
    expect(detalle.diag.diagnosisId).toBe(latestCaptureDiagnosis(nursery, card.sectorId)?.id);
    expect(detalle.diag.diagnosisId).toMatch(/^DX-/);
    expect(detalle.diag.detectionCount).toBeGreaterThan(0);
  });

  it('ignora los DG- derivados y, entre los DX-, elige el más reciente', () => {
    const nursery = buildNursery(SEED);
    const base = nursery.diagnoses[0];
    const conDiag = {
      ...nursery,
      diagnoses: [
        { ...base, id: 'DG-001', sectorId: 'MZ-9-001', creadoEn: 3000 },
        { ...base, id: 'DX-00001', sectorId: 'MZ-9-001', creadoEn: 1000 },
        { ...base, id: 'DX-00002', sectorId: 'MZ-9-001', creadoEn: 2000 },
      ],
    };
    expect(latestCaptureDiagnosis(conDiag, 'MZ-9-001')?.id).toBe('DX-00002');
    expect(latestCaptureDiagnosis(conDiag, 'MZ-9-002')).toBeNull();
  });

  it('sin diagnóstico DX- no hay cajas que pedir, y el nivel de confianza sale del sector', () => {
    const { zonas } = buildNursery(SEED);
    const sector = zonas[0].sectors.find((s) => s.diagnosis.estado === 'Sano')!;
    const { diag } = buildSectorDetail(sector, zonas[0].lectura);
    expect(diag).toMatchObject({ diagnosisId: null, detectionCount: 0, confidence: sector.diagnosis.confidence });
  });
});
