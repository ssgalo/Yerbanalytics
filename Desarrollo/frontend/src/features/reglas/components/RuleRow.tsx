/*
 * Fila de una pieza del motor: nombre vigente con su código, menú ⋮ (Editar nombre, Eliminar) e interruptor. Si tiene
 * parámetros se despliega y los muestra; sin parámetros no tiene flecha ni reacciona al clic. Una fila de ejecución
 * (servicio sin interruptor) no tiene menú ni interruptor.
 */
import { useRef, useState, type FormEvent } from 'react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Field';
import { Icon } from '@/components/ui/Icon';
import { Popover } from '@/components/ui/Popover';
import { Switch } from '@/components/ui/Seleccion';
import type { ParametroRegla, ReglaCatalogo } from '@/types/domain';
import { idCopia, valorMostrado, type Borrador } from '../borrador';
import { fechaCorta, idFila } from './anclas';
import { RuleParam } from './RuleParam';
import styles from './RuleCatalog.module.css';

interface RuleRowProps {
  id: string;
  nombre: string;
  /** Lo que va entre paréntesis: código o nombre interno. */
  codigo: string;
  parametros: ParametroRegla[];
  /** La pieza con interruptor; ausente en una fila de ejecución. */
  regla?: ReglaCatalogo;
  abierta: boolean;
  onToggle: () => void;
  borrador: Borrador;
  /** Errores por copia (`idCopia`). */
  errores: ReadonlyMap<string, string>;
  /** Hay un guardado en vuelo. */
  disabled?: boolean;
  soloLectura?: boolean;
  onEditar: (p: ParametroRegla, texto: string) => void;
  onCambiarEstado?: (regla: ReglaCatalogo, activa: boolean, anchor: HTMLElement) => void;
  onRenombrar?: (regla: ReglaCatalogo, etiqueta: string | null) => Promise<void>;
  onEliminar?: (regla: ReglaCatalogo) => Promise<void>;
}

export function RuleRow({
  id,
  nombre,
  codigo,
  parametros,
  regla,
  abierta,
  onToggle,
  borrador,
  errores,
  disabled,
  soloLectura,
  onEditar,
  onCambiarEstado,
  onRenombrar,
  onEliminar,
}: RuleRowProps) {
  const [menu, setMenu] = useState<HTMLElement | null>(null);
  const [renombrando, setRenombrando] = useState(false);
  const [eliminando, setEliminando] = useState(false);
  const [ocupado, setOcupado] = useState(false);
  const kebab = useRef<HTMLButtonElement>(null);
  const interruptor = useRef<HTMLSpanElement>(null);

  const conParametros = parametros.length > 0;
  const sucia = parametros.some((p) => borrador.has(idCopia(p)));
  const conError = parametros.some((p) => errores.has(idCopia(p)));
  const apagada = regla !== undefined && !regla.activa;
  const nombreAccesible = `${nombre} (${codigo})`;
  const detalleApagada =
    apagada && regla.estado
      ? `Apagada por ${regla.estado.updatedBy} el ${fechaCorta(regla.estado.updatedTs)}${regla.estado.motivo ? `. Motivo: ${regla.estado.motivo}` : ''}`
      : undefined;

  const titulo = (
    <span className={styles.nombre} title={detalleApagada}>
      {nombre} <span className={styles.codigo}>({codigo})</span>
    </span>
  );
  const marca = sucia && (
    <span
      className={conError ? `${styles.sucia} ${styles.suciaError}` : styles.sucia}
      title={conError ? 'Valores inválidos' : 'Cambios sin guardar'}
      role="img"
      aria-label={conError ? 'Valores inválidos' : 'Cambios sin guardar'}
    />
  );

  const renombrar = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!regla || !onRenombrar) return;
    const texto = String(new FormData(e.currentTarget).get('etiqueta') ?? '').trim();
    setOcupado(true);
    try {
      await onRenombrar(regla, texto === '' ? null : texto);
      setRenombrando(false);
      kebab.current?.focus();
    } finally {
      setOcupado(false);
    }
  };

  const eliminar = async () => {
    if (!regla || !onEliminar) return;
    setOcupado(true);
    try {
      await onEliminar(regla);
    } finally {
      setOcupado(false);
      setEliminando(false);
    }
  };

  const conMenu = regla !== undefined && !soloLectura && onRenombrar !== undefined;

  return (
    <div
      id={idFila(id)}
      className={[styles.fila, abierta && conParametros && styles.abierta, apagada && styles.apagada]
        .filter(Boolean)
        .join(' ')}
      data-activa={regla ? regla.activa : undefined}
    >
      <div className={styles.renglon}>
        {renombrando && regla ? (
          <form className={styles.renombrar} onSubmit={renombrar}>
            <Input
              name="etiqueta"
              aria-label="Nombre de la regla"
              defaultValue={regla.label}
              placeholder={regla.etiquetaFabrica}
              maxLength={80}
              autoFocus
              onFocus={(e) => e.currentTarget.select()}
              onKeyDown={(e) => {
                if (e.key === 'Escape') {
                  setRenombrando(false);
                  kebab.current?.focus();
                }
              }}
              disabled={ocupado}
            />
            <Button type="submit" variant="primary" size="sm" disabled={ocupado}>
              Guardar
            </Button>
            <Button
              variant="ghost"
              size="sm"
              disabled={ocupado}
              onClick={() => {
                setRenombrando(false);
                kebab.current?.focus();
              }}
            >
              Cancelar
            </Button>
          </form>
        ) : conParametros ? (
          <button type="button" className={styles.desplegar} aria-expanded={abierta} onClick={onToggle}>
            <span className={styles.flecha}>
              <Icon name="chevron-right" size={16} strokeWidth={2} />
            </span>
            {titulo}
            {marca}
          </button>
        ) : (
          <div className={`${styles.desplegar} ${styles.estatica}`}>
            <span className={styles.flecha} />
            {titulo}
          </div>
        )}

        {conMenu && !renombrando && (
          <button
            ref={kebab}
            type="button"
            className={styles.kebab}
            aria-label={`Más acciones de ${nombreAccesible}`}
            aria-haspopup="menu"
            aria-expanded={menu !== null}
            onClick={(e) => setMenu(menu ? null : e.currentTarget)}
          >
            <Icon name="more" size={16} />
          </button>
        )}
        {regla && onCambiarEstado && !renombrando && (
          <span className={styles.interruptor} ref={interruptor}>
            <Switch
              checked={regla.activa}
              label={`${regla.activa ? 'Apagar' : 'Prender'} ${regla.label}`}
              disabled={soloLectura || disabled}
              onChange={(activa) => {
                const boton = interruptor.current?.querySelector('button');
                if (boton) onCambiarEstado(regla, activa, boton);
              }}
            />
          </span>
        )}
      </div>

      {menu && regla && (
        <Popover anchor={menu} onClose={() => setMenu(null)} label={`Acciones de ${nombreAccesible}`} width={180} alignEnd flush>
          <div role="menu" className={styles.menu}>
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                setMenu(null);
                setRenombrando(true);
              }}
            >
              <Icon name="edit" size={15} />
              Editar nombre
            </button>
            {regla.proteccion === null && (
              <>
                <hr />
                <button
                  type="button"
                  role="menuitem"
                  className={styles.borrar}
                  onClick={() => {
                    setMenu(null);
                    setEliminando(true);
                  }}
                >
                  <Icon name="trash" size={15} />
                  Eliminar
                </button>
              </>
            )}
          </div>
        </Popover>
      )}

      {abierta && conParametros && (
        <div className={styles.parametros}>
          {parametros.map((p) => (
            <RuleParam
              key={idCopia(p)}
              parametro={p}
              valor={valorMostrado(p, borrador)}
              editado={borrador.has(idCopia(p))}
              error={errores.get(idCopia(p)) ?? null}
              disabled={disabled}
              soloLectura={soloLectura}
              onChange={(texto) => onEditar(p, texto)}
            />
          ))}
        </div>
      )}

      {eliminando && regla && (
        <div className={styles.confirmar} role="alertdialog" aria-label={`Eliminar ${nombre}`}>
          <span className={styles.confirmarTexto}>¿Eliminar «{nombre}»?</span>
          <Button size="sm" disabled={ocupado} onClick={() => setEliminando(false)} autoFocus>
            Cancelar
          </Button>
          <Button size="sm" variant="danger" disabled={ocupado} onClick={() => void eliminar()}>
            Eliminar
          </Button>
        </div>
      )}
    </div>
  );
}
