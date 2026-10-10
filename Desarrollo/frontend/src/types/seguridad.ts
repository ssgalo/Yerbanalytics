/* ============================================================
   Tipos de seguridad: sesión, usuarios, roles, permisos y auditoría (HU-01 / HU-20).
   Espejan al pie de la letra el JSON del backend (camelCase, fechas ISO-8601 UTC).
   El catálogo y la matriz por defecto, como valores, viven en `lib/catalogoSeguridad.ts`.
   ============================================================ */

/** Los cinco roles fijos. El código viaja tal cual en el JSON. */
export type Rol = 'ADMINISTRADOR' | 'INGENIERO_AGRONOMO' | 'PRODUCTOR_VIVERISTA' | 'OPERARIO' | 'SERVICIO';

/** Código de permiso del catálogo (los 20 del enum `Permiso` del backend). */
export type Permiso =
  | 'vivero.ver'
  | 'diagnosticos.ver'
  | 'diagnosticos.registrar'
  | 'historial.ver'
  | 'configuracion.ver'
  | 'configuracion.editar'
  | 'reglas.ver'
  | 'reglas.editar'
  | 'hardware.ver'
  | 'hardware.gestionar'
  | 'topologia.ver'
  | 'topologia.gestionar'
  | 'capturas.ver'
  | 'capturas.ordenar'
  | 'camara.gestionar'
  | 'pasadas.ver'
  | 'pasadas.operar'
  | 'demo-expo.configurar'
  | 'usuarios.gestionar'
  | 'auditoria.ver';

/**
 * Por qué el backend dio la sesión por cerrada (401). `SIN_SESION` es "nunca la hubo" (o la
 * cookie no viajó); las otras dos llevan aviso en el login.
 */
export type MotivoCierre = 'SIN_SESION' | 'SESION_EXPIRADA' | 'SESION_REVOCADA';

/** Lo que devuelven el login y `GET /api/auth/perfil`. Nunca trae el token ni la clave. */
export interface PerfilSesion {
  id: number;
  username: string;
  /** Nombre a mostrar ("Ana Benítez"). */
  nombre: string;
  rol: Rol;
  /** Nombre del rol para la UI ("Ingeniero Agrónomo"). */
  rolNombre: string;
  /** Permisos efectivos del rol según la matriz vigente. */
  permisos: string[];
  /** Tiempo máximo de inactividad vigente, en minutos. */
  inactividadMin: number;
  /** Clave temporal (alta o blanqueo): hasta cambiarla, sólo se puede cambiar la clave. */
  debeCambiarClave: boolean;
}

export type EstadoUsuario = 'ACTIVO' | 'SUSPENDIDO' | 'BAJA';

export interface Usuario {
  id: number;
  username: string;
  nombre: string;
  rol: Rol;
  estado: EstadoUsuario;
  debeCambiarClave: boolean;
  /** ISO-8601 UTC. */
  creadoEn: string;
  ultimoIngreso: string | null;
}

/** Alta: la clave es temporal y obliga a cambiarla en el primer ingreso. */
export interface NuevoUsuario {
  username: string;
  nombre: string;
  rol: Rol;
  clave: string;
}

/** Edición: el username no se cambia nunca. */
export interface EdicionUsuario {
  nombre: string;
  rol: Rol;
}

/** Un permiso del catálogo. `lectura` es su par de lectura si es de edición. */
export interface PermisoCatalogo {
  codigo: Permiso;
  grupo: string;
  descripcion: string;
  lectura: Permiso | null;
}

/** Fila de la matriz: los permisos de un rol y los que no se le pueden quitar. */
export interface RolPermisos {
  rol: Rol;
  nombre: string;
  permisos: string[];
  intocables: string[];
}

export interface PoliticaSesion {
  inactividadMin: number;
  minimo: number;
  maximo: number;
}

export type ObjetivoAuditoria = 'USUARIO' | 'ROL' | 'POLITICA';

export type TipoAuditoria =
  | 'USUARIO_ALTA'
  | 'USUARIO_EDITADO'
  | 'USUARIO_ROL_CAMBIADO'
  | 'USUARIO_SUSPENDIDO'
  | 'USUARIO_REACTIVADO'
  | 'USUARIO_BAJA'
  | 'USUARIO_CLAVE_BLANQUEADA'
  | 'ROL_PERMISOS_CAMBIADOS'
  | 'POLITICA_SESION_CAMBIADA'
  | 'ADMIN_INICIAL_CREADO'
  | 'MATRIZ_SEMBRADA';

export interface RegistroAuditoria {
  id: number;
  /** ISO-8601 UTC con milisegundos. */
  ocurridoEn: string;
  /** null = sistema (administrador inicial, siembra de la matriz). */
  autorUsername: string | null;
  objetivoTipo: ObjetivoAuditoria;
  /** username, código de rol o "inactividad". */
  objetivoRef: string;
  tipo: TipoAuditoria;
  /** `{ anterior, nuevo }` o `{ agregados, quitados }`. Nunca contraseñas. */
  detalle: Record<string, unknown>;
}

export interface PaginaAuditoria {
  items: RegistroAuditoria[];
  pagina: number;
  tamanio: number;
  total: number;
  totalPaginas: number;
}

/** Filtros de `GET /api/auditoria`. Los vacíos no se mandan. */
export interface FiltrosAuditoria {
  autor?: string;
  objetivo?: string;
  tipo?: TipoAuditoria | '';
  /** Instantes ISO-8601. */
  desde?: string;
  hasta?: string;
  pagina?: number;
  tamanio?: number;
}

export interface VerificacionAuditoria {
  integra: boolean;
  verificados: number;
  primerIdRoto: number | null;
}

/** Usuario demo que la pantalla de login lista en modo mock (en http no hay ninguno). */
export interface UsuarioDemo {
  username: string;
  clave: string;
  nombre: string;
  rolNombre: string;
}
