/* Tarjeta de una pieza del motor: nombre y código, efecto, interruptor, aviso si está apagada y sus parámetros en línea. */
import { useRef } from 'react';
import { Callout } from '@/components/ui/Callout';
import { Card } from '@/components/ui/Card';
import { Chip, type ChipTone } from '@/components/ui/Chip';
import { Switch } from '@/components/ui/Seleccion';
import { formatearValor } from '@/lib/parametrosValidation';
import type { EfectoRegla, ParametroRegla, ReglaCatalogo } from '@/types/domain';
import { valorMostrado, type Borrador } from '../borrador';
import { fechaCorta, idTarjeta, irAlParametro } from './anclas';
import { ParametroInline } from './ParametroInline';
import styles from './ReglasTab.module.css';

/** El efecto no usa colores de estado: Actúa = info, Avisa = neutro, Protege = destacado. */
const EFECTO: Record<EfectoRegla, { texto: string; tono: ChipTone }> = {
  ACTS: { texto: 'Actúa', tono: 'info' },
  WARNS: { texto: 'Avisa', tono: 'line' },
  PROTECTS: { texto: 'Protege', tono: 'dark' },
};

interface ReglaCardProps {
  regla: ReglaCatalogo;
  indice: Map<string, ParametroRegla>;
  borrador: Borrador;
  erroresCliente: ReadonlyMap<string, string>;
  erroresServidor: ReadonlyMap<string, string>;
  /** Hay un guardado o un cambio de estado en vuelo. */
  disabled?: boolean;
  /** Sin `reglas.editar`. */
  soloLectura?: boolean;
  /** Llegó por `?regla=`: se resalta. */
  destacada?: boolean;
  /** Orden por prioridad del motor: la muestra, discreta, para que el orden se entienda. */
  mostrarPrioridad?: boolean;
  onCambiarEstado: (regla: ReglaCatalogo, activa: boolean, anchor: HTMLElement) => void;
  onEditar: (p: ParametroRegla, texto: string) => void;
  onRestablecer: (p: ParametroRegla) => void;
}

export function ReglaCard({
  regla,
  indice,
  borrador,
  erroresCliente,
  erroresServidor,
  disabled,
  soloLectura,
  destacada,
  mostrarPrioridad,
  onCambiarEstado,
  onEditar,
  onRestablecer,
}: ReglaCardProps) {
  const interruptor = useRef<HTMLSpanElement>(null);
  const params = regla.parametros.map((c) => indice.get(c)).filter((p): p is ParametroRegla => !!p);
  const efecto = EFECTO[regla.efecto];
  const esProteccion = regla.proteccion !== null;
  const titulo = idTarjeta(regla.id);

  return (
    <Card className={destacada ? `${styles.card} ${styles.destacada}` : styles.card}>
      <article id={titulo} aria-labelledby={`${titulo}-nombre`} data-activa={regla.activa}>
        <header className={styles.cabecera}>
          <div className={styles.titulos}>
            <h3 id={`${titulo}-nombre`} className={styles.nombre}>
              {regla.label}
              {regla.codigo && <span className={styles.codigo}> · {regla.codigo}</span>}
              {mostrarPrioridad && (
                <span
                  className={styles.prioridad}
                  aria-label={`Prioridad del motor ${regla.prioridad}`}
                >
                  {regla.prioridad}
                </span>
              )}
            </h3>
            <span className={styles.chips}>
              <Chip tone={efecto.tono}>{efecto.texto}</Chip>
              {!regla.activaPorDefecto && <Chip tone="line">Retirada</Chip>}
            </span>
          </div>
          <span className={styles.interruptor} ref={interruptor}>
            <span className={styles.estadoTexto}>{regla.activa ? 'Activa' : 'Apagada'}</span>
            <Switch
              checked={regla.activa}
              label={`${regla.activa ? 'Apagar' : 'Prender'} ${regla.label}`}
              disabled={disabled || soloLectura}
              onChange={(activa) => {
                const boton = interruptor.current?.querySelector('button');
                if (boton) onCambiarEstado(regla, activa, boton);
              }}
            />
          </span>
        </header>

        {!regla.activa && (
          <Callout tone={esProteccion ? 'crit' : 'neutral'} icon="lock">
            {regla.estado ? (
              <>
                Apagada por {regla.estado.updatedBy} el {fechaCorta(regla.estado.updatedTs)}.
                {regla.estado.motivo && <> Motivo: {regla.estado.motivo}.</>}
              </>
            ) : (
              <>Apagada de fábrica: la regla está retirada y no corre hasta que se la prenda.</>
            )}
            {esProteccion && (
              <>
                {' '}
                <b>Consecuencia:</b> {regla.proteccion}
              </>
            )}
          </Callout>
        )}
        {regla.apagadaPorDemo && (
          <Callout tone="warn" role="status">
            Apagada por la demo: el simulador la apagó en modo demo y mientras tanto no corre,
            aunque acá figure {regla.activa ? 'activa' : 'apagada'}.
          </Callout>
        )}

        {params.length > 0 && (
          <div className={styles.parametros}>
            {params.map((p) =>
              p.usadoPor.length > 1 ? (
                <div key={p.clave} className={styles.compartido} data-clave={p.clave}>
                  <span className={styles.compartidoNombre}>{p.etiqueta}</span>
                  <b>{formatearValor(p, valorMostrado(p, borrador))}</b>
                  <span className={styles.chipCompartido}>
                    Compartido con {p.usadoPor.length} reglas
                  </span>
                  <button
                    type="button"
                    className={styles.link}
                    onClick={() => irAlParametro(p.clave)}
                  >
                    Editar en Compartidos
                  </button>
                </div>
              ) : (
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
              ),
            )}
          </div>
        )}
      </article>
    </Card>
  );
}
