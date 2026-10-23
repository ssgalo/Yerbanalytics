// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ActionRecord } from '@/types/domain';
import { ActionDetail } from './ActionDetail';

afterEach(cleanup);

const withoutEffectiveness = (
  extra: Partial<NonNullable<ActionRecord['evo']>> = {},
): ActionRecord['evo'] => ({
  show: true,
  metric: 'Humedad de sustrato',
  antes: '44',
  ahora: '48',
  unit: '%',
  delta: '+4',
  latencia: '30 min',
  verdict: 'Sin efectividad',
  vSoft: '',
  vInk: '',
  motivo: 'Humedad de sustrato 44 % → 48 % (+4 puntos; hacen falta al menos +8).',
  bloqueando: true,
  ...extra,
});

const record = (e: Partial<ActionRecord> = {}): ActionRecord => ({
  id: 'HE-9',
  sectorId: 'MZ-2-014',
  zonaName: 'Macro-zona 2',
  tipo: 'Riego',
  time: 'hace 5 min',
  ts: 1,
  fecha: '03/10 10:05',
  lectura: 'Humedad de sustrato 38%.',
  decision: 'El motor de reglas ordena regar.',
  accion: 'Electroválvula abierta.',
  res: 'Sin efectividad',
  resSoft: '',
  resInk: '',
  sev: '—',
  tint: '',
  ink: '',
  path: 'M0 0',
  evo: withoutEffectiveness(),
  ...e,
});

const renderDetail = (r: ActionRecord, onReview?: (id: string, detail: string) => Promise<void>) =>
  render(
    <ActionDetail
      record={r}
      ruleLabel={(id) => id}
      hasReasoning={false}
      onOpenReasoning={() => undefined}
      open
      onBack={() => undefined}
      onReview={onReview}
    />,
  );

describe('ActionDetail · revisión de una acción sin efectividad (S-06)', () => {
  it('muestra por qué quedó sin efectividad y ofrece registrar la revisión', () => {
    renderDetail(record(), async () => undefined);

    expect(screen.getByText(/hacen falta al menos \+8/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Registrar revisión' })).toBeTruthy();
  });

  it('sin permiso (sin onReview) o si ya no bloquea no hay botón', () => {
    const { unmount } = renderDetail(record());
    expect(screen.queryByRole('button', { name: 'Registrar revisión' })).toBeNull();
    unmount();

    renderDetail(
      record({ evo: withoutEffectiveness({ bloqueando: false }) }),
      async () => undefined,
    );
    expect(screen.queryByRole('button', { name: 'Registrar revisión' })).toBeNull();
  });

  it('confirma con el detalle (obligatorio) y llama a onReview con el id del evento', async () => {
    const onReview = vi.fn(async () => undefined);
    renderDetail(record(), onReview);

    fireEvent.click(screen.getByRole('button', { name: 'Registrar revisión' }));
    const confirm = screen.getByRole('button', { name: 'Confirmar revisión' }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);

    fireEvent.change(screen.getByLabelText('Qué se constató'), {
      target: { value: '  Destapé el emisor  ' },
    });
    expect(confirm.disabled).toBe(false);
    fireEvent.click(confirm);

    await waitFor(() => expect(onReview).toHaveBeenCalledWith('HE-9', 'Destapé el emisor'));
  });

  it('si el backend rechaza la revisión muestra el motivo y conserva lo escrito', async () => {
    const onReview = vi.fn(async () => {
      throw new Error('El evento HE-9 no es una acción sin efectividad que siga bloqueando.');
    });
    renderDetail(record(), onReview);

    fireEvent.click(screen.getByRole('button', { name: 'Registrar revisión' }));
    fireEvent.change(screen.getByLabelText('Qué se constató'), { target: { value: 'Revisé' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar revisión' }));

    expect((await screen.findByRole('alert')).textContent).toContain('siga bloqueando');
    expect((screen.getByLabelText('Qué se constató') as HTMLTextAreaElement).value).toBe('Revisé');
  });
});
