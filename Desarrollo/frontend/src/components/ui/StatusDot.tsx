import type { CSSProperties } from 'react';

interface StatusDotProps {
  color: string;
  size?: number;
  /** animación CSS (p. ej. 'ybPulse 2s infinite') */
  animation?: string;
  style?: CSSProperties;
}

/** Punto de color para indicar estado. */
export function StatusDot({ color, size = 9, animation, style }: StatusDotProps) {
  return (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        background: color,
        flexShrink: 0,
        display: 'inline-block',
        animation,
        ...style,
      }}
    />
  );
}
