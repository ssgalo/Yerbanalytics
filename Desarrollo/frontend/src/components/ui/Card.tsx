import type { CSSProperties, ReactNode } from 'react';
import styles from './Card.module.css';

interface CardProps {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
}

/** Superficie blanca con borde y radio del sistema de diseño. */
export function Card({ children, className, style }: CardProps) {
  return (
    <div className={className ? `${styles.card} ${className}` : styles.card} style={style}>
      {children}
    </div>
  );
}
