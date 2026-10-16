/* Apartado 5: Demo Expo (migrada desde el frontend productivo).
   Owns the polling state for both pasada and secuencias to avoid
   redundant HTTP polling from sibling components. */
import { usePasadaSimulator } from '../hooks/usePasadaSimulator';
import { PasadaSimulatorPanel } from './PasadaSimulatorPanel';
import { SecuenciasSimulatorPanel } from './SecuenciasSimulatorPanel';

export function DemoExpoSection() {
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
        <SecuenciasSimulatorPanel pasadaEnCurso={pasadaEnCurso} />
      </div>
    </div>
  );
}
