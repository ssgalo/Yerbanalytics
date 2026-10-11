/* ============================================================
   Pestaña Auditoría (HU-20 CA-03): el registro de cambios de seguridad, del más reciente al más
   antiguo, paginado y filtrable por autor, usuario/rol objetivo, tipo y rango de fechas. Arriba,
   el estado de la cadena de hashes: íntegra, o el primer registro que no coincide.
   Es de sólo lectura: la API no tiene ninguna operación para modificarlo.
   ============================================================ */
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Card } from '@/components/ui/Card';
import { getSeguridadRepository } from '@/data';
import { ETIQUETA_TIPO_AUDITORIA } from '@/lib/catalogoSeguridad';
import type { PaginaAuditoria, TipoAuditoria, VerificacionAuditoria } from '@/types/seguridad';
import {
  aConsulta,
  etiquetaTipo,
  fechaHoraExacta,
  resumenDetalle,
  SIN_FILTROS_AUDITORIA as SIN_FILTROS,
  type FormFiltrosAuditoria as FormFiltros,
} from './presentacion';
import styles from './Usuarios.module.css';

export function AuditoriaTab() {
  const [form, setForm] = useState<FormFiltros>(SIN_FILTROS);
  const [aplicados, setAplicados] = useState<FormFiltros>(SIN_FILTROS);
  const [pagina, setPagina] = useState(0);
  const [datos, setDatos] = useState<PaginaAuditoria | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [verificacion, setVerificacion] = useState<VerificacionAuditoria | null>(null);
  const [errorVerificacion, setErrorVerificacion] = useState<string | null>(null);

  useEffect(() => {
    let activo = true;
    getSeguridadRepository()
      .getAuditoria(aConsulta(aplicados, pagina))
      .then((p) => {
        if (!activo) return;
        setDatos(p);
        setError(null);
      })
      .catch((e) => activo && setError(e instanceof Error ? e.message : String(e)));
    return () => {
      activo = false;
    };
  }, [aplicados, pagina]);

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

  const patch = (p: Partial<FormFiltros>) => setForm((f) => ({ ...f, ...p }));

  const filtrar = (e: FormEvent) => {
    e.preventDefault();
    setPagina(0);
    setAplicados(form);
  };

  const limpiar = () => {
    setForm(SIN_FILTROS);
    setPagina(0);
    setAplicados(SIN_FILTROS);
  };

  return (
    <div className={styles.page}>
      {verificacion && (
        <div
          role="status"
          className={`${styles.integridad} ${verificacion.integra ? styles.integra : styles.rota}`}
        >
          <span>
            {verificacion.integra
              ? `Cadena íntegra · ${verificacion.verificados} registros verificados`
              : `Cadena rota: el registro #${verificacion.primerIdRoto} no coincide con su hash. Alguien modificó el registro por fuera del sistema.`}
          </span>
          <span className={styles.spacer} />
          <button type="button" className={styles.btnLink} onClick={() => void verificar()}>
            Verificar de nuevo
          </button>
        </div>
      )}
      {errorVerificacion && (
        <div role="alert" className={styles.errorBox}>
          No se pudo verificar la cadena: {errorVerificacion}
        </div>
      )}

      <Card className={styles.section}>
        <form className={styles.filtros} onSubmit={filtrar} aria-label="Filtros de auditoría">
          <label className={styles.group}>
            <span className={styles.label}>Autor</span>
            <input
              className={styles.input}
              value={form.autor}
              placeholder="admin"
              onChange={(e) => patch({ autor: e.target.value })}
            />
          </label>
          <label className={styles.group}>
            <span className={styles.label}>Usuario o rol objetivo</span>
            <input
              className={styles.input}
              value={form.objetivo}
              placeholder="jperez"
              onChange={(e) => patch({ objetivo: e.target.value })}
            />
          </label>
          <label className={styles.group}>
            <span className={styles.label}>Tipo de cambio</span>
            <select
              className={styles.select}
              value={form.tipo}
              onChange={(e) => patch({ tipo: e.target.value as TipoAuditoria | '' })}
            >
              <option value="">Todos</option>
              {Object.entries(ETIQUETA_TIPO_AUDITORIA).map(([tipo, etiqueta]) => (
                <option key={tipo} value={tipo}>
                  {etiqueta}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.group}>
            <span className={styles.label}>Desde</span>
            <input
              className={styles.input}
              type="date"
              value={form.desde}
              onChange={(e) => patch({ desde: e.target.value })}
            />
          </label>
          <label className={styles.group}>
            <span className={styles.label}>Hasta</span>
            <input
              className={styles.input}
              type="date"
              value={form.hasta}
              onChange={(e) => patch({ hasta: e.target.value })}
            />
          </label>
          <button type="submit" className={styles.btnPrimary}>
            Filtrar
          </button>
          <button type="button" className={styles.btnSecondary} onClick={limpiar}>
            Limpiar
          </button>
        </form>
      </Card>

      {error ? (
        <div className={styles.state} style={{ color: 'var(--crit)' }}>
          No se pudo cargar la auditoría: {error}
        </div>
      ) : !datos ? (
        <div className={styles.state}>Cargando auditoría…</div>
      ) : (
        <Card className={styles.tableCard}>
          <div className={styles.tableScroll}>
            <table className={styles.table} aria-label="Registro de auditoría">
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Autor</th>
                  <th>Objetivo</th>
                  <th>Cambio</th>
                  <th>Detalle</th>
                </tr>
              </thead>
              <tbody>
                {datos.items.length === 0 && (
                  <tr>
                    <td colSpan={5} className={styles.empty}>
                      Ningún registro coincide con los filtros.
                    </td>
                  </tr>
                )}
                {datos.items.map((r) => (
                  <tr key={r.id}>
                    <td className={styles.mono} title={r.ocurridoEn}>
                      {fechaHoraExacta(r.ocurridoEn)}
                    </td>
                    <td className={r.autorUsername ? undefined : styles.muted}>{r.autorUsername ?? 'sistema'}</td>
                    <td>
                      <div className={styles.username}>{r.objetivoRef}</div>
                      <div className={styles.sub}>{r.objetivoTipo.toLowerCase()}</div>
                    </td>
                    <td>{etiquetaTipo(r.tipo)}</td>
                    <td className={styles.detalle}>{resumenDetalle(r)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className={styles.paginacion}>
            <span>
              Página {datos.pagina + 1} de {datos.totalPaginas} · {datos.total}{' '}
              {datos.total === 1 ? 'registro' : 'registros'}
            </span>
            <span className={styles.spacer} />
            <button
              type="button"
              className={styles.btnLink}
              disabled={datos.pagina <= 0}
              onClick={() => setPagina((p) => Math.max(0, p - 1))}
            >
              Anterior
            </button>
            <button
              type="button"
              className={styles.btnLink}
              disabled={datos.pagina + 1 >= datos.totalPaginas}
              onClick={() => setPagina((p) => p + 1)}
            >
              Siguiente
            </button>
          </div>
        </Card>
      )}
    </div>
  );
}
