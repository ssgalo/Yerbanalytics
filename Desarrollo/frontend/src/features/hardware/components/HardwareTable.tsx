/* Tabla de la flota con su estado técnico (HU-21 CA-01) */
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { describirFalla, desdeCuando } from '@/lib/fallas';
import type { Dispositivo } from '@/types/domain';
import styles from '../Hardware.module.css';

interface HardwareTableProps {
  dispositivos: Dispositivo[];
  /** Sin esto (rol sin `hardware.gestionar`) no se ofrece el recambio. */
  onRecambiar?: (device: Dispositivo) => void;
  /** Sin esto (rol sin `hardware.reparar`) no se ofrece registrar la reparación de un dispositivo averiado (S-05). */
  onReparar?: (device: Dispositivo) => void;
}

/** Color de la barra de batería según el nivel. */
function batteryColor(pct: number): string {
  if (pct < 20) return 'var(--crit)';
  if (pct < 45) return 'var(--warn)';
  return 'var(--ok)';
}

export function HardwareTable({ dispositivos, onRecambiar, onReparar }: HardwareTableProps) {
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
                      <Badge soft="var(--warn-soft)" ink="var(--warn-ink)">
                        Batería Baja
                      </Badge>
                    )}
                    {d.falla && <FallaTexto falla={d.falla} fallaDesde={d.fallaDesde} />}
                  </div>
                </td>
                <td>
                  <div className={styles.acciones}>
                    {d.falla && onReparar && (
                      <button type="button" className={styles.btnLink} onClick={() => onReparar(d)}>
                        Registrar reparación
                      </button>
                    )}
                    {recambiable && onRecambiar && (
                      <button type="button" className={styles.btnLink} onClick={() => onRecambiar(d)}>
                        Recambiar
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </Card>
  );
}

/** La falla marcada, traducida del código del contrato, y desde cuándo (S-05). */
function FallaTexto({ falla, fallaDesde }: { falla: string; fallaDesde: number | null }) {
  const { titulo, detalle } = describirFalla(falla);
  const desde = desdeCuando(fallaDesde);
  return (
    <span className={styles.fallaText}>
      <span>{titulo}</span>
      {detalle && <> · {detalle}</>}
      {desde && <span className={styles.fallaDesde}> · {desde}</span>}
    </span>
  );
}
