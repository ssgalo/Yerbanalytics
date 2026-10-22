/* ============================================================
   Secuencias directas de actuadores (Demo Expo - simulador).
   Four cards: Riego, Mediasombra, Abrir/cerrar mediasombra, Lectura de sensores.
   Adapted from the dashboard's SecuenciasPanel.tsx but without react-router-dom
   and using the simulator's useSecuenciaSimulator hook.
   ============================================================ */
import { useEffect, useState, type ReactNode } from 'react';
import { useSecuenciaSimulator } from '../hooks/useSecuenciaSimulator';
import type { PasoSecuencia, Secuencia, TipoPasoSecuencia } from '../types';

const RIEGO = { min: 1, max: 120, defecto: 10 };
const ESPERA = { min: 0, max: 600, defecto: 10 };

function enRango(texto: string, { min, max }: { min: number; max: number }): number | null {
  if (texto.trim() === '') return null;
  const n = Number(texto);
  return Number.isInteger(n) && n >= min && n <= max ? n : null;
}

function Tarjeta({ titulo, descripcion, children }: { titulo: string; descripcion: string; children: ReactNode }) {
  return (
    <div className="card" style={{ marginBottom: 0 }}>
      <h3 style={{ margin: '0 0 4px', fontSize: 14, fontWeight: 600 }}>{titulo}</h3>
      <p className="sectionHint">{descripcion}</p>
      <div className="row">{children}</div>
    </div>
  );
}

/* ---- Secuencia progress display ---- */

const EN_CURSO_TEXTO: Record<TipoPasoSecuencia, string> = {
  ABRIR: 'Abriendo…',
  ESPERAR: 'Esperando…',
  CERRAR: 'Cerrando…',
  DESPLEGAR: 'Desplegando…',
  ENROLLAR: 'Enrollando…',
  PEDIR: 'Pidiendo la lectura…',
  ESPERAR_TELEMETRIA: 'Esperando al nodo…',
  MOSTRAR: 'Preparando la lectura…',
};

const LISTO_TEXTO: Record<TipoPasoSecuencia, string> = {
  ABRIR: 'Válvula abierta',
  ESPERAR: 'Listo',
  CERRAR: 'Válvula cerrada',
  DESPLEGAR: 'Desplegada',
  ENROLLAR: 'Enrollada',
  PEDIR: 'Pedido enviado',
  ESPERAR_TELEMETRIA: 'Lectura recibida',
  MOSTRAR: 'Lista',
};

function tipoPasoLabel(paso: PasoSecuencia, sec: Secuencia): string {
  switch (paso.tipo) {
    case 'ABRIR': return `Abrir válvula (${sec.sectorId ?? '—'})`;
    case 'CERRAR': return 'Cerrar válvula';
    case 'DESPLEGAR': return sec.tipo === 'MEDIASOMBRA_CERRAR' ? 'Cerrar mediasombra (desplegar)' : 'Desplegar mediasombra';
    case 'ENROLLAR': return sec.tipo === 'MEDIASOMBRA_ABRIR' ? 'Abrir mediasombra (enrollar)' : 'Enrollar mediasombra';
    case 'ESPERAR':
      return sec.tipo === 'RIEGO'
        ? `Regar ${sec.parametros.duracionSeg ?? '—'} s`
        : `Mantener desplegada ${sec.parametros.esperaSeg ?? '—'} s`;
    case 'PEDIR':
      return sec.parametros.ejecutarMotor
        ? `Pedir lectura a ${sec.zonaId} + motor`
        : `Pedir lectura a ${sec.zonaId}`;
    case 'ESPERAR_TELEMETRIA': return 'Esperando telemetría…';
    case 'MOSTRAR': return 'Mostrar lectura';
  }
}

function textoPasoSecuencia(paso: PasoSecuencia): string {
  switch (paso.estado) {
    case 'PENDIENTE': return 'Pendiente';
    case 'ERROR': return paso.detalle ?? 'Falló';
    case 'OMITIDO': return paso.detalle ?? 'Omitido';
    case 'EN_CURSO': {
      if (paso.tipo === 'ESPERAR' && paso.esperaHasta) {
        const faltan = Math.max(0, Math.ceil((paso.esperaHasta - Date.now()) / 1000));
        return faltan === 0 ? 'Esperando confirmación del nodo…' : `Faltan ${faltan} s`;
      }
      return EN_CURSO_TEXTO[paso.tipo];
    }
    case 'OK': return LISTO_TEXTO[paso.tipo];
  }
}

const ETIQUETA_ESTADO: Record<string, string> = {
  EN_CURSO: 'En curso',
  COMPLETADA: 'Completada',
  FALLIDA: 'Falló',
  CANCELADA: 'Cancelada',
};

function SecuenciaProgreso({
  secuencia,
  onCancelar,
}: {
  secuencia: Secuencia;
  onCancelar?: () => void;
}) {
  const enCurso = secuencia.estado === 'EN_CURSO';
  return (
    <div className="card demoCard" style={{ marginTop: 8 }}>
      <div className="demoCardHeader">
        <span className={`badge ${enCurso ? 'badgeOn' : 'badgeOff'}`}>
          {ETIQUETA_ESTADO[secuencia.estado] ?? secuencia.estado}
          {enCurso && secuencia.cancelacionSolicitada ? ' · cancelando…' : ''}
        </span>
        {onCancelar && enCurso && !secuencia.cancelacionSolicitada && (
          <button type="button" className="small danger" onClick={onCancelar}>
            Cancelar
          </button>
        )}
      </div>

      {secuencia.error && !enCurso && (
        <p className="errorMsg" style={{ marginTop: 6 }}>{secuencia.error}</p>
      )}

      <ol style={{ listStyle: 'none', margin: '8px 0 0', padding: 0 }}>
        {secuencia.pasos.map((paso) => {
          const ok = paso.estado === 'OK';
          const err = paso.estado === 'ERROR';
          return (
            <li
              key={paso.n}
              style={{
                display: 'flex',
                gap: 10,
                padding: '6px 0',
                borderTop: '1px solid var(--line)',
                fontSize: 13,
                alignItems: 'center',
              }}
            >
              <span
                style={{
                  width: 22,
                  height: 22,
                  borderRadius: '50%',
                  background: ok ? 'var(--accent-soft)' : err ? 'var(--error-soft)' : 'var(--bg)',
                  color: ok ? 'var(--accent)' : err ? 'var(--error)' : 'var(--muted)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 700,
                  fontSize: 11,
                  flexShrink: 0,
                }}
              >
                {ok ? '✓' : err ? '✗' : paso.n}
              </span>
              <div style={{ flex: 1 }}>
                <span>{tipoPasoLabel(paso, secuencia)}</span>
                <span className="muted" style={{ marginLeft: 8 }}>{textoPasoSecuencia(paso)}</span>
              </div>
            </li>
          );
        })}
      </ol>

      {/* Lectura table for LECTURA sequences */}
      {secuencia.tipo === 'LECTURA' && secuencia.lectura && (
        <div style={{ marginTop: 10 }}>
          <strong style={{ fontSize: 12.5 }}>Lectura de {secuencia.zonaId}</strong>
          <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: 4, fontSize: 12.5 }}>
            <tbody>
              {Object.entries(secuencia.lectura.metricas)
                .filter(([, v]) => v != null)
                .map(([k, v]) => (
                  <tr key={k} style={{ borderTop: '1px solid var(--line)' }}>
                    <th scope="row" style={{ textAlign: 'left', padding: '3px 6px', color: 'var(--muted)', fontWeight: 'normal' }}>{k}</th>
                    <td style={{ padding: '3px 6px', fontWeight: 600 }}>{String(Math.round((v as number) * 100) / 100)}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

interface Props {
  pasadaEnCurso: boolean;
}

export function SecuenciasSimulatorPanel({ pasadaEnCurso }: Props) {
  const { secuencia, error, iniciando, iniciar, cancelar, descartarError } =
    useSecuenciaSimulator();
  const [riegoTxt, setRiegoTxt] = useState(String(RIEGO.defecto));
  const [esperaTxt, setEsperaTxt] = useState(String(ESPERA.defecto));

  useEffect(() => {
    descartarError();
  }, [secuencia, pasadaEnCurso, descartarError]);

  const ocupado = secuencia?.estado === 'EN_CURSO' || pasadaEnCurso || iniciando;
  const duracion = enRango(riegoTxt, RIEGO);
  const espera = enRango(esperaTxt, ESPERA);

  return (
    <section className="card">
      <h2 className="sectionTitle">Secuencias</h2>
      <p className="sectionHint">
        Comandos directos a los actuadores. Una a la vez, y nunca durante una pasada del riel.
      </p>

      <div className="sensors">
        <Tarjeta titulo="Riego" descripcion="Abre la válvula, espera y la cierra.">
          <label className="field">
            <span className="label">Segundos</span>
            <input
              type="number"
              className="num"
              min={RIEGO.min}
              max={RIEGO.max}
              value={riegoTxt}
              onChange={(e) => setRiegoTxt(e.target.value)}
            />
          </label>
          <button
            type="button"
            className="primary"
            disabled={ocupado || duracion === null}
            onClick={() => duracion !== null && void iniciar('RIEGO', { duracionSeg: duracion })}
          >
            Regar
          </button>
        </Tarjeta>

        <Tarjeta titulo="Mediasombra" descripcion="La despliega, espera y la enrolla.">
          <label className="field">
            <span className="label">Espera desplegada (s)</span>
            <input
              type="number"
              className="num"
              min={ESPERA.min}
              max={ESPERA.max}
              value={esperaTxt}
              onChange={(e) => setEsperaTxt(e.target.value)}
            />
          </label>
          <button
            type="button"
            className="primary"
            disabled={ocupado || espera === null}
            onClick={() => espera !== null && void iniciar('MEDIASOMBRA', { esperaSeg: espera })}
          >
            Desplegar y enrollar
          </button>
        </Tarjeta>

        <Tarjeta
          titulo="Abrir o cerrar mediasombra"
          descripcion="Una sola acción, sin espera. Abrir la enrolla (deja pasar la luz); cerrar la despliega."
        >
          <button
            type="button"
            className="small"
            disabled={ocupado}
            onClick={() => void iniciar('MEDIASOMBRA_ABRIR')}
          >
            Abrir mediasombra
          </button>
          <button
            type="button"
            className="small"
            disabled={ocupado}
            onClick={() => void iniciar('MEDIASOMBRA_CERRAR')}
          >
            Cerrar mediasombra
          </button>
        </Tarjeta>

        <Tarjeta
          titulo="Lectura"
          descripcion="Pide lectura al nodo testigo de la zona. Con motor, además evalúa las reglas."
        >
          <button
            type="button"
            className="small"
            disabled={ocupado}
            onClick={() => void iniciar('LECTURA')}
          >
            Leer sensores ahora
          </button>
          <button
            type="button"
            className="primary"
            disabled={ocupado}
            onClick={() => void iniciar('LECTURA', { ejecutarMotor: true })}
          >
            Leer y ejecutar motor
          </button>
        </Tarjeta>
      </div>

      {error && (
        <p className="errorMsg" style={{ marginTop: 10 }} role="alert">
          {error}
        </p>
      )}

      {secuencia && (
        <SecuenciaProgreso
          secuencia={secuencia}
          onCancelar={() => void cancelar()}
        />
      )}
    </section>
  );
}
