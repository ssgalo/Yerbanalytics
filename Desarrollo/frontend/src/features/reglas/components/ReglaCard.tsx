/* Tarjeta de una regla: colapsada muestra resumen y valores vigentes; abierta, sus parámetros. */
import { Card } from '@/components/ui/Card';
import { formatearValor } from '@/lib/parametrosValidation';
import type { ParametroRegla, ReglaCatalogo } from '@/types/domain';
import { valorMostrado, type Borrador } from '../borrador';
import { resumenRegla, textoResumen } from '../catalogoView';
import { ParametroRow } from './ParametroRow';
import styles from './Parametros.module.css';

interface ReglaCardProps {
  regla: ReglaCatalogo;
  indice: Map<string, ParametroRegla>;
  nombresReglas: Record<string, string>;
  borrador: Borrador;
  erroresCliente: ReadonlyMap<string, string>;
  erroresServidor: ReadonlyMap<string, string>;
  abierta: boolean;
  onToggle: () => void;
  onEditar: (p: ParametroRegla, texto: string) => void;
  onRestablecer: (p: ParametroRegla) => void;
}

export function ReglaCard({
  regla,
  indice,
  nombresReglas,
  borrador,
  erroresCliente,
  erroresServidor,
  abierta,
  onToggle,
  onEditar,
  onRestablecer,
}: ReglaCardProps) {
  const params = regla.parametros.map((c) => indice.get(c)).filter((p): p is ParametroRegla => !!p);
  const resumen = resumenRegla(regla, indice);
  const sinParametros = params.length === 0;
  const hayError = params.some((p) => erroresCliente.has(p.clave) || erroresServidor.has(p.clave));
  const hayEdicion = params.some((p) => borrador.has(p.clave));

  return (
    <Card className={styles.regla}>
      <button
        type="button"
        className={styles.reglaCabecera}
        aria-expanded={abierta}
        aria-controls={`regla-${regla.id}`}
        onClick={onToggle}
      >
        <span className={abierta ? `${styles.chevron} ${styles.chevronAbierto}` : styles.chevron} aria-hidden="true">
          ›
        </span>
        <span className={styles.reglaNombre}>
          <span className={styles.reglaLabel}>{regla.label}</span>
          <span className={styles.reglaId}>
            {regla.id} · prioridad {regla.prioridad}
          </span>
        </span>

        {/* El valor vigente se ve sin abrir: chips de valor, con la etiqueta como tooltip. */}
        {!sinParametros && (
          <span className={styles.valores} data-testid={`valores-${regla.id}`}>
            {params.map((p) => (
              <span
                key={p.clave}
                className={p.modificado ? `${styles.valorChip} ${styles.valorChipMod}` : styles.valorChip}
                title={p.etiqueta}
              >
                {formatearValor(p, valorMostrado(p, borrador))}
              </span>
            ))}
          </span>
        )}

        <span className={styles.resumen}>
          {hayError && <span className={styles.puntoError} title="Hay errores en esta regla" />}
          {hayEdicion && !hayError && <span className={styles.puntoEditado} title="Cambios sin guardar" />}
          {textoResumen(resumen)}
        </span>
      </button>

      {abierta && (
        <div className={styles.reglaCuerpo} id={`regla-${regla.id}`}>
          {sinParametros ? (
            <p className={styles.sinParametros}>Sin parámetros configurables</p>
          ) : (
            params.map((p) => (
              <ParametroRow
                key={p.clave}
                parametro={p}
                valor={valorMostrado(p, borrador)}
                editado={borrador.has(p.clave)}
                errorCliente={erroresCliente.get(p.clave) ?? null}
                errorServidor={erroresServidor.get(p.clave) ?? null}
                nombresReglas={nombresReglas}
                reglaActual={regla.id}
                onChange={(texto) => onEditar(p, texto)}
                onRestablecer={() => onRestablecer(p)}
              />
            ))
          )}
        </div>
      )}
    </Card>
  );
}
