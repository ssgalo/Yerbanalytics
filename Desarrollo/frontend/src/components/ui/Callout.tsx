import type { ReactNode } from 'react';
import { Icon, type IconName } from './Icon';
import { cx } from './estado';
import styles from './Callout.module.css';

/** `info` = el sistema está actuando; `warn` = advertencia; `crit` = crítico o destructivo. */
export type CalloutTone = 'neutral' | 'info' | 'warn' | 'crit' | 'ok';

const ICONO: Record<CalloutTone, IconName> = {
  neutral: 'info',
  info: 'info',
  warn: 'alert',
  crit: 'alert',
  ok: 'check',
};

interface CalloutProps {
  tone?: CalloutTone;
  icon?: IconName;
  children: ReactNode;
  /** `alert` para un error que aparece después de una acción; `note` para una advertencia que acompaña un formulario; si no, se lee en orden. */
  role?: 'alert' | 'status' | 'note';
}

/** Aviso en línea, dentro del contenido. */
export function Callout({ tone = 'neutral', icon, children, role }: CalloutProps) {
  return (
    <div className={cx(styles.callout, tone !== 'neutral' && styles[tone])} role={role}>
      <Icon name={icon ?? ICONO[tone]} size={18} />
      <div className={styles.body}>{children}</div>
    </div>
  );
}
