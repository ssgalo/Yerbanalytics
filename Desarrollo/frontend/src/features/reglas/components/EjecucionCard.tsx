/* Tarjeta de un servicio que ejecuta lo que las reglas deciden: sus parámetros, sin interruptor. */
import { Card } from '@/components/ui/Card';
import { Chip } from '@/components/ui/Chip';
import type { ParametroRegla } from '@/types/domain';
import { valorMostrado, type Borrador } from '../borrador';
import type { GrupoConsumidor } from '../catalogoView';
import { ParametroInline } from './ParametroInline';
import styles from './ReglasTab.module.css';

interface EjecucionCardProps {
  grupo: GrupoConsumidor;
  borrador: Borrador;
  erroresCliente: ReadonlyMap<string, string>;
  erroresServidor: ReadonlyMap<string, string>;
  disabled?: boolean;
  soloLectura?: boolean;
  onEditar: (p: ParametroRegla, texto: string) => void;
  onRestablecer: (p: ParametroRegla) => void;
}

export function EjecucionCard({
  grupo,
  borrador,
  erroresCliente,
  erroresServidor,
  disabled,
  soloLectura,
  onEditar,
  onRestablecer,
}: EjecucionCardProps) {
  return (
    <Card className={styles.card}>
      <article aria-label={grupo.titulo} data-consumidor={grupo.id}>
        <header className={styles.cabecera}>
          <div className={styles.titulos}>
            <h3 className={styles.nombre}>{grupo.titulo}</h3>
            <span className={styles.chips}>
              <Chip tone="line">Ejecución</Chip>
            </span>
          </div>
          <span className={styles.estadoTexto}>Sin interruptor: ejecuta</span>
        </header>
        {grupo.descripcion && <p className={styles.descripcion}>{grupo.descripcion}</p>}
        <div className={styles.parametros}>
          {grupo.parametros.map((p) => (
            <ParametroInline
              key={p.clave}
              parametro={p}
              valor={valorMostrado(p, borrador)}
              editado={borrador.has(p.clave)}
              errorCliente={erroresCliente.get(p.clave) ?? null}
              errorServidor={erroresServidor.get(p.clave) ?? null}
              disabled={disabled}
              soloLectura={soloLectura}
              onChange={(texto) => onEditar(p, texto)}
              onRestablecer={() => onRestablecer(p)}
            />
          ))}
        </div>
      </article>
    </Card>
  );
}
