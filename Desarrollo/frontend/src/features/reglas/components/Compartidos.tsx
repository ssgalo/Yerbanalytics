/* Bloque "Compartidos del grupo": los parámetros que usan varias piezas, editados una sola vez. */
import { useState } from 'react';
import { buttonClass } from '@/components/ui/clases';
import { Chip } from '@/components/ui/Chip';
import { Icon } from '@/components/ui/Icon';
import { Popover } from '@/components/ui/Popover';
import { Card } from '@/components/ui/Card';
import type { ParametroRegla } from '@/types/domain';
import { valorMostrado, type Borrador } from '../borrador';
import { ParametroInline } from './ParametroInline';
import styles from './ReglasTab.module.css';

interface CompartidosProps {
  parametros: ParametroRegla[];
  /** id de pieza o de servicio → nombre legible. */
  nombres: Record<string, string>;
  /** id de pieza → código corto (R-01…); las que no tienen, sin chip. */
  codigos?: Record<string, string>;
  borrador: Borrador;
  erroresCliente: ReadonlyMap<string, string>;
  erroresServidor: ReadonlyMap<string, string>;
  disabled?: boolean;
  soloLectura?: boolean;
  onEditar: (p: ParametroRegla, texto: string) => void;
  onRestablecer: (p: ParametroRegla) => void;
}

export function Compartidos({
  parametros,
  nombres,
  codigos = {},
  borrador,
  erroresCliente,
  erroresServidor,
  disabled,
  soloLectura,
  onEditar,
  onRestablecer,
}: CompartidosProps) {
  return (
    <Card className={`${styles.card} ${styles.compartidos}`}>
      <section aria-label="Compartidos del grupo">
        <h3 className={styles.nombre}>Compartidos del grupo</h3>
        <p className={styles.descripcion}>
          Un solo valor que usan varias reglas: se edita acá y cambia en todas.
        </p>
        <div className={styles.parametros}>
          {parametros.map((p) => (
            <ParametroInline
              key={p.clave}
              parametro={p}
              valor={valorMostrado(p, borrador)}
              editado={borrador.has(p.clave)}
              errorCliente={erroresCliente.get(p.clave) ?? null}
              errorServidor={erroresServidor.get(p.clave) ?? null}
              disabled={disabled}
              soloLectura={soloLectura}
              pie={<UsadoPor ids={p.usadoPor} nombres={nombres} codigos={codigos} />}
              onChange={(texto) => onEditar(p, texto)}
              onRestablecer={() => onRestablecer(p)}
            />
          ))}
        </div>
      </section>
    </Card>
  );
}

function UsadoPor({
  ids,
  nombres,
  codigos,
}: {
  ids: string[];
  nombres: Record<string, string>;
  codigos: Record<string, string>;
}) {
  const [ancla, setAncla] = useState<HTMLButtonElement | null>(null);
  const [abierto, setAbierto] = useState(false);
  return (
    <div className={styles.usadoPor}>
      <button
        type="button"
        ref={setAncla}
        className={buttonClass('ghost', 'sm')}
        aria-haspopup="dialog"
        aria-expanded={abierto}
        onClick={() => setAbierto((v) => !v)}
      >
        Lo usan {ids.length} reglas <Icon name="chevron-down" size={14} />
      </button>
      {abierto && (
        <Popover
          anchor={ancla}
          onClose={() => setAbierto(false)}
          label="Reglas que lo usan"
          width={300}
        >
          <ul className={styles.listaReglas}>
            {ids.map((id) => (
              <li key={id}>
                {codigos[id] && <Chip tone="line">{codigos[id]}</Chip>}
                <span>{nombres[id] ?? id}</span>
              </li>
            ))}
          </ul>
        </Popover>
      )}
    </div>
  );
}
