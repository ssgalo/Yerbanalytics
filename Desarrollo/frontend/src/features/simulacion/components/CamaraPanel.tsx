/* ============================================================
   Panel de cámara del simulador.

   Banco de pruebas del dispositivo de captura: pedir una foto, verla llegar y cargar a mano
   el diagnóstico que devolvería el modelo. Al confirmarlo aparece una tarjeta nueva en
   Diagnósticos de IA, con la fotografía real.

   No usa NINGÚN endpoint exclusivo del simulador: la emisión de la orden es la que hará el
   planificador de pasadas del riel, y el alta del diagnóstico la que hará el servicio de
   inferencia. Por eso el recorrido que se ensaya acá es literalmente el que va a correr solo.
   ============================================================ */
import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { useCamaraSim } from '@/hooks/useCamaraSim';
import type { EstadoOrdenCaptura } from '@/types/domain';
import styles from '../Simulacion.module.css';

/** Taxonomía de la plataforma. Espejo de `DiagnosticoService.ESTADOS` del backend. */
const ESTADOS = [
  'Sano',
  'Clorosis',
  'Estrés solar',
  'Daño biótico',
  'Ácaro',
  'Plaga foliar',
  'Daño fúngico',
  'No concluyente',
];

const SEVERIDADES = ['—', 'Baja', 'Media', 'Alta'];

const AVANCE: Record<EstadoOrdenCaptura, { texto: string; soft: string; ink: string }> = {
  PENDIENTE: { texto: 'Pendiente · esperando al dispositivo', soft: '#FBF0DC', ink: '#A66A12' },
  ENTREGADA: { texto: 'Entregada · capturando', soft: '#E4EEF8', ink: '#2C5C8F' },
  RECIBIDA: { texto: 'Imagen recibida', soft: '#E7F1EA', ink: '#2E7A4F' },
  FALLIDA: { texto: 'Fallida · se reintenta', soft: '#FBF0DC', ink: '#A66A12' },
  VENCIDA: { texto: 'Vencida · se reintenta', soft: '#FBF0DC', ink: '#A66A12' },
  ERROR: { texto: 'Sin captura · intentos agotados', soft: '#FBE6E0', ink: '#A8331C' },
};

interface Props {
  /** Sectores de la topología vigente. */
  sectores: { id: string; zonaName: string }[];
  sectoresLoading: boolean;
}

export function CamaraPanel({ sectores, sectoresLoading }: Props) {
  const cam = useCamaraSim();

  const [sectorId, setSectorId] = useState('');
  const [posicionRiel, setPosicionRiel] = useState('1200');

  const [estado, setEstado] = useState('Clorosis');
  const [sev, setSev] = useState('Media');
  const [conf, setConf] = useState('92');
  const [sectorDiag, setSectorDiag] = useState('');
  const [zonaDiag, setZonaDiag] = useState('');
  const [guardando, setGuardando] = useState(false);

  // Preselecciona el primer sector y descarta el elegido si dejó de existir tras un cambio
  // de topología, igual que hace el alta de sensores simulados.
  useEffect(() => {
    if (sectores.length === 0) setSectorId('');
    else if (!sectores.some((s) => s.id === sectorId)) setSectorId(sectores[0].id);
  }, [sectores, sectorId]);

  // Sector y macro-zona del diagnóstico vienen precargados desde la orden, y son editables.
  useEffect(() => {
    if (cam.orden) {
      setSectorDiag(cam.orden.sectorId);
      setZonaDiag(cam.orden.zonaId);
    }
  }, [cam.orden]);

  const dispositivo = cam.dispositivos[0];
  const recibida = cam.orden?.estado === 'RECIBIDA' && cam.orden.imagenUrl;

  const pedir = async () => {
    const riel = Number(posicionRiel);
    if (!sectorId || Number.isNaN(riel)) return;
    await cam.pedirCaptura(sectorId, riel);
  };

  const guardar = async () => {
    if (!cam.orden?.capturaId) return;
    setGuardando(true);
    try {
      await cam.cargarDiagnostico({
        capturaId: cam.orden.capturaId,
        sectorId: sectorDiag,
        zonaId: zonaDiag,
        estado,
        conf: Number(conf),
        sev,
      });
    } catch {
      // El hook ya expone el mensaje; acá sólo se libera el botón.
    } finally {
      setGuardando(false);
    }
  };

  return (
    <>
      {/* ---- Estado del dispositivo ---- */}
      <Card className={styles.section}>
        <div className={styles.sectionHead}>
          <span className={styles.sectionTitle}>Dispositivo de captura</span>
          <span className={styles.sectionHint}>
            La app de cámara corre en el teléfono montado en el riel
          </span>
        </div>

        {dispositivo ? (
          <div className={styles.formGrid}>
            <div className={styles.group}>
              <span className={styles.label}>Equipo</span>
              <strong style={{ fontSize: 14 }}>{dispositivo.nombre}</strong>
            </div>
            <div className={styles.group}>
              <span className={styles.label}>Estado</span>
              <Badge soft={dispositivo.estadoSoft} ink={dispositivo.estadoInk}>
                {dispositivo.estadoLabel}
              </Badge>
            </div>
            <div className={styles.group}>
              <span className={styles.label}>Última señal</span>
              <span style={{ fontSize: 13 }}>{dispositivo.ultimoHeartbeatAgo}</span>
            </div>
            <div className={styles.group}>
              <span className={styles.label}>Capturas</span>
              <span style={{ fontSize: 13 }}>
                {dispositivo.capturasOk} ok · {dispositivo.capturasError} con error
              </span>
            </div>
          </div>
        ) : (
          <div className={styles.note}>
            No hay ningún dispositivo de captura enrolado. Generá un código, abrí la app de
            cámara en el teléfono y tipealo ahí. Se hace una sola vez.
          </div>
        )}

        <div className={styles.formGrid} style={{ marginTop: 14 }}>
          <button className={styles.btnSmall} onClick={cam.generarCodigo}>
            Generar código de vinculación
          </button>
          {cam.codigo && (
            <div className={styles.group}>
              <span className={styles.label}>Código (un solo uso)</span>
              <strong style={{ fontFamily: 'monospace', fontSize: 18, letterSpacing: 2 }}>
                {cam.codigo.codigo}
              </strong>
            </div>
          )}
        </div>
      </Card>

      {/* ---- Pedido de captura ---- */}
      <Card className={styles.section}>
        <div className={styles.sectionHead}>
          <span className={styles.sectionTitle}>Pedir una captura</span>
          <span className={styles.sectionHint}>
            Mismo endpoint que usará el planificador de pasadas del riel
          </span>
        </div>

        <div className={styles.formGrid}>
          <label className={styles.group}>
            <span className={styles.label}>Sector</span>
            <select
              className={styles.select}
              value={sectorId}
              onChange={(e) => setSectorId(e.target.value)}
              disabled={sectoresLoading || sectores.length === 0}
            >
              {sectores.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.id} · {s.zonaName}
                </option>
              ))}
            </select>
          </label>

          <label className={styles.group}>
            <span className={styles.label}>Posición de riel</span>
            <input
              className={styles.input}
              type="number"
              value={posicionRiel}
              onChange={(e) => setPosicionRiel(e.target.value)}
              style={{ width: 130 }}
            />
          </label>

          <button className={styles.btnPrimary} onClick={pedir} disabled={cam.pidiendo || !sectorId}>
            {cam.pidiendo ? 'Esperando la foto…' : 'Pedir captura'}
          </button>

          {cam.orden && (
            <button className={styles.btnSmall} onClick={cam.limpiar}>
              Limpiar
            </button>
          )}
        </div>

        {cam.orden && (
          <div style={{ marginTop: 16, display: 'grid', gap: 12 }}>
            <div className={styles.formGrid}>
              <div className={styles.group}>
                <span className={styles.label}>Orden</span>
                <span style={{ fontFamily: 'monospace', fontSize: 12 }}>
                  {cam.orden.ordenId.slice(0, 18)}…
                </span>
              </div>
              <div className={styles.group}>
                <span className={styles.label}>Avance</span>
                <Badge
                  soft={AVANCE[cam.orden.estado].soft}
                  ink={AVANCE[cam.orden.estado].ink}
                >
                  {AVANCE[cam.orden.estado].texto}
                </Badge>
              </div>
              <div className={styles.group}>
                <span className={styles.label}>Intento</span>
                <span style={{ fontSize: 13 }}>{cam.orden.intentos}</span>
              </div>
              {cam.orden.motivoFallo && (
                <div className={styles.group}>
                  <span className={styles.label}>Último fallo</span>
                  <span style={{ fontSize: 13 }}>{cam.orden.motivoFallo}</span>
                </div>
              )}
            </div>

            {recibida && (
              <img
                src={cam.orden.imagenUrl ?? ''}
                alt={`Captura del sector ${cam.orden.sectorId}`}
                style={{
                  maxWidth: 380,
                  width: '100%',
                  borderRadius: 12,
                  border: '1px solid var(--line)',
                  display: 'block',
                }}
              />
            )}
          </div>
        )}

        {cam.error && <div className={styles.formError}>{cam.error}</div>}
        {cam.aviso && (
          <div className={styles.note} style={{ marginTop: 12, marginBottom: 0 }}>
            {cam.aviso}
          </div>
        )}
      </Card>

      {/* ---- Diagnóstico manual ---- */}
      <Card className={styles.section}>
        <div className={styles.sectionHead}>
          <span className={styles.sectionTitle}>Cargar el diagnóstico</span>
          <span className={styles.sectionHint}>
            Mismo endpoint que usará el servicio de inferencia
          </span>
        </div>

        {!recibida ? (
          <div className={styles.note}>
            Primero pedí una captura. Todo diagnóstico nace del análisis de una imagen, así que
            no se puede cargar uno sin su fotografía.
          </div>
        ) : (
          <>
            <div className={styles.formGrid}>
              <label className={styles.group}>
                <span className={styles.label}>Sector</span>
                <input
                  className={styles.input}
                  value={sectorDiag}
                  onChange={(e) => setSectorDiag(e.target.value)}
                  style={{ width: 150 }}
                />
              </label>

              <label className={styles.group}>
                <span className={styles.label}>Macro-zona</span>
                <input
                  className={styles.input}
                  value={zonaDiag}
                  onChange={(e) => setZonaDiag(e.target.value)}
                  style={{ width: 110 }}
                />
              </label>

              <label className={styles.group}>
                <span className={styles.label}>Estado</span>
                <select
                  className={styles.select}
                  value={estado}
                  onChange={(e) => setEstado(e.target.value)}
                >
                  {ESTADOS.map((e) => (
                    <option key={e} value={e}>
                      {e}
                    </option>
                  ))}
                </select>
              </label>

              <label className={styles.group}>
                <span className={styles.label}>Severidad</span>
                <select className={styles.select} value={sev} onChange={(e) => setSev(e.target.value)}>
                  {SEVERIDADES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </label>

              <label className={styles.group}>
                <span className={styles.label}>Confianza (%)</span>
                <input
                  className={styles.input}
                  type="number"
                  min={0}
                  max={100}
                  step="0.1"
                  value={conf}
                  onChange={(e) => setConf(e.target.value)}
                  style={{ width: 110 }}
                />
              </label>

              <button className={styles.btnPrimary} onClick={guardar} disabled={guardando}>
                {guardando ? 'Registrando…' : 'Registrar diagnóstico'}
              </button>
            </div>

            <div className={styles.note} style={{ marginTop: 14, marginBottom: 0 }}>
              Por debajo del 85% de confianza el diagnóstico queda marcado como no concluyente
              (HU-04 CA-03), igual que cuando lo emita el modelo.
            </div>
          </>
        )}
      </Card>
    </>
  );
}
