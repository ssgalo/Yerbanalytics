/* ============================================================
   Espejo del catálogo de permisos, de los roles y de la matriz por defecto del backend
   (`enum Permiso` / `enum Rol` y la siembra de `rol_permiso`). Lo usan el repositorio mock
   —que tiene que restringir igual que el sistema real— y la UI para rotular.

   La fuente de verdad es el backend: en modo http la vista Usuarios pide el catálogo y la
   matriz a la API, y esta copia sólo se usa para la demo y para nombres a mostrar. Si el
   backend agrega un permiso, se agrega acá también.
   ============================================================ */
import type { Permiso, PermisoCatalogo, Rol, TipoAuditoria } from '@/types/seguridad';

/** Los cinco roles, en el orden de la tabla de roles (el mismo que devuelve `GET /api/roles`). */
export const ROLES: readonly Rol[] = [
  'ADMINISTRADOR',
  'INGENIERO_AGRONOMO',
  'PRODUCTOR_VIVERISTA',
  'OPERARIO',
  'SERVICIO',
];

export const NOMBRE_ROL: Record<Rol, string> = {
  ADMINISTRADOR: 'Administrador',
  INGENIERO_AGRONOMO: 'Ingeniero Agrónomo',
  PRODUCTOR_VIVERISTA: 'Productor Viverista',
  OPERARIO: 'Operario',
  SERVICIO: 'Servicio',
};

/** El catálogo de 20 permisos, en el orden del backend (ya viene agrupado). */
export const CATALOGO_PERMISOS: readonly PermisoCatalogo[] = [
  { codigo: 'vivero.ver', grupo: 'Vivero', descripcion: 'Panel general, detalle de macro-zona y de sector', lectura: null },
  { codigo: 'diagnosticos.ver', grupo: 'Diagnósticos', descripcion: 'Diagnósticos de IA y su consulta', lectura: null },
  { codigo: 'diagnosticos.registrar', grupo: 'Diagnósticos', descripcion: 'Alta de diagnósticos', lectura: 'diagnosticos.ver' },
  { codigo: 'historial.ver', grupo: 'Historial', descripcion: 'Historial de acciones', lectura: null },
  { codigo: 'configuracion.ver', grupo: 'Configuración', descripcion: 'Ver la configuración agronómica', lectura: null },
  { codigo: 'configuracion.editar', grupo: 'Configuración', descripcion: 'Editar la configuración agronómica', lectura: 'configuracion.ver' },
  { codigo: 'reglas.ver', grupo: 'Motor de reglas', descripcion: 'Grafo del motor y trazas de evaluación', lectura: null },
  { codigo: 'reglas.editar', grupo: 'Motor de reglas', descripcion: 'Editar el catálogo de parámetros', lectura: 'reglas.ver' },
  { codigo: 'hardware.ver', grupo: 'Hardware', descripcion: 'Estado del hardware', lectura: null },
  { codigo: 'hardware.gestionar', grupo: 'Hardware', descripcion: 'Alta y recambio de dispositivos', lectura: 'hardware.ver' },
  { codigo: 'topologia.ver', grupo: 'Topología', descripcion: 'Ver la topología del vivero', lectura: null },
  { codigo: 'topologia.gestionar', grupo: 'Topología', descripcion: 'Generar la topología y cambiar su disposición', lectura: 'topologia.ver' },
  { codigo: 'capturas.ver', grupo: 'Captura', descripcion: 'Órdenes de captura e imágenes', lectura: null },
  { codigo: 'capturas.ordenar', grupo: 'Captura', descripcion: 'Emitir órdenes de captura', lectura: 'capturas.ver' },
  { codigo: 'camara.gestionar', grupo: 'Captura', descripcion: 'Vinculación, listado y revocación de dispositivos de captura', lectura: null },
  { codigo: 'pasadas.ver', grupo: 'Pasadas del riel', descripcion: 'Estado de la pasada del riel', lectura: null },
  { codigo: 'pasadas.operar', grupo: 'Pasadas del riel', descripcion: 'Iniciar y cancelar la pasada', lectura: 'pasadas.ver' },
  { codigo: 'demo-expo.configurar', grupo: 'Demo Expo', descripcion: 'Mostrar u ocultar la pestaña Demo Expo', lectura: null },
  { codigo: 'usuarios.gestionar', grupo: 'Seguridad', descripcion: 'Usuarios, matriz de permisos y política de sesión', lectura: null },
  { codigo: 'auditoria.ver', grupo: 'Seguridad', descripcion: 'Registro de auditoría de seguridad', lectura: null },
];

/** "Todos los .ver" de la matriz por defecto. NO incluye `auditoria.ver`: ése es sólo del Administrador. */
const TODOS_LOS_VER: Permiso[] = [
  'vivero.ver',
  'diagnosticos.ver',
  'historial.ver',
  'configuracion.ver',
  'reglas.ver',
  'hardware.ver',
  'topologia.ver',
  'capturas.ver',
  'pasadas.ver',
];

/** La matriz que siembra el backend en una base vacía. */
export const MATRIZ_POR_DEFECTO: Record<Rol, readonly Permiso[]> = {
  ADMINISTRADOR: CATALOGO_PERMISOS.map((p) => p.codigo),
  INGENIERO_AGRONOMO: [...TODOS_LOS_VER, 'configuracion.editar', 'reglas.editar'],
  PRODUCTOR_VIVERISTA: [...TODOS_LOS_VER, 'pasadas.operar'],
  OPERARIO: ['vivero.ver', 'diagnosticos.ver', 'historial.ver', 'capturas.ver', 'pasadas.ver'],
  SERVICIO: [
    'vivero.ver',
    'topologia.ver',
    'capturas.ver',
    'capturas.ordenar',
    'diagnosticos.ver',
    'diagnosticos.registrar',
    'camara.gestionar',
  ],
};

/** Lo que el Administrador no puede perder nunca: sin esto el sistema quedaría sin quien lo administre. */
export const INTOCABLES: Record<Rol, readonly Permiso[]> = {
  ADMINISTRADOR: ['usuarios.gestionar', 'auditoria.ver'],
  INGENIERO_AGRONOMO: [],
  PRODUCTOR_VIVERISTA: [],
  OPERARIO: [],
  SERVICIO: [],
};

/** Rango admitido del tiempo máximo de inactividad, en minutos. */
export const INACTIVIDAD_MIN = 5;
export const INACTIVIDAD_MAX = 480;
export const INACTIVIDAD_POR_DEFECTO = 60;

export const ETIQUETA_TIPO_AUDITORIA: Record<TipoAuditoria, string> = {
  USUARIO_ALTA: 'Alta de usuario',
  USUARIO_EDITADO: 'Usuario editado',
  USUARIO_ROL_CAMBIADO: 'Cambio de rol',
  USUARIO_SUSPENDIDO: 'Suspensión',
  USUARIO_REACTIVADO: 'Reactivación',
  USUARIO_BAJA: 'Baja',
  USUARIO_CLAVE_BLANQUEADA: 'Blanqueo de contraseña',
  ROL_PERMISOS_CAMBIADOS: 'Cambio de permisos',
  POLITICA_SESION_CAMBIADA: 'Cambio de política de sesión',
  ADMIN_INICIAL_CREADO: 'Administrador inicial',
  MATRIZ_SEMBRADA: 'Matriz sembrada',
};

/** Nombre de un rol para la UI; un código desconocido se muestra tal cual. */
export function nombreRol(rol: string): string {
  return NOMBRE_ROL[rol as Rol] ?? rol;
}

/** Iniciales para el avatar: primera letra de las dos primeras palabras ("Ana Benítez" → "AB"). */
export function iniciales(nombre: string): string {
  const partes = nombre.trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return '?';
  return partes
    .slice(0, 2)
    .map((p) => p[0].toLocaleUpperCase('es-AR'))
    .join('');
}
