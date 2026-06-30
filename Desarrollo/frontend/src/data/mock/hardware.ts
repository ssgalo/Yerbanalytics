/* ============================================================
   Flota de hardware de fábrica (HU-18 / HU-21). Determinística: refleja el
   seed del backend (data.sql) e incluye un caso de cada estado. La derivación
   de estado/KPIs/incompletos y el dedupe espejan a `HardwareService` para que
   el mock se comporte igual que el backend real.
   ============================================================ */
import type {
  Dispositivo,
  EstadoHardware,
  HardwareData,
  NuevoDispositivo,
  SectorIncompleto,
} from '@/types/domain';

export const NODO_TESTIGO = 'nodo_testigo';
export const ELECTROVALVULA = 'electrovalvula';
export const BOMBA = 'bomba_peristaltica';
export const MEDIASOMBRA = 'mediasombra';

/** Macro-zonas disponibles del vivero (alineadas con la topología; por defecto el seed 6×100). */
export let ZONA_IDS = ['MZ-1', 'MZ-2', 'MZ-3', 'MZ-4', 'MZ-5', 'MZ-6'];

/**
 * Ajusta las macro-zonas disponibles tras una (re)generación de la topología (HU-18 CA-01),
 * para que el alta de hardware valide y ofrezca las zonas reales del vivero. El backend
 * valida server-side contra la base; el mock mantiene esta lista en sincronía.
 */
export function setZonasDisponibles(macroZonas: number): void {
  ZONA_IDS = Array.from({ length: Math.max(0, macroZonas) }, (_, i) => `MZ-${i + 1}`);
}

/** Tipos seleccionables en el alta, con su etiqueta. */
export const TIPO_OPTIONS: { value: string; label: string }[] = [
  { value: NODO_TESTIGO, label: 'Nodo sensor testigo' },
  { value: ELECTROVALVULA, label: 'Electroválvula' },
  { value: BOMBA, label: 'Bomba peristáltica' },
  { value: MEDIASOMBRA, label: 'Mediasombra' },
];

const TIPO_LABEL: Record<string, string> = Object.fromEntries(
  TIPO_OPTIONS.map((t) => [t.value, t.label]),
);

/** Actuadores que un sector debe tener para considerarse completo (HU-18 CA-04). */
const ACTUADORES_REQUERIDOS = [ELECTROVALVULA, BOMBA, MEDIASOMBRA];

// Umbrales (espejo de application.properties; el mock usa los valores de producción).
const BATERIA_MIN = 20;
const INTERMITENTE_MS = 2 * 60 * 60 * 1000; // 2 h
const CRITICO_MS = 24 * 60 * 60 * 1000; // 24 h

const ESTADO_LABEL: Record<EstadoHardware, string> = {
  operativo: 'Operativo',
  intermitente: 'Señal intermitente',
  fuera_de_servicio: 'Fuera de servicio',
};
const ESTADO_COLOR: Record<EstadoHardware, { soft: string; ink: string }> = {
  operativo: { soft: '#E7F1EA', ink: '#2E7A4F' },
  intermitente: { soft: '#FBF0DC', ink: '#A66A12' },
  fuera_de_servicio: { soft: '#FBE6E0', ink: '#A8331C' },
};

/** Registro crudo de un dispositivo (lo que persistiría el backend). */
export interface RawDispositivo {
  id: string;
  serial: string;
  tipo: string;
  zonaId: string | null;
  sectorId: string | null;
  bateria: number | null;
  senal: number | null;
  ultimoUpdate: number | null;
  falla: string | null;
}

const MIN = 60 * 1000;
const HOUR = 60 * MIN;

/** Construye la flota de fábrica. `now` permite estados relativos reproducibles. */
export function buildFleet(now: number = Date.now()): RawDispositivo[] {
  return [
    // Nodos testigo (1 por macro-zona). Casos: MZ-2 batería baja · MZ-3 intermitente · MZ-4 caído.
    nodo('DEV-001', 'A4:CF:12:9A:00:01', 'MZ-1', 88, -62, now - 20 * 1000),
    nodo('DEV-002', 'A4:CF:12:9A:00:02', 'MZ-2', 16, -78, now - 30 * 1000),
    nodo('DEV-003', 'A4:CF:12:9A:00:03', 'MZ-3', 75, -55, now - 3 * HOUR),
    nodo('DEV-004', 'A4:CF:12:9A:00:04', 'MZ-4', 90, -60, now - 26 * HOUR),
    nodo('DEV-005', 'A4:CF:12:9A:00:05', 'MZ-5', 64, -70, now - 45 * 1000),
    nodo('DEV-006', 'A4:CF:12:9A:00:06', 'MZ-6', 82, -66, now - 50 * 1000),
    // MZ-1-001 y MZ-1-002: completos.
    act('DEV-101', 'EV-1-001', ELECTROVALVULA, 'MZ-1-001', null),
    act('DEV-102', 'BP-1-001', BOMBA, 'MZ-1-001', null),
    act('DEV-103', 'MS-1-001', MEDIASOMBRA, 'MZ-1-001', null),
    act('DEV-104', 'EV-1-002', ELECTROVALVULA, 'MZ-1-002', null),
    act('DEV-105', 'BP-1-002', BOMBA, 'MZ-1-002', null),
    act('DEV-106', 'MS-1-002', MEDIASOMBRA, 'MZ-1-002', null),
    // MZ-1-003: incompleto (falta la bomba peristáltica).
    act('DEV-107', 'EV-1-003', ELECTROVALVULA, 'MZ-1-003', null),
    act('DEV-108', 'MS-1-003', MEDIASOMBRA, 'MZ-1-003', null),
    // MZ-3-010: incompleto (faltan bomba y mediasombra).
    act('DEV-109', 'EV-3-010', ELECTROVALVULA, 'MZ-3-010', null),
    // MZ-5-042: completo pero con la electroválvula averiada (HU-21 CA-04).
    act('DEV-110', 'EV-5-042', ELECTROVALVULA, 'MZ-5-042', 'Falla Hidráulica'),
    act('DEV-111', 'BP-5-042', BOMBA, 'MZ-5-042', null),
    act('DEV-112', 'MS-5-042', MEDIASOMBRA, 'MZ-5-042', null),
  ];
}

function nodo(id: string, serial: string, zonaId: string, bateria: number, senal: number, ultimoUpdate: number): RawDispositivo {
  return { id, serial, tipo: NODO_TESTIGO, zonaId, sectorId: null, bateria, senal, ultimoUpdate, falla: null };
}

function act(id: string, serial: string, tipo: string, sectorId: string, falla: string | null): RawDispositivo {
  return { id, serial, tipo, zonaId: null, sectorId, bateria: null, senal: null, ultimoUpdate: null, falla };
}

/** Deriva el estado operativo (la falla manda; el nodo testigo usa el watchdog). */
function deriveEstado(r: RawDispositivo, now: number): EstadoHardware {
  if (r.falla) return 'fuera_de_servicio';
  if (r.tipo === NODO_TESTIGO) {
    if (r.ultimoUpdate === null || now - r.ultimoUpdate > CRITICO_MS) return 'fuera_de_servicio';
    if (now - r.ultimoUpdate > INTERMITENTE_MS) return 'intermitente';
    return 'operativo';
  }
  return 'operativo'; // actuadores sin heartbeat propio
}

function formatAgo(ts: number, now: number): string {
  const diffSec = Math.max(0, Math.floor((now - ts) / 1000));
  if (diffSec < 60) return `hace ${diffSec} s`;
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `hace ${diffMin} min`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `hace ${diffH} h`;
  return `hace ${Math.floor(diffH / 24)} d`;
}

function zonaIdDe(sectorId: string): string {
  const last = sectorId.lastIndexOf('-');
  return last > 0 ? sectorId.slice(0, last) : sectorId;
}

/** Nombre de presentación de una macro-zona derivado de su id (`MZ-3` → `Macro-zona 3`). */
function zonaNameDe(zonaId: string): string {
  const m = /^MZ-(\d+)$/.exec(zonaId);
  return m ? `Macro-zona ${m[1]}` : zonaId;
}

/** Deriva la vista de un dispositivo (estado, etiquetas, colores). */
export function toDispositivo(r: RawDispositivo, now: number = Date.now()): Dispositivo {
  const esNodo = r.tipo === NODO_TESTIGO;
  const estado = deriveEstado(r, now);
  const color = ESTADO_COLOR[estado];
  return {
    id: r.id,
    serial: r.serial,
    tipo: r.tipo,
    tipoLabel: TIPO_LABEL[r.tipo] ?? r.tipo,
    zonaId: r.zonaId,
    sectorId: r.sectorId,
    ubicacion: r.sectorId ?? r.zonaId ?? '—',
    bateria: r.bateria,
    senal: r.senal,
    ultimoUpdate: r.ultimoUpdate,
    ultimoUpdateLabel:
      r.ultimoUpdate !== null ? formatAgo(r.ultimoUpdate, now) : esNodo ? 'Sin reportes' : '—',
    estado,
    estadoLabel: ESTADO_LABEL[estado],
    estadoSoft: color.soft,
    estadoInk: color.ink,
    bateriaBaja: r.bateria !== null && r.bateria < BATERIA_MIN,
    falla: r.falla,
  };
}

/** Sectores con al menos un dispositivo pero faltando actuadores requeridos (HU-18 CA-04). */
function sectoresIncompletos(fleet: RawDispositivo[]): SectorIncompleto[] {
  const tiposPorSector = new Map<string, Set<string>>();
  for (const r of fleet) {
    if (!r.sectorId) continue;
    if (!tiposPorSector.has(r.sectorId)) tiposPorSector.set(r.sectorId, new Set());
    tiposPorSector.get(r.sectorId)!.add(r.tipo);
  }
  const out: SectorIncompleto[] = [];
  for (const [sectorId, tipos] of tiposPorSector) {
    const faltantes = ACTUADORES_REQUERIDOS.filter((t) => !tipos.has(t)).map((t) => TIPO_LABEL[t]);
    if (faltantes.length > 0) {
      out.push({ sectorId, zonaName: zonaNameDe(zonaIdDe(sectorId)), faltantes });
    }
  }
  return out.sort((a, b) => a.sectorId.localeCompare(b.sectorId));
}

/** Arma el `HardwareData` derivado a partir de la flota cruda. */
export function buildHardware(fleet: RawDispositivo[], now: number = Date.now()): HardwareData {
  const dispositivos = [...fleet]
    .sort((a, b) => a.id.localeCompare(b.id))
    .map((r) => toDispositivo(r, now));
  return {
    dispositivos,
    total: dispositivos.length,
    operativos: dispositivos.filter((d) => d.estado === 'operativo').length,
    bateriaBaja: dispositivos.filter((d) => d.bateriaBaja).length,
    fueraDeServicio: dispositivos.filter((d) => d.estado === 'fuera_de_servicio').length,
    averiados: dispositivos.filter((d) => d.falla !== null).length,
    incompletos: sectoresIncompletos(fleet),
  };
}

/** Valida un alta igual que el backend; devuelve el mensaje de error o null. */
export function altaError(fleet: RawDispositivo[], nuevo: NuevoDispositivo): string | null {
  const serial = nuevo.serial?.trim();
  if (!serial) return 'El serial/MAC es obligatorio.';
  if (!TIPO_LABEL[nuevo.tipo]) return 'Tipo de dispositivo inválido.';
  if (fleet.some((d) => d.serial === serial)) {
    return `Ya existe un dispositivo con el serial/MAC «${serial}».`;
  }
  if (nuevo.tipo === NODO_TESTIGO) {
    const zonaId = nuevo.zonaId?.trim();
    if (!zonaId) return 'El nodo testigo debe asociarse a una macro-zona.';
    if (!ZONA_IDS.includes(zonaId)) return `La macro-zona «${zonaId}» no existe.`;
    if (fleet.some((d) => d.tipo === NODO_TESTIGO && d.zonaId === zonaId)) {
      return `La macro-zona «${zonaId}» ya tiene un nodo testigo.`;
    }
  } else {
    const sectorId = nuevo.sectorId?.trim();
    if (!sectorId) return 'El actuador debe asociarse a un sector.';
    if (fleet.some((d) => d.tipo === nuevo.tipo && d.sectorId === sectorId)) {
      return `El sector «${sectorId}» ya tiene asignada una ${TIPO_LABEL[nuevo.tipo]}.`;
    }
  }
  return null;
}

/** Siguiente id `DEV-###` libre. */
export function nextDeviceId(fleet: RawDispositivo[]): string {
  let max = 0;
  for (const d of fleet) {
    const m = /^DEV-(\d+)$/.exec(d.id);
    if (m) max = Math.max(max, Number(m[1]));
  }
  return `DEV-${String(max + 1).padStart(3, '0')}`;
}

/** Agrega un dispositivo validado a la flota (muta y devuelve el nuevo registro). */
export function altaDispositivo(fleet: RawDispositivo[], nuevo: NuevoDispositivo): RawDispositivo {
  const error = altaError(fleet, nuevo);
  if (error) throw new Error(error);
  const esNodo = nuevo.tipo === NODO_TESTIGO;
  const raw: RawDispositivo = {
    id: nextDeviceId(fleet),
    serial: nuevo.serial.trim(),
    tipo: nuevo.tipo,
    zonaId: esNodo ? (nuevo.zonaId?.trim() ?? null) : null,
    sectorId: esNodo ? null : (nuevo.sectorId?.trim() ?? null),
    bateria: null,
    senal: null,
    ultimoUpdate: null,
    falla: null,
  };
  fleet.push(raw);
  return raw;
}

/** Recambia un dispositivo: pieza nueva, avería limpia, heartbeat reiniciado (HU-21 CA-05). */
export function recambioDispositivo(fleet: RawDispositivo[], id: string, nuevo: NuevoDispositivo): void {
  const raw = fleet.find((d) => d.id === id);
  if (!raw) throw new Error(`El dispositivo «${id}» no existe.`);
  const nuevoSerial = nuevo.serial?.trim();
  if (!nuevoSerial) throw new Error('El serial/MAC de la pieza nueva es obligatorio.');
  if (nuevoSerial !== raw.serial && fleet.some((d) => d.serial === nuevoSerial)) {
    throw new Error(`Ya existe un dispositivo con el serial/MAC «${nuevoSerial}».`);
  }
  raw.serial = nuevoSerial;
  raw.falla = null;
  raw.bateria = null;
  raw.senal = null;
  raw.ultimoUpdate = null;
}
