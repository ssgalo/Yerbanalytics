/* ============================================================
   Sesión y gestión de seguridad en memoria, para la demo (`VITE_DATA_SOURCE=mock`).

   No es un atajo: aplica la misma matriz por defecto, las mismas validaciones (400/409), las
   mismas salvaguardas y el mismo vencimiento por inactividad que el backend, y avisa a la capa
   de sesión por el mismo canal que el cliente HTTP. Así la demo con el Operario se ve igual de
   restringida que un Operario real.

   Una sola sesión (la de esta pestaña): revocar "las sesiones de un usuario" sólo tiene efecto
   visible si ese usuario es quien está usando la demo. Las claves se guardan en claro porque
   es una demo en memoria; el backend real guarda sólo el hash BCrypt.
   ============================================================ */
import type { SeguridadRepository } from '@/data/seguridadRepository';
import {
  CambioClaveRequeridoError,
  CredencialesIncorrectasError,
  OperacionRechazadaError,
  PermisoDenegadoError,
  SesionCerradaError,
} from '@/data/seguridadErrores';
import { avisarCambioClaveRequerido, avisarPermisoDenegado, avisarSesionCerrada } from '@/data/sesionEventos';
import {
  CATALOGO_PERMISOS,
  INACTIVIDAD_MAX,
  INACTIVIDAD_MIN,
  INACTIVIDAD_POR_DEFECTO,
  INTOCABLES,
  MATRIZ_POR_DEFECTO,
  NOMBRE_ROL,
  ROLES,
} from '@/lib/catalogoSeguridad';
import type {
  EdicionUsuario,
  FiltrosAuditoria,
  MotivoCierre,
  NuevoUsuario,
  ObjetivoAuditoria,
  PaginaAuditoria,
  PerfilSesion,
  Permiso,
  PermisoCatalogo,
  PoliticaSesion,
  RegistroAuditoria,
  Rol,
  RolPermisos,
  TipoAuditoria,
  Usuario,
  UsuarioDemo,
  VerificacionAuditoria,
} from '@/types/seguridad';

/** Clave de los usuarios demo. Se lista en el login, así que no tiene sentido que sea fuerte. */
export const CLAVE_DEMO = 'demo';

const USUARIOS_DEMO: { username: string; nombre: string; rol: Rol }[] = [
  { username: 'admin', nombre: 'Lucía Fernández', rol: 'ADMINISTRADOR' },
  { username: 'agronomo', nombre: 'Ana Benítez', rol: 'INGENIERO_AGRONOMO' },
  { username: 'productor', nombre: 'Mariano Duarte', rol: 'PRODUCTOR_VIVERISTA' },
  { username: 'operario', nombre: 'Carlos Ramírez', rol: 'OPERARIO' },
];

const USERNAME_VALIDO = /^[a-z0-9._-]{3,40}$/;

interface UsuarioInterno extends Usuario {
  clave: string;
}

interface Sesion {
  usuarioId: number;
  ultimaActividad: number;
  revocada: boolean;
}

/** Qué se le pide a una operación antes de ejecutarla. */
interface Exigencia {
  permiso?: Permiso;
  /** Las que no son lectura cuentan como actividad (como cualquier petición no GET). */
  actividad?: boolean;
  /** Admitida aun con la clave temporal (perfil, actividad y cambio de clave). */
  conClaveTemporal?: boolean;
}

export class MockSeguridadRepository implements SeguridadRepository {
  private usuarios: UsuarioInterno[] = [];
  private matriz = new Map<Rol, Set<string>>();
  private inactividadMin = INACTIVIDAD_POR_DEFECTO;
  private auditoria: RegistroAuditoria[] = [];
  private sesion: Sesion | null = null;
  private proximoUsuario = 1;

  /** `ahora` es inyectable para probar el vencimiento sin esperar una hora. */
  constructor(private readonly ahora: () => number = () => Date.now()) {
    for (const rol of ROLES) this.matriz.set(rol, new Set(MATRIZ_POR_DEFECTO[rol]));

    // La historia que tendría una base recién instalada: la siembra, el admin inicial y las altas.
    this.auditar(null, 'ROL', 'ADMINISTRADOR', 'MATRIZ_SEMBRADA', { roles: ROLES.length });
    for (const d of USUARIOS_DEMO) {
      const u = this.agregarUsuario(d.username, d.nombre, d.rol, CLAVE_DEMO, false);
      if (d.rol === 'ADMINISTRADOR') {
        this.auditar(null, 'USUARIO', u.username, 'ADMIN_INICIAL_CREADO', { nuevo: { username: u.username, rol: u.rol } });
      } else {
        this.auditar('admin', 'USUARIO', u.username, 'USUARIO_ALTA', {
          nuevo: { username: u.username, nombre: u.nombre, rol: u.rol },
        });
      }
    }
  }

  /* ============ internos ============ */

  private agregarUsuario(username: string, nombre: string, rol: Rol, clave: string, debeCambiarClave: boolean) {
    const u: UsuarioInterno = {
      id: this.proximoUsuario++,
      username,
      nombre,
      rol,
      estado: 'ACTIVO',
      debeCambiarClave,
      creadoEn: new Date(this.ahora()).toISOString(),
      ultimoIngreso: null,
      clave,
    };
    this.usuarios.push(u);
    return u;
  }

  private auditar(
    autor: string | null,
    objetivoTipo: ObjetivoAuditoria,
    objetivoRef: string,
    tipo: TipoAuditoria,
    detalle: Record<string, unknown>,
  ) {
    this.auditoria.push({
      id: this.auditoria.length + 1,
      ocurridoEn: new Date(this.ahora()).toISOString(),
      autorUsername: autor,
      objetivoTipo,
      objetivoRef,
      tipo,
      detalle,
    });
  }

  private cerrar(motivo: MotivoCierre): never {
    this.sesion = null;
    avisarSesionCerrada(motivo);
    throw new SesionCerradaError(motivo);
  }

  /** Lo que hace la cadena de seguridad del backend en cada petición. */
  private exigir({ permiso, actividad = false, conClaveTemporal = false }: Exigencia = {}): UsuarioInterno {
    const s = this.sesion;
    if (!s) this.cerrar('SIN_SESION');
    if (s.revocada) this.cerrar('SESION_REVOCADA');
    if (this.ahora() - s.ultimaActividad > this.inactividadMin * 60_000) this.cerrar('SESION_EXPIRADA');

    const u = this.usuarios.find((x) => x.id === s.usuarioId);
    if (!u || u.estado !== 'ACTIVO') this.cerrar('SESION_REVOCADA');

    if (u.debeCambiarClave && !conClaveTemporal) {
      avisarCambioClaveRequerido();
      throw new CambioClaveRequeridoError();
    }
    if (permiso && !this.matriz.get(u.rol)?.has(permiso)) {
      avisarPermisoDenegado(permiso);
      throw new PermisoDenegadoError(permiso);
    }
    if (actividad) s.ultimaActividad = this.ahora();
    return u;
  }

  /** Revoca "las sesiones" de un usuario: con una sola sesión, sólo importa si es la vigente. */
  private revocarSesionesDe(usuarioId: number) {
    if (this.sesion?.usuarioId === usuarioId) this.sesion.revocada = true;
  }

  private perfilDe(u: UsuarioInterno): PerfilSesion {
    return {
      id: u.id,
      username: u.username,
      nombre: u.nombre,
      rol: u.rol,
      rolNombre: NOMBRE_ROL[u.rol],
      permisos: CATALOGO_PERMISOS.map((p) => p.codigo).filter((c) => this.matriz.get(u.rol)?.has(c)),
      inactividadMin: this.inactividadMin,
      debeCambiarClave: u.debeCambiarClave,
    };
  }

  private publico(u: UsuarioInterno): Usuario {
    // Nunca sale la clave, igual que en el backend nunca sale el hash.
    return {
      id: u.id,
      username: u.username,
      nombre: u.nombre,
      rol: u.rol,
      estado: u.estado,
      debeCambiarClave: u.debeCambiarClave,
      creadoEn: u.creadoEn,
      ultimoIngreso: u.ultimoIngreso,
    };
  }

  private buscar(id: number): UsuarioInterno {
    const u = this.usuarios.find((x) => x.id === id);
    if (!u) throw new OperacionRechazadaError(400, 'El usuario no existe.');
    return u;
  }

  private validarClave(clave: string, username: string) {
    if (clave.length < 8) throw new OperacionRechazadaError(400, 'La contraseña debe tener al menos 8 caracteres.');
    if (clave.toLowerCase() === username.toLowerCase()) {
      throw new OperacionRechazadaError(400, 'La contraseña no puede ser igual al nombre de usuario.');
    }
  }

  private validarRol(rol: string): asserts rol is Rol {
    if (!ROLES.includes(rol as Rol)) throw new OperacionRechazadaError(400, `El rol «${rol}» no existe.`);
  }

  /** Nunca menos de un Administrador activo. */
  private exigirOtroAdministrador(u: UsuarioInterno) {
    if (u.rol !== 'ADMINISTRADOR' || u.estado !== 'ACTIVO') return;
    const activos = this.usuarios.filter((x) => x.rol === 'ADMINISTRADOR' && x.estado === 'ACTIVO').length;
    if (activos <= 1) {
      throw new OperacionRechazadaError(409, `El sistema quedaría sin Administrador activo: «${u.username}» es el último.`);
    }
  }

  private rolPermisos(rol: Rol): RolPermisos {
    const actuales = this.matriz.get(rol) ?? new Set<string>();
    return {
      rol,
      nombre: NOMBRE_ROL[rol],
      permisos: CATALOGO_PERMISOS.map((p) => p.codigo).filter((c) => actuales.has(c)),
      intocables: [...INTOCABLES[rol]],
    };
  }

  /* ============ sesión ============ */

  async login(username: string, clave: string): Promise<PerfilSesion> {
    const u = this.usuarios.find((x) => x.username === username.trim().toLowerCase());
    // Mismo rechazo para usuario inexistente, clave errónea, suspendido o baja (HU-01 CA-02).
    if (!u || u.clave !== clave || u.estado !== 'ACTIVO') throw new CredencialesIncorrectasError();
    u.ultimoIngreso = new Date(this.ahora()).toISOString();
    this.sesion = { usuarioId: u.id, ultimaActividad: this.ahora(), revocada: false };
    return this.perfilDe(u);
  }

  async getPerfil(): Promise<PerfilSesion> {
    return this.perfilDe(this.exigir({ conClaveTemporal: true }));
  }

  async registrarActividad(): Promise<void> {
    this.exigir({ actividad: true, conClaveTemporal: true });
  }

  async logout(): Promise<void> {
    this.sesion = null;
  }

  async cambiarClave(actual: string, nueva: string): Promise<void> {
    const u = this.exigir({ actividad: true, conClaveTemporal: true });
    if (u.clave !== actual) throw new OperacionRechazadaError(400, 'La contraseña actual no es correcta.');
    this.validarClave(nueva, u.username);
    u.clave = nueva;
    u.debeCambiarClave = false;
    // No se audita: no es un cambio hecho por el Administrador sobre otro usuario (design D7).
  }

  usuariosDemo(): UsuarioDemo[] {
    return USUARIOS_DEMO.map((d) => ({
      username: d.username,
      clave: CLAVE_DEMO,
      nombre: d.nombre,
      rolNombre: NOMBRE_ROL[d.rol],
    }));
  }

  /* ============ usuarios ============ */

  async listarUsuarios(incluirBajas: boolean): Promise<Usuario[]> {
    this.exigir({ permiso: 'usuarios.gestionar' });
    return this.usuarios
      .filter((u) => incluirBajas || u.estado !== 'BAJA')
      .sort((a, b) => a.username.localeCompare(b.username))
      .map((u) => this.publico(u));
  }

  async crearUsuario(nuevo: NuevoUsuario): Promise<Usuario> {
    const autor = this.exigir({ permiso: 'usuarios.gestionar', actividad: true });
    const username = nuevo.username.trim().toLowerCase();
    if (!USERNAME_VALIDO.test(username)) {
      throw new OperacionRechazadaError(
        400,
        'El nombre de usuario debe tener entre 3 y 40 caracteres: letras minúsculas, números, punto, guion o guion bajo.',
      );
    }
    if (!nuevo.nombre.trim()) throw new OperacionRechazadaError(400, 'El nombre a mostrar es obligatorio.');
    this.validarRol(nuevo.rol);
    this.validarClave(nuevo.clave, username);
    // Único sin distinguir mayúsculas, incluso contra las bajas: un username no se reutiliza.
    if (this.usuarios.some((u) => u.username === username)) {
      throw new OperacionRechazadaError(409, `Ya existe un usuario «${username}» (aunque esté dado de baja).`);
    }
    const u = this.agregarUsuario(username, nuevo.nombre.trim(), nuevo.rol, nuevo.clave, true);
    this.auditar(autor.username, 'USUARIO', username, 'USUARIO_ALTA', {
      nuevo: { username, nombre: u.nombre, rol: u.rol },
    });
    return this.publico(u);
  }

  async editarUsuario(id: number, edicion: EdicionUsuario): Promise<Usuario> {
    const autor = this.exigir({ permiso: 'usuarios.gestionar', actividad: true });
    const u = this.buscar(id);
    const nombre = edicion.nombre.trim();
    if (!nombre) throw new OperacionRechazadaError(400, 'El nombre a mostrar es obligatorio.');
    this.validarRol(edicion.rol);
    if (u.estado === 'BAJA') throw new OperacionRechazadaError(409, 'No se puede editar un usuario dado de baja.');
    const cambiaRol = edicion.rol !== u.rol;
    if (cambiaRol) this.exigirOtroAdministrador(u);

    if (nombre !== u.nombre) {
      this.auditar(autor.username, 'USUARIO', u.username, 'USUARIO_EDITADO', {
        anterior: { nombre: u.nombre },
        nuevo: { nombre },
      });
      u.nombre = nombre;
    }
    if (cambiaRol) {
      this.auditar(autor.username, 'USUARIO', u.username, 'USUARIO_ROL_CAMBIADO', {
        anterior: { rol: u.rol },
        nuevo: { rol: edicion.rol },
      });
      u.rol = edicion.rol;
      this.revocarSesionesDe(u.id);
    }
    return this.publico(u);
  }

  async suspenderUsuario(id: number): Promise<Usuario> {
    const autor = this.exigir({ permiso: 'usuarios.gestionar', actividad: true });
    const u = this.buscar(id);
    if (u.id === autor.id) throw new OperacionRechazadaError(409, 'No podés suspenderte a vos mismo.');
    if (u.estado === 'BAJA') throw new OperacionRechazadaError(409, 'No se puede suspender un usuario dado de baja.');
    if (u.estado === 'SUSPENDIDO') return this.publico(u);
    this.exigirOtroAdministrador(u);
    this.auditar(autor.username, 'USUARIO', u.username, 'USUARIO_SUSPENDIDO', {
      anterior: { estado: u.estado },
      nuevo: { estado: 'SUSPENDIDO' },
    });
    u.estado = 'SUSPENDIDO';
    this.revocarSesionesDe(u.id);
    return this.publico(u);
  }

  async reactivarUsuario(id: number): Promise<Usuario> {
    const autor = this.exigir({ permiso: 'usuarios.gestionar', actividad: true });
    const u = this.buscar(id);
    if (u.estado === 'BAJA') throw new OperacionRechazadaError(409, 'Un usuario dado de baja no se puede reactivar.');
    if (u.estado === 'ACTIVO') return this.publico(u);
    this.auditar(autor.username, 'USUARIO', u.username, 'USUARIO_REACTIVADO', {
      anterior: { estado: u.estado },
      nuevo: { estado: 'ACTIVO' },
    });
    u.estado = 'ACTIVO';
    return this.publico(u);
  }

  async darDeBajaUsuario(id: number): Promise<Usuario> {
    const autor = this.exigir({ permiso: 'usuarios.gestionar', actividad: true });
    const u = this.buscar(id);
    if (u.id === autor.id) throw new OperacionRechazadaError(409, 'No podés darte de baja a vos mismo.');
    if (u.estado === 'BAJA') return this.publico(u);
    this.exigirOtroAdministrador(u);
    this.auditar(autor.username, 'USUARIO', u.username, 'USUARIO_BAJA', {
      anterior: { estado: u.estado },
      nuevo: { estado: 'BAJA' },
    });
    u.estado = 'BAJA';
    this.revocarSesionesDe(u.id);
    return this.publico(u);
  }

  async blanquearClave(id: number, clave: string): Promise<void> {
    const autor = this.exigir({ permiso: 'usuarios.gestionar', actividad: true });
    const u = this.buscar(id);
    if (u.estado === 'BAJA') throw new OperacionRechazadaError(409, 'No se puede blanquear la contraseña de una baja.');
    this.validarClave(clave, u.username);
    u.clave = clave;
    u.debeCambiarClave = true;
    this.revocarSesionesDe(u.id);
    // Sin la clave: la auditoría registra el hecho, nunca el valor.
    this.auditar(autor.username, 'USUARIO', u.username, 'USUARIO_CLAVE_BLANQUEADA', {});
  }

  /* ============ roles y permisos ============ */

  async getCatalogoPermisos(): Promise<PermisoCatalogo[]> {
    this.exigir({ permiso: 'usuarios.gestionar' });
    return CATALOGO_PERMISOS.map((p) => ({ ...p }));
  }

  async getRoles(): Promise<RolPermisos[]> {
    this.exigir({ permiso: 'usuarios.gestionar' });
    return ROLES.map((r) => this.rolPermisos(r));
  }

  async guardarPermisosRol(rol: Rol, permisos: string[]): Promise<RolPermisos> {
    const autor = this.exigir({ permiso: 'usuarios.gestionar', actividad: true });
    this.validarRol(rol);
    const nuevos = new Set(permisos);
    for (const c of nuevos) {
      const p = CATALOGO_PERMISOS.find((x) => x.codigo === c);
      if (!p) throw new OperacionRechazadaError(400, `El permiso «${c}» no existe.`);
      if (p.lectura && !nuevos.has(p.lectura)) {
        throw new OperacionRechazadaError(400, `«${c}» requiere también «${p.lectura}».`);
      }
    }
    const faltantes = INTOCABLES[rol].filter((c) => !nuevos.has(c));
    if (faltantes.length > 0) {
      throw new OperacionRechazadaError(
        409,
        `El rol ${NOMBRE_ROL[rol]} no puede perder ${faltantes.map((c) => `«${c}»`).join(' ni ')}.`,
      );
    }
    const anteriores = this.matriz.get(rol) ?? new Set<string>();
    const agregados = [...nuevos].filter((c) => !anteriores.has(c));
    const quitados = [...anteriores].filter((c) => !nuevos.has(c));
    if (agregados.length > 0 || quitados.length > 0) {
      this.matriz.set(rol, nuevos);
      this.auditar(autor.username, 'ROL', rol, 'ROL_PERMISOS_CAMBIADOS', { agregados, quitados });
      // Se revocan las sesiones del rol salvo la del autor; en la demo la única sesión es la suya.
    }
    return this.rolPermisos(rol);
  }

  /* ============ política ============ */

  async getPolitica(): Promise<PoliticaSesion> {
    this.exigir({ permiso: 'usuarios.gestionar' });
    return { inactividadMin: this.inactividadMin, minimo: INACTIVIDAD_MIN, maximo: INACTIVIDAD_MAX };
  }

  async guardarPolitica(inactividadMin: number): Promise<PoliticaSesion> {
    const autor = this.exigir({ permiso: 'usuarios.gestionar', actividad: true });
    if (!Number.isInteger(inactividadMin) || inactividadMin < INACTIVIDAD_MIN || inactividadMin > INACTIVIDAD_MAX) {
      throw new OperacionRechazadaError(
        400,
        `El tiempo máximo de inactividad debe estar entre ${INACTIVIDAD_MIN} y ${INACTIVIDAD_MAX} minutos.`,
      );
    }
    if (inactividadMin !== this.inactividadMin) {
      this.auditar(autor.username, 'POLITICA', 'inactividad', 'POLITICA_SESION_CAMBIADA', {
        anterior: { inactividadMin: this.inactividadMin },
        nuevo: { inactividadMin },
      });
      this.inactividadMin = inactividadMin;
    }
    return { inactividadMin, minimo: INACTIVIDAD_MIN, maximo: INACTIVIDAD_MAX };
  }

  /* ============ auditoría ============ */

  async getAuditoria(filtros: FiltrosAuditoria): Promise<PaginaAuditoria> {
    this.exigir({ permiso: 'auditoria.ver' });
    const igual = (a: string | null, b: string) => (a ?? '').toLowerCase() === b.trim().toLowerCase();
    const desde = filtros.desde ? Date.parse(filtros.desde) : null;
    const hasta = filtros.hasta ? Date.parse(filtros.hasta) : null;
    const todos = this.auditoria
      .filter((r) => !filtros.autor || igual(r.autorUsername, filtros.autor))
      .filter((r) => !filtros.objetivo || igual(r.objetivoRef, filtros.objetivo))
      .filter((r) => !filtros.tipo || r.tipo === filtros.tipo)
      .filter((r) => desde === null || Date.parse(r.ocurridoEn) >= desde)
      .filter((r) => hasta === null || Date.parse(r.ocurridoEn) <= hasta)
      .slice()
      .reverse();
    const tamanio = filtros.tamanio ?? 50;
    const pagina = filtros.pagina ?? 0;
    return {
      items: todos.slice(pagina * tamanio, (pagina + 1) * tamanio).map((r) => structuredClone(r)),
      pagina,
      tamanio,
      total: todos.length,
      totalPaginas: Math.max(1, Math.ceil(todos.length / tamanio)),
    };
  }

  async verificarAuditoria(): Promise<VerificacionAuditoria> {
    this.exigir({ permiso: 'auditoria.ver' });
    // En memoria nadie puede tocar el registro por fuera: la cadena está íntegra por construcción.
    return { integra: true, verificados: this.auditoria.length, primerIdRoto: null };
  }
}
