import styles from './AlcanceDiagnostico.module.css';

/**
 * Aclara el alcance del diagnóstico: sale de UNA foto del sector y vale para el sector completo.
 * No dice —ni hay dato para decir— cuál plantín se fotografió.
 */
export function AlcanceDiagnostico() {
  return (
    <div className={styles.box}>
      El diagnóstico de este sector sale de <b>una foto</b> y se aplica al <b>sector completo</b>{' '}
      (sus 100 tubetes), no de una medición tubete por tubete.
      <span className={styles.futuro}>a futuro</span> una segunda pasada del riel podría fotografiar
      más de una vez cada sector.
    </div>
  );
}
