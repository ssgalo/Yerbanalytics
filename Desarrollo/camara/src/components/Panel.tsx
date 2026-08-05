import type { EstadoConexion } from '@/hooks/useDispositivo';
import type { EstadoCamara } from '@/hooks/useCamara';
import type { EntradaLog } from '@/hooks/useLog';

/* ============================================================
   Panel de estado del dispositivo.

   El operario no mira esta pantalla de cerca ni seguido: tiene que poder pararse a un metro
   del riel y saber de un vistazo si el sistema está respondiendo. De ahí el indicador de
   conexión grande y el aviso explícito cuando la app deja de estar operativa — si el
   teléfono se sale de la app, el sistema entero deja de capturar y nadie más se entera.
   ============================================================ */

const COLOR_CONEXION: Record<EstadoConexion, string> = {
  conectado: 'var(--verde)',
  conectando: 'var(--ambar)',
  desconectado: 'var(--rojo)',
};

const TEXTO_CONEXION: Record<EstadoConexion, string> = {
  conectado: 'Conectado',
  conectando: 'Reconectando…',
  desconectado: 'Desconectado',
};

const TEXTO_CAMARA: Record<EstadoCamara, string> = {
  apagada: 'Cámara apagada',
  iniciando: 'Iniciando cámara…',
  calentando: 'Calentando sensor…',
  lista: 'Cámara lista',
  error: 'Error de cámara',
};

const COLOR_NIVEL: Record<EntradaLog['nivel'], string> = {
  info: 'var(--texto-2)',
  ok: 'var(--verde)',
  warn: 'var(--ambar)',
  error: 'var(--rojo)',
};

const hora = (ts: number) =>
  new Date(ts).toLocaleTimeString('es-AR', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });

interface Props {
  videoRef: React.RefObject<HTMLVideoElement>;
  estadoCamara: EstadoCamara;
  errorCamara: string | null;
  conexion: EstadoConexion;
  dispositivoId: string;
  capturasOk: number;
  capturasError: number;
  pendientes: number;
  ultima: { url: string; capturaId: string; ts: number } | null;
  entradas: EntradaLog[];
  onIniciar: () => void;
  onCapturarPrueba: () => void;
  onReconectar: () => void;
  onDesvincular: () => void;
}

export function Panel({
  videoRef,
  estadoCamara,
  errorCamara,
  conexion,
  dispositivoId,
  capturasOk,
  capturasError,
  pendientes,
  ultima,
  entradas,
  onIniciar,
  onCapturarPrueba,
  onReconectar,
  onDesvincular,
}: Props) {
  const operativo = estadoCamara === 'lista' && conexion === 'conectado';

  return (
    <div style={{ display: 'grid', gap: 12, padding: 12, minHeight: '100%' }}>
      {/* Barra de estado: lo único que hay que poder leer a distancia. */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '14px 16px',
          borderRadius: 'var(--radio)',
          background: operativo ? 'var(--panel)' : 'rgba(240,138,114,.14)',
          border: `2px solid ${operativo ? 'var(--borde)' : 'var(--rojo)'}`,
        }}
      >
        <span
          aria-hidden
          style={{
            width: 16,
            height: 16,
            borderRadius: '50%',
            background: COLOR_CONEXION[conexion],
            flexShrink: 0,
            boxShadow: `0 0 12px ${COLOR_CONEXION[conexion]}`,
          }}
        />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 20, fontWeight: 800 }}>{TEXTO_CONEXION[conexion]}</div>
          <div style={{ fontSize: 13, color: 'var(--texto-2)' }}>
            {TEXTO_CAMARA[estadoCamara]} · {dispositivoId}
          </div>
        </div>
      </div>

      {!operativo && (
        <div
          role="status"
          style={{
            padding: '12px 16px',
            borderRadius: 'var(--radio)',
            background: 'rgba(245,196,81,.14)',
            border: '1px solid var(--ambar)',
            color: 'var(--ambar)',
            fontWeight: 600,
            lineHeight: 1.45,
          }}
        >
          El sistema NO está capturando. Esta app tiene que quedar abierta y en primer plano:
          iOS no ejecuta nada en segundo plano.
        </div>
      )}

      {/* Preview en vivo. playsinline/muted/autoplay: sin ellos iOS lo abre a pantalla
          completa y rompe la interfaz. */}
      <div
        style={{
          position: 'relative',
          borderRadius: 'var(--radio)',
          overflow: 'hidden',
          background: '#000',
          aspectRatio: '3 / 4',
        }}
      >
        <video
          ref={videoRef}
          playsInline
          muted
          autoPlay
          style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />
        {estadoCamara === 'apagada' && (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              display: 'grid',
              placeItems: 'center',
              padding: 24,
              textAlign: 'center',
              gap: 16,
            }}
          >
            <div>
              <p style={{ color: 'var(--texto-2)', lineHeight: 1.5, margin: '0 0 16px' }}>
                iOS exige un toque para dar permiso de cámara: no se puede pedir solo.
              </p>
              <button onClick={onIniciar}>Iniciar cámara</button>
            </div>
          </div>
        )}
        {estadoCamara === 'error' && (
          <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', padding: 24 }}>
            <div style={{ textAlign: 'center' }}>
              <p style={{ color: 'var(--rojo)', lineHeight: 1.5 }}>{errorCamara}</p>
              <button onClick={onIniciar}>Reintentar</button>
            </div>
          </div>
        )}
      </div>

      {/* Contadores de la sesión */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8 }}>
        {[
          { rotulo: 'Exitosas', valor: capturasOk, color: 'var(--verde)' },
          { rotulo: 'Fallidas', valor: capturasError, color: 'var(--rojo)' },
          { rotulo: 'Sin enviar', valor: pendientes, color: 'var(--ambar)' },
        ].map((c) => (
          <div
            key={c.rotulo}
            style={{
              background: 'var(--fondo-2)',
              borderRadius: 'var(--radio)',
              padding: '12px 14px',
              border: '1px solid var(--borde)',
            }}
          >
            <div style={{ fontSize: 26, fontWeight: 800, color: c.color, lineHeight: 1.1 }}>
              {c.valor}
            </div>
            <div style={{ fontSize: 12, color: 'var(--texto-2)' }}>{c.rotulo}</div>
          </div>
        ))}
      </div>

      {/* Última captura */}
      {ultima && (
        <div
          style={{
            display: 'flex',
            gap: 12,
            alignItems: 'center',
            background: 'var(--fondo-2)',
            border: '1px solid var(--borde)',
            borderRadius: 'var(--radio)',
            padding: 10,
          }}
        >
          <img
            src={ultima.url}
            alt="Última captura"
            style={{ width: 64, height: 64, objectFit: 'cover', borderRadius: 10, flexShrink: 0 }}
          />
          <div style={{ minWidth: 0 }}>
            <div style={{ fontWeight: 700, fontFamily: 'var(--mono)', fontSize: 14 }}>
              {ultima.capturaId}
            </div>
            <div style={{ fontSize: 13, color: 'var(--texto-2)' }}>{hora(ultima.ts)}</div>
          </div>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 8 }}>
        <button onClick={onCapturarPrueba} disabled={estadoCamara !== 'lista'}>
          Captura de prueba
        </button>
        <button className="secundario" onClick={onReconectar}>
          Reconectar
        </button>
      </div>

      {/* Log en pantalla: depurar con devtools enchufadas a un teléfono en un riel no es
          practicable. */}
      <div
        style={{
          background: '#0d2418',
          border: '1px solid var(--borde)',
          borderRadius: 'var(--radio)',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '8px 12px',
            fontSize: 12,
            color: 'var(--texto-2)',
            borderBottom: '1px solid var(--borde)',
          }}
        >
          Registro de eventos
        </div>
        <div style={{ maxHeight: 260, overflowY: 'auto', padding: 8, fontFamily: 'var(--mono)', fontSize: 12 }}>
          {entradas.length === 0 && <div style={{ color: 'var(--texto-2)' }}>Sin eventos todavía.</div>}
          {entradas.map((e) => (
            <div key={e.id} style={{ display: 'flex', gap: 8, padding: '3px 4px', lineHeight: 1.45 }}>
              <span style={{ color: 'var(--texto-2)', flexShrink: 0 }}>{hora(e.ts)}</span>
              <span style={{ color: COLOR_NIVEL[e.nivel], wordBreak: 'break-word' }}>{e.mensaje}</span>
            </div>
          ))}
        </div>
      </div>

      <button className="secundario" onClick={onDesvincular} style={{ fontSize: 14 }}>
        Desvincular este dispositivo
      </button>
    </div>
  );
}
