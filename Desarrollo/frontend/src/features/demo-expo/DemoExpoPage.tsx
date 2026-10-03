/* ============================================================
   Vista Demo Expo: la pasada del riel de punta a punta (dashboard → ESP32 → celular → IA).
   Pensada para mostrarse en una expo: el titular dice a distancia qué está pasando y cada paso
   tiene su estado, su foto y su diagnóstico. Sólo consume `DataRepository` (vía `usePasada`).
   ============================================================ */
import { Link } from 'react-router-dom';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { usePageTitle } from '@/hooks/PageMeta';
import { useDemoExpo } from '@/hooks/DemoExpoContext';
import { usePasada } from '@/hooks/usePasada';
import type { EstadoPasada } from '@/types/domain';
import { PasoItem } from './components/PasoItem';
import { ETIQUETA_ESTADO, titularPasada } from './pasadaPresentacion';
import styles from './DemoExpoPage.module.css';

const COLOR_ESTADO: Record<EstadoPasada, { soft: string; ink: string }> = {
  EN_CURSO: { soft: 'var(--info-soft)', ink: 'var(--info-ink)' },
  COMPLETADA: { soft: 'var(--ok-soft)', ink: 'var(--ok-ink)' },
  FALLIDA: { soft: 'var(--crit-soft)', ink: 'var(--crit-ink)' },
  CANCELADA: { soft: 'var(--off-soft)', ink: 'var(--muted)' },
};

function Desactivada() {
  return (
    <Card className={styles.aviso}>
      <p className={styles.avisoTitulo}>La sección Demo Expo está desactivada.</p>
      <p className={styles.avisoTexto}>
        Activala en <Link to="/configuracion">Configuración</Link>.
      </p>
    </Card>
  );
}

function PasadaView() {
  const { pasada, cargando, error, iniciando, iniciar, cancelar } = usePasada();
  const enCurso = pasada?.estado === 'EN_CURSO';
  const puedeCancelar = enCurso && !pasada.cancelacionSolicitada;
  const terminados = pasada?.pasos.filter((s) => s.estado === 'OK' || s.estado === 'OMITIDO' || s.estado === 'ERROR').length ?? 0;
  const total = pasada?.pasos.length ?? 0;
  const ahoraMs = Date.now();
  const color = pasada ? COLOR_ESTADO[pasada.estado] : null;

  return (
    <div className={styles.page}>
      <Card className={styles.panel}>
        <div className={styles.panelTop}>
          <div className={styles.titularBox}>
            <div role="status" className={styles.titular}>
              {pasada ? titularPasada(pasada) : cargando ? 'Consultando el riel…' : 'Todavía no hubo ninguna pasada'}
            </div>
            {!pasada && !cargando && (
              <p className={styles.vacio}>
                Iniciá una pasada: el riel va a recorrer dos sectores, el celular va a sacar una foto en cada uno
                y la IA los va a diagnosticar.
              </p>
            )}
          </div>
          <div className={styles.acciones}>
            {pasada && color && (
              <Badge soft={color.soft} ink={color.ink} style={{ fontSize: '13px', padding: '5px 14px' }}>
                {ETIQUETA_ESTADO[pasada.estado]}
                {enCurso && pasada.cancelacionSolicitada ? ' · cancelando…' : ''}
              </Badge>
            )}
            {puedeCancelar && (
              <button type="button" className={styles.btnCancelar} onClick={() => void cancelar()}>
                Cancelar
              </button>
            )}
            <button
              type="button"
              className={styles.btnIniciar}
              disabled={enCurso || iniciando}
              onClick={() => void iniciar()}
            >
              {iniciando ? 'Iniciando…' : 'Iniciar pasada'}
            </button>
          </div>
        </div>

        {pasada && (
          <div className={styles.progreso} aria-hidden="true">
            <div
              className={styles.progresoBarra}
              style={{ width: `${total ? (terminados / total) * 100 : 0}%`, background: color?.ink }}
            />
          </div>
        )}
      </Card>

      {error && (
        <div role="alert" className={styles.error}>
          {error}
        </div>
      )}
      {pasada?.error && pasada.estado !== 'EN_CURSO' && (
        <div role="alert" className={styles.error}>
          {pasada.error}
        </div>
      )}

      {pasada && (
        <ol className={styles.pasos}>
          {pasada.pasos.map((paso) => (
            <PasoItem key={paso.n} paso={paso} ahoraMs={ahoraMs} />
          ))}
        </ol>
      )}
    </div>
  );
}

export function DemoExpoPage() {
  const { visible, cargando } = useDemoExpo();
  usePageTitle('Demo Expo', 'Pasada del riel: del dashboard al ESP32, al celular y a la IA');

  if (cargando) return <div className={styles.cargando}>Cargando…</div>;
  // La ruta existe siempre; con el interruptor apagado no se arma la vista (ni su polling).
  if (!visible) return <Desactivada />;
  return <PasadaView />;
}
