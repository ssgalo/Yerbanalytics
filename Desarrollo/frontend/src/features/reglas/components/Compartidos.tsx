/* Bloque "Compartidos del grupo": los parámetros que usan varias piezas, editados una sola vez. */
import { Card } from '@/components/ui/Card';
import type { ParametroRegla } from '@/types/domain';
import { valorMostrado, type Borrador } from '../borrador';
import { ParametroInline } from './ParametroInline';
import styles from './ReglasTab.module.css';

interface CompartidosProps {
  parametros: ParametroRegla[];
  /** id de pieza o de servicio → nombre legible. */
  nombres: Record<string, string>;
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
  borrador,
  erroresCliente,
  erroresServidor,
  disabled,
  soloLectura,
  onEditar,
  onRestablecer,
}: CompartidosProps) {
  return (
    <Card className={styles.card}>
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
              pie={
                <div className={styles.usadoPor}>
                  Compartido con {p.usadoPor.length} reglas:{' '}
                  {p.usadoPor.map((id) => nombres[id] ?? id).join(', ')}
                </div>
              }
              onChange={(texto) => onEditar(p, texto)}
              onRestablecer={() => onRestablecer(p)}
            />
          ))}
        </div>
      </section>
    </Card>
  );
}
