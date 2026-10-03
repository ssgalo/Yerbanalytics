import styles from './PlanoVivero.module.css';

/** Tira que explica la jerarquía física del vivero a quien no conoce el dominio. */
export function ComoLeer() {
  return (
    <div className={styles.howto}>
      <span className={styles.howtoLabel}>Cómo leer esta pantalla:</span>
      <span>Vivero</span>
      <span className={styles.howtoSub}>→</span>
      <span>
        Macro-zona <i>(área con 1 sensor testigo)</i>
      </span>
      <span className={styles.howtoSub}>→</span>
      <span>
        Sector <i>(4 bandejas)</i>
      </span>
      <span className={styles.howtoSub}>→</span>
      <span>
        Bandeja <i>(25 plantines)</i>
      </span>
    </div>
  );
}
