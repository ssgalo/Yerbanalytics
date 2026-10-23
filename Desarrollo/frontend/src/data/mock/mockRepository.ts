/* ============================================================
   Repositorio mock: genera el vivero determinístico una sola vez
   y lo cachea. Implementa el mismo contrato que el backend real.
   ============================================================ */
import type { DataRepository, FiltroHistorial } from '@/data/repository';
import { ParametrosInvalidosError } from '@/data/parametrosError';
import { PasadaRechazadaError } from '@/data/pasadaError';
import { SecuenciaRechazadaError } from '@/data/secuenciaError';
import { ManualLockError } from '@/data/manualLockError';
import { TankLoadError } from '@/data/tankError';
import { NutritionPlanError } from '@/data/nutritionPlanError';
import { PlanRustificacionError } from '@/data/planRustificacionError';
import type {
  ActionRecord,
  CambioParametro,
  CambioEstadoRegla,
  CatalogoReglas,
  Configuracion,
  DagSchema,
  Detection,
  DiagnosisCriteria,
  DisposicionTopologia,
  HardwareData,
  NurseryData,
  NuevaTopologia,
  NuevoDispositivo,
  OrigenEvaluacion,
  ParametrosSecuencia,
  Pasada,
  Secuencia,
  TipoSecuencia,
  TopologiaVivero,
  TrazaEvaluacion,
  ManualLock,
  NewManualLock,
  NewTankLoad,
  Supply,
  TankLevel,
  NutritionPlan,
  NuevoPlanRustificacion,
  PlanRustificacion,
  ReglaCatalogo,
} from '@/types/domain';
import { validateConfig } from '@/lib/configValidation';
import { criteriaErrors, defaultDiagnosisCriteria } from '@/lib/diagnosisCriteria';
import { mockDetections } from './detections';
import { addDays, todayIso } from '@/lib/horario';
import { ETAPAS_SUGERIDAS, planErrors, planVigente, progreso } from '@/lib/planRustificacion';
import { buildConfig, mockStaleThresholdSeconds } from './config';
import { buildNursery, type TopologiaGrid } from './generators';
import { buildHistory, buildRuleStateEvent } from './history';
import { buildMockNutritionPlan } from './nutritionPlanMock';
import { nutritionIntervalError } from '@/lib/nutritionPlan';
import {
  buildRuleSchema,
  catalogoVigente,
  soloReglasDeLaCadena,
  validarCambioEstado,
  validarCambios,
  type EstadoRegla,
  type OverrideParametro,
} from './reglasMock';
import { evaluarMotor } from './trazaReglas';
import { simularPasada } from './pasadaMock';
import { simularSecuencia } from './secuenciaMock';
import {
  altaDispositivo,
  buildFleet,
  buildHardware,
  recambioDispositivo,
  repararDispositivo,
  setZonasDisponibles,
  type RawDispositivo,
} from './hardware';
import {
  clampDisposicion,
  DEFAULT_MACRO_ZONAS,
  DEFAULT_MACRO_ZONAS_POR_FILA,
  DEFAULT_SECTORES_POR_FILA,
  DEFAULT_SECTORES_POR_MACRO_ZONA,
  disposicionError,
  topologiaError,
  topologiaSummary,
} from './topologia';

export class MockRepository implements DataRepository {
  private cache: NurseryData | null = null;
  private historyCache: ActionRecord[] | null = null;
  private configCache: Configuracion | null = null;
  private fleetCache: RawDispositivo[] | null = null;
  /** Topología generada por el Administrador; null = grilla demo por defecto (HU-18 CA-01). */
  private topologiaOverride: TopologiaGrid | null = null;
  /** Disposición visual elegida por el Administrador; null = defaults (HU-18 CA-01). */
  private disposicionOverride: DisposicionTopologia | null = null;
  /** Planes de rustificación por macro-zona (el último de cada una); null = sin sembrar todavía. */
  private planes: Map<string, PlanRustificacion> | null = null;
  private manualLocks: ManualLock[] = [];
  private nextLockId = 1;
  /** Tanques de insumo de la sesión; arranca sin ninguno registrado, como un backend recién instalado. */
  private tanks: TankLevel[] = [];
  /** Intervalos del plan de nutrición que el usuario fijó en la sesión, por macro-zona; sin entrada rige el general. */
  private readonly nutritionIntervals = new Map<string, number>();
  /** Parámetros del motor editados en la sesión; vacío = valores de fábrica. */
  private readonly parametrosEditados = new Map<string, OverrideParametro>();
  /** Interruptores de las reglas cambiados en la sesión; sin entrada rige el estado de fábrica (las retiradas apagadas). */
  private readonly estadosReglas = new Map<string, EstadoRegla>();

  /** Criterio de diagnóstico guardado en la sesión; arranca con el de fábrica, como el backend. */
  private diagnosisCriteria: DiagnosisCriteria = defaultDiagnosisCriteria();
  /** Inicio de la última pasada simulada y, si se canceló, cuándo; null = no hubo ninguna. */
  private pasadaInicioMs: number | null = null;
  private pasadaCanceladaMs: number | null = null;
  /** La última secuencia simulada: qué es, con qué parámetros, cuándo empezó y si se canceló. */
  private secuencia: {
    tipo: TipoSecuencia;
    parametros: ParametrosSecuencia;
    inicioMs: number;
    canceladaMs: number | null;
    lectura: Record<string, number | null> | undefined;
  } | null = null;

  constructor(private readonly seed: number) {}

  async getNursery(): Promise<NurseryData> {
    if (!this.cache) {
      this.cache = buildNursery(
        this.seed,
        this.topologiaOverride ?? undefined,
        this.disposicionOverride ?? undefined,
      );
      const now = Date.now();
      this.cache.zonas.forEach((z, i) => {
        z.nutritionPlan = buildMockNutritionPlan(i, this.nutritionIntervals.get(z.id) ?? null, now);
      });
    }
    return this.cache;
  }

  async getHistory(filtro: FiltroHistorial = {}): Promise<ActionRecord[]> {
    if (!this.historyCache) {
      this.historyCache = buildHistory(this.seed);
    }
    const { tipo, sector, desde, hasta, limit } = filtro;
    const filtrado =
      tipo == null && sector == null && desde == null && hasta == null
        ? this.historyCache
        : this.historyCache.filter(
            (h) =>
              (tipo == null || h.tipo === tipo) &&
              (sector == null || h.sectorId === sector) &&
              (desde == null || h.ts >= desde) &&
              (hasta == null || h.ts <= hasta),
          );
    return limit == null ? filtrado : filtrado.slice(0, limit);
  }

  async getConfig(): Promise<Configuracion> {
    if (!this.configCache) {
      this.configCache = buildConfig();
    }
    return structuredClone(this.configCache);
  }

  async saveConfig(config: Configuracion): Promise<Configuracion> {
    // Mismo comportamiento que el backend: rechaza configuraciones inválidas (HU-15 CA-03).
    const errors = validateConfig(config);
    if (errors.length > 0) {
      throw new Error(errors[0]);
    }
    const saved: Configuracion = {
      ...structuredClone(config),
      operativa: {
        ...config.operativa,
        updatedBy: 'Ingeniero Agrónomo',
        updatedTs: Date.now(),
        // The real backend computes this read-only value; the mock stands in for it so the
        // UI hint refreshes on save exactly like against the real API.
        effectiveStaleThresholdSeconds: mockStaleThresholdSeconds(config.operativa.intervaloSensadoMinutos),
      },
    };
    this.configCache = saved;
    return structuredClone(saved);
  }

  async getDiagnosisCriteria(): Promise<DiagnosisCriteria> {
    return structuredClone(this.diagnosisCriteria);
  }

  async saveDiagnosisCriteria(criteria: DiagnosisCriteria): Promise<DiagnosisCriteria> {
    // Mismo comportamiento que el backend: un criterio inválido se rechaza entero, con su motivo.
    const errores = criteriaErrors(criteria);
    if (errores.length > 0) throw new Error(errores[0]);
    this.diagnosisCriteria = structuredClone(criteria);
    return structuredClone(this.diagnosisCriteria);
  }

  async getDiagnosisDetections(diagnosisId: string): Promise<Detection[]> {
    const card = (await this.getNursery()).diagById[diagnosisId];
    // Igual que el backend: un id que no existe es un 404. (La demo no tiene tarjetas `DG-`.)
    if (!card) {
      throw new Error(`El diagnóstico ${diagnosisId} no existe.`);
    }
    return mockDetections(card.id, card.estado, card.sev, card.conf);
  }

  /**
   * La demo arranca con un plan en curso en MZ-3 (empezó hace 9 días: etapa 2) para que se vea la
   * tarjeta con progreso; el resto de las zonas, sin plan.
   */
  private planesDemo(): Map<string, PlanRustificacion> {
    if (!this.planes) {
      const fechaInicio = addDays(todayIso(), -9);
      this.planes = new Map([
        [
          'MZ-3',
          {
            id: 'PR-1',
            zonaId: 'MZ-3',
            fechaInicio,
            etapas: ETAPAS_SUGERIDAS.map((e, i) => ({ ...e, orden: i + 1 })),
            estado: 'en_curso',
            creadoPor: 'Ana Benítez',
            creadoEn: Date.parse(`${fechaInicio}T09:00:00`),
            canceladoEn: null,
          },
        ],
      ]);
    }
    return this.planes;
  }

  /** El estado según las fechas, como lo recalcula el backend. */
  private conEstado(p: PlanRustificacion): PlanRustificacion {
    if (p.estado === 'cancelado') return p;
    const pr = progreso(p);
    // Pasada la última etapa sigue en curso: la última se sostiene hasta que alguien elimina el plan.
    return { ...p, estado: pr.dia === 0 ? 'programado' : 'en_curso' };
  }

  async getPlanRustificacion(zonaId: string): Promise<PlanRustificacion | null> {
    const p = this.planesDemo().get(zonaId);
    const actual = p ? this.conEstado(p) : null;
    return planVigente(actual) ? structuredClone(actual) : null;
  }

  async iniciarPlanRustificacion(zonaId: string, plan: NuevoPlanRustificacion): Promise<PlanRustificacion> {
    if (await this.getPlanRustificacion(zonaId)) {
      throw new PlanRustificacionError('La macro-zona ya tiene un plan de rustificación vigente.');
    }
    const errores = planErrors(plan);
    if (errores.length > 0) throw new PlanRustificacionError(errores[0]);
    const nuevo = this.conEstado({
      id: `PR-${Date.now()}`,
      zonaId,
      fechaInicio: plan.fechaInicio,
      etapas: plan.etapas.map((e, i) => ({ ...e, orden: i + 1 })),
      estado: 'programado',
      creadoPor: 'Ingeniero Agrónomo',
      creadoEn: Date.now(),
      canceladoEn: null,
    });
    this.planesDemo().set(zonaId, nuevo);
    return structuredClone(nuevo);
  }

  async cancelarPlanRustificacion(zonaId: string): Promise<PlanRustificacion> {
    const actual = await this.getPlanRustificacion(zonaId);
    if (!actual) throw new PlanRustificacionError('La macro-zona no tiene un plan de rustificación vigente.');
    const cancelado: PlanRustificacion = { ...actual, estado: 'cancelado', canceladoEn: Date.now() };
    this.planesDemo().set(zonaId, cancelado);
    return structuredClone(cancelado);
  }

  async getManualLocks(): Promise<ManualLock[]> {
    return structuredClone(this.manualLocks);
  }

  async activateManualLock(lock: NewManualLock): Promise<ManualLock> {
    const motivo = lock.motivo.trim();
    if (!motivo) throw new ManualLockError('El motivo del bloqueo es obligatorio.');
    const sectorId = lock.sectorId ?? null;
    const alcance = sectorId ? `El sector ${sectorId}` : `La macro-zona ${lock.zonaId}`;
    if (this.manualLocks.some((l) => l.zonaId === lock.zonaId && l.sectorId === sectorId)) {
      throw new ManualLockError(`${alcance} ya tiene un bloqueo vigente.`);
    }
    const creado: ManualLock = {
      id: this.nextLockId++,
      zonaId: lock.zonaId,
      sectorId,
      activadoPor: 'Usuario de la demo',
      activadoTs: Date.now(),
      motivo,
    };
    this.manualLocks = [creado, ...this.manualLocks];
    return structuredClone(creado);
  }

  async releaseManualLock(id: number): Promise<void> {
    if (!this.manualLocks.some((l) => l.id === id)) {
      throw new ManualLockError(`No hay un bloqueo vigente con id ${id}.`);
    }
    this.manualLocks = this.manualLocks.filter((l) => l.id !== id);
  }

  async getTanks(): Promise<TankLevel[]> {
    return structuredClone(this.tanks);
  }

  async loadTank(zoneId: string, supply: Supply, load: NewTankLoad): Promise<TankLevel> {
    if (!Number.isFinite(load.liters) || load.liters <= 0) {
      throw new TankLoadError('Los litros de la carga deben ser un número mayor que cero.');
    }
    const existing = this.tanks.find((t) => t.zoneId === zoneId && t.supply === supply);
    if (!existing && load.capacityLiters == null) {
      throw new TankLoadError('El tanque todavía no está registrado: indicá su capacidad en litros.');
    }
    const capacityMl = Math.round((load.capacityLiters ?? existing!.capacityMl / 1000) * 1000);
    const levelMl = (existing?.levelMl ?? 0) + Math.round(load.liters * 1000);
    if (levelMl > capacityMl) {
      throw new TankLoadError(
        `La carga no entra: el tanque quedaría en ${(levelMl / 1000).toFixed(1)} L y su capacidad es de ${(capacityMl / 1000).toFixed(1)} L.`,
      );
    }
    const levelPct = (levelMl * 100) / capacityMl;
    const tank: TankLevel = {
      zoneId,
      supply,
      supplyLabel: supply === 'FERTILIZER' ? 'Fertilizante' : 'Fitosanitario',
      capacityMl,
      levelMl,
      levelPct,
      low: levelPct < 20,
      updatedTs: Date.now(),
      updatedBy: 'Usuario de la demo',
    };
    this.tanks = [...this.tanks.filter((t) => t !== existing), tank].sort(
      (a, b) => a.zoneId.localeCompare(b.zoneId) || a.supply.localeCompare(b.supply),
    );
    return structuredClone(tank);
  }

  async setNutritionPlanInterval(zoneId: string, intervalDays: number): Promise<NutritionPlan> {
    const error = nutritionIntervalError(String(intervalDays));
    if (error) throw new NutritionPlanError(error);
    const nursery = await this.getNursery();
    const index = nursery.zonas.findIndex((z) => z.id === zoneId);
    if (index < 0) throw new NutritionPlanError(`La macro-zona ${zoneId} no existe.`);
    this.nutritionIntervals.set(zoneId, intervalDays);
    const plan = buildMockNutritionPlan(index, intervalDays, Date.now());
    nursery.zonas[index].nutritionPlan = plan;
    return structuredClone(plan);
  }

  private fleet(): RawDispositivo[] {
    if (!this.fleetCache) {
      this.fleetCache = buildFleet();
    }
    return this.fleetCache;
  }

  async getHardware(): Promise<HardwareData> {
    return buildHardware(this.fleet());
  }

  async registerDevice(device: NuevoDispositivo): Promise<HardwareData> {
    // Mismo comportamiento que el backend: valida unicidad antes de agregar (HU-18 CA-03).
    altaDispositivo(this.fleet(), device);
    return buildHardware(this.fleet());
  }

  async replaceDevice(id: string, device: NuevoDispositivo): Promise<HardwareData> {
    recambioDispositivo(this.fleet(), id, device);
    return buildHardware(this.fleet());
  }

  async registerRepair(id: string, detalle: string): Promise<HardwareData> {
    repararDispositivo(this.fleet(), id, detalle);
    return buildHardware(this.fleet());
  }

  /** Topología actual: la generada por el Administrador o, por defecto, el seed 6 × 100. */
  private topologia(): TopologiaGrid {
    return (
      this.topologiaOverride ?? {
        macroZonas: DEFAULT_MACRO_ZONAS,
        sectoresPorMacroZona: DEFAULT_SECTORES_POR_MACRO_ZONA,
      }
    );
  }

  /** Disposición visual actual, acotada a la grilla; defaults si no se configuró. */
  private disposicion(grid: TopologiaGrid): DisposicionTopologia {
    return {
      macroZonasPorFila: clampDisposicion(
        this.disposicionOverride?.macroZonasPorFila ?? DEFAULT_MACRO_ZONAS_POR_FILA,
        grid.macroZonas,
      ),
      sectoresPorFila: clampDisposicion(
        this.disposicionOverride?.sectoresPorFila ?? DEFAULT_SECTORES_POR_FILA,
        grid.sectoresPorMacroZona,
      ),
    };
  }

  async getTopologia(): Promise<TopologiaVivero> {
    const t = this.topologia();
    return topologiaSummary(t.macroZonas, t.sectoresPorMacroZona, this.disposicion(t));
  }

  async generarTopologia(input: NuevaTopologia): Promise<TopologiaVivero> {
    // Mismo comportamiento que el backend: valida rango y exige confirmación para regenerar
    // sobre una topología ya cargada (en el mock siempre hay grilla por el seed demo).
    const error = topologiaError(true, input);
    if (error) throw new Error(error);

    // La disposición del payload se guarda junto con la grilla, acotada a las nuevas cantidades.
    const disposicion: DisposicionTopologia = {
      macroZonasPorFila: clampDisposicion(
        input.macroZonasPorFila ?? DEFAULT_MACRO_ZONAS_POR_FILA,
        input.macroZonas,
      ),
      sectoresPorFila: clampDisposicion(
        input.sectoresPorFila ?? DEFAULT_SECTORES_POR_FILA,
        input.sectoresPorMacroZona,
      ),
    };
    this.topologiaOverride = {
      macroZonas: input.macroZonas,
      sectoresPorMacroZona: input.sectoresPorMacroZona,
    };
    this.disposicionOverride = disposicion;
    // Regenerar reemplaza la grilla y limpia las referencias colgantes (igual que el backend):
    // se reconstruye el vivero y se descarta la flota, y se ajustan las zonas disponibles.
    this.cache = null;
    this.fleetCache = [];
    setZonasDisponibles(input.macroZonas);

    return topologiaSummary(input.macroZonas, input.sectoresPorMacroZona, disposicion);
  }

  async guardarDisposicion(input: DisposicionTopologia): Promise<TopologiaVivero> {
    // Mismo comportamiento que el backend: valida rango contra la grilla actual y guarda sin
    // regenerar la grilla ni descartar la flota.
    const t = this.topologia();
    const error = disposicionError(input, t.macroZonas, t.sectoresPorMacroZona);
    if (error) throw new Error(error);

    this.disposicionOverride = {
      macroZonasPorFila: input.macroZonasPorFila,
      sectoresPorFila: input.sectoresPorFila,
    };
    // Solo se invalida el snapshot para que los paneles tomen la nueva disposición; la flota
    // y el resto del estado quedan intactos.
    this.cache = null;

    return topologiaSummary(t.macroZonas, t.sectoresPorMacroZona, this.disposicionOverride);
  }

  async getCatalogoReglas(): Promise<CatalogoReglas> {
    return catalogoVigente(this.parametrosEditados, this.estadosReglas);
  }

  async saveParametros(cambios: CambioParametro[]): Promise<CatalogoReglas> {
    // Mismo comportamiento que el backend: todo o nada, con la misma validación que la UI.
    const errores = validarCambios(cambios);
    if (errores.length > 0) throw new ParametrosInvalidosError(errores);

    const ahora = Date.now();
    for (const c of cambios) {
      if (c.valor === null) {
        this.parametrosEditados.delete(c.clave);
      } else {
        this.parametrosEditados.set(c.clave, {
          valor: c.valor,
          updatedBy: 'Ingeniero Agrónomo',
          updatedTs: ahora,
        });
      }
    }
    return catalogoVigente(this.parametrosEditados, this.estadosReglas);
  }

  async setEstadoRegla(nombre: string, cambio: CambioEstadoRegla): Promise<ReglaCatalogo> {
    const catalogo = await this.getCatalogoReglas();
    const regla = catalogo.reglas.find((r) => r.id === nombre);
    if (!regla) throw new Error(`Error 404: la regla ${nombre} no existe`);
    // Mismo orden que el backend: se valida antes de mirar si el estado cambia.
    validarCambioEstado(regla, cambio);
    // Mismo estado que el vigente: no escribe ni registra nada.
    if (regla.activa === cambio.activa) return regla;

    const motivo = cambio.motivo?.trim() || null;
    const usuario = 'Ingeniero Agrónomo';
    const ts = Date.now();
    this.estadosReglas.set(nombre, { activa: cambio.activa, motivo, updatedBy: usuario, updatedTs: ts });

    await this.getHistory(); // arma la caché antes de sumarle el evento
    this.historyCache!.unshift(
      buildRuleStateEvent({
        id: `HE-CFG-${ts}`,
        regla: nombre,
        etiqueta: regla.label,
        codigo: regla.codigo,
        activa: cambio.activa,
        usuario,
        motivo,
        consecuencia: !cambio.activa ? regla.proteccion : null,
        ts,
      }),
    );
    return (await this.getCatalogoReglas()).reglas.find((r) => r.id === nombre)!;
  }

  async getRuleSchema(): Promise<DagSchema> {
    return buildRuleSchema(this.estadosReglas);
  }

  async getTrazaEvaluacion(
    sectorId: string,
    origen: OrigenEvaluacion = 'TELEMETRIA',
  ): Promise<TrazaEvaluacion | null> {
    const nursery = await this.getNursery();
    const sector = nursery.byId[sectorId];
    if (!sector) throw new Error(`Error 404: el sector ${sectorId} no existe`);

    const zonaIdx = Math.max(
      0,
      nursery.zonas.findIndex((z) => z.id === sector.zona),
    );
    const zona = nursery.zonas[zonaIdx];
    const lecturaTs = zona.lectura.ts;
    const ref = lecturaTs ?? Date.now();

    // El pronóstico del mock es una grilla de franjas: cada macro-zona toma una distinta, así
    // la demo muestra tanto riego normal como riego pospuesto por lluvia.
    const slot = nursery.weather.forecast[zonaIdx % nursery.weather.forecast.length];

    const historial = await this.getHistory();
    const delSector = (tipo: string) =>
      historial.filter((h) => h.sectorId === sectorId && h.tipo === tipo && h.ts <= ref);
    const ultimo = (eventos: { ts: number }[]) =>
      eventos.reduce<number | null>((m, h) => (m === null || h.ts > m ? h.ts : m), null);
    const dosis24h = delSector('Insumo').filter((h) => h.ts > ref - 86_400_000).length;

    // El despacho riega de a tandas: los sectores que el mapa muestra "Regando" ya tienen su riego
    // despachado (y abierto) en este ciclo; los "En cola" todavía no. Así la demo muestra el corte
    // "ya regó en este ciclo" en unos y el riego por déficit en otros.
    const regando = sector.actuadores.valve === 'Regando';
    const ultimoRiegoMs = regando ? ref - 60_000 : ultimo(delSector('Riego'));

    return evaluarMotor(soloReglasDeLaCadena(await this.getCatalogoReglas()), {
      sectorId,
      zonaId: sector.zona,
      origen,
      // El motor corre con la lectura del pulso, apenas llega: lectura fresca y con métricas.
      ts: new Date(ref).toISOString(),
      antiguedadSeg: lecturaTs === null ? null : 1,
      bloqueoManual: false,
      humSus: zona.lectura.metrics.find((m) => m.key === 'humSus')?.raw ?? null,
      // En la demo, el diagnóstico de un sector con alguna patología es nuevo (lo trajo la última
      // pasada); el de uno sano ya se usó en un pulso anterior, y uno sin diagnóstico no tiene nada
      // nuevo. Así se ven los dos casos.
      diagnosticoNuevo: sector.diagnosis.estado !== 'Sano' && sector.diagnosis.estado !== 'Sin diagnóstico',
      lluviaPct: slot?.rain ?? null,
      uvIndex: slot?.uv ?? null,
      dosis24h,
      estadoSector: sector.status,
      confianza: sector.diagnosis.conf,
      lluviaMm: slot ? Math.round(slot.rain) / 10 : null,
      ultimoRiegoMs,
      ultimoRiegoCriticoMs: ultimo(
        delSector('Riego').filter((h) => h.regla === 'DeficitCriticoRule'),
      ),
      ultimaAplicacionMs: ultimo(delSector('Insumo')),
      riegoEnCursoHastaMs: regando ? ref + 300_000 : null,
    });
  }

  /** La pasada simulada tal como estaría ahora; null si nunca se inició. */
  private pasadaAhora(): Pasada | null {
    if (this.pasadaInicioMs === null) return null;
    return simularPasada(this.pasadaInicioMs, Date.now(), this.pasadaCanceladaMs);
  }

  async iniciarPasada(): Promise<Pasada> {
    if (this.pasadaAhora()?.estado === 'EN_CURSO') {
      throw new PasadaRechazadaError('Ya hay una pasada en curso.');
    }
    // Misma exclusión que el guardia del backend: un solo ESP32 para la pasada y las secuencias.
    const secuencia = this.secuenciaAhora();
    if (secuencia?.estado === 'EN_CURSO') {
      throw new PasadaRechazadaError(`Hay una secuencia de ${secuencia.tipo} en curso.`);
    }
    this.pasadaInicioMs = Date.now();
    this.pasadaCanceladaMs = null;
    return this.pasadaAhora() as Pasada;
  }

  async getPasadaActual(): Promise<Pasada | null> {
    return this.pasadaAhora();
  }

  async cancelarPasada(): Promise<Pasada> {
    const actual = this.pasadaAhora();
    if (!actual || actual.estado !== 'EN_CURSO') {
      throw new PasadaRechazadaError('No hay una pasada en curso.');
    }
    // Cancelar dos veces no reinicia el regreso a home.
    this.pasadaCanceladaMs ??= Date.now();
    return this.pasadaAhora() as Pasada;
  }

  /** La secuencia simulada tal como estaría ahora; null si nunca se inició. */
  private secuenciaAhora(): Secuencia | null {
    const q = this.secuencia;
    if (!q) return null;
    return simularSecuencia(q.tipo, q.parametros, q.inicioMs, Date.now(), q.canceladaMs, q.lectura);
  }

  async iniciarSecuencia(
    tipo: TipoSecuencia,
    parametros: ParametrosSecuencia = {},
  ): Promise<Secuencia> {
    if (this.pasadaAhora()?.estado === 'EN_CURSO') {
      throw new SecuenciaRechazadaError('Hay una pasada del riel en curso.');
    }
    if (this.secuenciaAhora()?.estado === 'EN_CURSO') {
      throw new SecuenciaRechazadaError('Ya hay una secuencia en curso.');
    }
    // La lectura de demostración es la de la primera zona del mock (la del destino fijo).
    const nursery = await this.getNursery();
    const metricas = nursery.zonas[0]?.lectura.metrics;
    const lectura = metricas
      ? Object.fromEntries(metricas.map((m) => [m.key, m.raw] as const))
      : undefined;
    this.secuencia = { tipo, parametros, inicioMs: Date.now(), canceladaMs: null, lectura };
    return this.secuenciaAhora() as Secuencia;
  }

  async getSecuenciaActual(): Promise<Secuencia | null> {
    return this.secuenciaAhora();
  }

  async cancelarSecuencia(): Promise<Secuencia> {
    const actual = this.secuenciaAhora();
    if (!this.secuencia || !actual || actual.estado !== 'EN_CURSO') {
      throw new SecuenciaRechazadaError('No hay una secuencia en curso.');
    }
    if (actual.cancelacionSolicitada) {
      throw new SecuenciaRechazadaError('La secuencia ya se está cancelando.');
    }
    this.secuencia.canceladaMs = Date.now();
    return this.secuenciaAhora() as Secuencia;
  }
}
