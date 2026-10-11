import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpSeguridadRepository } from './httpSeguridadRepository';
import { escucharSesion } from '@/data/sesionEventos';
import {
  CredencialesIncorrectasError,
  OperacionRechazadaError,
  SesionNoEstablecidaError,
} from '@/data/seguridadErrores';

const BASE = 'http://localhost:8000/api';

interface Respuesta {
  status: number;
  body?: unknown;
}

/** Responde en orden; registra cada llamada. */
function stubFetch(...respuestas: Respuesta[]) {
  const llamadas: { url: string; init: RequestInit }[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit) => {
      llamadas.push({ url, init });
      const r = respuestas[Math.min(llamadas.length - 1, respuestas.length - 1)];
      return {
        ok: r.status >= 200 && r.status < 300,
        status: r.status,
        json: async () => {
          if (r.body === undefined) throw new SyntaxError('sin cuerpo');
          return r.body;
        },
      };
    }),
  );
  return llamadas;
}

const PERFIL = {
  id: 2,
  username: 'ana',
  nombre: 'Ana Benítez',
  rol: 'INGENIERO_AGRONOMO',
  rolNombre: 'Ingeniero Agrónomo',
  permisos: ['vivero.ver', 'reglas.ver', 'reglas.editar'],
  inactividadMin: 60,
  debeCambiarClave: false,
};

afterEach(() => vi.unstubAllGlobals());

describe('HttpSeguridadRepository · login', () => {
  it('POST /auth/login con { username, clave } y verifica la cookie pidiendo el perfil', async () => {
    const llamadas = stubFetch({ status: 200, body: PERFIL }, { status: 200, body: PERFIL });

    const perfil = await new HttpSeguridadRepository(BASE).login('ana', 'secreta123');

    expect(perfil).toEqual(PERFIL);
    expect(llamadas[0].url).toBe(`${BASE}/auth/login`);
    expect(llamadas[0].init.method).toBe('POST');
    expect(JSON.parse(String(llamadas[0].init.body))).toEqual({ username: 'ana', clave: 'secreta123' });
    expect(llamadas[0].init.credentials).toBe('include');
    expect(llamadas[1].url).toBe(`${BASE}/auth/perfil`);
  });

  it('un 401 es CredencialesIncorrectasError y no se toma como sesión cerrada', async () => {
    const cerradas: string[] = [];
    const baja = escucharSesion({ sesionCerrada: (m) => cerradas.push(m) });
    stubFetch({ status: 401, body: { error: 'Credenciales incorrectas' } });

    await expect(new HttpSeguridadRepository(BASE).login('ana', 'mala')).rejects.toBeInstanceOf(
      CredencialesIncorrectasError,
    );
    expect(cerradas).toEqual([]);
    baja();
  });

  it('login 200 pero la cookie no vuelve (esquema mixto): SesionNoEstablecidaError visible', async () => {
    stubFetch({ status: 200, body: PERFIL }, { status: 401, body: { error: 'x', motivo: 'SIN_SESION' } });

    await expect(new HttpSeguridadRepository(BASE).login('ana', 'secreta123')).rejects.toBeInstanceOf(
      SesionNoEstablecidaError,
    );
  });
});

describe('HttpSeguridadRepository · sesión y gestión', () => {
  it('registrarActividad es POST /auth/actividad (204)', async () => {
    const llamadas = stubFetch({ status: 204 });
    await new HttpSeguridadRepository(BASE).registrarActividad();
    expect(llamadas[0].url).toBe(`${BASE}/auth/actividad`);
    expect(llamadas[0].init.method).toBe('POST');
  });

  it('cambiarClave es PUT /auth/clave con { actual, nueva }; un 400 trae el mensaje', async () => {
    const llamadas = stubFetch({ status: 400, body: { error: 'La contraseña actual no es correcta.' } });

    const intento = new HttpSeguridadRepository(BASE).cambiarClave('mala', 'nueva12345');

    await expect(intento).rejects.toBeInstanceOf(OperacionRechazadaError);
    await expect(intento).rejects.toThrow('La contraseña actual no es correcta.');
    expect(llamadas[0].init.method).toBe('PUT');
    expect(JSON.parse(String(llamadas[0].init.body))).toEqual({ actual: 'mala', nueva: 'nueva12345' });
  });

  it('crearUsuario con username repetido rechaza con 409 y el mensaje del backend', async () => {
    stubFetch({ status: 409, body: { error: 'Ya existe el usuario jperez.' } });

    const intento = new HttpSeguridadRepository(BASE).crearUsuario({
      username: 'jperez',
      nombre: 'Juan Pérez',
      rol: 'OPERARIO',
      clave: 'temporal1',
    });

    await expect(intento).rejects.toMatchObject({ status: 409, message: 'Ya existe el usuario jperez.' });
  });

  it('las operaciones sobre un usuario usan sus rutas', async () => {
    const llamadas = stubFetch({ status: 200, body: {} });
    const repo = new HttpSeguridadRepository(BASE);

    await repo.listarUsuarios(true);
    await repo.editarUsuario(7, { nombre: 'Juan', rol: 'OPERARIO' });
    await repo.suspenderUsuario(7);
    await repo.reactivarUsuario(7);
    await repo.darDeBajaUsuario(7);
    await repo.guardarPermisosRol('OPERARIO', ['vivero.ver']);
    await repo.guardarPolitica(15);

    expect(llamadas.map((l) => `${l.init.method ?? 'GET'} ${l.url.replace(BASE, '')}`)).toEqual([
      'GET /usuarios?incluirBajas=true',
      'PUT /usuarios/7',
      'POST /usuarios/7/suspender',
      'POST /usuarios/7/reactivar',
      'POST /usuarios/7/baja',
      'PUT /roles/OPERARIO/permisos',
      'PUT /seguridad/politica',
    ]);
    expect(JSON.parse(String(llamadas[5].init.body))).toEqual({ permisos: ['vivero.ver'] });
    expect(JSON.parse(String(llamadas[6].init.body))).toEqual({ inactividadMin: 15 });
  });

  it('getAuditoria manda sólo los filtros con valor, más página y tamaño', async () => {
    const llamadas = stubFetch({ status: 200, body: { items: [], pagina: 0, tamanio: 25, total: 0, totalPaginas: 1 } });

    await new HttpSeguridadRepository(BASE).getAuditoria({ objetivo: 'jperez', tipo: '', pagina: 2, tamanio: 25 });

    expect(llamadas[0].url).toBe(`${BASE}/auditoria?objetivo=jperez&pagina=2&tamanio=25`);
  });

  it('en el sistema real no hay usuarios demo', () => {
    expect(new HttpSeguridadRepository(BASE).usuariosDemo()).toEqual([]);
  });
});
