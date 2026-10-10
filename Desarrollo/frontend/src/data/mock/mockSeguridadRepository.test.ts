import { describe, expect, it } from 'vitest';
import { MockSeguridadRepository } from './mockSeguridadRepository';
import {
  CambioClaveRequeridoError,
  CredencialesIncorrectasError,
  OperacionRechazadaError,
  PermisoDenegadoError,
  SesionCerradaError,
} from '@/data/seguridadErrores';
import { MATRIZ_POR_DEFECTO } from '@/lib/catalogoSeguridad';

/** Reloj manejable: el vencimiento por inactividad se prueba sin esperar una hora. */
function reloj(inicio = Date.parse('2026-10-10T12:00:00.000Z')) {
  let t = inicio;
  return { ahora: () => t, avanzar: (ms: number) => (t += ms) };
}

async function comoAdmin() {
  const r = reloj();
  const repo = new MockSeguridadRepository(r.ahora);
  await repo.login('admin', 'demo');
  return { repo, r };
}

const rechazoCon = (status: 400 | 409) => expect.objectContaining({ name: 'OperacionRechazadaError', status });

describe('MockSeguridadRepository · login (6.3)', () => {
  it('los cuatro usuarios demo entran con "demo" y traen la matriz por defecto de su rol', async () => {
    const repo = new MockSeguridadRepository();
    for (const [usuario, rol] of [
      ['admin', 'ADMINISTRADOR'],
      ['agronomo', 'INGENIERO_AGRONOMO'],
      ['productor', 'PRODUCTOR_VIVERISTA'],
      ['operario', 'OPERARIO'],
    ] as const) {
      const perfil = await repo.login(usuario, 'demo');
      expect(perfil.rol).toBe(rol);
      expect(new Set(perfil.permisos)).toEqual(new Set(MATRIZ_POR_DEFECTO[rol]));
      expect(perfil.inactividadMin).toBe(60);
    }
    expect(repo.usuariosDemo().map((u) => u.username)).toEqual(['admin', 'agronomo', 'productor', 'operario']);
  });

  it('usuario inexistente, clave errónea y cuenta suspendida rechazan igual', async () => {
    const { repo } = await comoAdmin();
    const operario = (await repo.listarUsuarios(false)).find((u) => u.username === 'operario')!;
    await repo.suspenderUsuario(operario.id);

    for (const [u, c] of [
      ['nadie', 'demo'],
      ['agronomo', 'mala'],
      ['operario', 'demo'],
    ]) {
      await expect(repo.login(u, c)).rejects.toBeInstanceOf(CredencialesIncorrectasError);
    }
  });
});

describe('MockSeguridadRepository · inactividad simulada (6.3)', () => {
  it('las lecturas no refrescan la actividad: la sesión vence igual', async () => {
    const { repo, r } = await comoAdmin();
    for (let i = 0; i < 12; i++) {
      r.avanzar(5 * 60_000);
      if (i < 11) await repo.getPerfil(); // "sondeo" cada 5 min, 55 min en total
    }
    // 60 min sin actividad: vence.
    r.avanzar(1);
    await expect(repo.getPerfil()).rejects.toMatchObject({ motivo: 'SESION_EXPIRADA' });
  });

  it('la señal de actividad la mantiene viva', async () => {
    const { repo, r } = await comoAdmin();
    r.avanzar(50 * 60_000);
    await repo.registrarActividad();
    r.avanzar(50 * 60_000);
    await expect(repo.getPerfil()).resolves.toBeTruthy();
  });

  it('después del vencimiento ya no hay sesión', async () => {
    const { repo, r } = await comoAdmin();
    r.avanzar(61 * 60_000);
    await expect(repo.getPerfil()).rejects.toBeInstanceOf(SesionCerradaError);
    await expect(repo.getPerfil()).rejects.toMatchObject({ motivo: 'SIN_SESION' });
  });
});

describe('MockSeguridadRepository · permisos', () => {
  it('el Operario no puede gestionar usuarios (403)', async () => {
    const repo = new MockSeguridadRepository();
    await repo.login('operario', 'demo');
    await expect(repo.listarUsuarios(false)).rejects.toBeInstanceOf(PermisoDenegadoError);
  });

  it('con la clave temporal sólo se puede cambiar la clave', async () => {
    const { repo } = await comoAdmin();
    await repo.crearUsuario({ username: 'jperez', nombre: 'Juan Pérez', rol: 'ADMINISTRADOR', clave: 'temporal1' });
    await repo.login('jperez', 'temporal1');

    expect((await repo.getPerfil()).debeCambiarClave).toBe(true);
    await expect(repo.listarUsuarios(false)).rejects.toBeInstanceOf(CambioClaveRequeridoError);

    await repo.cambiarClave('temporal1', 'definitiva1');
    await expect(repo.listarUsuarios(false)).resolves.toBeTruthy();
  });
});

describe('MockSeguridadRepository · gestión de usuarios', () => {
  it('alta válida: normaliza el username, queda activo y con clave temporal; se audita', async () => {
    const { repo } = await comoAdmin();
    const u = await repo.crearUsuario({ username: 'JPerez', nombre: 'Juan Pérez', rol: 'OPERARIO', clave: 'temporal1' });

    expect(u).toMatchObject({ username: 'jperez', estado: 'ACTIVO', debeCambiarClave: true });
    expect(u).not.toHaveProperty('clave');
    const audit = await repo.getAuditoria({ objetivo: 'jperez' });
    expect(audit.items[0]).toMatchObject({ tipo: 'USUARIO_ALTA', autorUsername: 'admin' });
  });

  it('username repetido (sin distinguir mayúsculas, incluso contra una baja) → 409', async () => {
    const { repo } = await comoAdmin();
    const u = await repo.crearUsuario({ username: 'jperez', nombre: 'Juan', rol: 'OPERARIO', clave: 'temporal1' });
    await repo.darDeBajaUsuario(u.id);

    await expect(
      repo.crearUsuario({ username: 'JPerez', nombre: 'Otro', rol: 'OPERARIO', clave: 'temporal1' }),
    ).rejects.toEqual(rechazoCon(409));
  });

  it('formato de username, rol inexistente y clave débil → 400 sin auditar', async () => {
    const { repo } = await comoAdmin();
    const antes = (await repo.getAuditoria({})).total;

    await expect(repo.crearUsuario({ username: 'j', nombre: 'J', rol: 'OPERARIO', clave: 'temporal1' })).rejects.toEqual(
      rechazoCon(400),
    );
    await expect(
      repo.crearUsuario({ username: 'jperez', nombre: 'J', rol: 'SUPERVISOR' as never, clave: 'temporal1' }),
    ).rejects.toEqual(rechazoCon(400));
    await expect(repo.crearUsuario({ username: 'jperez', nombre: 'J', rol: 'OPERARIO', clave: 'corta' })).rejects.toEqual(
      rechazoCon(400),
    );
    await expect(repo.crearUsuario({ username: 'jperez', nombre: 'J', rol: 'OPERARIO', clave: 'jperez' })).rejects.toEqual(
      rechazoCon(400),
    );
    expect((await repo.getAuditoria({})).total).toBe(antes);
  });

  it('autosuspensión y autobaja → 409', async () => {
    const { repo } = await comoAdmin();
    const yo = (await repo.getPerfil()).id;
    await expect(repo.suspenderUsuario(yo)).rejects.toEqual(rechazoCon(409));
    await expect(repo.darDeBajaUsuario(yo)).rejects.toEqual(rechazoCon(409));
  });

  it('el último Administrador activo no puede perder el rol', async () => {
    const { repo } = await comoAdmin();
    const yo = (await repo.getPerfil()).id;
    await expect(repo.editarUsuario(yo, { nombre: 'Lucía Fernández', rol: 'OPERARIO' })).rejects.toEqual(rechazoCon(409));
  });

  it('una baja no se reactiva', async () => {
    const { repo } = await comoAdmin();
    const u = (await repo.listarUsuarios(false)).find((x) => x.username === 'operario')!;
    await repo.darDeBajaUsuario(u.id);
    await expect(repo.reactivarUsuario(u.id)).rejects.toEqual(rechazoCon(409));
    expect((await repo.listarUsuarios(false)).some((x) => x.username === 'operario')).toBe(false);
    expect((await repo.listarUsuarios(true)).some((x) => x.username === 'operario')).toBe(true);
  });

  it('cambiar el rol de quien está usando la sesión la revoca', async () => {
    const { repo } = await comoAdmin();
    const otro = await repo.crearUsuario({ username: 'admin2', nombre: 'Otra', rol: 'ADMINISTRADOR', clave: 'temporal1' });
    expect(otro.rol).toBe('ADMINISTRADOR');
    const yo = (await repo.getPerfil()).id;

    await repo.editarUsuario(yo, { nombre: 'Lucía Fernández', rol: 'PRODUCTOR_VIVERISTA' });

    await expect(repo.getPerfil()).rejects.toMatchObject({ motivo: 'SESION_REVOCADA' });
    const audit = await (async () => {
      await repo.login('admin2', 'temporal1');
      await repo.cambiarClave('temporal1', 'definitiva1');
      return repo.getAuditoria({ tipo: 'USUARIO_ROL_CAMBIADO' });
    })();
    expect(audit.items[0].detalle).toEqual({ anterior: { rol: 'ADMINISTRADOR' }, nuevo: { rol: 'PRODUCTOR_VIVERISTA' } });
  });
});

describe('MockSeguridadRepository · matriz y política', () => {
  it('edición sin su par de lectura → 400; quitarle un intocable al Administrador → 409', async () => {
    const { repo } = await comoAdmin();
    await expect(repo.guardarPermisosRol('OPERARIO', ['reglas.editar'])).rejects.toEqual(rechazoCon(400));
    const sinGestion = MATRIZ_POR_DEFECTO.ADMINISTRADOR.filter((p) => p !== 'usuarios.gestionar');
    await expect(repo.guardarPermisosRol('ADMINISTRADOR', [...sinGestion])).rejects.toEqual(rechazoCon(409));
  });

  it('guardar la matriz audita agregados y quitados y rige en el perfil', async () => {
    const { repo } = await comoAdmin();
    const r = await repo.guardarPermisosRol('PRODUCTOR_VIVERISTA', [
      ...MATRIZ_POR_DEFECTO.PRODUCTOR_VIVERISTA.filter((p) => p !== 'pasadas.operar'),
    ]);
    expect(r.permisos).not.toContain('pasadas.operar');

    const audit = await repo.getAuditoria({ tipo: 'ROL_PERMISOS_CAMBIADOS' });
    expect(audit.items[0]).toMatchObject({ objetivoRef: 'PRODUCTOR_VIVERISTA', detalle: { agregados: [], quitados: ['pasadas.operar'] } });

    await repo.login('productor', 'demo');
    expect((await repo.getPerfil()).permisos).not.toContain('pasadas.operar');
  });

  it('la política admite 5–480 minutos y el cambio queda auditado', async () => {
    const { repo } = await comoAdmin();
    await expect(repo.guardarPolitica(2)).rejects.toBeInstanceOf(OperacionRechazadaError);
    await expect(repo.guardarPolitica(481)).rejects.toEqual(rechazoCon(400));

    expect(await repo.guardarPolitica(15)).toEqual({ inactividadMin: 15, minimo: 5, maximo: 480 });
    expect((await repo.getPerfil()).inactividadMin).toBe(15);
    const audit = await repo.getAuditoria({ tipo: 'POLITICA_SESION_CAMBIADA' });
    expect(audit.items[0].detalle).toEqual({ anterior: { inactividadMin: 60 }, nuevo: { inactividadMin: 15 } });
  });
});

describe('MockSeguridadRepository · auditoría', () => {
  it('del más reciente al más antiguo, filtrable por autor (sin distinguir mayúsculas) y paginada', async () => {
    const { repo, r } = await comoAdmin();
    for (const n of ['uno', 'dos', 'tres']) {
      r.avanzar(1000);
      await repo.crearUsuario({ username: n, nombre: n, rol: 'OPERARIO', clave: 'temporal1' });
    }

    const pag = await repo.getAuditoria({ autor: 'ADMIN', tipo: 'USUARIO_ALTA', pagina: 0, tamanio: 2 });

    expect(pag.items.map((i) => i.objetivoRef)).toEqual(['tres', 'dos']);
    expect(pag.total).toBe(6); // 3 de la siembra demo + 3 nuevas
    expect(pag.totalPaginas).toBe(3);
    expect(await repo.verificarAuditoria()).toMatchObject({ integra: true, primerIdRoto: null });
  });
});
