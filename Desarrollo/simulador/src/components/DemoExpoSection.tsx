/* Apartado 5: Demo Expo (migrada desde el frontend productivo).
   Owns the polling state for both pasada and secuencias to avoid
   redundant HTTP polling from sibling components. */
import type { SectorRef, SimulatedSensor } from '../types';
import { usePasadaSimulator } from '../hooks/usePasadaSimulator';
import { HappyPathPanel } from './HappyPathPanel';
import { PasadaSimulatorPanel } from './PasadaSimulatorPanel';
import { SecuenciasSimulatorPanel } from './SecuenciasSimulatorPanel';

interface Props {
  sectors: SectorRef[];
  sensors: SimulatedSensor[];
}

export function DemoExpoSection({ sectors, sensors }: Props) {
  const pasadaResult = usePasadaSimulator();
  const pasadaEnCurso = pasadaResult.pasada?.estado === 'EN_CURSO' === true;

  return (
    <div>
      <PasadaSimulatorPanel
        pasada={pasadaResult.pasada}
        cargando={pasadaResult.cargando}
        error={pasadaResult.error}
        iniciando={pasadaResult.iniciando}
        iniciar={pasadaResult.iniciar}
        cancelar={pasadaResult.cancelar}
      />
      <div style={{ marginTop: 16 }}>
        <HappyPathPanel sectors={sectors} sensors={sensors} pasadaEnCurso={pasadaEnCurso} />
      </div>
      <div style={{ marginTop: 16 }}>
        <SecuenciasSimulatorPanel pasadaEnCurso={pasadaEnCurso} />
      </div>
    </div>
  );
}
