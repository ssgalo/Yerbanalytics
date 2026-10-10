/* Marco de las pantallas sin shell (login, cambio de contraseña): marca a la izquierda, tarjeta a la derecha. */
import type { ReactNode } from 'react';
import { Card } from '@/components/ui/Card';
import { Icon } from '@/components/ui/Icon';
import styles from './Auth.module.css';

export function PantallaAuth({ children }: { children: ReactNode }) {
  return (
    <div className={styles.pantalla}>
      <aside className={styles.marca}>
        <div className={styles.marcaLogo}>
          <div className={styles.logo}>
            <Icon name="leaf" size={23} stroke="#EBFBF1" strokeWidth={1.9} />
          </div>
          <div>
            <div className={styles.marcaNombre}>Yerbanalytics</div>
            <div className={styles.marcaSub}>Monitoreo IA</div>
          </div>
        </div>
        <div className={styles.marcaLema}>Cada plantín, mirado de cerca.</div>
        <p className={styles.marcaTexto}>
          Sensado por macro-zona, diagnóstico de IA por sector y acciones correctivas autónomas para el
          vivero de yerba mate.
        </p>
        <div className={styles.marcaPie}>Vivero San Ignacio · Misiones</div>
      </aside>
      <main className={styles.contenido}>
        <Card className={styles.tarjeta}>{children}</Card>
      </main>
    </div>
  );
}

/** Mensaje centrado mientras se resuelve la sesión. */
export function PantallaEspera({ texto = 'Verificando la sesión…' }: { texto?: string }) {
  return <div className={styles.centro}>{texto}</div>;
}
