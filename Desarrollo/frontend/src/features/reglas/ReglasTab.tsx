/* ============================================================
   Pestaña Reglas: cada pieza del motor con su interruptor y sus umbrales, agrupada por objetivo.

   Dos tiempos distintos a propósito (design D-5): los parámetros se editan en un borrador y se guardan en lote
   con la SaveBar (todo o nada); el interruptor es una acción, se confirma y se aplica en el momento, sin tocar
   ese borrador. El borrador NO vive acá: lo posee `ReglasPage`, porque esta pestaña se desmonta al ir al Inspector.
   ============================================================ */
import { useEffect, useMemo, useState, type Dispatch, type SetStateAction } from 'react';
import { Callout } from '@/components/ui/Callout';
import { Input } from '@/components/ui/Field';
import { SaveBar } from '@/components/ui/EstadosPantalla';
import { FilterChip, FilterChips, Segmented } from '@/components/ui/Seleccion';
import { Toast } from '@/components/ui/Toast';
import { EstadoReglaError } from '@/data/estadoReglaError';
import { ParametrosInvalidosError } from '@/data/parametrosError';
import { contar } from '@/lib/plural';
import type {
  CambioEstadoRegla,
  CambioParametro,
  CatalogoReglas,
  ParametroRegla,
  ReglaCatalogo,
} from '@/types/domain';
import {
  cambiosDelBorrador,
  editar,
  erroresDelBorrador,
  restablecer,
  sinEnviados,
  type Borrador,
} from './borrador';
import {
  agruparPorObjetivo,
  indicePorClave,
  nombreConsumidor,
  SIN_FILTROS,
  type OrdenPiezas,
  type Filtros,
} from './catalogoView';
import { Compartidos } from './components/Compartidos';
import { ConfirmarApagado } from './components/ConfirmarApagado';
import { EjecucionCard } from './components/EjecucionCard';
import { idTarjeta } from './components/anclas';
import { ReglaCard } from './components/ReglaCard';
import styles from './components/ReglasTab.module.css';

interface ReglasTabProps {
  catalogo: CatalogoReglas;
  saving: boolean;
  /** Guarda los parámetros. Rechaza (con `ParametrosInvalidosError` si el servidor los rechaza). */
  onGuardar: (cambios: CambioParametro[]) => Promise<unknown>;
  /** Prende o apaga una pieza en el momento. */
  onCambiarEstado: (nombre: string, cambio: CambioEstadoRegla) => Promise<ReglaCatalogo>;
  /** Ediciones sin guardar, indexadas por clave. Las posee el padre para que sobrevivan al cambio de pestaña. */
  borrador: Borrador;
  onBorradorChange: Dispatch<SetStateAction<Borrador>>;
  /** Pieza a la que se lleva al abrir (viene de "Editar parámetro" en el Inspector). */
  reglaInicial?: string | null;
  /** El rol no tiene `reglas.editar`: todo en sólo lectura. */
  soloLectura?: boolean;
}

type Aviso = { tono: 'ok' | 'crit'; texto: string } | null;

const CLAVE_ORDEN = 'reglas.orden';
const leerOrden = (): OrdenPiezas => {
  try {
    return localStorage.getItem(CLAVE_ORDEN) === 'prioridad' ? 'prioridad' : 'codigo';
  } catch {
    return 'codigo';
  }
};

export function ReglasTab({
  catalogo,
  saving,
  onGuardar,
  onCambiarEstado,
  borrador,
  onBorradorChange: setBorrador,
  reglaInicial,
  soloLectura = false,
}: ReglasTabProps) {
  const [filtros, setFiltros] = useState<Filtros>(SIN_FILTROS);
  const [orden, setOrden] = useState<OrdenPiezas>(leerOrden);
  const elegirOrden = (o: OrdenPiezas) => {
    setOrden(o);
    try {
      localStorage.setItem(CLAVE_ORDEN, o);
    } catch {
      /* sin almacenamiento, sólo no se recuerda */
    }
  };
  const [erroresServidor, setErroresServidor] = useState<Map<string, string>>(new Map());
  const [erroresGenerales, setErroresGenerales] = useState<string[]>([]);
  /** Diálogo de apagado abierto: la pieza y su interruptor. */
  const [apagando, setApagando] = useState<{ regla: ReglaCatalogo; anchor: HTMLElement } | null>(
    null,
  );
  const [errorEstado, setErrorEstado] = useState<string | null>(null);
  const [cambiando, setCambiando] = useState(false);
  const [aviso, setAviso] = useState<Aviso>(null);

  const bloqueado = saving || soloLectura;
  const indice = useMemo(() => indicePorClave(catalogo), [catalogo]);
  const nombres = useMemo(
    () => ({
      ...Object.fromEntries(
        catalogo.parametros.flatMap((p) => p.usadoPor).map((id) => [id, nombreConsumidor(id)]),
      ),
      ...Object.fromEntries(catalogo.reglas.map((r) => [r.id, r.label])),
    }),
    [catalogo],
  );
  const codigos = useMemo(
    () =>
      Object.fromEntries(
        catalogo.reglas.flatMap((r) => (r.codigo ? [[r.id, r.codigo] as const] : [])),
      ),
    [catalogo],
  );
  const erroresCliente = useMemo(
    () => erroresDelBorrador(catalogo, borrador),
    [catalogo, borrador],
  );
  const cambios = useMemo(() => cambiosDelBorrador(catalogo, borrador), [catalogo, borrador]);
  const editadas = useMemo(() => new Set(cambios.map((c) => c.clave)), [cambios]);
  const grupos = useMemo(
    () => agruparPorObjetivo(catalogo, filtros, editadas, orden),
    [catalogo, filtros, editadas, orden],
  );

  const totalApagadas = catalogo.reglas.filter((r) => !r.activa).length;
  const totalProtecciones = catalogo.reglas.filter((r) => r.efecto === 'PROTECTS').length;

  // Llega por `?regla=`: se lleva a la tarjeta.
  useEffect(() => {
    if (reglaInicial)
      document.getElementById(idTarjeta(reglaInicial))?.scrollIntoView?.({ block: 'center' });
  }, [reglaInicial]);

  const patch = (p: Partial<Filtros>) => setFiltros((f) => ({ ...f, ...p }));

  const limpiarErrorServidor = (clave: string) =>
    setErroresServidor((m) => {
      if (!m.has(clave)) return m;
      const n = new Map(m);
      n.delete(clave);
      return n;
    });

  const onEditar = (p: ParametroRegla, texto: string) => {
    setBorrador((b) => editar(b, p, texto));
    limpiarErrorServidor(p.clave);
  };
  const onRestablecer = (p: ParametroRegla) => {
    setBorrador((b) => restablecer(b, p));
    limpiarErrorServidor(p.clave);
  };
  const onDescartar = () => {
    setBorrador(new Map());
    setErroresServidor(new Map());
    setErroresGenerales([]);
  };

  const onGuardarClick = async () => {
    setErroresServidor(new Map());
    setErroresGenerales([]);
    // Lo que se envía es esta foto del borrador: al volver se limpia sólo eso, sin pisar lo editado después.
    const enviado = borrador;
    try {
      await onGuardar(cambios);
      setBorrador((actual) => sinEnviados(actual, enviado));
    } catch (e) {
      if (e instanceof ParametrosInvalidosError) {
        const porClave = new Map<string, string>();
        const generales: string[] = [];
        for (const err of e.errores) {
          if (err.clave && indice.has(err.clave)) porClave.set(err.clave, err.mensaje);
          else generales.push(err.mensaje);
        }
        setErroresServidor(porClave);
        setErroresGenerales(generales);
      } else {
        setErroresGenerales([e instanceof Error ? e.message : String(e)]);
      }
    }
  };

  const aplicarEstado = async (regla: ReglaCatalogo, cambio: CambioEstadoRegla) => {
    setCambiando(true);
    setErrorEstado(null);
    try {
      const nueva = await onCambiarEstado(regla.id, cambio);
      setApagando(null);
      setAviso({
        tono: 'ok',
        texto: `«${regla.label}» quedó ${nueva.activa ? 'prendida' : 'apagada'}.`,
      });
    } catch (e) {
      const texto = e instanceof Error ? e.message : String(e);
      if (cambio.activa)
        setAviso({ tono: 'crit', texto: `No se pudo prender «${regla.label}»: ${texto}` });
      else if (e instanceof EstadoReglaError) setErrorEstado(texto);
      else {
        setApagando(null);
        setAviso({ tono: 'crit', texto: `No se pudo apagar «${regla.label}»: ${texto}` });
      }
    } finally {
      setCambiando(false);
    }
  };

  /** Prender es inmediato; apagar pide confirmación (con consecuencia y motivo si es una protección). */
  const onCambiarEstado_ = (regla: ReglaCatalogo, activa: boolean, anchor: HTMLElement) => {
    if (activa) void aplicarEstado(regla, { activa: true });
    else {
      setErrorEstado(null);
      setApagando({ regla, anchor });
    }
  };

  const hayErroresCliente = erroresCliente.size > 0;
  const clavesVisibles = new Set(
    grupos.flatMap((g) => [
      ...g.reglas.flatMap((r) => r.parametros),
      ...g.compartidos.map((p) => p.clave),
      ...g.consumidores.flatMap((c) => c.parametros.map((p) => p.clave)),
    ]),
  );
  const ocultosConError = [...erroresCliente.keys(), ...erroresServidor.keys()].filter(
    (c) => !clavesVisibles.has(c),
  ).length;

  const comunes = {
    borrador,
    erroresCliente,
    erroresServidor,
    disabled: bloqueado,
    soloLectura,
    onEditar,
    onRestablecer,
  };

  return (
    <>
      <div className={styles.barra}>
        <Input
          type="search"
          placeholder="Buscar regla o parámetro…"
          aria-label="Buscar regla o parámetro"
          value={filtros.busqueda}
          onChange={(e) => patch({ busqueda: e.target.value })}
        />
        <FilterChips label="Filtros">
          <FilterChip
            pressed={filtros.soloApagadas}
            count={totalApagadas}
            onClick={() => patch({ soloApagadas: !filtros.soloApagadas })}
          >
            Apagadas
          </FilterChip>
          <FilterChip
            pressed={filtros.soloProtecciones}
            count={totalProtecciones}
            onClick={() => patch({ soloProtecciones: !filtros.soloProtecciones })}
          >
            Protecciones
          </FilterChip>
          <FilterChip
            pressed={filtros.soloConCambios}
            count={cambios.length}
            onClick={() => patch({ soloConCambios: !filtros.soloConCambios })}
          >
            Con cambios
          </FilterChip>
        </FilterChips>
        <Segmented<OrdenPiezas>
          label="Orden de las reglas"
          options={[
            { value: 'codigo', label: 'Por código' },
            { value: 'prioridad', label: 'Por prioridad del motor' },
          ]}
          value={orden}
          onChange={elegirOrden}
        />
      </div>

      {soloLectura && (
        <Callout tone="info" role="note">
          Para cambiar umbrales o prender y apagar reglas hace falta el permiso «Editar el motor de
          reglas».
        </Callout>
      )}

      {erroresGenerales.length > 0 && (
        <Callout tone="crit" role="alert">
          {erroresGenerales.map((m) => (
            <div key={m}>{m}</div>
          ))}
        </Callout>
      )}

      {grupos.length === 0 ? (
        <div className={styles.vacio}>Ninguna regla coincide con los filtros.</div>
      ) : (
        grupos.map((g) => (
          <section
            key={g.objetivo}
            className={styles.grupo}
            aria-labelledby={`grupo-${g.objetivo}`}
          >
            <div className={styles.grupoCabecera}>
              <h2 id={`grupo-${g.objetivo}`} className={styles.grupoTitulo}>
                {g.titulo}
                {g.reglas.length > 0 && <> · {contar(g.reglas.length, 'regla', 'reglas')}</>}
                {g.apagadas > 0 && <> · {contar(g.apagadas, 'apagada', 'apagadas')}</>}
              </h2>
              <span className={styles.grupoDesc}>{g.descripcion}</span>
            </div>
            {g.compartidos.length > 0 && (
              <Compartidos
                parametros={g.compartidos}
                nombres={nombres}
                codigos={codigos}
                {...comunes}
              />
            )}
            {g.reglas.map((r) => (
              <ReglaCard
                key={r.id}
                regla={r}
                mostrarPrioridad={orden === 'prioridad'}
                indice={indice}
                destacada={r.id === reglaInicial}
                onCambiarEstado={onCambiarEstado_}
                {...comunes}
                disabled={bloqueado || cambiando}
              />
            ))}
            {g.consumidores.map((c) => (
              <EjecucionCard key={c.id} grupo={c} {...comunes} />
            ))}
          </section>
        ))
      )}

      {apagando && (
        <ConfirmarApagado
          regla={apagando.regla}
          anchor={apagando.anchor}
          error={errorEstado}
          ocupado={cambiando}
          onCancelar={() => setApagando(null)}
          onConfirmar={(motivo) =>
            void aplicarEstado(apagando.regla, {
              activa: false,
              ...(motivo ? { motivo } : {}),
              ...(apagando.regla.proteccion !== null ? { confirmaProteccion: true } : {}),
            })
          }
        />
      )}

      {!soloLectura && (
        <SaveBar
          count={cambios.length}
          detalle={
            hayErroresCliente
              ? 'Corregí los valores marcados para poder guardar.'
              : ocultosConError > 0
                ? `${contar(ocultosConError, 'parámetro con error queda oculto', 'parámetros con error quedan ocultos')} por los filtros.`
                : undefined
          }
          saveLabel="Guardar cambios"
          saving={saving}
          saveDisabled={hayErroresCliente}
          onDiscard={onDescartar}
          onSave={onGuardarClick}
        />
      )}

      {aviso && (
        <Toast tone={aviso.tono} onClose={() => setAviso(null)}>
          {aviso.texto}
        </Toast>
      )}
    </>
  );
}
