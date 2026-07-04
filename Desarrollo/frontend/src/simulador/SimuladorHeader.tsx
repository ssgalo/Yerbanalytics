/* ============================================================
   Encabezado del simulador standalone. Reemplaza al Sidebar/Topbar del
   dashboard principal (que dependen del NurseryProvider) con una barra
   propia que muestra la marca y la metadata de la página.
   ============================================================ */
import { usePageMeta } from '@/hooks/PageMeta';
import { Icon } from '@/components/ui/Icon';
import styles from './SimuladorApp.module.css';

export function SimuladorHeader() {
  const { title, subtitle } = usePageMeta();

  return (
    <header className={styles.header}>
      <div className={styles.brand}>
        <div className={styles.logo}>
          <Icon name="signal" size={20} stroke="#EBFBF1" strokeWidth={1.9} />
        </div>
        <div style={{ lineHeight: 1.05 }}>
          <div className={styles.brandName}>Yerbanalytics</div>
          <div className={styles.brandSub}>Simulador de sensores</div>
        </div>
      </div>

      <div className={styles.meta}>
        <h1 className={styles.title}>{title}</h1>
        {subtitle && <div className={styles.subtitle}>{subtitle}</div>}
      </div>
    </header>
  );
}
