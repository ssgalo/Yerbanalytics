import { cx } from './estado';
import botones from './Button.module.css';

export type ButtonVariant = 'secondary' | 'primary' | 'ghost' | 'danger';
export type ButtonSize = 'md' | 'sm';

/**
 * Clases del botón, para dárselas a algo que no es un `<button>` (un `<Link>` de navegación).
 * Primario: una sola acción principal por vista. Peligro: destructivo, siempre con confirmación.
 */
export function buttonClass(variant: ButtonVariant = 'secondary', size: ButtonSize = 'md', extra?: string) {
  return cx(botones.btn, variant !== 'secondary' && botones[variant], size === 'sm' && botones.sm, extra);
}

/** Clase de la acción con aspecto de enlace (p. ej. "Reintentar" dentro de un aviso). */
export const linkClass = botones.link;

/** id de la ayuda/error de un campo, para `aria-describedby` del control. */
export const ayudaDe = (id: string) => `${id}-ayuda`;
