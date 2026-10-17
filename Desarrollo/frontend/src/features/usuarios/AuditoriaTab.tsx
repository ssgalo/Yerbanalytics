/* ============================================================
   Pestaña Auditoría (HU-20 CA-03): el registro de cambios de seguridad, del más reciente al más
   antiguo. Arriba, el estado de la cadena de hashes: íntegra, o el primer registro que no coincide.
   Es de sólo lectura: la API no tiene ninguna operación para modificarlo.

   Cada columna se filtra y se ordena desde su encabezado, como en una planilla. Para eso se trae
   el registro entero (de a 200, el máximo de la API) y se filtra, ordena y pagina acá: son cambios
   de seguridad, no telemetría, así que se esperan cientos o pocos miles de filas. Si alguna vez
   crece más, el filtro y el orden tienen que pasar a la API.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { ColumnHeader } from '@/components/ui/ColumnHeader';
import { useColumnQuery } from '@/hooks/useColumnQuery';
import { Skeleton } from '@/components/ui/EstadosPantalla';
import { Toast } from '@/components/ui/Toast';
import { linkClass } from '@/components/ui/clases';
import { getSeguridadRepository } from '@/data';
import type { Column } from '@/lib/columnQuery';
import type { RegistroAuditoria, VerificacionAuditoria } from '@/types/seguridad';
import { EmptyRow, TableResult } from './components/TableParts';
import { etiquetaTipo, fechaHoraExacta, resumenDetalle, TAMANIO_PAGINA_AUDITORIA } from './presentacion';
import styles from './Usuarios.module.css';

/** Lo más que devuelve la API por página. */
const TAMANIO_CARGA = 200;
const AZ = { asc: 'De A a Z', desc: 'De Z a A' };
const SIN_REGISTROS: RegistroAuditoria[] = [];

const COLUMNS: Column<RegistroAuditoria>[] = [
  {
    key: 'fecha',
    label: 'Fecha',
    kind: 'date',
    date: (r) => Date.parse(r.ocurridoEn),
    sortLabels: { asc: 'Más antiguos primero', desc: 'Más recientes primero' },
  },
  {
    key: 'autor',
    label: 'Autor',
    kind: 'values',
    value: (r) => r.autorUsername ?? 'sistema',
    sortLabels: AZ,
  },
  {
    key: 'objetivo',
    label: 'Objetivo',
    kind: 'values',
    value: (r) => r.objetivoRef,
    optionLabel: (v, r) => `${v} · ${r.objetivoTipo.toLowerCase()}`,
    searchable: true,
    sortLabels: AZ,
  },
  {
    key: 'cambio',
    label: 'Cambio',
    kind: 'values',
    value: (r) => etiquetaTipo(r.tipo),
    sortLabels: AZ,
  },
  {
    key: 'detalle',
    label: 'Detalle',
    kind: 'values',
    value: resumenDetalle,
    searchable: true,
    sortLabels: AZ,
  },
];

/** El registro completo, del más reciente al más antiguo. */
async function cargarRegistro(): Promise<RegistroAuditoria[]> {
  const repo = getSeguridadRepository();
  const todos: RegistroAuditoria[] = [];
  for (let pagina = 0; ; pagina++) {
    const p = await repo.getAuditoria({ pagina, tamanio: TAMANIO_CARGA });
    todos.push(...p.items);
    if (pagina + 1 >= p.totalPaginas || p.items.length === 0) return todos;
  }
}

export function AuditoriaTab() {
  const [registros, setRegistros] = useState<RegistroAuditoria[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pagina, setPagina] = useState(0);
  const [verificacion, setVerificacion] = useState<VerificacionAuditoria | null>(null);
  const [errorVerificacion, setErrorVerificacion] = useState<string | null>(null);

  const query = useColumnQuery(registros ?? SIN_REGISTROS, COLUMNS);
  const { filters, sort } = query;

  useEffect(() => {
    let activo = true;
    cargarRegistro()
      .then((r) => {
        if (!activo) return;
        setRegistros(r);
        setError(null);
      })
      .catch((e) => activo && setError(e instanceof Error ? e.message : String(e)));
    return () => {
      activo = false;
    };
  }, []);

  // Otro filtro u orden: vuelve a la primera página.
  useEffect(() => setPagina(0), [filters, sort]);

  const verificar = useCallback(async () => {
    setErrorVerificacion(null);
    try {
      setVerificacion(await getSeguridadRepository().verificarAuditoria());
    } catch (e) {
      setErrorVerificacion(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    void verificar();
  }, [verificar]);

  const totalPaginas = Math.max(1, Math.ceil(query.visible.length / TAMANIO_PAGINA_AUDITORIA));
  const enPagina = query.visible.slice(pagina * TAMANIO_PAGINA_AUDITORIA, (pagina + 1) * TAMANIO_PAGINA_AUDITORIA);

  return (
    <div className={styles.page}>
      {/* Siempre ocupa su lugar (aun verificando): que llegue el resultado no corre la tabla. */}
      <Callout tone={!verificacion ? 'neutral' : verificacion.integra ? 'ok' : 'crit'} role="status">
        <span className={styles.integrity}>
          <span>
            {!verificacion
              ? 'Verificando la cadena de registros…'
              : verificacion.integra
                ? `Cadena íntegra · ${verificacion.verificados} registros verificados`
                : `Cadena rota: el registro #${verificacion.primerIdRoto} no coincide con su hash. Alguien modificó el registro por fuera del sistema.`}
          </span>
          {verificacion && (
            <button type="button" className={linkClass} onClick={() => void verificar()}>
              Verificar de nuevo
            </button>
          )}
        </span>
      </Callout>
      {errorVerificacion && (
        <Toast tone="crit" onClose={() => setErrorVerificacion(null)}>
          No se pudo verificar la cadena: {errorVerificacion}
        </Toast>
      )}

      {error ? (
        <Callout tone="crit" role="alert">
          No se pudo cargar la auditoría: {error}
        </Callout>
      ) : !registros ? (
        <div aria-busy="true">
          <span className="sr">Cargando auditoría…</span>
          <Skeleton height={320} />
        </div>
      ) : (
        <section className={styles.tableSection}>
          <header className={styles.sectionHead}>
            <div>
              <h2 className={styles.sectionTitle}>Registro de cambios</h2>
              <p className={styles.sectionHint}>Usuarios, permisos por rol y política de sesión</p>
            </div>
            <TableResult query={query} noun={['registro', 'registros']} />
          </header>
          <div className={styles.tableScroll}>
            <table className={styles.table} aria-label="Registro de auditoría">
              <thead>
                <tr>
                  {COLUMNS.map((c, i) => (
                    <th key={c.key} className={styles.thMenu}>
                      <ColumnHeader column={c} query={query} alignEnd={i === COLUMNS.length - 1} />
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {enPagina.length === 0 && (
                  <EmptyRow query={query} colSpan={COLUMNS.length} empty="Todavía no hay registros." />
                )}
                {enPagina.map((r) => (
                  <tr key={r.id}>
                    <td className={styles.num} title={r.ocurridoEn}>
                      {fechaHoraExacta(r.ocurridoEn)}
                    </td>
                    <td className={r.autorUsername ? undefined : styles.muted}>{r.autorUsername ?? 'sistema'}</td>
                    <td>
                      <div className={styles.userCell}>{r.objetivoRef}</div>
                      <div className={styles.sub}>{r.objetivoTipo.toLowerCase()}</div>
                    </td>
                    <td>{etiquetaTipo(r.tipo)}</td>
                    <td className={styles.detail}>{resumenDetalle(r)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {totalPaginas > 1 && (
            <div className={styles.pagination}>
              <span>
                Página {pagina + 1} de {totalPaginas}
              </span>
              <span className={styles.spacer} />
              <Button size="sm" disabled={pagina <= 0} onClick={() => setPagina((p) => Math.max(0, p - 1))}>
                Anterior
              </Button>
              <Button size="sm" disabled={pagina + 1 >= totalPaginas} onClick={() => setPagina((p) => p + 1)}>
                Siguiente
              </Button>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
