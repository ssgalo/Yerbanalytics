/* ============================================================
   Motor de reglas: las piezas agrupadas por objetivo, una fila por pieza (design D-6).

   Dos tiempos distintos a propósito: los parámetros se editan en un borrador por copia `(regla, clave)` y se
   guardan en lote con la SaveBar (todo o nada); el interruptor, el nombre y eliminar son acciones que se aplican
   en el momento, sin tocar ese borrador.
   ============================================================ */
import { useEffect, useMemo, useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { Card } from '@/components/ui/Card';
import { SaveBar } from '@/components/ui/EstadosPantalla';
import { Switch } from '@/components/ui/Seleccion';
import { Toast } from '@/components/ui/Toast';
import { linkClass } from '@/components/ui/clases';
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
  idCopia,
  sinEnviados,
  sinRegla,
  type Borrador,
} from './borrador';
import { agruparPorObjetivo, codigoVisible, estadoGrupo, parametrosDe, type GrupoObjetivo } from './catalogoView';
import { idFila } from './components/anclas';
import { ConfirmarApagado } from './components/ConfirmarApagado';
import { RuleRow } from './components/RuleRow';
import styles from './components/RuleCatalog.module.css';

interface RuleCatalogProps {
  catalogo: CatalogoReglas;
  saving: boolean;
  /** Guarda los parámetros. Rechaza (con `ParametrosInvalidosError` si el servidor los rechaza). */
  onGuardar: (cambios: CambioParametro[]) => Promise<unknown>;
  onCambiarEstado: (nombre: string, cambio: CambioEstadoRegla) => Promise<ReglaCatalogo>;
  onRenombrar: (nombre: string, etiqueta: string | null) => Promise<ReglaCatalogo>;
  onEliminar: (nombre: string) => Promise<ReglaCatalogo>;
  onRestaurar: (nombre: string) => Promise<ReglaCatalogo>;
  /** Pieza que se despliega al abrir (viene de "Editar parámetro" en el Inspector). */
  reglaInicial?: string | null;
  /** El rol no tiene `reglas.editar`: todo en sólo lectura. */
  soloLectura?: boolean;
}

type Aviso = { tono: 'ok' | 'crit'; texto: string } | null;

/** Diálogo de apagado abierto: lo que se apaga, el grupo (si es el interruptor de un grupo) y el interruptor. */
type Apagado = { reglas: ReglaCatalogo[]; grupo?: string; anchor: HTMLElement } | null;

const mensaje = (e: unknown) => (e instanceof Error ? e.message : String(e));

export function RuleCatalog({
  catalogo,
  saving,
  onGuardar,
  onCambiarEstado,
  onRenombrar,
  onEliminar,
  onRestaurar,
  reglaInicial,
  soloLectura = false,
}: RuleCatalogProps) {
  const [borrador, setBorrador] = useState<Borrador>(new Map());
  const [erroresServidor, setErroresServidor] = useState<Map<string, string>>(new Map());
  const [erroresGenerales, setErroresGenerales] = useState<string[]>([]);
  const [abierta, setAbierta] = useState<string | null>(reglaInicial ?? null);
  const [apagando, setApagando] = useState<Apagado>(null);
  const [errorEstado, setErrorEstado] = useState<string | null>(null);
  const [cambiando, setCambiando] = useState(false);
  const [aviso, setAviso] = useState<Aviso>(null);
  const [verEliminadas, setVerEliminadas] = useState(false);
  /** Evita dos pedidos de estado a la vez sin deshabilitar el interruptor (perdería el foco). */
  const enVuelo = useRef(false);

  const grupos = useMemo(() => agruparPorObjetivo(catalogo), [catalogo]);
  const eliminadas = useMemo(() => catalogo.reglas.filter((r) => r.eliminada), [catalogo]);
  const cambios = useMemo(() => cambiosDelBorrador(catalogo, borrador), [catalogo, borrador]);
  const erroresCliente = useMemo(() => erroresDelBorrador(catalogo, borrador), [catalogo, borrador]);
  const errores = useMemo(() => new Map([...erroresServidor, ...erroresCliente]), [erroresServidor, erroresCliente]);

  // Llega por `?regla=`: se despliega y se lleva a la fila.
  useEffect(() => {
    if (!reglaInicial) return;
    setAbierta(reglaInicial);
    document.getElementById(idFila(reglaInicial))?.scrollIntoView?.({ block: 'center' });
  }, [reglaInicial]);

  const limpiarErrorServidor = (id: string) =>
    setErroresServidor((m) => {
      if (!m.has(id)) return m;
      const n = new Map(m);
      n.delete(id);
      return n;
    });

  const onEditar = (p: ParametroRegla, texto: string) => {
    setBorrador((b) => editar(b, p, texto));
    limpiarErrorServidor(idCopia(p));
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
      setAviso({ tono: 'ok', texto: 'Cambios guardados.' });
    } catch (e) {
      if (!(e instanceof ParametrosInvalidosError)) {
        setErroresGenerales([mensaje(e)]);
        return;
      }
      const porCopia = new Map<string, string>();
      const generales: string[] = [];
      for (const err of e.errores) {
        const p = catalogo.parametros.find((x) => x.regla === err.regla && x.clave === err.clave);
        if (p) porCopia.set(idCopia(p), err.mensaje);
        else generales.push(err.mensaje);
      }
      setErroresServidor(porCopia);
      setErroresGenerales(generales);
      // Se despliega la primera fila con un error, para que se vea junto a su campo.
      const primera = catalogo.parametros.find((p) => porCopia.has(idCopia(p)));
      if (primera) setAbierta(primera.regla);
    }
  };

  /** Aplica el mismo cambio de estado a un lote; devuelve los rechazos. */
  const aplicarLote = async (
    reglas: ReglaCatalogo[],
    cambio: (r: ReglaCatalogo) => CambioEstadoRegla,
  ): Promise<{ regla: ReglaCatalogo; error: unknown }[]> => {
    const fallas: { regla: ReglaCatalogo; error: unknown }[] = [];
    for (const r of reglas) {
      try {
        await onCambiarEstado(r.id, cambio(r));
      } catch (error) {
        fallas.push({ regla: r, error });
      }
    }
    return fallas;
  };

  const avisarFallas = (activa: boolean, fallas: { regla: ReglaCatalogo; error: unknown }[]) => {
    if (fallas.length === 0) return;
    const verbo = activa ? 'prender' : 'apagar';
    setAviso({
      tono: 'crit',
      texto:
        fallas.length === 1
          ? `No se pudo ${verbo} «${fallas[0].regla.label}»: ${mensaje(fallas[0].error)}`
          : `No se pudieron ${verbo} ${fallas.length} reglas: ${fallas.map((f) => f.regla.label).join(', ')}.`,
    });
  };

  /** Prende o apaga sin confirmación (prender, o apagar algo que no es una protección). */
  const directo = async (reglas: ReglaCatalogo[], activa: boolean) => {
    if (enVuelo.current || reglas.length === 0) return;
    enVuelo.current = true;
    setCambiando(true);
    try {
      avisarFallas(activa, await aplicarLote(reglas, () => ({ activa })));
    } finally {
      enVuelo.current = false;
      setCambiando(false);
    }
  };

  /** Apagado confirmado con motivo (el lote incluye al menos una protección). */
  const confirmarApagado = async (motivo: string) => {
    if (!apagando || enVuelo.current) return;
    enVuelo.current = true;
    setCambiando(true);
    setErrorEstado(null);
    try {
      const fallas = await aplicarLote(apagando.reglas, (r) => ({
        activa: false,
        ...(motivo ? { motivo } : {}),
        ...(r.proteccion !== null ? { confirmaProteccion: true } : {}),
      }));
      if (fallas.length === 1 && apagando.reglas.length === 1 && fallas[0].error instanceof EstadoReglaError) {
        setErrorEstado(mensaje(fallas[0].error));
        return;
      }
      const anchor = apagando.anchor;
      setApagando(null);
      avisarFallas(false, fallas);
      if (anchor.isConnected) anchor.focus();
    } finally {
      enVuelo.current = false;
      setCambiando(false);
    }
  };

  const onCambiarRegla = (regla: ReglaCatalogo, activa: boolean, anchor: HTMLElement) => {
    if (!activa && regla.proteccion !== null) {
      setErrorEstado(null);
      setApagando({ reglas: [regla], anchor });
    } else void directo([regla], activa);
  };

  /** Interruptor del grupo: si están todas prendidas las apaga; si no, prende las apagadas. */
  const onCambiarGrupo = (g: GrupoObjetivo, activa: boolean, anchor: HTMLElement) => {
    const lote = g.reglas.filter((r) => r.activa !== activa);
    if (!activa && lote.some((r) => r.proteccion !== null)) {
      setErrorEstado(null);
      setApagando({ reglas: lote, grupo: g.titulo, anchor });
    } else void directo(lote, activa);
  };

  const renombrar = async (regla: ReglaCatalogo, etiqueta: string | null) => {
    try {
      await onRenombrar(regla.id, etiqueta);
    } catch (e) {
      setAviso({ tono: 'crit', texto: `No se pudo cambiar el nombre: ${mensaje(e)}` });
      throw e;
    }
  };

  const eliminar = async (regla: ReglaCatalogo) => {
    try {
      await onEliminar(regla.id);
      setBorrador((b) => sinRegla(b, regla.id));
      if (abierta === regla.id) setAbierta(null);
      setAviso({ tono: 'ok', texto: 'Regla eliminada.' });
    } catch (e) {
      setAviso({ tono: 'crit', texto: `No se pudo eliminar «${regla.label}»: ${mensaje(e)}` });
    }
  };

  const restaurar = async (regla: ReglaCatalogo) => {
    try {
      await onRestaurar(regla.id);
      setAviso({ tono: 'ok', texto: `«${regla.label}» volvió, apagada.` });
    } catch (e) {
      setAviso({ tono: 'crit', texto: `No se pudo restaurar «${regla.label}»: ${mensaje(e)}` });
    }
  };

  const alternar = (id: string) => setAbierta((a) => (a === id ? null : id));
  const comunes = {
    borrador,
    errores,
    disabled: saving,
    soloLectura,
    onEditar,
  };

  return (
    <div className={styles.lista}>
      {soloLectura && (
        <Callout tone="info" role="note">
          Sólo lectura: hace falta el permiso «Editar el motor de reglas».
        </Callout>
      )}

      {erroresGenerales.length > 0 && (
        <Callout tone="crit" role="alert">
          {erroresGenerales.map((m) => (
            <div key={m}>{m}</div>
          ))}
        </Callout>
      )}

      {grupos.map((g) => {
        const activas = g.reglas.filter((r) => r.activa).length;
        return (
          <Card key={g.objetivo}>
            <section aria-labelledby={`grupo-${g.objetivo}`}>
              <div className={styles.grupoCabecera}>
                <h2 id={`grupo-${g.objetivo}`} className={styles.grupoTitulo}>
                  {g.titulo}
                </h2>
                {g.reglas.length > 0 && (
                  <>
                    <span className={styles.grupoCuenta}>
                      {activas} de {g.reglas.length}
                    </span>
                    <span className={styles.interruptor} data-grupo={g.objetivo}>
                      <Switch
                        checked={estadoGrupo(g.reglas)}
                        label={`Todas las reglas de ${g.titulo}`}
                        disabled={soloLectura}
                        onChange={(activa) => {
                          const boton = document.querySelector<HTMLElement>(
                            `[data-grupo="${g.objetivo}"] [role="switch"]`,
                          );
                          if (boton) onCambiarGrupo(g, activa, boton);
                        }}
                      />
                    </span>
                  </>
                )}
              </div>
              {g.reglas.map((r) => (
                <RuleRow
                  key={r.id}
                  id={r.id}
                  nombre={r.label}
                  codigo={codigoVisible(r)}
                  parametros={parametrosDe(catalogo, r.id)}
                  regla={r}
                  abierta={abierta === r.id}
                  onToggle={() => alternar(r.id)}
                  onCambiarEstado={onCambiarRegla}
                  onRenombrar={renombrar}
                  onEliminar={eliminar}
                  {...comunes}
                />
              ))}
              {g.ejecucion.map((x) => (
                <RuleRow
                  key={x.id}
                  id={x.id}
                  nombre={x.titulo}
                  codigo={x.id}
                  parametros={x.parametros}
                  abierta={abierta === x.id}
                  onToggle={() => alternar(x.id)}
                  {...comunes}
                />
              ))}
            </section>
          </Card>
        );
      })}

      {eliminadas.length > 0 && (
        <div className={styles.pie}>
          <span className={styles.pieResumen}>
            {contar(eliminadas.length, 'regla eliminada', 'reglas eliminadas')} ·
            <button
              type="button"
              className={linkClass}
              aria-expanded={verEliminadas}
              onClick={() => setVerEliminadas((v) => !v)}
            >
              {verEliminadas ? 'Ocultar' : 'Ver'}
            </button>
          </span>
          {verEliminadas && (
            <Card>
              <ul className={styles.eliminadas} aria-label="Reglas eliminadas">
                {eliminadas.map((r) => (
                  <li key={r.id}>
                    <span>
                      {r.label} <span className={styles.codigo}>({codigoVisible(r)})</span>
                    </span>
                    {!soloLectura && (
                      <Button size="sm" onClick={() => void restaurar(r)}>
                        Restaurar
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>
      )}

      {apagando && (
        <ConfirmarApagado
          reglas={apagando.reglas}
          grupo={apagando.grupo}
          anchor={apagando.anchor}
          error={errorEstado}
          ocupado={cambiando}
          onCancelar={() => setApagando(null)}
          onConfirmar={(motivo) => void confirmarApagado(motivo)}
        />
      )}

      {!soloLectura && (
        <SaveBar
          count={cambios.length}
          detalle={erroresCliente.size > 0 ? 'Corregí los valores marcados.' : undefined}
          saveLabel="Guardar"
          saving={saving}
          saveDisabled={erroresCliente.size > 0}
          onDiscard={onDescartar}
          onSave={onGuardarClick}
        />
      )}

      {aviso && (
        <Toast tone={aviso.tono} onClose={() => setAviso(null)}>
          {aviso.texto}
        </Toast>
      )}
    </div>
  );
}
