/* ============================================================
   Catálogo del sistema de diseño (sólo en desarrollo).
   Es la referencia viva: una pantalla nueva o rediseñada se compara contra esto.
   Spec: openspec/changes/add-sistema-diseno.
   ============================================================ */
import { useState } from 'react';
import type { Detection, MetricSpec, Status } from '@/types/domain';
import { DetectionLegend, DetectionOverlay } from '@/features/sector/components/DetectionOverlay';
import { Button } from '@/components/ui/Button';
import { linkClass } from '@/components/ui/clases';
import { Chip } from '@/components/ui/Chip';
import { StateGlyph } from '@/components/ui/StateGlyph';
import { Callout } from '@/components/ui/Callout';
import { Field, Input, InputUnit, Select } from '@/components/ui/Field';
import { FilterChip, FilterChips, Segmented, Switch } from '@/components/ui/Seleccion';
import { EmptyState, OfflineBanner, SaveBar, Skeleton } from '@/components/ui/EstadosPantalla';
import { CompositionBar, RangeBar } from '@/components/ui/Medidas';
import { Bandeja, GridCell, TrayCell } from '@/components/ui/SectorCell';
import { ETIQUETA_ESTADO } from '@/components/ui/estado';
import styles from './SistemaDiseno.module.css';

const BASE: [string, string, string][] = [
  ['--ink', '#15231B', 'Texto, botón primario, foco (16:1)'],
  ['--bg', '#F1F3EE', 'Fondo de la app'],
  ['--card', '#FFFFFF', 'Superficies'],
  ['--tray', '#1C2420', 'Sólo plano y grillas de sectores'],
  ['--muted', '#5B6A61', 'Texto secundario (5,7:1)'],
  ['--faint', '#7D8A82', 'Sólo íconos y decoración'],
  ['--line-field', '#8D978F', 'Borde de campos (3:1)'],
  ['--brand', '#2C6A4F', 'Marca, ítem activo, links'],
];

const ESTADOS: [string, string, string][] = [
  ['--ok', '#2F9B62', 'Saludable · texto con --ok-ink'],
  ['--warn', '#E2A230', 'Observación · texto con --warn-ink'],
  ['--crit', '#D2462E', 'Crítico · texto con --crit-ink'],
  ['--off', '#9BA39E', 'Sin señal · texto con --off-ink'],
  ['--info', '#2D6FB5', 'El sistema actuó: riego, acciones, traza'],
];

const CLASES_DIAGNOSTICO: [string, string, string][] = [
  ['--dx-sano', '#57965F', 'Sano'],
  ['--dx-clorosis', '#C2B14E', 'Clorosis'],
  ['--dx-estres', '#C4843E', 'Estrés solar'],
  ['--dx-plaga', '#4F8F86', 'Plaga foliar'],
  ['--dx-fungico', '#7A5A40', 'Daño fúngico'],
  ['--dx-nc', '#9BA39E', 'No concluyente · rayado con --dx-nc-2'],
];

const CAJAS_DETECCION: [string, string, string][] = [
  ['--det-sano', '#5CD6FF', 'Sano'],
  ['--det-clorosis', '#FFE45C', 'Clorosis'],
  ['--det-estres-solar', '#FF9A3C', 'Estrés solar'],
  ['--det-plaga-foliar', '#FF5CCB', 'Plaga foliar'],
  ['--det-dano-fungico', '#C9A2FF', 'Daño fúngico'],
];

/** Muestra de cajas sobre una captura de la demo: el único lugar donde van los --det-*. */
const DETECCIONES_MUESTRA: Detection[] = [
  { label: 'Clorosis', score: 0.74, box: [0.43, 0.3, 0.59, 0.74] },
  { label: 'Clorosis', score: 0.66, box: [0.7, 0.48, 0.93, 0.89] },
  { label: 'Sano', score: 0.88, box: [0.15, 0.27, 0.45, 0.6] },
  { label: 'Sano', score: 0.81, box: [0.45, 0.04, 0.69, 0.36] },
  { label: 'Plaga foliar', score: 0.41, box: [0.31, 0.62, 0.47, 0.92] },
];

const TODOS: Status[] = ['ok', 'warning', 'critical', 'offline'];

const HUMEDAD: Pick<MetricSpec, 'label' | 'unit' | 'dec' | 'ideal' | 'warn' | 'crit'> = {
  label: 'Humedad de sustrato',
  unit: '%',
  dec: 0,
  ideal: [42, 68],
  warn: [32, 80],
  crit: [22, 90],
};

function Swatches({ items }: { items: [string, string, string][] }) {
  return (
    <ul className={styles.swGrid}>
      {items.map(([token, hex, uso]) => (
        <li key={token} className={styles.sw}>
          <span className={styles.swColor} style={{ background: `var(${token})` }} />
          <b>{token}</b>
          <span className={styles.code}>{hex}</span>
          <span className={styles.meta}>{uso}</span>
        </li>
      ))}
    </ul>
  );
}

export function SistemaDisenoPage() {
  const [rango, setRango] = useState<'hoy' | '7d'>('hoy');
  const [filtro, setFiltro] = useState('todos');
  const [encendido, setEncendido] = useState(true);
  const [cambios, setCambios] = useState(1);

  return (
    <main className={styles.page}>
      <div className={styles.inner}>
        <header>
          <h1 className={styles.titulo}>Sistema de diseño</h1>
          <p className={styles.lead}>
            Tokens, tipografía, estados y componentes del dashboard. Toda vista los usa en vez de colores
            sueltos: si algo de acá cambia, cambia en todas. Sólo visible en desarrollo.
          </p>
        </header>

        <section className={styles.sec}>
          <h2 className={styles.h2}>Color</h2>
          <h3 className={styles.h3}>Base</h3>
          <Swatches items={BASE} />
          <h3 className={styles.h3}>Estados (los únicos colores saturados)</h3>
          <Swatches items={ESTADOS} />
          <h3 className={styles.h3}>Clases de diagnóstico (sólo composición, nunca texto)</h3>
          <Swatches items={CLASES_DIAGNOSTICO} />
          <h3 className={styles.h3}>Cajas de detección (sólo superpuestas a una fotografía)</h3>
          <p className={styles.meta}>
            Saturadas y claras para leerse sobre follaje, siempre con un halo de --tray debajo. El estado
            diagnosticado va con trazo continuo grueso; las demás clases, discontinuo fino. Fuera de una foto no se
            usan: ni texto, ni estado, ni composición.
          </p>
          <Swatches items={CAJAS_DETECCION} />
          <Bandeja className={styles.deteccion}>
            <div className={styles.deteccionFoto}>
              <img src="/demo/capturas/clorosis/01.jpg" alt="Captura de muestra con cajas de detección" width={360} />
              <DetectionOverlay detections={DETECCIONES_MUESTRA} diagnosedState="Clorosis" view="all" />
            </div>
            <DetectionLegend detections={DETECCIONES_MUESTRA} diagnosedState="Clorosis" view="all" />
          </Bandeja>
        </section>

        <section className={styles.sec}>
          <h2 className={styles.h2}>Estados: color y forma</h2>
          <div className={styles.row}>
            <div className={styles.box}>
              <span className={styles.meta}>Glifos</span>
              <div className={styles.legend}>
                {TODOS.map((s) => (
                  <span key={s}>
                    <StateGlyph status={s} size="lg" /> {ETIQUETA_ESTADO[s]}
                  </span>
                ))}
              </div>
            </div>
            <div className={styles.box}>
              <span className={styles.meta}>Chips</span>
              <div className={styles.flex}>
                {TODOS.map((s) => (
                  <Chip key={s} tone={s} glyph />
                ))}
                <Chip tone="info">En curso</Chip>
                <Chip tone="line">Bloqueado</Chip>
              </div>
            </div>
            <div className={styles.boxDark}>
              <span className={styles.meta}>Bandeja (un punto = un sector)</span>
              <div className={styles.trayGrid}>
                {(['ok', 'ok', 'warning', 'warning', 'critical', 'critical', 'offline', 'offline'] as Status[]).map(
                  (s, i) => (
                    <TrayCell key={i} status={s} selected={i === 0} />
                  ),
                )}
              </div>
            </div>
            <div className={styles.boxDark}>
              <span className={styles.meta}>Grilla de macro-zona</span>
              <div className={styles.zGrid}>
                {TODOS.map((s, i) => (
                  <GridCell key={s} status={s} numero={i + 1} label={`Sector MZ-4-00${i + 1}`} />
                ))}
                <GridCell status="ok" numero={5} label="Sector MZ-4-005" diag />
              </div>
            </div>
          </div>
          <div className={styles.row}>
            <div className={styles.box}>
              <span className={styles.meta}>Composición</span>
              <CompositionBar counts={{ ok: 412, warning: 141, critical: 41, offline: 6 }} />
            </div>
            <div className={styles.box}>
              <span className={styles.meta}>Valor contra su rango</span>
              <RangeBar value={40} spec={HUMEDAD} scale />
            </div>
          </div>
        </section>

        <section className={styles.sec}>
          <h2 className={styles.h2}>Tipografía: Archivo en tres anchos</h2>
          <div className={styles.box}>
            <div className={styles.tipo}>
              <p className={styles.display} style={{ fontSize: 'var(--t-48)' }}>
                179
              </p>
              <span className={styles.meta}>Cifras grandes, 48 px, expandida 118 %</span>
              <p className={styles.display} style={{ fontSize: 'var(--t-24)' }}>
                Macro-zona 4
              </p>
              <span className={styles.meta}>Título de página, 24 px, expandida</span>
              <p style={{ margin: 0, fontSize: 'var(--t-18)', fontWeight: 650 }}>Qué atender ahora</p>
              <span className={styles.meta}>Título de sección, 18 px, 650</span>
              <p style={{ margin: 0, fontSize: 'var(--t-15)' }}>Humedad de sustrato 28 %, debajo del ideal.</p>
              <span className={styles.meta}>Cuerpo, 15 px, ancho normal</span>
              <p className={styles.meta} style={{ margin: 0 }}>
                Nodo NT-04, lectura de las 14:32
              </p>
              <span className={styles.meta}>Metadatos y tablas, 13 px, semicondensada 86 %</span>
            </div>
          </div>
        </section>

        <section className={styles.sec}>
          <h2 className={styles.h2}>Componentes</h2>
          <div className={styles.row}>
            <div className={styles.box}>
              <span className={styles.meta}>Botones</span>
              <div className={styles.flex}>
                <Button variant="primary">Guardar cambio</Button>
                <Button>Exportar</Button>
                <Button variant="ghost">Descartar</Button>
                <Button variant="danger">Regenerar…</Button>
                <Button size="sm">Chico</Button>
                <Button disabled>Deshabilitado</Button>
              </div>
            </div>
            <div className={styles.box}>
              <span className={styles.meta}>Campos</span>
              <Field label="Volumen" htmlFor="ds-vol" hint="El motor riega hasta 6 L por evento.">
                <InputUnit id="ds-vol" defaultValue="8" unit="litros" />
              </Field>
              <Field label="Umbral modificado" htmlFor="ds-mod">
                <InputUnit id="ds-mod" defaultValue="55" unit="%" modified />
              </Field>
              <Field label="Motivo" htmlFor="ds-err" error="Contá por qué cambiás el valor.">
                <Input id="ds-err" invalid placeholder="Ej.: se viene una semana de calor" />
              </Field>
              <Field label="Hasta" htmlFor="ds-sel" optional>
                <Select id="ds-sel" defaultValue="manual">
                  <option value="manual">Hasta que lo reanude alguien</option>
                  <option value="2h">2 horas</option>
                </Select>
              </Field>
            </div>
            <div className={styles.box}>
              <span className={styles.meta}>Selección</span>
              <Segmented
                label="Rango"
                value={rango}
                onChange={setRango}
                options={[
                  { value: 'hoy', label: 'Hoy' },
                  { value: '7d', label: '7 días' },
                ]}
              />
              <FilterChips label="Diagnóstico">
                <FilterChip pressed={filtro === 'todos'} onClick={() => setFiltro('todos')} count={600}>
                  Todos
                </FilterChip>
                <FilterChip pressed={filtro === 'clorosis'} onClick={() => setFiltro('clorosis')} count={162}>
                  Clorosis
                </FilterChip>
              </FilterChips>
              <div className={styles.flex}>
                <Switch checked={encendido} onChange={setEncendido} label="Mostrar Demo Expo" />
                <span className={styles.meta}>Interruptor</span>
              </div>
            </div>
          </div>
          <div className={styles.row}>
            <div className={styles.box}>
              <span className={styles.meta}>Avisos en línea</span>
              <Callout>Neutro</Callout>
              <Callout tone="info">El sistema está actuando</Callout>
              <Callout tone="warn">Advertencia</Callout>
              <Callout tone="crit">Crítico o destructivo</Callout>
            </div>
            <div className={styles.box}>
              <span className={styles.meta}>Barra de guardado</span>
              <SaveBar
                count={cambios}
                detalle="Umbral de riego 45 → 55 %, lo usan 4 reglas"
                onDiscard={() => setCambios(0)}
                onSave={() => setCambios(0)}
              />
              {cambios === 0 && (
                <Button size="sm" onClick={() => setCambios(1)}>
                  Volver a mostrarla
                </Button>
              )}
            </div>
          </div>
        </section>

        <section className={styles.sec}>
          <h2 className={styles.h2}>Estados de pantalla</h2>
          <div className={styles.row}>
            <div className={styles.box}>
              <span className={styles.meta}>Cargando (con el shell visible)</span>
              <Skeleton width="60%" height={18} />
              <Skeleton height={90} />
              <Skeleton width="40%" />
            </div>
            <div className={styles.box}>
              <span className={styles.meta}>Error, sin tumbar la app</span>
              <Callout tone="crit">
                <b>No se pudo actualizar el vivero.</b> Mostramos los datos de las 14:31.{' '}
                <button type="button" className={linkClass}>
                  Reintentar
                </button>
              </Callout>
            </div>
            <div className={styles.box}>
              <span className={styles.meta}>Vacío con acción</span>
              <EmptyState
                title="Todavía no hay equipos registrados"
                action={
                  <Button size="sm" variant="primary">
                    Registrar el primero
                  </Button>
                }
              >
                Registrá los nodos testigo de cada macro-zona para empezar a medir.
              </EmptyState>
            </div>
            <div className={styles.box}>
              <span className={styles.meta}>Sin conexión</span>
              <OfflineBanner>
                <b>Sin conexión.</b> Datos de las 14:31. Las acciones manuales quedan deshabilitadas hasta que
                vuelva.
              </OfflineBanner>
            </div>
          </div>
        </section>

        <section className={styles.sec}>
          <h2 className={styles.h2}>La bandeja</h2>
          <Bandeja style={{ maxWidth: 260 }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(10, 1fr)', gap: 4 }}>
              {Array.from({ length: 30 }, (_, i) => (
                <TrayCell key={i} status={TODOS[i % 7 === 0 ? 2 : i % 5 === 0 ? 1 : i === 29 ? 3 : 0]} />
              ))}
            </div>
          </Bandeja>
        </section>
      </div>
    </main>
  );
}
