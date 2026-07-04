/* ============================================================
   App standalone del simulador. Corre en su propio puerto (5180), aislada
   del dashboard principal: sólo contiene el panel de simulación (switch de
   modo + alta de sensores + envío manual de telemetría por MQTT).
   No monta el NurseryProvider — la vista de simulación no lo necesita.
   ============================================================ */
import { PageMetaProvider } from '@/hooks/PageMeta';
import { SimulacionPage } from '@/features/simulacion/SimulacionPage';
import { SimuladorHeader } from './SimuladorHeader';
import styles from './SimuladorApp.module.css';

export function SimuladorApp() {
  return (
    <PageMetaProvider>
      <div className={styles.shell}>
        <SimuladorHeader />
        <main className={styles.scroll}>
          <SimulacionPage />
        </main>
      </div>
    </PageMetaProvider>
  );
}
