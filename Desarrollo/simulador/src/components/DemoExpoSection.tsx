/* Apartado 5: Demo Expo (migrada desde el frontend productivo).
   Owns the polling state for both pasada and secuencias to avoid
   redundant HTTP polling from sibling components. */
import { useState } from 'react';
import type { SectorRef, SimulatedSensor } from '../types';
import { usePasadaSimulator } from '../hooks/usePasadaSimulator';
import { HappyPathPanel } from './HappyPathPanel';
import { PasadaSimulatorPanel } from './PasadaSimulatorPanel';
import { SecuenciasSimulatorPanel } from './SecuenciasSimulatorPanel';
import { TelemetrySourceSwitch } from './TelemetrySourceSwitch';

interface Props {
  sectors: SectorRef[];
  sensors: SimulatedSensor[];
  /** La fuente de telemetría sólo existe con el backend en modo demo. */
  demoEnabled: boolean;
}

export function DemoExpoSection({ sectors, sensors, demoEnabled }: Props) {
  const pasadaResult = usePasadaSimulator();
  const pasadaEnCurso = pasadaResult.pasada?.estado === 'EN_CURSO' === true;
  // Elegir otra fuente cambia el modo del happy path (simulador o ESP32): se le avisa para que lo relea.
  const [sourceVersion, setSourceVersion] = useState(0);

  return (
    <div>
      {demoEnabled && (
        <div style={{ marginBottom: 16 }}>
          <TelemetrySourceSwitch onChange={() => setSourceVersion((v) => v + 1)} />
        </div>
      )}
      <PasadaSimulatorPanel
        pasada={pasadaResult.pasada}
        cargando={pasadaResult.cargando}
        error={pasadaResult.error}
        iniciando={pasadaResult.iniciando}
        iniciar={pasadaResult.iniciar}
        cancelar={pasadaResult.cancelar}
      />
      <div style={{ marginTop: 16 }}>
        <HappyPathPanel
          sectors={sectors}
          sensors={sensors}
          pasadaEnCurso={pasadaEnCurso}
          sourceVersion={sourceVersion}
        />
      </div>
      <div style={{ marginTop: 16 }}>
        <SecuenciasSimulatorPanel pasadaEnCurso={pasadaEnCurso} />
      </div>
    </div>
  );
}
