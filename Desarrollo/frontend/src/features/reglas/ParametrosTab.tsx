/* ============================================================
   Pestaña Parámetros: los umbrales del motor agrupados por regla.

   Vista "por regla" (default): reglas por rama y prioridad, colapsadas, con el valor vigente
   a la vista. Vista "por parámetro": cada umbral una sola vez, útil para auditar que no haya
   duplicados. Las dos comparten UN borrador indexado por clave, así que un parámetro
   compartido se edita una vez y se guarda una vez.

   El borrador NO vive acá: lo posee `ReglasPage`, porque esta pestaña se desmonta al ir al
   Inspector y las ediciones sin guardar no pueden perderse en el viaje.
   ============================================================ */
import { Fragment, useMemo, useState, type Dispatch, type SetStateAction } from 'react';
import { ParametrosInvalidosError } from '@/data/parametrosError';
import { contar } from '@/lib/plural';
import type { CambioParametro, CatalogoReglas, ParametroRegla, RamaRegla } from '@/types/domain';
import {
  cambiosDelBorrador,
  editar,
  erroresDelBorrador,
  restablecer,
  sinEnviados,
  valorMostrado,
  type Borrador,
} from './borrador';
import {
  agruparPorConsumidor,
  agruparPorRama,
  DESCRIPCION_RAMA,
  filtrarParametros,
  filtrarReglas,
  indicePorClave,
  nombreConsumidor,
  NOMBRE_RAMA,
  ramaDeConsumidor,
  ORDEN_RAMAS,
  SIN_FILTROS,
  type Filtros,
  type GrupoConsumidor,
} from './catalogoView';
import { ParametroRow } from './components/ParametroRow';
import { ReglaCard } from './components/ReglaCard';
import styles from './components/Parametros.module.css';

interface ParametrosTabProps {
  catalogo: CatalogoReglas;
  saving: boolean;
  /** Guarda los cambios. Rechaza (con `ParametrosInvalidosError` si el servidor los rechaza). */
  onGuardar: (cambios: CambioParametro[]) => Promise<unknown>;
  /** Ediciones sin guardar, indexadas por clave. Las posee el padre para que sobrevivan al cambio de pestaña. */
  borrador: Borrador;
  onBorradorChange: Dispatch<SetStateAction<Borrador>>;
  /** Regla que arranca abierta (viene de "Editar parámetro" en el Inspector). */
  reglaInicial?: string | null;
  /** El rol no tiene `reglas.editar`: campos deshabilitados y sin barra de guardado. */
  soloLectura?: boolean;
}

type Vista = 'regla' | 'parametro';

export function ParametrosTab({
  catalogo,
  saving,
  onGuardar,
  borrador,
  onBorradorChange: setBorrador,
  reglaInicial,
  soloLectura = false,
}: ParametrosTabProps) {
  const bloqueado = saving || soloLectura;
  const [filtros, setFiltros] = useState<Filtros>(SIN_FILTROS);
  const [vista, setVista] = useState<Vista>('regla');
  const [abiertas, setAbiertas] = useState<Set<string>>(new Set(reglaInicial ? [reglaInicial] : []));
  const [erroresServidor, setErroresServidor] = useState<Map<string, string>>(new Map());
  const [erroresGenerales, setErroresGenerales] = useState<string[]>([]);

  const indice = useMemo(() => indicePorClave(catalogo), [catalogo]);
  // También rotula a los consumidores que no son reglas (p. ej. `DespachoRiego` → "Ejecución del riego").
  const nombresReglas = useMemo(
    () => ({
      ...Object.fromEntries(catalogo.parametros.flatMap((p) => p.usadoPor).map((id) => [id, nombreConsumidor(id)])),
      ...Object.fromEntries(catalogo.reglas.map((r) => [r.id, r.label])),
    }),
    [catalogo],
  );
  const erroresCliente = useMemo(() => erroresDelBorrador(catalogo, borrador), [catalogo, borrador]);
  const cambios = useMemo(() => cambiosDelBorrador(catalogo, borrador), [catalogo, borrador]);

  const reglas = useMemo(() => filtrarReglas(catalogo, filtros), [catalogo, filtros]);
  const grupos = useMemo(() => agruparPorRama(reglas), [reglas]);
  const parametros = useMemo(() => filtrarParametros(catalogo, filtros), [catalogo, filtros]);
  // Parámetros que lee algo que no es una regla: grupo aparte, siempre visible (no se colapsa).
  const consumidores = useMemo(() => agruparPorConsumidor(catalogo, parametros), [catalogo, parametros]);

  const patchFiltros = (p: Partial<Filtros>) => setFiltros((f) => ({ ...f, ...p }));

  const onBuscar = (busqueda: string) => {
    patchFiltros({ busqueda });
    // Al empezar a buscar se abren las coincidencias: es lo que el usuario vino a ver.
    if (busqueda.trim() !== '') {
      setAbiertas(new Set(filtrarReglas(catalogo, { ...filtros, busqueda }).map((r) => r.id)));
    }
  };

  const alternar = (id: string) =>
    setAbiertas((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });

  // Editar una clave limpia su error del servidor: ya no corresponde al valor que muestra.
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
        // Se abren las reglas con errores para que el mensaje quede a la vista, junto al parámetro.
        setAbiertas((s) => {
          const n = new Set(s);
          for (const r of catalogo.reglas) if (r.parametros.some((c) => porClave.has(c))) n.add(r.id);
          return n;
        });
      } else {
        setErroresGenerales([e instanceof Error ? e.message : String(e)]);
      }
    }
  };

  // Errores (de cliente o de servidor) en parámetros que los filtros actuales no muestran.
  const clavesConError = useMemo(
    () => new Set([...erroresCliente.keys(), ...erroresServidor.keys()]),
    [erroresCliente, erroresServidor],
  );
  const clavesVisibles = useMemo(() => {
    if (vista === 'parametro') return new Set(parametros.map((p) => p.clave));
    return new Set([...reglas.flatMap((r) => r.parametros), ...consumidores.flatMap((g) => g.parametros.map((p) => p.clave))]);
  }, [vista, parametros, reglas, consumidores]);
  const ocultosConError = [...clavesConError].filter((c) => !clavesVisibles.has(c)).length;

  const verConError = () => {
    setFiltros(SIN_FILTROS);
    setAbiertas((s) => {
      const n = new Set(s);
      for (const r of catalogo.reglas) if (r.parametros.some((c) => clavesConError.has(c))) n.add(r.id);
      return n;
    });
  };

  const grupoConsumidor = (g: GrupoConsumidor) => (
    <section key={g.id} className={styles.grupo} aria-label={g.titulo}>
      <div className={styles.grupoCabecera}>
        <h2 className={styles.grupoTitulo}>{g.titulo}</h2>
        <span className={styles.grupoDesc}>{g.descripcion}</span>
      </div>
      <div className={`${styles.regla} ${styles.listaParametros}`}>
        {g.parametros.map((p) => (
          <ParametroRow
            key={p.clave}
            parametro={p}
            valor={valorMostrado(p, borrador)}
            editado={borrador.has(p.clave)}
            errorCliente={erroresCliente.get(p.clave) ?? null}
            errorServidor={erroresServidor.get(p.clave) ?? null}
            nombresReglas={nombresReglas}
            reglaActual={g.id}
            disabled={bloqueado}
            soloLectura={soloLectura}
            onChange={(texto) => onEditar(p, texto)}
            onRestablecer={() => onRestablecer(p)}
          />
        ))}
      </div>
    </section>
  );

  const hayErroresCliente = erroresCliente.size > 0;
  const puedeGuardar = cambios.length > 0 && !hayErroresCliente && !saving;

  return (
    <>
      <div className={styles.barra}>
        <input
          className={styles.buscador}
          type="search"
          placeholder="Buscar regla o parámetro…"
          aria-label="Buscar regla o parámetro"
          value={filtros.busqueda}
          onChange={(e) => onBuscar(e.target.value)}
        />
        <select
          className={styles.select}
          aria-label="Rama"
          value={filtros.rama}
          onChange={(e) => patchFiltros({ rama: e.target.value as RamaRegla | 'TODAS' })}
        >
          <option value="TODAS">Todas las ramas</option>
          {ORDEN_RAMAS.map((r) => (
            <option key={r} value={r}>
              {NOMBRE_RAMA[r]}
            </option>
          ))}
        </select>
        <label className={styles.check}>
          <input
            type="checkbox"
            checked={filtros.soloModificados}
            onChange={(e) => patchFiltros({ soloModificados: e.target.checked })}
          />
          Sólo modificados
        </label>
        <div className={styles.segmentado} role="group" aria-label="Agrupar">
          <button
            type="button"
            className={vista === 'regla' ? `${styles.seg} ${styles.segActivo}` : styles.seg}
            aria-pressed={vista === 'regla'}
            onClick={() => setVista('regla')}
          >
            Por regla
          </button>
          <button
            type="button"
            className={vista === 'parametro' ? `${styles.seg} ${styles.segActivo}` : styles.seg}
            aria-pressed={vista === 'parametro'}
            onClick={() => setVista('parametro')}
          >
            Por parámetro
          </button>
        </div>
      </div>

      {erroresGenerales.length > 0 && (
        <div className={styles.banner} role="alert">
          {erroresGenerales.map((m) => (
            <div key={m}>{m}</div>
          ))}
        </div>
      )}

      {vista === 'regla' ? (
        grupos.length === 0 && consumidores.length === 0 ? (
          <div className={styles.vacio}>Ninguna regla coincide con los filtros.</div>
        ) : (
          <>
            {grupos.map((g) => (
              <Fragment key={g.rama}>
                <section className={styles.grupo}>
                  <div className={styles.grupoCabecera}>
                    <h2 className={styles.grupoTitulo}>
                      {NOMBRE_RAMA[g.rama]} · {contar(g.reglas.length, 'regla', 'reglas')}
                    </h2>
                    <span className={styles.grupoDesc}>{DESCRIPCION_RAMA[g.rama]}</span>
                  </div>
                  {g.reglas.map((r) => (
                    <ReglaCard
                      key={r.id}
                      regla={r}
                      indice={indice}
                      nombresReglas={nombresReglas}
                      borrador={borrador}
                      erroresCliente={erroresCliente}
                      erroresServidor={erroresServidor}
                      abierta={abiertas.has(r.id)}
                      disabled={bloqueado}
                      soloLectura={soloLectura}
                      onToggle={() => alternar(r.id)}
                      onEditar={onEditar}
                      onRestablecer={onRestablecer}
                    />
                  ))}
                </section>
                {/* Lo que lee parámetros sin ser una regla va justo después de la rama a la que pertenece. */}
                {consumidores.filter((c) => ramaDeConsumidor(c.id) === g.rama).map(grupoConsumidor)}
              </Fragment>
            ))}
            {/* Consumidores de una rama que los filtros ocultaron (o desconocida): al final. */}
            {consumidores.filter((c) => !grupos.some((g) => g.rama === ramaDeConsumidor(c.id))).map(grupoConsumidor)}
          </>
        )
      ) : parametros.length === 0 ? (
        <div className={styles.vacio}>Ningún parámetro coincide con los filtros.</div>
      ) : (
        <section className={styles.grupo}>
          <div className={styles.grupoCabecera}>
            <h2 className={styles.grupoTitulo}>
              {contar(parametros.length, 'parámetro', 'parámetros')}
            </h2>
            <span className={styles.grupoDesc}>Cada umbral una sola vez, con las reglas que lo usan</span>
          </div>
          <div className={`${styles.regla} ${styles.listaParametros}`}>
            {parametros.map((p) => (
              <ParametroRow
                key={p.clave}
                parametro={p}
                valor={valorMostrado(p, borrador)}
                editado={borrador.has(p.clave)}
                errorCliente={erroresCliente.get(p.clave) ?? null}
                errorServidor={erroresServidor.get(p.clave) ?? null}
                nombresReglas={nombresReglas}
                disabled={bloqueado}
                soloLectura={soloLectura}
                onChange={(texto) => onEditar(p, texto)}
                onRestablecer={() => onRestablecer(p)}
              />
            ))}
          </div>
        </section>
      )}

      {soloLectura ? (
        <div className={styles.acciones}>
          <span className={styles.aviso}>
            Sólo lectura: tu rol puede ver los parámetros del motor pero no modificarlos.
          </span>
        </div>
      ) : (
        <div className={styles.acciones}>
          <span className={hayErroresCliente || ocultosConError > 0 ? styles.avisoErr : styles.aviso}>
            {hayErroresCliente
              ? 'Corregí los valores marcados para poder guardar.'
              : cambios.length === 0
                ? 'Sin cambios pendientes.'
                : `${contar(cambios.length, 'cambio', 'cambios')} sin guardar`}
            {ocultosConError > 0 && (
              <>
                {' '}
                {ocultosConError === 1
                  ? '1 parámetro con error queda oculto por los filtros.'
                  : `${ocultosConError} parámetros con error quedan ocultos por los filtros.`}{' '}
                <button type="button" className={styles.btnLink} onClick={verConError}>
                  Ver los que tienen error
                </button>
              </>
            )}
          </span>
          <span className={styles.spacer} />
          <button
            type="button"
            className={styles.btnSecondary}
            disabled={saving || (borrador.size === 0 && erroresServidor.size === 0)}
            onClick={onDescartar}
          >
            Descartar
          </button>
          <button type="button" className={styles.btnPrimary} disabled={!puedeGuardar} onClick={onGuardarClick}>
            {saving ? 'Guardando…' : 'Guardar cambios'}
          </button>
        </div>
      )}
    </>
  );
}
