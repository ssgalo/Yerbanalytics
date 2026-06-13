import type { CSSProperties } from 'react';

interface SparklineProps {
  /** path SVG de la línea (generado por pathFrom) */
  line: string;
  color: string;
  /** viewBox width/height — deben coincidir con los usados al generar el path */
  vbWidth: number;
  vbHeight: number;
  /** path SVG del área bajo la curva (opcional) */
  area?: string;
  /** id del gradiente para el área (opcional) */
  areaFill?: string;
  height?: number | string;
  strokeWidth?: number;
  opacity?: number;
  style?: CSSProperties;
}

/** Mini gráfico de línea (sparkline / gráfico de tendencia). */
export function Sparkline({
  line,
  color,
  vbWidth,
  vbHeight,
  area,
  areaFill,
  height = 34,
  strokeWidth = 2,
  opacity = 0.85,
  style,
}: SparklineProps) {
  return (
    <svg
      width="100%"
      height={height}
      viewBox={`0 0 ${vbWidth} ${vbHeight}`}
      preserveAspectRatio="none"
      style={{ display: 'block', ...style }}
    >
      {area && areaFill && <path d={area} fill={areaFill} />}
      <path
        d={line}
        fill="none"
        stroke={color}
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
        opacity={opacity}
      />
    </svg>
  );
}
