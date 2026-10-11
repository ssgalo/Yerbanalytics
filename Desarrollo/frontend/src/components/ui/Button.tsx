import type { ButtonHTMLAttributes } from 'react';
import { buttonClass, type ButtonSize, type ButtonVariant } from './clases';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
}

/** Botón del sistema. Para un enlace con aspecto de botón, `buttonClass` de `clases.ts`. */
export function Button({ variant, size, className, type = 'button', ...rest }: ButtonProps) {
  return <button type={type} className={buttonClass(variant, size, className)} {...rest} />;
}
