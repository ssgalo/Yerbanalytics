/* Apartado 2: Telemetría, Reglas y Clima.
   Manual sensor telemetry, sensing pulse, demo rule controls and simulated weather. */
import type { SimulatedSensor, SimulatorStatus, TelemetryInput } from '../types';
import { ReadNowButton } from './ReadNowButton';
import { RuleToggles } from './RuleToggles';
import { SensorCard } from './SensorCard';
import { Section } from './Section';
import { WeatherSimulation } from './WeatherSimulation';
import * as api from '../api';

interface Props {
  sensors: SimulatedSensor[];
  status: SimulatorStatus | null;
  demoEnabled: boolean;
}

export function TelemetryRulesSection({ sensors, demoEnabled }: Props) {
  const sendTelemetry = async (input: TelemetryInput) => {
    await api.sendTelemetry(input);
  };

  return (
    <div>
      {sensors.length === 0 ? (
        <Section title="Telemetría manual">
          <p className="empty">
            No hay sensores simulados. Creá uno en la pestaña Configuración para poder enviar
            lecturas.
          </p>
        </Section>
      ) : (
        <Section
          title="Telemetría manual"
          hint="Enviá métricas individuales o el conjunto completo de 10 métricas con fecha y hora."
        >
          <div className="sensors">
            {sensors.map((s) => (
              <SensorCard
                key={s.serial}
                sensor={s}
                onSend={sendTelemetry}
                onDelete={async () => {
                  // Sensor deletion is handled from the Config tab; no-op here.
                }}
              />
            ))}
          </div>
        </Section>
      )}

      {demoEnabled ? (
        <>
          <ReadNowButton demoEnabled={demoEnabled} />
          <WeatherSimulation />
          <RuleToggles />
        </>
      ) : (
        <Section title="Modo demo">
          <p className="note">
            El backend NO está en modo demo (
            <code>yerbanalytics.demo.enabled=false</code>). El pulso de sensado funciona
            igual, pero los controles de reglas y clima simulado no están disponibles.
          </p>
          <ReadNowButton demoEnabled={false} />
        </Section>
      )}
    </div>
  );
}
