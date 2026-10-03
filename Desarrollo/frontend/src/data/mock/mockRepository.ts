/* ============================================================
   Repositorio mock: genera el vivero determinístico una sola vez
   y lo cachea. Implementa el mismo contrato que el backend real.
   ============================================================ */
import type { DataRepository } from '@/data/repository';
import { ParametrosInvalidosError } from '@/data/parametrosError';
import type {
  ActionRecord,
  CambioParametro,
  CatalogoReglas,
  Configuracion,
  DagSchema,
  DisposicionTopologia,
  HardwareData,
  NurseryData,
  NuevaTopologia,
  NuevoDispositivo,
  OrigenEvaluacion,
  TopologiaVivero,
  TrazaEvaluacion,
} from '@/types/domain';
import { validateConfig } from '@/lib/configValidation';
import { buildConfig } from './config';
import { buildNursery, type TopologiaGrid } from './generators';
import { buildHistory } from './history';
import { buildRuleSchema, catalogoVigente, validarCambios, type OverrideParametro } from './reglasMock';
import { evaluarMotor } from './trazaReglas';
import {
  altaDispositivo,
  buildFleet,
  buildHardware,
  recambioDispositivo,
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
  /** Parámetros del motor editados en la sesión; vacío = valores de fábrica. */
  private readonly parametrosEditados = new Map<string, OverrideParametro>();

  constructor(private readonly seed: number) {}

  async getNursery(): Promise<NurseryData> {
    if (!this.cache) {
      this.cache = buildNursery(
        this.seed,
        this.topologiaOverride ?? undefined,
        this.disposicionOverride ?? undefined,
      );
    }
    return this.cache;
  }

  async getHistory(): Promise<ActionRecord[]> {
    if (!this.historyCache) {
      this.historyCache = buildHistory(this.seed);
    }
    return this.historyCache;
  }

  async getConfig(): Promise<Configuracion> {
    if (!this.configCache) {
      this.configCache = buildConfig();
    }
    return structuredClone(this.configCache);
  }

  async saveConfig(config: Configuracion): Promise<Configuracion> {
    // Mismo comportamiento que el backend: rechaza configuraciones inválidas (HU-15 CA-03).
    // El tope de apertura del plan de rustificación es un parámetro del catálogo del motor.
    const aperturaMax = Number(
      catalogoVigente(this.parametrosEditados).parametros.find(
        (p) => p.clave === 'mediasombra.apertura-maxima',
      )?.valor ?? 100,
    );
    const errors = validateConfig(config, aperturaMax);
    if (errors.length > 0) {
      throw new Error(errors[0]);
    }
    const saved: Configuracion = {
      ...structuredClone(config),
      operativa: {
        ...config.operativa,
        updatedBy: 'Ingeniero Agrónomo',
        updatedTs: Date.now(),
      },
    };
    this.configCache = saved;
    return structuredClone(saved);
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
    return catalogoVigente(this.parametrosEditados);
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
    return catalogoVigente(this.parametrosEditados);
  }

  async getRuleSchema(): Promise<DagSchema> {
    return buildRuleSchema();
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
    const en24h = (tipo: string) =>
      historial.filter((h) => h.sectorId === sectorId && h.tipo === tipo && h.ts <= ref && h.ts > ref - 86_400_000)
        .length;

    // La telemetría dispara la evaluación al llegar (lectura fresca y con métricas); el barrido
    // corre 5 min después y no trae métricas, igual que `NurseryWatchdog`.
    const telemetria = origen === 'TELEMETRIA';
    const ts = lecturaTs === null ? ref : ref + (telemetria ? 0 : 300_000);

    return evaluarMotor(await this.getCatalogoReglas(), {
      sectorId,
      zonaId: sector.zona,
      origen,
      ts: new Date(ts).toISOString(),
      antiguedadSeg: lecturaTs === null ? null : telemetria ? 1 : 300,
      bloqueoManual: false,
      humSus: telemetria ? (zona.lectura.metrics.find((m) => m.key === 'humSus')?.raw ?? null) : null,
      lluviaPct: slot?.rain ?? null,
      uvIndex: slot?.uv ?? null,
      riegos24h: en24h('Riego'),
      dosis24h: en24h('Insumo'),
      estadoSector: sector.status,
      confianza: sector.diagnosis.conf,
    });
  }
}
