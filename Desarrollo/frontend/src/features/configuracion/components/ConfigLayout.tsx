/* Armazón común de las vistas de configuración: estados de carga, encabezado, aviso de sólo
   lectura, resultado del guardado y la barra de guardado. */
import type { ReactNode } from 'react';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { SaveBar, Skeleton } from '@/components/ui/EstadosPantalla';
import type { ConfigDraft } from '../useConfigDraft';
import styles from '../ConfigLayout.module.css';

function formatTs(ts: number | null): string {
  if (!ts) return '';
  const d = new Date(ts);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** Bloque de una vista: título, de qué se trata y el contenido. */
export function ConfigSection({ title, hint, children }: { title: string; hint: string; children: ReactNode }) {
  return (
    <section className={styles.section}>
      <header className={styles.sectionHead}>
        <h2 className={styles.sectionTitle}>{title}</h2>
        <p className={styles.sectionHint}>{hint}</p>
      </header>
      {children}
    </section>
  );
}

/** Secciones chicas lado a lado mientras entren. */
export function ConfigRow({ children }: { children: ReactNode }) {
  return <div className={styles.row}>{children}</div>;
}

/** Envuelve un formulario: en sólo lectura deshabilita todos sus campos. */
export function ConfigFieldset({ disabled, children }: { disabled: boolean; children: ReactNode }) {
  return (
    <fieldset className={styles.fieldset} disabled={disabled}>
      {children}
    </fieldset>
  );
}

interface ConfigLayoutProps<T> {
  state: ConfigDraft<T>;
  intro: ReactNode;
  /** Quién y cuándo editó por última vez; sin él (o en null), no se muestra. */
  audit?: (draft: T) => { by: string | null; ts: number | null } | null;
  /** Qué se carga, para el mensaje de error ("la configuración", "el criterio de diagnóstico"). */
  resourceName?: string;
  /** Sin `configuracion.editar`: sin restablecer ni barra de guardado, con aviso. */
  editable: boolean;
  /** Restablece a fábrica sólo lo que edita esta vista. */
  onReset?: () => void;
  /** Por qué no se puede guardar todavía (valores a corregir, falta un dato). */
  blockedReason?: string;
  /** Contenido; recibe el borrador ya cargado. */
  children: ReactNode;
}

export function ConfigLayout<T>({
  state,
  intro,
  audit,
  resourceName = 'la configuración',
  editable,
  onReset,
  blockedReason,
  children,
}: ConfigLayoutProps<T>) {
  const { draft, error, loading, saving, changes, feedback, save, discard } = state;

  if (error) {
    return (
      <Callout tone="crit" role="alert">
        No se pudo cargar {resourceName}: {error.message}
      </Callout>
    );
  }

  if (loading || !draft) {
    return (
      <div className={styles.page} aria-busy="true">
        <span className="sr">Cargando {resourceName}…</span>
        <Skeleton height={260} />
        <Skeleton height={180} />
      </div>
    );
  }

  const lastEdit = audit?.(draft) ?? null;

  return (
    <div className={styles.page}>
      <div className={styles.head}>
        <p className={styles.intro}>{intro}</p>
        <div className={styles.headSide}>
          {lastEdit?.by && (
            <span className={styles.audit}>
              Última edición: <b>{lastEdit.by}</b>
              {lastEdit.ts ? ` · ${formatTs(lastEdit.ts)}` : ''}
            </span>
          )}
          {editable && onReset && (
            <Button size="sm" onClick={onReset}>
              Restablecer valores de fábrica
            </Button>
          )}
        </div>
      </div>

      {!editable && (
        <Callout icon="lock">Sólo lectura: tu rol puede consultar {resourceName}, pero no cambiar sus valores.</Callout>
      )}

      {children}

      {feedback && (
        <Callout tone={feedback.kind === 'ok' ? 'ok' : 'crit'} role={feedback.kind === 'ok' ? 'status' : 'alert'}>
          {feedback.msg}
        </Callout>
      )}

      {editable && (
        <SaveBar
          count={changes}
          detalle={blockedReason}
          onDiscard={discard}
          onSave={() => void save()}
          saveLabel="Guardar cambios"
          saving={saving}
          saveDisabled={saving || blockedReason !== undefined}
        />
      )}
    </div>
  );
}
