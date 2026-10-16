/* Apartado 1: Configuración General.
   Topology, simulated sensors, optional hardware registration and the current
   rule thresholds from the backend. */
import type { NurseryTopology, SimulatedSensor } from '../types';
import { AutoEmission } from './AutoEmission';
import { NewSensor } from './NewSensor';
import { OperationalThresholds } from './OperationalThresholds';
import { SensorCard } from './SensorCard';
import { Section } from './Section';
import { Topology } from './Topology';
import * as api from '../api';
import type { TelemetryInput, SimulatorStatus } from '../types';

interface Props {
  topology: NurseryTopology | null;
  zones: string[];
  sensors: SimulatedSensor[];
  status: SimulatorStatus | null;
  onTopologyRegenerated: (next: NurseryTopology) => void;
  onSensorsChanged: () => void;
  onStatusChanged: (active: boolean) => void;
}

export function GeneralConfigSection({
  topology,
  zones,
  sensors,
  status,
  onTopologyRegenerated,
  onSensorsChanged,
  onStatusChanged,
}: Props) {
  const createSensor = async (sensor: SimulatedSensor) => {
    await api.createSensor(sensor);
    onSensorsChanged();
  };

  const deleteSensor = async (serial: string) => {
    await api.deleteSensor(serial);
    onSensorsChanged();
  };

  const sendTelemetry = async (input: TelemetryInput) => {
    await api.sendTelemetry(input);
  };

  return (
    <div>
      <Topology topology={topology} onRegenerated={onTopologyRegenerated} />

      {status && (
        <AutoEmission
          active={status.emission.active}
          onChange={onStatusChanged}
        />
      )}

      <NewSensor zones={zones} onCreate={createSensor} />

      {sensors.length === 0 ? (
        <Section title="Sensores simulados">
          <p className="empty">
            No hay sensores simulados todavía. Creá uno arriba para empezar a publicar lecturas.
          </p>
        </Section>
      ) : (
        <Section title="Sensores simulados" hint="Usá el botón 'Enviar todo' para publicar las 10 métricas de una vez.">
          <div className="sensors">
            {sensors.map((s) => (
              <SensorCard
                key={s.serial}
                sensor={s}
                onSend={sendTelemetry}
                onDelete={deleteSensor}
              />
            ))}
          </div>
        </Section>
      )}

      <OperationalThresholds />
    </div>
  );
}
