import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from './apiFetch';
import { escucharSesion } from '@/data/sesionEventos';
import {
  CambioClaveRequeridoError,
  PermisoDenegadoError,
  SesionCerradaError,
  SIN_PERMISO_ACCION,
} from '@/data/seguridadErrores';

function stubFetch(status: number, body?: unknown) {
  const fn = vi.fn(async () => ({
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      if (body === undefined) throw new SyntaxError('sin cuerpo');
      return body;
    },
  }));
  vi.stubGlobal('fetch', fn);
  return fn;
}

/** Registra un oyente de la capa de sesión y devuelve lo que fue recibiendo. */
function oyente() {
  const recibido = { cerradas: [] as string[], denegados: [] as (string | null)[], cambioClave: 0 };
  const baja = escucharSesion({
    sesionCerrada: (m) => recibido.cerradas.push(m),
    permisoDenegado: (p) => recibido.denegados.push(p),
    cambioClaveRequerido: () => recibido.cambioClave++,
  });
  return { recibido, baja };
}

let bajas: (() => void)[] = [];
afterEach(() => {
  vi.unstubAllGlobals();
  bajas.forEach((b) => b());
  bajas = [];
});
const escuchar = () => {
  const o = oyente();
  bajas.push(o.baja);
  return o.recibido;
};

describe('apiFetch (6.2)', () => {
  it('manda la cookie de sesión (credentials: include) y conserva el resto del init', async () => {
    const fn = stubFetch(200, {});
    await apiFetch('http://x/api/nursery', { method: 'PUT', body: '{}' });

    const [, init] = fn.mock.calls[0] as unknown as [string, RequestInit];
    expect(init.credentials).toBe('include');
    expect(init.method).toBe('PUT');
    expect(init.body).toBe('{}');
  });

  it('devuelve tal cual cualquier respuesta que no sea 401/403', async () => {
    stubFetch(409, { error: 'conflicto' });
    const res = await apiFetch('http://x/api/pasadas');
    expect(res.status).toBe(409);
  });

  it.each(['SIN_SESION', 'SESION_EXPIRADA', 'SESION_REVOCADA'] as const)(
    '401 con motivo %s avisa a la sesión y lanza SesionCerradaError',
    async (motivo) => {
      const recibido = escuchar();
      stubFetch(401, { error: 'x', motivo });

      const intento = apiFetch('http://x/api/nursery');

      await expect(intento).rejects.toBeInstanceOf(SesionCerradaError);
      await expect(intento).rejects.toMatchObject({ motivo });
      expect(recibido.cerradas).toEqual([motivo]);
    },
  );

  it('un 401 sin motivo (o sin cuerpo) se trata como SIN_SESION', async () => {
    const recibido = escuchar();
    stubFetch(401);
    await expect(apiFetch('http://x/api/nursery')).rejects.toMatchObject({ motivo: 'SIN_SESION' });
    expect(recibido.cerradas).toEqual(['SIN_SESION']);
  });

  it('403 con permiso lanza PermisoDenegadoError con el mensaje uniforme y avisa para recargar el perfil', async () => {
    const recibido = escuchar();
    stubFetch(403, { error: 'Sin permiso', permiso: 'reglas.editar' });

    const intento = apiFetch('http://x/api/rules/parametros', { method: 'PUT' });

    await expect(intento).rejects.toBeInstanceOf(PermisoDenegadoError);
    await expect(intento).rejects.toMatchObject({ permiso: 'reglas.editar', message: SIN_PERMISO_ACCION });
    expect(recibido.denegados).toEqual(['reglas.editar']);
    expect(recibido.cerradas).toEqual([]);
  });

  it('403 con CAMBIO_CLAVE_REQUERIDO lanza su error y avisa', async () => {
    const recibido = escuchar();
    stubFetch(403, { error: 'x', motivo: 'CAMBIO_CLAVE_REQUERIDO' });

    await expect(apiFetch('http://x/api/nursery')).rejects.toBeInstanceOf(CambioClaveRequeridoError);
    expect(recibido.cambioClave).toBe(1);
    expect(recibido.denegados).toEqual([]);
  });

  it('silencioso: lanza igual pero no avisa a la sesión (lo usa el login)', async () => {
    const recibido = escuchar();
    stubFetch(401, { error: 'Credenciales incorrectas' });

    await expect(apiFetch('http://x/api/auth/login', {}, { silencioso: true })).rejects.toBeInstanceOf(
      SesionCerradaError,
    );
    expect(recibido.cerradas).toEqual([]);
  });
});
