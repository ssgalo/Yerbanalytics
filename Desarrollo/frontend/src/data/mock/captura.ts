/* ============================================================
   Simulación en memoria del pipeline de captura (VITE_DATA_SOURCE=mock).

   Sin backend no hay dispositivo, así que se finge uno: la orden pasa de PENDIENTE a
   ENTREGADA y a RECIBIDA con el paso del tiempo, y la "fotografía" es un SVG generado al
   vuelo. Alcanza para desarrollar el panel sin levantar el backend ni el teléfono.

   Lo que NO se finge es la forma: los mismos estados, los mismos campos y las mismas
   validaciones que el backend real, para que el panel no descubra diferencias al cambiar
   VITE_DATA_SOURCE.
   ============================================================ */
import type {
  CodigoVinculacion,
  DiagnosticoRegistrado,
  DispositivoCamara,
  NuevaOrdenCaptura,
  NuevoDiagnostico,
  OrdenCaptura,
} from '@/types/domain';

/** Cuánto tarda la "cámara" simulada en responder una orden. */
const MS_HASTA_ENTREGADA = 400;
const MS_HASTA_RECIBIDA = 1600;

/** Taxonomía aceptada, espejo de `DiagnosticoService.ESTADOS` del backend. */
export const ESTADOS_DIAGNOSTICO = [
  'Sano',
  'Clorosis',
  'Estrés solar',
  'Daño biótico',
  'No concluyente',
  'Ácaro',
  'Plaga foliar',
  'Daño fúngico',
];

export class CapturaMock {
  private ordenes = new Map<string, OrdenCaptura & { pedidaEn: number }>();
  private imagenes = new Map<string, string>();
  private diagnosticos: DiagnosticoRegistrado[] = [];
  private secuencia = 0;

  private readonly dispositivo: DispositivoCamara = {
    id: 'CAM-001',
    nombre: 'iPhone simulado',
    plataforma: 'mock',
    estado: 'operativo',
    estadoLabel: 'Operativo',
    estadoSoft: '#E7F1EA',
    estadoInk: '#2E7A4F',
    ultimoHeartbeat: Date.now(),
    ultimoHeartbeatAgo: 'hace 1 s',
    capturaListo: true,
    capturasOk: 0,
    capturasError: 0,
  };

  async getDispositivos(): Promise<DispositivoCamara[]> {
    return [{ ...this.dispositivo, ultimoHeartbeat: Date.now(), ultimoHeartbeatAgo: 'hace 1 s' }];
  }

  async generarCodigo(): Promise<CodigoVinculacion> {
    const alfabeto = '23456789ACDEFGHJKLMNPQRSTUVWXYZ';
    const parte = () =>
      Array.from({ length: 4 }, () => alfabeto[Math.floor(Math.random() * alfabeto.length)]).join('');
    return { codigo: `${parte()}-${parte()}`, expiraEn: Date.now() + 10 * 60_000 };
  }

  async emitirOrden(input: NuevaOrdenCaptura, zonaId: string): Promise<OrdenCaptura> {
    if (!input.sectorId) throw new Error('El sector es obligatorio.');
    if (input.posicionRiel == null) throw new Error('La posición de riel es obligatoria.');

    const ahora = Date.now();
    const orden: OrdenCaptura & { pedidaEn: number } = {
      ordenId: `mock-${++this.secuencia}-${ahora.toString(36)}`,
      sectorId: input.sectorId,
      zonaId,
      posicionRiel: input.posicionRiel,
      estado: 'PENDIENTE',
      intentos: 1,
      motivoFallo: null,
      detalleFallo: null,
      capturaId: null,
      imagenUrl: null,
      creadaEn: ahora,
      entregadaEn: null,
      venceEn: ahora + 60_000,
      pedidaEn: ahora,
    };
    this.ordenes.set(orden.ordenId, orden);
    return this.sinInternos(orden);
  }

  async getOrden(ordenId: string): Promise<OrdenCaptura> {
    const orden = this.ordenes.get(ordenId);
    if (!orden) throw new Error(`La orden '${ordenId}' no existe.`);

    const transcurrido = Date.now() - orden.pedidaEn;
    if (transcurrido >= MS_HASTA_RECIBIDA && orden.estado !== 'RECIBIDA') {
      const capturaId = `CAP-${String(this.secuencia).padStart(6, '0')}`;
      orden.estado = 'RECIBIDA';
      orden.capturaId = capturaId;
      orden.imagenUrl = this.fabricarImagen(capturaId, orden);
      this.imagenes.set(capturaId, orden.imagenUrl);
      this.dispositivo.capturasOk++;
    } else if (transcurrido >= MS_HASTA_ENTREGADA && orden.estado === 'PENDIENTE') {
      orden.estado = 'ENTREGADA';
      orden.entregadaEn = Date.now();
    }
    return this.sinInternos(orden);
  }

  async crearDiagnostico(input: NuevoDiagnostico): Promise<DiagnosticoRegistrado> {
    // Mismas validaciones que el backend: sin ellas el panel se comportaría distinto según
    // el origen de datos, y las diferencias se descubrirían recién contra el backend real.
    if (!input.capturaId) {
      throw new Error('La captura es obligatoria: todo diagnóstico nace del análisis de una imagen.');
    }
    const imagenUrl = this.imagenes.get(input.capturaId);
    if (!imagenUrl) throw new Error(`La captura '${input.capturaId}' no existe.`);
    if (!ESTADOS_DIAGNOSTICO.includes(input.estado)) {
      throw new Error(`Estado fuera de la taxonomía. Los válidos son: ${ESTADOS_DIAGNOSTICO.join(', ')}`);
    }
    if (input.conf == null || input.conf < 0 || input.conf > 100) {
      throw new Error('El nivel de confianza debe ser un porcentaje entre 0 y 100.');
    }

    const orden = [...this.ordenes.values()].find((o) => o.capturaId === input.capturaId);
    const d: DiagnosticoRegistrado = {
      id: `DX-${String(this.diagnosticos.length + 1).padStart(5, '0')}`,
      sectorId: input.sectorId || orden?.sectorId || '',
      zonaId: input.zonaId || orden?.zonaId || '',
      capturaId: input.capturaId,
      imagenUrl,
      estado: input.estado,
      conf: input.conf,
      sev: input.sev || '—',
      concluyente: input.conf >= 85,
      creadoEn: Date.now(),
    };
    this.diagnosticos.unshift(d);
    return d;
  }

  /** Los diagnósticos cargados, para que el mock los mezcle en el snapshot como el backend. */
  listarDiagnosticos(): DiagnosticoRegistrado[] {
    return this.diagnosticos;
  }

  /** Copia sin `pedidaEn`, que es contabilidad interna del mock y no existe en el DTO real. */
  private sinInternos(o: OrdenCaptura & { pedidaEn: number }): OrdenCaptura {
    return {
      ordenId: o.ordenId,
      sectorId: o.sectorId,
      zonaId: o.zonaId,
      posicionRiel: o.posicionRiel,
      estado: o.estado,
      intentos: o.intentos,
      motivoFallo: o.motivoFallo,
      detalleFallo: o.detalleFallo,
      capturaId: o.capturaId,
      imagenUrl: o.imagenUrl,
      creadaEn: o.creadaEn,
      entregadaEn: o.entregadaEn,
      venceEn: o.venceEn,
    };
  }

  /**
   * "Fotografía" simulada: un SVG con la identificación de la captura, embebido como data
   * URI. No pretende parecerse a un plantín — pretende ser inconfundiblemente una imagen de
   * prueba, para que nadie la tome por una captura real.
   */
  private fabricarImagen(capturaId: string, orden: OrdenCaptura): string {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="480" height="360">
      <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="#2E7A4F"/><stop offset="1" stop-color="#12301F"/>
      </linearGradient></defs>
      <rect width="480" height="360" fill="url(#g)"/>
      <circle cx="240" cy="150" r="62" fill="none" stroke="rgba(255,255,255,.45)" stroke-width="2"/>
      <text x="240" y="158" text-anchor="middle" fill="rgba(255,255,255,.85)"
            font-family="monospace" font-size="17">${capturaId}</text>
      <text x="240" y="262" text-anchor="middle" fill="rgba(255,255,255,.7)"
            font-family="sans-serif" font-size="15">${orden.sectorId} · riel ${orden.posicionRiel}</text>
      <text x="240" y="288" text-anchor="middle" fill="rgba(255,255,255,.45)"
            font-family="sans-serif" font-size="12">captura simulada (sin backend)</text>
    </svg>`;
    return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
  }
}
