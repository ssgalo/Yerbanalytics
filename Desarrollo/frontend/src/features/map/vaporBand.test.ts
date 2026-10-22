import { describe, expect, it } from 'vitest';
import { VAPOR_BAND_STATUS } from './vaporBand';

describe('VAPOR_BAND_STATUS', () => {
  it('aire saturado y calor seco llaman la atención; el rango confortable y la transición, no', () => {
    expect(VAPOR_BAND_STATUS.SATURATED).toBe('warning');
    expect(VAPOR_BAND_STATUS.DRY_HEAT).toBe('critical');
    expect(VAPOR_BAND_STATUS.COMFORTABLE).toBe('ok');
    expect(VAPOR_BAND_STATUS.TRANSITION).toBe('ok');
  });
});
