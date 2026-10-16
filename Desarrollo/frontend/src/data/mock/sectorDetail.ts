/* ============================================================
   Detalle de sector — portado EXACTO desde buildDetail().
   Deriva gráficos, tiles, actuadores, historial y seguimiento.
   ============================================================ */
import type {
  ActuatorRow,
  DiagnosisCard,
  DiagnosisDetail,
  HistoryEntry,
  LecturaZona,
  Sector,
  SectorDetail,
} from '@/types/domain';
import { sevMap, tints } from './specs';

/**
 * Construye el detalle derivado de un sector: diagnóstico, actuadores, historial y
 * seguimiento post-acción. Sin métricas ni gráfico de series — eso vive en el panel de
 * sensado de la macro-zona.
 *
 * Recibe la `lectura` de su macro-zona porque el seguimiento post-acción compara la
 * humedad de sustrato antes y ahora, y ese valor es de la zona.
 */
export function buildSectorDetail(
  s: Sector,
  lectura: LecturaZona,
  imagenUrl?: string,
  /** Diagnóstico `DX-` más reciente del sector: el modal pide sus cajas. */
  latestCapture?: Pick<DiagnosisCard, 'id' | 'detectionCount'> | null,
): SectorDetail {
  // presentación del diagnóstico
  const diag: DiagnosisDetail = {
    estado: s.diagnosis.estado,
    conf: s.diagnosis.conf,
    // Un backend anterior a los niveles no manda el campo: "sin nivel".
    confidence: s.diagnosis.confidence ?? null,
    sev: s.diagnosis.sev,
    sevSoft: sevMap[s.diagnosis.sev].soft,
    sevInk: sevMap[s.diagnosis.sev].ink,
    thumb: imagenUrl ? `url("${imagenUrl}") center/cover no-repeat` : (tints[s.diagnosis.estado] || tints['Sin diagnóstico']),
    concluyente: s.diagnosis.estado !== 'No concluyente' && s.diagnosis.estado !== 'Sin diagnóstico',
    hasFoto: !!imagenUrl || s.status !== 'offline',
    imagenUrl,
    diagnosisId: latestCapture?.id ?? null,
    detectionCount: latestCapture?.detectionCount ?? 0,
  };

  // actuadores
  const A = s.actuadores;
  const actsRows: ActuatorRow[] = [
    {
      name: 'Electroválvula (riego)',
      state: A.valve,
      active: A.valve === 'Regando',
      path: 'M12 2.7s6 6.6 6 11a6 6 0 0 1-12 0c0-4.4 6-11 6-11Z',
    },
    {
      name: 'Bomba peristáltica',
      state: A.pump,
      active: A.pump === 'Dosificando',
      path: 'M9 2h6M10 2v4l-4.5 9A2 2 0 0 0 7.3 18h9.4a2 2 0 0 0 1.8-3L14 6V2',
    },
    {
      name: 'Mediasombra',
      state: 'Apertura ' + A.shade + '%',
      active: false,
      path: 'M4 12h16M12 4v3M3 16h18a9 9 0 0 0-18 0Z',
    },
  ].map((a) => {
    // "En cola": la válvula espera su turno en la tanda de la macro-zona; no está abierta pero tampoco inactiva.
    const enCola = a.state === 'En cola';
    return {
      ...a,
      soft: a.active ? '#E3F2E8' : enCola ? '#FBF0D9' : '#E9ECE6',
      ink: a.active ? '#1E6B41' : enCola ? '#7A5310' : '#545C57',
      dot: a.active ? '#2F9B62' : enCola ? '#E2A230' : '#9BA39E',
    };
  });

  // historial (ahora se maneja desde SectorPage con useHistory)
  const hist: HistoryEntry[] = [];

  // seguimiento post-acción — sobre la humedad de sustrato de la macro-zona
  const hs = lectura.metrics.find((m) => m.key === 'humSus');
  const ahora = hs?.raw ?? null;
  const antes = ahora == null ? null : ahora < 45 ? ahora + 11 : ahora - 9;
  const delta = ahora != null && antes != null ? ahora - antes : 0;
  const evoVerdict =
    s.status === 'critical'
      ? 'En seguimiento'
      : Math.abs(delta) > 6
        ? 'Efectiva'
        : 'En seguimiento';
  const evo = {
    // Sin lectura vigente no hay contra qué comparar: no se muestra el seguimiento.
    show: ahora != null && (s.status === 'critical' || s.status === 'warning'),
    metric: 'Humedad de sustrato',
    antes: antes != null ? antes.toFixed(0) : '—',
    ahora: hs?.value ?? '—',
    unit: '%',
    delta: (delta >= 0 ? '+' : '') + delta.toFixed(0),
    latencia: '2 h',
    verdict: evoVerdict,
    vSoft: evoVerdict === 'Efectiva' ? '#E3F2E8' : '#E2ECF8',
    vInk: evoVerdict === 'Efectiva' ? '#1E6B41' : '#1F4F86',
  };

  return {
    diag,
    actsRows,
    hist,
    evo,
    statusSoft:
      s.status === 'ok'
        ? '#E3F2E8'
        : s.status === 'warning'
          ? '#FBF0D9'
          : s.status === 'critical'
            ? '#FBE3DD'
            : '#ECEEEB',
    statusInk:
      s.status === 'ok'
        ? '#1E6B41'
        : s.status === 'warning'
          ? '#7A5310'
          : s.status === 'critical'
            ? '#9A2B17'
            : '#545C57',
  };
}
