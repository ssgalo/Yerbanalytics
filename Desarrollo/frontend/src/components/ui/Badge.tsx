import type { CSSProperties, ReactNode } from 'react';
import styles from './Badge.module.css';

interface BadgeProps {
  children: ReactNode;
  soft: string;
  ink: string;
  style?: CSSProperties;
}

/** Chip de estado/severidad: fondo soft + texto ink. */
export function Badge({ children, soft, ink, style }: BadgeProps) {
  return (
    <span className={styles.badge} style={{ background: soft, color: ink, ...style }}>
      {children}
    </span>
  );
}
