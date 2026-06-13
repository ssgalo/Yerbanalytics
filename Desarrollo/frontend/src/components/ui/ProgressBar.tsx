interface ProgressBarProps {
  /** porcentaje 0–100 */
  value: number;
  color: string;
  height?: number;
  trackColor?: string;
}

/** Barra de progreso (confianza, % saludable, etc.). */
export function ProgressBar({ value, color, height = 7, trackColor = 'var(--line2)' }: ProgressBarProps) {
  return (
    <div
      style={{
        height,
        borderRadius: 5,
        background: trackColor,
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          height: '100%',
          borderRadius: 5,
          background: color,
          width: `${Math.max(0, Math.min(100, value))}%`,
        }}
      />
    </div>
  );
}
