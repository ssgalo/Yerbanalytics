/* Resultado de una comparación del motor, dibujado con el sistema: tilde si se cumple, cruz si no y el glifo de sin señal si falta el dato. */
import { Icon } from '@/components/ui/Icon';
import { StateGlyph } from '@/components/ui/StateGlyph';
import type { ResultadoComparacion } from '@/types/domain';

const TEXTO: Record<ResultadoComparacion, string> = {
  CUMPLE: 'Se cumple',
  NO_CUMPLE: 'No se cumple',
  SIN_DATO: 'Sin dato',
};

interface MarcaResultadoProps {
  resultado: ResultadoComparacion;
  className?: string;
}

export function MarcaResultado({ resultado, className }: MarcaResultadoProps) {
  return (
    <span className={className} data-resultado={resultado}>
      {resultado === 'CUMPLE' && <Icon name="check" size={14} />}
      {resultado === 'NO_CUMPLE' && <Icon name="close" size={14} />}
      {resultado === 'SIN_DATO' && <StateGlyph status="offline" />}
      <span className="sr">{TEXTO[resultado]}</span>
    </span>
  );
}
