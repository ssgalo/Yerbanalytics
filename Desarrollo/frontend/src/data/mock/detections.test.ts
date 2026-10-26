import { describe, expect, it } from 'vitest';
import { DEFAULT_DIAGNOSIS_CRITERIA } from '@/lib/diagnosisCriteria';
import { buildNursery } from './generators';
import { mockConfidenceLevel, mockDetections } from './detections';
import { MockRepository } from './mockRepository';

const SEED = 20260613;

describe('detecciones de la demo', () => {
  const { diagnoses } = buildNursery(SEED);

  it('son determinísticas y cada tarjeta cuenta exactamente las suyas', () => {
    for (const d of diagnoses) {
      const a = mockDetections(d.id, d.estado, d.sev, d.conf);
      expect(a).toEqual(mockDetections(d.id, d.estado, d.sev, d.conf));
      expect(a).toHaveLength(d.detectionCount);
    }
    expect(diagnoses.some((d) => d.detectionCount > 0)).toBe(true);
  });

  it('las cajas del estado diagnosticado caen en la banda de su severidad y promedian su score', () => {
    for (const d of diagnoses.filter((x) => x.estado !== 'No concluyente')) {
      const propias = mockDetections(d.id, d.estado, d.sev, d.conf).filter((x) => x.label === d.estado);
      const band = DEFAULT_DIAGNOSIS_CRITERIA.severityBands.find((b) => b.state === d.estado)!;
      const n = propias.length;
      if (d.sev === 'Alta') expect(n).toBeGreaterThanOrEqual(band.highFrom);
      if (d.sev === 'Media') expect(n >= band.mediumFrom && n < band.highFrom).toBe(true);
      if (d.sev === 'Baja') expect(n >= band.minimum && n < band.mediumFrom).toBe(true);
      const mean = propias.reduce((s, x) => s + x.score, 0) / n;
      expect(Math.abs(mean * 100 - (d.conf as number))).toBeLessThan(0.05);
      expect(d.confidence).toBe(mockConfidenceLevel(d.estado, d.conf));
    }
  });

  it('No concluyente no tiene score, nivel ni cajas propias; cajas normalizadas y bien formadas', () => {
    for (const d of diagnoses) {
      const dets = mockDetections(d.id, d.estado, d.sev, d.conf);
      if (d.estado === 'No concluyente') {
        expect(d.conf).toBeNull();
        expect(d.confidence).toBeNull();
        expect(dets.some((x) => x.label === 'No concluyente')).toBe(false);
      }
      for (const { box, score } of dets) {
        const [x1, y1, x2, y2] = box;
        expect(x1).toBeGreaterThanOrEqual(0);
        expect(y2).toBeLessThanOrEqual(1);
        expect(x1).toBeLessThan(x2);
        expect(y1).toBeLessThan(y2);
        expect(score).toBeGreaterThan(0);
        expect(score).toBeLessThanOrEqual(1);
      }
    }
  });

  it('la demo muestra los tres niveles de confianza', () => {
    const niveles = new Set(diagnoses.map((d) => d.confidence).filter(Boolean));
    expect(niveles).toEqual(new Set(['Baja', 'Media', 'Alta']));
  });

  it('el repositorio mock las sirve por id, y un id inexistente es un error', async () => {
    const repo = new MockRepository(SEED);
    const d = diagnoses.find((x) => x.detectionCount > 0)!;
    const { detections, crop } = await repo.getDiagnosisDetections(d.id);
    expect(detections).toHaveLength(d.detectionCount);
    expect(crop).toBeNull();
    await expect(repo.getDiagnosisDetections('DX-99999')).rejects.toThrow(/no existe/);
  });

  it('el repositorio mock guarda el criterio válido y rechaza el inválido como el backend', async () => {
    const repo = new MockRepository(SEED);
    const c = await repo.getDiagnosisCriteria();
    expect(c).toEqual(DEFAULT_DIAGNOSIS_CRITERIA);

    c.severityBands[3].mediumFrom = 3;
    expect((await repo.saveDiagnosisCriteria(c)).severityBands[3].mediumFrom).toBe(3);

    c.severityBands[0].minimum = 50;
    await expect(repo.saveDiagnosisCriteria(c)).rejects.toThrow(/Clorosis/);
    expect((await repo.getDiagnosisCriteria()).severityBands[0].minimum).toBe(5);
  });
});
