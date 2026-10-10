/* ============================================================
   Sesión y gestión de seguridad contra el backend real (`/api/auth`, `/api/usuarios`,
   `/api/roles`, `/api/seguridad/politica`, `/api/auditoria`). Se activa con
   VITE_DATA_SOURCE=http. Las formas JSON son las del contrato del cambio add-login-y-permisos.
   ============================================================ */
import type { SeguridadRepository } from '@/data/seguridadRepository';
import {
  CredencialesIncorrectasError,
  OperacionRechazadaError,
  SesionCerradaError,
  SesionNoEstablecidaError,
} from '@/data/seguridadErrores';
import type {
  EdicionUsuario,
  FiltrosAuditoria,
  NuevoUsuario,
  PaginaAuditoria,
  PerfilSesion,
  PermisoCatalogo,
  PoliticaSesion,
  Rol,
  RolPermisos,
  Usuario,
  UsuarioDemo,
  VerificacionAuditoria,
} from '@/types/seguridad';
import { apiFetch } from './apiFetch';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export class HttpSeguridadRepository implements SeguridadRepository {
  constructor(private readonly baseUrl: string) {}

  /**
   * Lanza el error del cuerpo (`{ error }`) para un 400/409 y uno genérico para el resto.
   * Los 401/403 ya los convirtió `apiFetch`.
   */
  private async fallar(res: Response, accion: string): Promise<never> {
    const cuerpo = (await res.json().catch(() => null)) as { error?: string } | null;
    if (res.status === 400 || res.status === 409) {
      throw new OperacionRechazadaError(res.status, cuerpo?.error ?? `No se pudo ${accion}.`);
    }
    throw new Error(cuerpo?.error ?? `Error ${res.status} al ${accion}`);
  }

  private async json<T>(url: string, accion: string, init?: RequestInit): Promise<T> {
    const res = await apiFetch(url, init);
    if (!res.ok) return this.fallar(res, accion);
    return (await res.json()) as T;
  }

  private async vacio(url: string, accion: string, init?: RequestInit): Promise<void> {
    const res = await apiFetch(url, init);
    if (!res.ok) await this.fallar(res, accion);
  }

  /* ---- Sesión ---- */

  async login(username: string, clave: string): Promise<PerfilSesion> {
    let res: Response;
    try {
      // Silencioso: un 401 acá es "credenciales incorrectas", no una sesión que se cerró.
      res = await apiFetch(
        `${this.baseUrl}/auth/login`,
        { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ username, clave }) },
        { silencioso: true },
      );
    } catch (e) {
      if (e instanceof SesionCerradaError) throw new CredencialesIncorrectasError();
      throw e;
    }
    if (!res.ok) return this.fallar(res, 'iniciar sesión');

    // El 200 no garantiza que el navegador haya guardado la cookie (esquema mixto http/https,
    // hosts distintos). Se comprueba con el perfil para fallar de forma visible y no con un
    // login que "anda" y un dashboard que rebota al login en el primer sondeo.
    try {
      const verificada = await apiFetch(`${this.baseUrl}/auth/perfil`, {}, { silencioso: true });
      if (!verificada.ok) return this.fallar(verificada, 'verificar la sesión');
      return (await verificada.json()) as PerfilSesion;
    } catch (e) {
      if (e instanceof SesionCerradaError) throw new SesionNoEstablecidaError();
      throw e;
    }
  }

  getPerfil(): Promise<PerfilSesion> {
    return this.json(`${this.baseUrl}/auth/perfil`, 'obtener el perfil');
  }

  registrarActividad(): Promise<void> {
    return this.vacio(`${this.baseUrl}/auth/actividad`, 'registrar la actividad', { method: 'POST' });
  }

  async logout(): Promise<void> {
    // Silencioso: si la sesión ya estaba cerrada, el resultado es el mismo.
    await apiFetch(`${this.baseUrl}/auth/logout`, { method: 'POST' }, { silencioso: true }).catch(() => undefined);
  }

  cambiarClave(actual: string, nueva: string): Promise<void> {
    return this.vacio(`${this.baseUrl}/auth/clave`, 'cambiar la contraseña', {
      method: 'PUT',
      headers: JSON_HEADERS,
      body: JSON.stringify({ actual, nueva }),
    });
  }

  usuariosDemo(): UsuarioDemo[] {
    return [];
  }

  /* ---- Usuarios ---- */

  listarUsuarios(incluirBajas: boolean): Promise<Usuario[]> {
    return this.json(`${this.baseUrl}/usuarios?incluirBajas=${incluirBajas}`, 'listar los usuarios');
  }

  crearUsuario(nuevo: NuevoUsuario): Promise<Usuario> {
    return this.json(`${this.baseUrl}/usuarios`, 'dar de alta el usuario', {
      method: 'POST',
      headers: JSON_HEADERS,
      body: JSON.stringify(nuevo),
    });
  }

  editarUsuario(id: number, edicion: EdicionUsuario): Promise<Usuario> {
    return this.json(`${this.baseUrl}/usuarios/${id}`, 'guardar el usuario', {
      method: 'PUT',
      headers: JSON_HEADERS,
      body: JSON.stringify(edicion),
    });
  }

  suspenderUsuario(id: number): Promise<Usuario> {
    return this.json(`${this.baseUrl}/usuarios/${id}/suspender`, 'suspender el usuario', { method: 'POST' });
  }

  reactivarUsuario(id: number): Promise<Usuario> {
    return this.json(`${this.baseUrl}/usuarios/${id}/reactivar`, 'reactivar el usuario', { method: 'POST' });
  }

  darDeBajaUsuario(id: number): Promise<Usuario> {
    return this.json(`${this.baseUrl}/usuarios/${id}/baja`, 'dar de baja el usuario', { method: 'POST' });
  }

  blanquearClave(id: number, clave: string): Promise<void> {
    return this.vacio(`${this.baseUrl}/usuarios/${id}/clave`, 'blanquear la contraseña', {
      method: 'PUT',
      headers: JSON_HEADERS,
      body: JSON.stringify({ clave }),
    });
  }

  /* ---- Roles y permisos ---- */

  getCatalogoPermisos(): Promise<PermisoCatalogo[]> {
    return this.json(`${this.baseUrl}/roles/permisos`, 'obtener el catálogo de permisos');
  }

  getRoles(): Promise<RolPermisos[]> {
    return this.json(`${this.baseUrl}/roles`, 'obtener la matriz de permisos');
  }

  guardarPermisosRol(rol: Rol, permisos: string[]): Promise<RolPermisos> {
    return this.json(`${this.baseUrl}/roles/${rol}/permisos`, 'guardar los permisos del rol', {
      method: 'PUT',
      headers: JSON_HEADERS,
      body: JSON.stringify({ permisos }),
    });
  }

  /* ---- Política ---- */

  getPolitica(): Promise<PoliticaSesion> {
    return this.json(`${this.baseUrl}/seguridad/politica`, 'obtener la política de sesión');
  }

  guardarPolitica(inactividadMin: number): Promise<PoliticaSesion> {
    return this.json(`${this.baseUrl}/seguridad/politica`, 'guardar la política de sesión', {
      method: 'PUT',
      headers: JSON_HEADERS,
      body: JSON.stringify({ inactividadMin }),
    });
  }

  /* ---- Auditoría ---- */

  getAuditoria(filtros: FiltrosAuditoria): Promise<PaginaAuditoria> {
    const q = new URLSearchParams();
    for (const clave of ['autor', 'objetivo', 'tipo', 'desde', 'hasta'] as const) {
      const v = filtros[clave];
      if (v) q.set(clave, v);
    }
    q.set('pagina', String(filtros.pagina ?? 0));
    q.set('tamanio', String(filtros.tamanio ?? 50));
    return this.json(`${this.baseUrl}/auditoria?${q.toString()}`, 'obtener la auditoría');
  }

  verificarAuditoria(): Promise<VerificacionAuditoria> {
    return this.json(`${this.baseUrl}/auditoria/verificacion`, 'verificar la auditoría');
  }
}
