/* Validación de contraseñas: la misma regla que el backend, para avisar antes de enviar. */

export const CLAVE_LARGO_MINIMO = 8;

/** Al menos 8 caracteres y distinta del nombre de usuario. null = válida. */
export function errorClave(clave: string, username: string): string | null {
  if (clave.length < CLAVE_LARGO_MINIMO) return `La contraseña debe tener al menos ${CLAVE_LARGO_MINIMO} caracteres.`;
  if (username && clave.toLowerCase() === username.trim().toLowerCase()) {
    return 'La contraseña no puede ser igual al nombre de usuario.';
  }
  return null;
}
