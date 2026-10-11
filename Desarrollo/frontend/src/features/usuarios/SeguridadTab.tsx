/* ============================================================
   Pestaña Seguridad: tiempo máximo de inactividad (HU-01 CA-03). Rige para todas las sesiones
   desde que se guarda y queda auditado. El rango (5–480 min) lo valida la UI y lo vuelve a
   validar el backend.
   ============================================================ */
import { useEffect, useState, type FormEvent } from 'react';
import { Card } from '@/components/ui/Card';
import { getSeguridadRepository } from '@/data';
import { useAuth } from '@/hooks/AuthContext';
import type { PoliticaSesion } from '@/types/seguridad';
import { errorInactividad } from './presentacion';
import styles from './Usuarios.module.css';

export function SeguridadTab() {
  const { recargarPerfil } = useAuth();
  const [politica, setPolitica] = useState<PoliticaSesion | null>(null);
  const [valor, setValor] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ tipo: 'ok' | 'err'; msg: string } | null>(null);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    let activo = true;
    getSeguridadRepository()
      .getPolitica()
      .then((p) => {
        if (!activo) return;
        setPolitica(p);
        setValor(String(p.inactividadMin));
      })
      .catch((e) => activo && setError(e instanceof Error ? e.message : String(e)));
    return () => {
      activo = false;
    };
  }, []);

  if (error) {
    return (
      <div className={styles.state} style={{ color: 'var(--crit)' }}>
        No se pudo cargar la política de sesión: {error}
      </div>
    );
  }
  if (!politica) return <div className={styles.state}>Cargando la política de sesión…</div>;

  const invalido = errorInactividad(valor, politica.minimo, politica.maximo);
  const sinCambios = Number(valor) === politica.inactividadMin;

  const guardar = async (e: FormEvent) => {
    e.preventDefault();
    if (invalido) return;
    setFeedback(null);
    setGuardando(true);
    try {
      const nueva = await getSeguridadRepository().guardarPolitica(Number(valor));
      setPolitica(nueva);
      setValor(String(nueva.inactividadMin));
      setFeedback({ tipo: 'ok', msg: `Tiempo máximo de inactividad: ${nueva.inactividadMin} min.` });
      // El temporizador local de esta pestaña toma el valor nuevo del perfil.
      await recargarPerfil();
    } catch (err) {
      setFeedback({ tipo: 'err', msg: err instanceof Error ? err.message : String(err) });
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Card className={styles.section}>
      <div className={styles.sectionHead}>
        <h2 className={styles.sectionTitle}>Cierre de sesión por inactividad</h2>
        <span className={styles.sectionHint}>
          Rige para todas las sesiones desde que se guarda · queda en la auditoría
        </span>
      </div>
      <form onSubmit={guardar}>
        <div className={styles.formGrid}>
          <label className={styles.group}>
            <span className={styles.label}>Tiempo máximo de inactividad (min)</span>
            <input
              className={`${styles.input} ${styles.inputCorto}`}
              type="number"
              min={politica.minimo}
              max={politica.maximo}
              step={1}
              value={valor}
              aria-invalid={invalido !== null}
              onChange={(e) => {
                setValor(e.target.value);
                setFeedback(null);
              }}
            />
          </label>
          <button type="submit" className={styles.btnPrimary} disabled={guardando || invalido !== null || sinCambios}>
            {guardando ? 'Guardando…' : 'Guardar'}
          </button>
          {feedback && (
            <span
              role={feedback.tipo === 'err' ? 'alert' : 'status'}
              className={feedback.tipo === 'ok' ? styles.feedbackOk : styles.feedbackErr}
            >
              {feedback.msg}
            </span>
          )}
        </div>
        {invalido && (
          <div role="alert" className={styles.errorBox}>
            {invalido}
          </div>
        )}
        <div className={styles.info}>
          Sólo cuenta la interacción real (teclado, puntero, toque, scroll) y las acciones que modifican datos: las
          consultas automáticas del dashboard no mantienen viva la sesión. Un minuto antes del cierre se avisa en
          pantalla.
        </div>
      </form>
    </Card>
  );
}
