// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { IntervalosForm } from './IntervalosForm';
import { buildConfig } from '@/data/mock/config';
import type { ConfigOperativa } from '@/types/domain';

afterEach(cleanup);

const mount = (patch: Partial<ConfigOperativa> = {}, onChange = vi.fn()) => {
  render(<IntervalosForm value={{ ...buildConfig().operativa, ...patch }} onChange={onChange} />);
  return onChange;
};

describe('IntervalosForm · node read request frequency', () => {
  it('shows a seconds field distinct from the irrigation sensing interval', () => {
    mount();
    const field = screen.getByLabelText(/frecuencia de pedido de lectura.*segundos/i);
    expect((field as HTMLInputElement).value).toBe('60');
    expect(screen.getByLabelText(/Intervalo de Sensado \(IoT\)/)).toBeTruthy();
  });

  it('emits the new value in seconds', () => {
    const onChange = mount();
    fireEvent.change(screen.getByLabelText(/frecuencia de pedido de lectura/i), { target: { value: '30' } });
    expect(onChange).toHaveBeenCalledWith({ sensingPullIntervalSeconds: 30 });
  });

  it('flags an out-of-range value as invalid', () => {
    mount({ sensingPullIntervalSeconds: 5 });
    expect(screen.getByLabelText(/frecuencia de pedido de lectura/i).getAttribute('aria-invalid')).toBe('true');
  });

  it('renders the backend-computed stale threshold hint', () => {
    mount({ effectiveStaleThresholdSeconds: 180 });
    expect(screen.getByText(/180 s/)).toBeTruthy();
  });

  it('shows no threshold hint when the backend has not provided one', () => {
    mount({ effectiveStaleThresholdSeconds: undefined });
    expect(screen.queryByText(/umbral de lectura vieja/i)).toBeNull();
  });
});
