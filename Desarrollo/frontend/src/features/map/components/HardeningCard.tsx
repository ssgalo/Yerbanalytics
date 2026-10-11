/* ============================================================
   Rustificación de la macro-zona: iniciar un plan y seguir los que
   están en curso. Los planes todavía no existen en el sistema, así que
   la tarjeta reserva su lugar y lo dice.
   ============================================================ */
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EstadosPantalla';
import { cx } from '@/components/ui/estado';
import panel from './Panel.module.css';

export function HardeningCard({ className }: { className?: string }) {
  return (
    <Card className={cx(panel.panel, className)}>
      <div className={panel.head}>
        <h2 className={panel.title}>Rustificación</h2>
        <span className={panel.meta}>Endurecimiento progresivo antes del trasplante</span>
      </div>

      <EmptyState
        title="Sin planes de rustificación"
        action={
          <Button variant="primary" disabled>
            Iniciar plan
          </Button>
        }
      >
        La gestión de planes todavía no está disponible. Desde acá se van a iniciar y seguir los
        planes de esta macro-zona, que regulan la mediasombra día por día.
      </EmptyState>
    </Card>
  );
}
