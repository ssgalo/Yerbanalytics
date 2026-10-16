/* Pasada del riel en vivo (Demo Expo - simulador).
   Equivalent of the dashboard's DemoExpoPage PasadaView, but without react-router-dom.
   Receives state from its parent (DemoExpoSection) to avoid redundant polling. */
import type { DiagnosticoPaso, EstadoPaso, Pasada, PasoPasada } from '../types';
import type { UsePasadaSimulatorResult } from '../hooks/usePasadaSimulator';

const ETIQUETA_ESTADO: Record<string, string> = {
  EN_CURSO: 'En curso',
  COMPLETADA: 'Completada',
  FALLIDA: 'Falló',
  CANCELADA: 'Cancelada',
};

const COLOR_ESTADO: Record<string, { bg: string; fg: string }> = {
  EN_CURSO: { bg: 'var(--accent-soft)', fg: 'var(--accent)' },
  COMPLETADA: { bg: 'var(--accent-soft)', fg: 'var(--accent)' },
  FALLIDA: { bg: 'var(--error-soft)', fg: 'var(--error)' },
  CANCELADA: { bg: 'var(--warn-soft)', fg: 'var(--warn)' },
};

const PASO_COLOR: Record<EstadoPaso, { bg: string; fg: string }> = {
  PENDIENTE: { bg: 'var(--bg)', fg: 'var(--muted)' },
  EN_CURSO: { bg: 'var(--accent-soft)', fg: 'var(--accent)' },
  OK: { bg: 'var(--accent-soft)', fg: 'var(--accent)' },
  ERROR: { bg: 'var(--error-soft)', fg: 'var(--error)' },
  OMITIDO: { bg: 'var(--warn-soft)', fg: 'var(--warn)' },
};

function titularPaso(paso: PasoPasada): string {
  switch (paso.tipo) {
    case 'MOVER': return `Riel → posición ${paso.posicion}`;
    case 'CAPTURAR': return `Foto del sector ${paso.sectorId ?? '—'}`;
    case 'HOME': return 'Riel → home';
  }
}

function estadoTextoPaso(paso: PasoPasada): string {
  switch (paso.estado) {
    case 'PENDIENTE': return 'Pendiente';
    case 'ERROR': return paso.detalle ?? 'Falló';
    case 'OMITIDO': return paso.detalle ?? 'Omitido';
    case 'EN_CURSO':
      if (paso.tipo === 'MOVER') return 'Moviendo…';
      if (paso.tipo === 'HOME') return 'Volviendo a home…';
      if (paso.estadoOrden === 'PENDIENTE') return 'Esperando al celular';
      return 'Sacando la foto…';
    case 'OK':
      if (paso.tipo === 'MOVER') return 'Llegó';
      if (paso.tipo === 'HOME') return 'En home';
      return 'Foto recibida';
  }
}

function DxDiagnostico({ d }: { d: DiagnosticoPaso }) {
  return (
    <div style={{ marginTop: 6, fontSize: 12.5 }}>
      <span className="badge badgeOn" style={{ marginRight: 6 }}>{d.estado}</span>
      <span className="muted">{Math.round(d.conf)}% · {d.sev}</span>
    </div>
  );
}

function PasoRow({ paso }: { paso: PasoPasada }) {
  const { bg, fg } = PASO_COLOR[paso.estado];
  const hayFoto = paso.tipo === 'CAPTURAR' && paso.capturaId !== null;
  const seg =
    paso.iniciadoEn !== null
      ? Math.max(0, Math.round(((paso.terminadoEn ?? Date.now()) - paso.iniciadoEn) / 1000))
      : null;

  return (
    <li
      style={{
        display: 'flex',
        gap: 12,
        padding: '10px 0',
        borderTop: '1px solid var(--line)',
        alignItems: 'flex-start',
      }}
    >
      <span
        style={{
          width: 32,
          height: 32,
          borderRadius: '50%',
          background: bg,
          color: fg,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontWeight: 700,
          fontSize: 13,
          flexShrink: 0,
        }}
      >
        {paso.estado === 'EN_CURSO' ? '…' : paso.n}
      </span>
      <div style={{ flex: 1 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'baseline' }}>
          <strong style={{ fontSize: 13 }}>{titularPaso(paso)}</strong>
          {seg !== null && <span className="muted" style={{ fontSize: 12 }}>{seg} s</span>}
        </div>
        <div style={{ fontSize: 12.5, color: fg }}>{estadoTextoPaso(paso)}</div>
        {hayFoto && paso.diagnostico && <DxDiagnostico d={paso.diagnostico} />}
        {hayFoto && !paso.diagnostico && paso.capturaId && (
          <div style={{ fontSize: 12, marginTop: 4 }} className="muted">
            Esperando diagnóstico de IA…
          </div>
        )}
      </div>
      {paso.imagenUrl && (
        <img
          src={paso.imagenUrl}
          alt={`Foto del sector ${paso.sectorId}`}
          style={{
            width: 96,
            height: 64,
            objectFit: 'cover',
            borderRadius: 6,
            border: '1px solid var(--line)',
            flexShrink: 0,
          }}
        />
      )}
    </li>
  );
}

function titularPasada(p: Pasada): string {
  if (p.estado === 'EN_CURSO') {
    if (p.cancelacionSolicitada) return 'Cancelación en curso: el riel vuelve a home…';
    const activo = p.pasos.find((s) => s.estado === 'EN_CURSO');
    if (!activo) return 'Preparando el siguiente paso…';
    if (activo.tipo === 'HOME') return 'El riel vuelve a home…';
    if (activo.tipo === 'MOVER') return `El riel se mueve a la posición ${activo.posicion}…`;
    return `Capturando el sector ${activo.sectorId ?? ''}…`;
  }
  if (p.estado === 'COMPLETADA') return 'Pasada completa';
  if (p.estado === 'FALLIDA') return 'La pasada falló';
  return 'Pasada cancelada';
}

type Props = Pick<UsePasadaSimulatorResult, 'pasada' | 'cargando' | 'error' | 'iniciando' | 'iniciar' | 'cancelar'>;

export function PasadaSimulatorPanel({ pasada, cargando, error, iniciando, iniciar, cancelar }: Props) {
  const enCurso = pasada?.estado === 'EN_CURSO';
  const terminados = pasada?.pasos.filter((s) => ['OK', 'OMITIDO', 'ERROR'].includes(s.estado)).length ?? 0;
  const total = pasada?.pasos.length ?? 0;
  const color = pasada ? (COLOR_ESTADO[pasada.estado] ?? COLOR_ESTADO.EN_CURSO) : null;

  return (
    <section className="card">
      <div className="demoCardHeader">
        <h2 className="sectionTitle">Pasada del riel</h2>
        {pasada && color && (
          <span className="badge" style={{ background: color.bg, color: color.fg, fontSize: 12 }}>
            {ETIQUETA_ESTADO[pasada.estado] ?? pasada.estado}
            {enCurso && pasada.cancelacionSolicitada ? ' · cancelando…' : ''}
          </span>
        )}
      </div>
      <p className="sectionHint">
        Inicia la pasada completa del riel: se mueve a cada sector, el celular saca una foto y
        la IA la diagnostica.
      </p>

      <div role="status" style={{ fontSize: 14, fontWeight: 600, margin: '8px 0', minHeight: 22 }}>
        {cargando
          ? 'Consultando el riel…'
          : pasada
            ? titularPasada(pasada)
            : 'Todavía no hubo ninguna pasada'}
      </div>

      {pasada && total > 0 && (
        <div style={{ height: 6, background: 'var(--line)', borderRadius: 3, overflow: 'hidden', margin: '8px 0' }}>
          <div
            style={{
              height: '100%',
              width: `${(terminados / total) * 100}%`,
              background: color?.fg ?? 'var(--accent)',
              transition: 'width 0.3s',
            }}
          />
        </div>
      )}

      <div className="actions">
        <button
          type="button"
          className="primary"
          disabled={enCurso || iniciando}
          onClick={() => void iniciar()}
        >
          {iniciando ? 'Iniciando…' : 'Iniciar pasada'}
        </button>
        {enCurso && !pasada?.cancelacionSolicitada && (
          <button type="button" className="small danger" onClick={() => void cancelar()}>
            Cancelar
          </button>
        )}
      </div>

      {error && <p className="errorMsg" style={{ marginTop: 8 }}>{error}</p>}

      {pasada && pasada.pasos.length > 0 && (
        <ol style={{ listStyle: 'none', margin: '8px 0 0', padding: 0 }}>
          {pasada.pasos.map((paso) => (
            <PasoRow key={paso.n} paso={paso} />
          ))}
        </ol>
      )}
    </section>
  );
}
