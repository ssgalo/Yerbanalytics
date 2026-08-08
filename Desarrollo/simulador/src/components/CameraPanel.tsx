/* ============================================================
   Camera panel: test bench for the capture device.

   Request a photo, watch it arrive and enter by hand the diagnosis the model would return.
   Confirming it makes a new card appear under the dashboard's AI Diagnoses view, with the
   real photograph.

   The simulator NEVER talks to the camera app: it emits the order to the backend, the backend
   pushes it to the device over SSE, the device uploads the image and this panel polls the
   order's progress. It does not touch `/api/camara/v1/**` either — that is the device's
   contract.

   None of the endpoints it uses is simulator-exclusive: emitting the order is what the rail
   pass planner will do, and registering the diagnosis is what the inference service will do.
   That is why the round trip rehearsed here is the one that will run on its own.
   ============================================================ */
import { useCallback, useEffect, useRef, useState } from 'react';
import { Section } from './Section';
import {
  createDiagnosis,
  emitCaptureOrder,
  generatePairingCode,
  getCameraDevices,
  getCaptureOrder,
} from '../api';
import { DIAGNOSIS_STATES, SEVERITIES } from '../metrics';
import type { CameraDevice, CaptureOrder, CaptureOrderState, PairingCode, SectorRef } from '../types';

/** How often an in-flight order's progress is polled. */
const POLL_MS = 1000;

/** Upper bound on waiting before polling stops, even if the order is still unresolved. */
const MAX_WAIT_MS = 3 * 60_000;

const TERMINAL: CaptureOrderState[] = ['RECIBIDA', 'ERROR'];

/** Progress labels shown to the operator. */
const PROGRESS: Record<CaptureOrderState, string> = {
  PENDIENTE: 'Pendiente · esperando al dispositivo',
  ENTREGADA: 'Entregada · capturando',
  RECIBIDA: 'Imagen recibida',
  FALLIDA: 'Fallida · se reintenta',
  VENCIDA: 'Vencida · se reintenta',
  ERROR: 'Sin captura · intentos agotados',
};

interface Props {
  sectors: SectorRef[];
}

export function CameraPanel({ sectors }: Props) {
  const [devices, setDevices] = useState<CameraDevice[]>([]);
  const [code, setCode] = useState<PairingCode | null>(null);
  const [order, setOrder] = useState<CaptureOrder | null>(null);
  const [requesting, setRequesting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [sectorId, setSectorId] = useState('');
  const [railPosition, setRailPosition] = useState('1200');

  const [state, setState] = useState('Clorosis');
  const [severity, setSeverity] = useState('Media');
  const [confidence, setConfidence] = useState('92');
  const [diagSector, setDiagSector] = useState('');
  const [diagZone, setDiagZone] = useState('');
  const [saving, setSaving] = useState(false);

  const pollRef = useRef<number | null>(null);
  const deadlineRef = useRef(0);

  const stopPoll = useCallback(() => {
    if (pollRef.current !== null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  /** Fleet status. Refreshed on its own to reflect the device's heartbeat. */
  useEffect(() => {
    let alive = true;
    const refresh = async () => {
      try {
        const list = await getCameraDevices();
        if (alive) setDevices(list);
      } catch {
        // A failing list must not break the panel: there may be none enrolled yet.
        if (alive) setDevices([]);
      }
    };
    void refresh();
    const id = window.setInterval(() => void refresh(), 5000);
    return () => {
      alive = false;
      window.clearInterval(id);
    };
  }, []);

  useEffect(() => stopPoll, [stopPoll]);

  // Preselects the first sector and drops the chosen one if it disappeared after the topology
  // was regenerated.
  useEffect(() => {
    if (sectors.length === 0) setSectorId('');
    else if (!sectors.some((s) => s.id === sectorId)) setSectorId(sectors[0].id);
  }, [sectors, sectorId]);

  // The diagnosis' sector and macro-zone are prefilled from the order, and remain editable.
  useEffect(() => {
    if (order) {
      setDiagSector(order.sectorId);
      setDiagZone(order.zonaId);
    }
  }, [order]);

  const device = devices[0];
  const received = order?.estado === 'RECIBIDA' && order.imagenUrl;

  const requestCode = async () => {
    setError(null);
    try {
      setCode(await generatePairingCode());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  /** Requests a capture and follows its progress until it resolves or the wait expires. */
  const requestCapture = async () => {
    const rail = Number(railPosition);
    if (!sectorId || Number.isNaN(rail)) return;

    setError(null);
    setNotice(null);
    setRequesting(true);
    stopPoll();

    try {
      const emitted = await emitCaptureOrder(sectorId, rail);
      setOrder(emitted);
      deadlineRef.current = Date.now() + MAX_WAIT_MS;

      pollRef.current = window.setInterval(async () => {
        try {
          const current = await getCaptureOrder(emitted.ordenId);
          setOrder(current);

          if (TERMINAL.includes(current.estado)) {
            stopPoll();
            setRequesting(false);
            if (current.estado === 'ERROR') {
              setNotice(
                `La orden agotó sus reintentos${current.motivoFallo ? ` (${current.motivoFallo})` : ''}.` +
                  ' Revisá que la app de cámara esté abierta y conectada.',
              );
            }
          } else if (Date.now() > deadlineRef.current) {
            // The panel is not left waiting forever: the backend keeps retrying on its own,
            // but the operator needs to know nothing happened here.
            stopPoll();
            setRequesting(false);
            setNotice(
              'La orden sigue sin resolverse. El backend la va a reintentar; mientras tanto,' +
                ' verificá que haya un dispositivo de captura conectado.',
            );
          }
        } catch (e) {
          stopPoll();
          setRequesting(false);
          setError(e instanceof Error ? e.message : String(e));
        }
      }, POLL_MS);
    } catch (e) {
      setRequesting(false);
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  const clear = () => {
    stopPoll();
    setOrder(null);
    setRequesting(false);
    setError(null);
    setNotice(null);
  };

  /** Registers the diagnosis through the same endpoint the inference service will use. */
  const registerDiagnosis = async () => {
    if (!order?.capturaId) return;
    setError(null);
    setNotice(null);
    setSaving(true);
    try {
      const created = await createDiagnosis({
        capturaId: order.capturaId,
        sectorId: diagSector,
        zonaId: diagZone,
        estado: state,
        conf: Number(confidence),
        sev: severity,
      });
      setNotice(
        `Diagnóstico ${created.id} registrado. Ya aparece en la vista de Diagnósticos de IA` +
          ' del dashboard, con su fotografía.',
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Section
        title="Dispositivo de captura"
        hint="La app de cámara corre en el teléfono montado en el riel."
      >
        {device ? (
          <div className="row">
            <div className="field">
              <span className="label">Equipo</span>
              <strong>{device.nombre}</strong>
            </div>
            <div className="field">
              <span className="label">Estado</span>
              <span
                className="badge"
                style={{ background: device.estadoSoft, color: device.estadoInk }}
              >
                {device.estadoLabel}
              </span>
            </div>
            <div className="field">
              <span className="label">Última señal</span>
              <span>{device.ultimoHeartbeatAgo}</span>
            </div>
            <div className="field">
              <span className="label">Capturas</span>
              <span>
                {device.capturasOk} ok · {device.capturasError} con error
              </span>
            </div>
          </div>
        ) : (
          <p className="note">
            No hay ningún dispositivo de captura enrolado. Generá un código, abrí la app de
            cámara en el teléfono y tipealo ahí. Se hace una sola vez.
          </p>
        )}

        <div className="row" style={{ marginTop: 14 }}>
          <button type="button" className="small" onClick={requestCode}>
            Generar código de vinculación
          </button>
          {code && (
            <div className="field">
              <span className="label">Código (un solo uso)</span>
              <strong className="mono" style={{ fontSize: 18, letterSpacing: 2 }}>
                {code.codigo}
              </strong>
            </div>
          )}
        </div>
      </Section>

      <Section
        title="Pedir una captura"
        hint="Mismo endpoint que usará el planificador de pasadas del riel."
      >
        <div className="row">
          <label className="field">
            <span className="label">Sector</span>
            <select
              value={sectorId}
              onChange={(e) => setSectorId(e.target.value)}
              disabled={sectors.length === 0}
            >
              {sectors.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.id} · {s.zonaName}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="label">Posición de riel</span>
            <input
              className="num"
              type="number"
              value={railPosition}
              onChange={(e) => setRailPosition(e.target.value)}
            />
          </label>
          <button
            type="button"
            className="primary"
            onClick={requestCapture}
            disabled={requesting || !sectorId}
          >
            {requesting ? 'Esperando la foto…' : 'Pedir captura'}
          </button>
          {order && (
            <button type="button" className="small" onClick={clear}>
              Limpiar
            </button>
          )}
        </div>

        {order && (
          <>
            <div className="row" style={{ marginTop: 16 }}>
              <div className="field">
                <span className="label">Orden</span>
                <span className="mono" style={{ fontSize: 12 }}>
                  {order.ordenId.slice(0, 18)}…
                </span>
              </div>
              <div className="field">
                <span className="label">Avance</span>
                <span>{PROGRESS[order.estado]}</span>
              </div>
              <div className="field">
                <span className="label">Intento</span>
                <span>{order.intentos}</span>
              </div>
              {order.motivoFallo && (
                <div className="field">
                  <span className="label">Último fallo</span>
                  <span>{order.motivoFallo}</span>
                </div>
              )}
            </div>
            {received && (
              <img
                className="capture"
                src={order.imagenUrl ?? ''}
                alt={`Captura del sector ${order.sectorId}`}
              />
            )}
          </>
        )}

        {error && <p className="errorText">{error}</p>}
        {notice && (
          <p className="note" style={{ marginTop: 12 }}>
            {notice}
          </p>
        )}
      </Section>

      <Section
        title="Cargar el diagnóstico"
        hint="Mismo endpoint que usará el servicio de inferencia."
      >
        {!received ? (
          <p className="note">
            Primero pedí una captura. Todo diagnóstico nace del análisis de una imagen, así que
            no se puede cargar uno sin su fotografía.
          </p>
        ) : (
          <>
            <div className="row">
              <label className="field">
                <span className="label">Sector</span>
                <input
                  value={diagSector}
                  onChange={(e) => setDiagSector(e.target.value)}
                  style={{ width: 150 }}
                />
              </label>
              <label className="field">
                <span className="label">Macro-zona</span>
                <input
                  value={diagZone}
                  onChange={(e) => setDiagZone(e.target.value)}
                  style={{ width: 110 }}
                />
              </label>
              <label className="field">
                <span className="label">Estado</span>
                <select value={state} onChange={(e) => setState(e.target.value)}>
                  {DIAGNOSIS_STATES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span className="label">Severidad</span>
                <select value={severity} onChange={(e) => setSeverity(e.target.value)}>
                  {SEVERITIES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span className="label">Confianza (%)</span>
                <input
                  className="num"
                  type="number"
                  min={0}
                  max={100}
                  step="0.1"
                  value={confidence}
                  onChange={(e) => setConfidence(e.target.value)}
                />
              </label>
              <button
                type="button"
                className="primary"
                onClick={registerDiagnosis}
                disabled={saving}
              >
                {saving ? 'Registrando…' : 'Registrar diagnóstico'}
              </button>
            </div>
            <p className="sectionHint" style={{ marginTop: 14, marginBottom: 0 }}>
              Por debajo del 85 % de confianza el diagnóstico queda marcado como no
              concluyente (HU-04 CA-03), igual que cuando lo emita el modelo.
            </p>
          </>
        )}
      </Section>
    </>
  );
}
