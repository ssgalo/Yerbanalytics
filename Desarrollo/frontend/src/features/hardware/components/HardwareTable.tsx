/* Tabla de la flota con su estado técnico (HU-21 CA-01) */
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { ProgressBar } from '@/components/ui/ProgressBar';
import type { Dispositivo } from '@/types/domain';
import styles from '../Hardware.module.css';

interface HardwareTableProps {
  dispositivos: Dispositivo[];
  onRecambiar: (device: Dispositivo) => void;
}

/** Color de la barra de batería según el nivel. */
function batteryColor(pct: number): string {
  if (pct < 20) return 'var(--crit)';
  if (pct < 45) return 'var(--warn)';
  return 'var(--ok)';
}

export function HardwareTable({ dispositivos, onRecambiar }: HardwareTableProps) {
  return (
    <Card className={styles.tableCard}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>Dispositivo</th>
            <th>Serial / MAC</th>
            <th>Ubicación</th>
            <th>Batería</th>
            <th>Señal</th>
            <th>Último update</th>
            <th>Estado</th>
            <th aria-label="Acciones" />
          </tr>
        </thead>
        <tbody>
          {dispositivos.length === 0 && (
            <tr>
              <td className={styles.empty} colSpan={8}>
                No hay dispositivos para los criterios seleccionados.
              </td>
            </tr>
          )}
          {dispositivos.map((d) => {
            const recambiable = d.estado === 'fuera_de_servicio' || d.falla !== null;
            return (
              <tr key={d.id}>
                <td>
                  <div className={styles.devName}>{d.tipoLabel}</div>
                  <div className={styles.devId}>{d.id}</div>
                </td>
                <td className={styles.mono}>{d.serial}</td>
                <td className={styles.mono}>{d.ubicacion}</td>
                <td>
                  {d.bateria !== null ? (
                    <div className={styles.batteryCell}>
                      <span className={styles.batteryBar}>
                        <ProgressBar value={d.bateria} color={batteryColor(d.bateria)} />
                      </span>
                      <span className={styles.mono}>{d.bateria}%</span>
                    </div>
                  ) : (
                    <span className={styles.muted}>—</span>
                  )}
                </td>
                <td className={d.senal !== null ? styles.mono : styles.muted}>
                  {d.senal !== null ? `${d.senal} dBm` : '—'}
                </td>
                <td className={styles.muted}>{d.ultimoUpdateLabel}</td>
                <td>
                  <div className={styles.estadoCell}>
                    <Badge soft={d.estadoSoft} ink={d.estadoInk}>
                      {d.estadoLabel}
                    </Badge>
                    {d.bateriaBaja && (
                      <Badge soft="#FBF0DC" ink="#A66A12">
                        Batería Baja
                      </Badge>
                    )}
                    {d.falla && <span className={styles.fallaText}>{d.falla}</span>}
                  </div>
                </td>
                <td>
                  {recambiable && (
                    <button type="button" className={styles.btnLink} onClick={() => onRecambiar(d)}>
                      Recambiar
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </Card>
  );
}
