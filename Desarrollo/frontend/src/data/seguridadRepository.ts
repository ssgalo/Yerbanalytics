/* ============================================================
   Contrato de la sesión y de la gestión de seguridad (HU-01 / HU-20). La UI sólo conoce esta
   interface; detrás hay el backend (`HttpSeguridadRepository`) o una demo en memoria
   (`MockSeguridadRepository`), según el mismo `VITE_DATA_SOURCE` que el `DataRepository`.

   Va separada del `DataRepository` porque la sesión tiene que existir ANTES que los datos del
   vivero (sin sesión no se pide nada), y porque aquél ya es grande.

   Errores comunes a todos los métodos: `SesionCerradaError` (401), `PermisoDenegadoError`
   (403), `CambioClaveRequeridoError` (403 con clave temporal). Los de gestión además rechazan
   con `OperacionRechazadaError` (400/409) y el mensaje del backend.
   ============================================================ */
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

export interface SeguridadRepository {
  /* ---- Sesión ---- */

  /** Inicia sesión. Rechaza con `CredencialesIncorrectasError` (siempre el mismo, a propósito). */
  login(username: string, clave: string): Promise<PerfilSesion>;
  /** Perfil de la sesión vigente. No cuenta como actividad. */
  getPerfil(): Promise<PerfilSesion>;
  /** Señal de interacción real del usuario: mantiene viva la sesión (design D4). */
  registrarActividad(): Promise<void>;
  /** Cierra la sesión en el servidor. */
  logout(): Promise<void>;
  /** Cambio de clave propio. Cierra las demás sesiones del usuario; la actual sigue. */
  cambiarClave(actual: string, nueva: string): Promise<void>;
  /** Usuarios demo para listar en el login. En el sistema real, siempre vacío. */
  usuariosDemo(): UsuarioDemo[];

  /* ---- Usuarios (usuarios.gestionar) ---- */

  listarUsuarios(incluirBajas: boolean): Promise<Usuario[]>;
  crearUsuario(nuevo: NuevoUsuario): Promise<Usuario>;
  /** Si cambia el rol, el backend revoca las sesiones del usuario. */
  editarUsuario(id: number, edicion: EdicionUsuario): Promise<Usuario>;
  suspenderUsuario(id: number): Promise<Usuario>;
  reactivarUsuario(id: number): Promise<Usuario>;
  /** Baja lógica y definitiva. */
  darDeBajaUsuario(id: number): Promise<Usuario>;
  /** Asigna una clave temporal: revoca sus sesiones y lo obliga a cambiarla. */
  blanquearClave(id: number, clave: string): Promise<void>;

  /* ---- Roles y permisos (usuarios.gestionar) ---- */

  getCatalogoPermisos(): Promise<PermisoCatalogo[]>;
  getRoles(): Promise<RolPermisos[]>;
  /** Reemplaza los permisos de un rol. Revoca las sesiones del rol salvo la del autor. */
  guardarPermisosRol(rol: Rol, permisos: string[]): Promise<RolPermisos>;

  /* ---- Política de sesión (usuarios.gestionar) ---- */

  getPolitica(): Promise<PoliticaSesion>;
  guardarPolitica(inactividadMin: number): Promise<PoliticaSesion>;

  /* ---- Auditoría (auditoria.ver) ---- */

  /** Del más reciente al más antiguo. */
  getAuditoria(filtros: FiltrosAuditoria): Promise<PaginaAuditoria>;
  verificarAuditoria(): Promise<VerificacionAuditoria>;
}
