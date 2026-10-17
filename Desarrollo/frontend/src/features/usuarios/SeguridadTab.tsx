/* ============================================================
   Pestaña Seguridad: tiempo máximo de inactividad (HU-01 CA-03). Rige para todas las sesiones
   desde que se guarda y queda auditado. El rango (5–480 min) lo valida la UI y lo vuelve a
   validar el backend.
   ============================================================ */
import { useEffect, useState } from 'react';
import { Callout } from '@/components/ui/Callout';
import { SaveBar, Skeleton } from '@/components/ui/EstadosPantalla';
import { Toast } from '@/components/ui/Toast';
import { Field, InputUnit } from '@/components/ui/Field';
import { ayudaDe } from '@/components/ui/clases';
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
      <Callout tone="crit" role="alert">
        No se pudo cargar la política de sesión: {error}
      </Callout>
    );
  }
  if (!politica) {
    return (
      <div className={styles.page} aria-busy="true">
        <span className="sr">Cargando la política de sesión…</span>
        <Skeleton height={180} />
      </div>
    );
  }

  const invalido = errorInactividad(valor, politica.minimo, politica.maximo);
  const sinCambios = Number(valor) === politica.inactividadMin;

  const guardar = async () => {
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
    <div className={styles.page}>
      <section className={styles.section}>
        <header className={styles.sectionHead}>
          <h2 className={styles.sectionTitle}>Cierre de sesión por inactividad</h2>
          <p className={styles.sectionHint}>Rige para todas las sesiones desde que se guarda · queda en la auditoría</p>
        </header>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void guardar();
          }}
          noValidate
        >
          <div className={styles.narrow}>
            <Field
              label="Tiempo máximo de inactividad"
              htmlFor="politica-inactividad"
              hint={`Entre ${politica.minimo} y ${politica.maximo} minutos`}
              error={invalido}
            >
              <InputUnit
                id="politica-inactividad"
                unit="min"
                type="number"
                inputMode="numeric"
                min={politica.minimo}
                max={politica.maximo}
                step={1}
                value={valor}
                invalid={invalido !== null}
                modified={!sinCambios && invalido === null}
                aria-describedby={ayudaDe('politica-inactividad')}
                onChange={(e) => {
                  setValor(e.target.value);
                  setFeedback(null);
                }}
              />
            </Field>
          </div>
        </form>
        <div className={styles.formNotes}>
          <Callout>
            Sólo cuenta la interacción real (teclado, puntero, toque, scroll) y las acciones que modifican datos: las
            consultas automáticas del dashboard no mantienen viva la sesión. Un minuto antes del cierre se avisa en
            pantalla.
          </Callout>
        </div>
      </section>

      {feedback && (
        <Toast tone={feedback.tipo === 'ok' ? 'ok' : 'crit'} onClose={() => setFeedback(null)}>
          {feedback.msg}
        </Toast>
      )}

      <SaveBar
        count={sinCambios ? 0 : 1}
        detalle={invalido ? 'Corregí el valor para poder guardar.' : `Inactividad: ${politica.inactividadMin} → ${valor} min.`}
        onDiscard={() => {
          setValor(String(politica.inactividadMin));
          setFeedback(null);
        }}
        onSave={() => void guardar()}
        saveLabel="Guardar cambios"
        saving={guardando}
        saveDisabled={invalido !== null}
      />
    </div>
  );
}
