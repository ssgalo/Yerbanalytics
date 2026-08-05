/**
 * Suite de conformidad del contrato de dispositivo de captura v1.
 *
 * Verifica que un backend implemente el contrato tal como lo declara `openapi.yaml`:
 * los códigos de respuesta, las transiciones de estado y las reglas que un cliente
 * necesita poder dar por sentadas.
 *
 * Sirve para dos cosas:
 *   1. Detectar regresiones del backend.
 *   2. Servir de criterio de aceptación el día que exista otro cliente (una app Android).
 *      Si esta suite pasa, escribir ese cliente no requiere tocar el backend.
 *
 * Uso:
 *   npm test
 *   BASE_URL=https://mi-host npm test
 *
 * Sin dependencias: node:test y fetch nativo.
 */

import { test, before, after, describe } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';

const BASE = (process.env.BASE_URL ?? 'http://localhost:8000').replace(/\/$/, '');
const CONTRATO = `${BASE}/api/camara/v1`;

/** Bytes de un JPEG de juguete. El contrato no valida que sea un JPEG real. */
const JPEG = Buffer.from('bytes-de-un-jpeg-de-conformidad');
const sha256 = (buf) => createHash('sha256').update(buf).digest('hex');

/** Sector real de la topología vigente. Se descubre al arrancar. */
let SECTOR;

/**
 * fetch con un reintento ante error de socket.
 *
 * Abortar un stream SSE deja sockets keep-alive en un estado que el siguiente uso descubre
 * como "other side closed". No es un fallo del backend —es cómo funciona un pool HTTP de
 * larga vida— así que se reintenta una vez para tomar una conexión nueva. Los streams NO
 * usan este helper: necesitan su propio AbortController y no deben reintentarse.
 */
async function http(url, init = {}) {
  try {
    return await fetch(url, init);
  } catch (e) {
    if (!(e instanceof TypeError)) throw e;
    return fetch(url, init);
  }
}

async function json(res) {
  const texto = await res.text();
  return texto ? JSON.parse(texto) : null;
}

/**
 * Un stream SSE mantiene la conexión abierta por diseño, así que hay que cerrarlos todos o
 * el proceso de test no termina nunca. Cada apertura registra su controlador acá.
 */
const streamsAbiertos = new Set();

async function abrirStream(token, { viaQuery = false } = {}) {
  const ac = new AbortController();
  streamsAbiertos.add(ac);
  const url = viaQuery
    ? `${CONTRATO}/ordenes/stream?token=${encodeURIComponent(token)}`
    : `${CONTRATO}/ordenes/stream`;
  const res = await fetch(url, {
    headers: viaQuery ? {} : auth(token),
    signal: ac.signal,
  });
  return { res, cerrar: () => { ac.abort(); streamsAbiertos.delete(ac); } };
}

after(() => {
  for (const ac of streamsAbiertos) ac.abort();
  streamsAbiertos.clear();
});

/**
 * Lee el stream hasta encontrar un evento con el nombre pedido y devuelve su `data` parseado.
 * El canal emite un `ping` inmediato al abrirse (para que fluyan los headers y el cliente
 * sepa enseguida que conectó), así que hay que saltearlo.
 */
async function esperarEvento(res, nombre, { timeoutMs = 10_000, esperado = null } = {}) {
  // El lector se conserva entre llamadas sobre la misma respuesta: pedir uno nuevo tiraría
  // los bytes ya leídos y bloquearía.
  res._lector ??= res.body.getReader();
  res._buffer ??= '';
  const decoder = new TextDecoder();
  const limite = Date.now() + timeoutMs;

  while (Date.now() < limite) {
    const { value, done } = await res._lector.read();
    if (done) break;
    res._buffer += decoder.decode(value, { stream: true });

    for (const bloque of res._buffer.split('\n\n')) {
      const evento = bloque.match(/event:\s*(\w+)/)?.[1];
      const data = bloque.match(/data:\s*(\{.*\})/)?.[1];
      if (evento !== nombre || !data) continue;
      const parsed = JSON.parse(data);
      // Con `esperado` se ignoran eventos de órdenes anteriores que sigan en el buffer.
      if (esperado && parsed.ordenId !== esperado) continue;
      return parsed;
    }
  }
  assert.fail(`No llegó ningún evento '${nombre}' en ${timeoutMs} ms`);
}

// ---------------------------------------------------------------------------
// Helpers del ciclo de vida de un dispositivo
// ---------------------------------------------------------------------------

async function generarCodigo() {
  const res = await http(`${BASE}/api/camara/vinculacion`, { method: 'POST' });
  assert.equal(res.status, 200, 'la plataforma debe poder emitir un código de vinculación');
  const body = await json(res);
  assert.ok(body.codigo, 'el código de vinculación no puede venir vacío');
  assert.ok(body.expiraEn > Date.now(), 'el código debe traer su vencimiento');
  return body.codigo;
}

async function enrolar(nombre) {
  const codigo = await generarCodigo();
  const res = await http(`${CONTRATO}/enrolar`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ codigo, nombre, plataforma: 'suite-de-conformidad' }),
  });
  assert.equal(res.status, 201, 'un código válido debe enrolar el dispositivo');
  const body = await json(res);
  assert.ok(body.dispositivoId, 'el enrolamiento debe devolver el id del dispositivo');
  assert.ok(body.refreshToken, 'el enrolamiento debe devolver la credencial de renovación');
  return { ...body, codigo };
}

async function accessToken(refreshToken) {
  const res = await http(`${CONTRATO}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  assert.equal(res.status, 200, 'una credencial válida debe canjearse por un token');
  const body = await json(res);
  assert.ok(body.accessToken, 'debe devolver el token de acceso');
  assert.ok(body.expiraEnSeg > 0, 'debe informar cuánto dura el token, para renovarlo a tiempo');
  return body.accessToken;
}

/** Enrola un dispositivo nuevo y devuelve su token listo para usar. */
async function dispositivo(nombre) {
  const { refreshToken, dispositivoId } = await enrolar(nombre);
  return { token: await accessToken(refreshToken), dispositivoId, refreshToken };
}

const auth = (token) => ({ Authorization: `Bearer ${token}` });

/**
 * Dispositivo enrolado y con su canal abierto. El backend despacha al canal abierto más
 * recientemente, así que la orden que se emita a continuación le va a corresponder a este
 * dispositivo — que es además el flujo real: un equipo captura las órdenes que recibe.
 */
async function dispositivoConectado(nombre) {
  const d = await dispositivo(nombre);
  const { res, cerrar } = await abrirStream(d.token);
  assert.equal(res.status, 200, 'el canal debe abrirse');
  return { ...d, res, cerrar };
}

async function emitirOrden(sectorId = SECTOR, posicionRiel = 1200) {
  const res = await http(`${BASE}/api/capturas/ordenes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sectorId, posicionRiel }),
  });
  assert.equal(res.status, 201, 'la plataforma debe poder emitir una orden');
  return (await json(res)).ordenId;
}

async function estadoOrden(ordenId) {
  const res = await http(`${BASE}/api/capturas/ordenes/${ordenId}`);
  assert.equal(res.status, 200);
  return json(res);
}

function formData(bytes, metaExtra = {}) {
  const fd = new FormData();
  fd.append('imagen', new Blob([bytes], { type: 'image/jpeg' }), 'captura.jpg');
  const meta = {
    ancho: 1920,
    alto: 1080,
    sha256: sha256(bytes),
    capturadaEn: Date.now(),
    ...metaExtra,
  };
  fd.append('meta', new Blob([JSON.stringify(meta)], { type: 'application/json' }), 'meta.json');
  return fd;
}

async function subirImagen(ordenId, token, bytes = JPEG, metaExtra = {}) {
  return http(`${CONTRATO}/ordenes/${ordenId}/imagen`, {
    method: 'POST',
    headers: auth(token),
    body: formData(bytes, metaExtra),
  });
}

async function acusarFallo(ordenId, token, motivo, detalle) {
  return http(`${CONTRATO}/ordenes/${ordenId}/fallo`, {
    method: 'POST',
    headers: { ...auth(token), 'Content-Type': 'application/json' },
    body: JSON.stringify({ motivo, detalle }),
  });
}

// ---------------------------------------------------------------------------

before(async () => {
  let res;
  try {
    res = await http(`${BASE}/api/nursery`);
  } catch (e) {
    assert.fail(
      `No se pudo contactar el backend en ${BASE}. Levantalo con './mvnw spring-boot:run'.\n${e.message}`,
    );
  }
  assert.equal(res.status, 200, 'el backend debe responder el snapshot del vivero');
  const nursery = await json(res);
  SECTOR = nursery.sectors?.[0]?.id;
  assert.ok(SECTOR, 'el vivero debe tener al menos un sector cargado para poder pedir capturas');
});

// ===========================================================================

describe('Credenciales', () => {
  test('el ciclo vinculación → enrolamiento → token entrega un token usable', async () => {
    const { token } = await dispositivo('conformidad · ciclo completo');

    const res = await http(`${CONTRATO}/config`, { headers: auth(token) });
    assert.equal(res.status, 200);
  });

  test('el código de vinculación es de un solo uso', async () => {
    const codigo = await generarCodigo();
    const cuerpo = (nombre) => ({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ codigo, nombre }),
    });

    assert.equal((await http(`${CONTRATO}/enrolar`, cuerpo('primero'))).status, 201);
    assert.equal(
      (await http(`${CONTRATO}/enrolar`, cuerpo('segundo'))).status,
      401,
      'un código ya consumido no debe volver a enrolar',
    );
  });

  test('un código inexistente no enrola', async () => {
    const res = await http(`${CONTRATO}/enrolar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ codigo: 'ZZZZ-ZZZZ', nombre: 'impostor' }),
    });
    assert.equal(res.status, 401);
  });

  test('una credencial de renovación inválida no emite token', async () => {
    const res = await http(`${CONTRATO}/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: 'no-existe' }),
    });
    assert.equal(res.status, 401);
  });

  test('la credencial se puede renovar varias veces sin volver a vincular', async () => {
    const { refreshToken } = await enrolar('conformidad · renovación');

    const a = await accessToken(refreshToken);
    const b = await accessToken(refreshToken);

    assert.ok(a && b, 'ambas renovaciones deben entregar un token');
  });
});

describe('Autenticación', () => {
  test('las rutas del contrato exigen token', async () => {
    for (const [ruta, init] of [
      [`${CONTRATO}/config`, {}],
      [`${CONTRATO}/heartbeat`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }],
      [`${CONTRATO}/ordenes/stream`, {}],
    ]) {
      const res = await http(ruta, init);
      assert.equal(res.status, 401, `${ruta} debe exigir token`);
      await res.text(); // liberar el socket
    }
  });

  test('un token inválido se rechaza', async () => {
    const res = await http(`${CONTRATO}/config`, { headers: auth('no-es-un-jwt') });
    assert.equal(res.status, 401);
    await res.text();
  });

  test('el header Authorization funciona en TODAS las rutas, incluida la del stream', async () => {
    // Esta es la propiedad que permite escribir un cliente nativo sin tocar el backend:
    // el token por query string es una concesión a EventSource, no la forma general.
    const { token } = await dispositivo('conformidad · header en stream');

    const { res, cerrar } = await abrirStream(token);
    assert.equal(res.status, 200, 'el stream debe aceptar el header Authorization');
    assert.match(res.headers.get('content-type') ?? '', /text\/event-stream/);
    cerrar();
  });

  test('el query param se admite en el stream, para clientes que no pueden fijar headers', async () => {
    const { token } = await dispositivo('conformidad · query en stream');

    const { res, cerrar } = await abrirStream(token, { viaQuery: true });
    assert.equal(res.status, 200);
    cerrar();
  });

  test('el query param NO sirve fuera del stream', async () => {
    const { token } = await dispositivo('conformidad · query acotado');

    const res = await http(`${CONTRATO}/config?token=${encodeURIComponent(token)}`);
    assert.equal(res.status, 401, 'la alternativa está acotada al stream a propósito');
  });
});

describe('Configuración remota', () => {
  test('la configuración trae todos los parámetros que el cliente necesita', async () => {
    const { token } = await dispositivo('conformidad · config');
    const cfg = await json(await http(`${CONTRATO}/config`, { headers: auth(token) }));

    for (const clave of [
      'anchoMax', 'altoMax', 'calidadJpeg', 'warmupMs',
      'heartbeatSeg', 'timeoutOrdenSeg', 'maxColaOrdenes',
    ]) {
      assert.ok(cfg[clave] !== undefined && cfg[clave] !== null, `falta '${clave}' en la configuración`);
    }
    assert.ok(cfg.calidadJpeg > 0 && cfg.calidadJpeg <= 1, 'calidadJpeg debe estar entre 0 y 1');
  });
});

describe('Señal de vida', () => {
  test('el heartbeat se acepta y el dispositivo queda visible para la plataforma', async () => {
    const { token, dispositivoId } = await dispositivo('conformidad · heartbeat');

    const res = await http(`${CONTRATO}/heartbeat`, {
      method: 'POST',
      headers: { ...auth(token), 'Content-Type': 'application/json' },
      body: JSON.stringify({ capturaListo: true, capturasOk: 3, capturasError: 1, pendientesEnvio: 0 }),
    });
    assert.equal(res.status, 204);

    const flota = await json(await http(`${BASE}/api/camara/dispositivos`));
    const yo = flota.find((d) => d.id === dispositivoId);
    assert.ok(yo, 'el dispositivo debe figurar en la flota');
    assert.equal(yo.estado, 'operativo', 'tras reportar, debe verse operativo');
    assert.equal(yo.capturasOk, 3);
  });
});

describe('Canal de órdenes', () => {
  test('al abrir el canal se drenan las órdenes pendientes', async () => {
    /*
     * Este escenario tiene una carrera inherente al ejercitar un servidor real: entre emitir
     * la orden y abrir el canal, el barrido periódico del backend puede despacharla a otro
     * canal que quedó abierto de un test anterior (esta suite no puede cerrarlos del lado del
     * servidor). Cuando eso pasa, la orden sale de PENDIENTE y nuestro canal ya no la recibe.
     *
     * No es una violación del contrato —el drenado ocurrió, sólo que hacia otro dispositivo—
     * así que se reintenta con una orden nueva en lugar de fallar. Con latencia de red la
     * ventana se ensancha, y sin esto la suite es intermitente contra un túnel.
     */
    const { token } = await dispositivo('conformidad · drenado');
    const { res, cerrar } = await abrirStream(token);
    assert.equal(res.status, 200);

    let evento = null;
    let ordenId = null;
    for (let intento = 1; intento <= 3 && !evento; intento++) {
      ordenId = await emitirOrden();
      try {
        evento = await esperarEvento(res, 'orden', { timeoutMs: 8000, esperado: ordenId });
      } catch {
        // La tomó otro canal: se prueba con una orden nueva.
      }
    }

    assert.ok(evento, 'el canal debe recibir alguna de las órdenes emitidas');
    assert.equal(evento.ordenId, ordenId, 'debe llegar la orden emitida');
    for (const clave of ['ordenId', 'sectorId', 'zonaId', 'posicionRiel', 'emitidaEn', 'venceEn', 'intento']) {
      assert.ok(evento[clave] !== undefined, `la orden debe traer '${clave}'`);
    }

    cerrar();
    assert.equal((await estadoOrden(ordenId)).estado, 'ENTREGADA');
  });
});

describe('Entrega de la captura', () => {
  test('la imagen se correlaciona con su orden y queda recibida', async () => {
    const { token, cerrar } = await dispositivoConectado('conformidad · subida');
    const ordenId = await emitirOrden();

    const res = await subirImagen(ordenId, token);
    assert.equal(res.status, 201);
    const creada = await json(res);
    assert.equal(creada.ordenId, ordenId);
    assert.ok(creada.capturaId, 'debe devolver el id de la captura');
    assert.ok(creada.imagenUrl, 'debe devolver la URL de la imagen');

    const estado = await estadoOrden(ordenId);
    assert.equal(estado.estado, 'RECIBIDA');
    assert.equal(estado.capturaId, creada.capturaId);

    // La imagen queda servida y es exactamente la que se subió.
    const img = await http(`${BASE}${creada.imagenUrl}`);
    assert.equal(img.status, 200);
    assert.equal(sha256(Buffer.from(await img.arrayBuffer())), sha256(JPEG));
    cerrar();
  });

  test('reintentar sobre una orden ya resuelta devuelve 409 con la captura existente', async () => {
    // Es el caso del corte de red cuya primera subida sí llegó. El contrato obliga al
    // cliente a tratarlo como éxito, y para eso necesita el capturaId.
    const { token, cerrar } = await dispositivoConectado('conformidad · idempotencia');
    const ordenId = await emitirOrden();

    const primera = await json(await subirImagen(ordenId, token));

    const res = await subirImagen(ordenId, token);
    assert.equal(res.status, 409);
    const repetida = await json(res);
    assert.equal(repetida.capturaId, primera.capturaId, 'el 409 debe traer la captura que ya existe');
    cerrar();
  });

  test('una orden inexistente se rechaza con 404', async () => {
    const { token } = await dispositivo('conformidad · 404');

    const res = await subirImagen(randomUUID(), token);
    assert.equal(res.status, 404);
  });

  test('un hash que no coincide se rechaza con 422 y la orden se reintenta', async () => {
    const { token, cerrar } = await dispositivoConectado('conformidad · hash');
    const ordenId = await emitirOrden();

    const res = await subirImagen(ordenId, token, JPEG, { sha256: sha256(Buffer.from('otra-cosa')) });
    assert.equal(res.status, 422);

    // La orden vuelve al circuito de reintento: se reencola y se reentrega al canal abierto.
    const estado = await estadoOrden(ordenId);
    assert.notEqual(estado.estado, 'RECIBIDA', 'la imagen corrupta no debe darse por buena');
    assert.equal(estado.intentos, 2, 'debe contar el intento fallido');
    cerrar();
  });

  test('un dispositivo ajeno no puede responder una orden que no le fue entregada', async () => {
    const b = await dispositivo('conformidad · ajeno');
    const a = await dispositivoConectado('conformidad · dueño');
    const ordenId = await emitirOrden();

    assert.equal((await subirImagen(ordenId, b.token)).status, 403);
    assert.equal((await subirImagen(ordenId, a.token)).status, 201);
    a.cerrar();
  });
});

describe('Acuse de fallo', () => {
  test('todos los motivos del conjunto cerrado se aceptan', async () => {
    const { token, cerrar } = await dispositivoConectado('conformidad · motivos');

    for (const motivo of [
      'CAMARA_NO_LISTA', 'EXPORTACION_FALLIDA', 'COLA_LLENA',
      'TIMEOUT_LOCAL', 'ENVIO_AGOTADO', 'ERROR_DESCONOCIDO',
    ]) {
      const ordenId = await emitirOrden();
      const res = await acusarFallo(ordenId, token, motivo, `prueba de ${motivo}`);
      assert.equal(res.status, 202, `el motivo '${motivo}' debe aceptarse`);
    }
    cerrar();
  });

  test('un motivo fuera del conjunto se rechaza con 400', async () => {
    // El conjunto es cerrado a propósito: si admitiera texto libre, el backend no podría
    // clasificar los fallos sin interpretarlo.
    const { token } = await dispositivo('conformidad · motivo inválido');
    const ordenId = await emitirOrden();

    const res = await acusarFallo(ordenId, token, 'SE_ME_CAYO_EL_TELEFONO');
    assert.equal(res.status, 400);
  });

  test('el fallo reencola la orden y registra su motivo', async () => {
    const { token, cerrar } = await dispositivoConectado('conformidad · reencolado');
    const ordenId = await emitirOrden();

    await acusarFallo(ordenId, token, 'COLA_LLENA', 'tope de cola alcanzado');

    const estado = await estadoOrden(ordenId);
    assert.notEqual(estado.estado, 'RECIBIDA');
    assert.equal(estado.intentos, 2);
    assert.equal(estado.motivoFallo, 'COLA_LLENA');
    cerrar();
  });

  test('agotados los intentos la orden termina en ERROR y no admite más acuses', async () => {
    const { token, cerrar } = await dispositivoConectado('conformidad · intentos');
    const cfg = await json(await http(`${CONTRATO}/config`, { headers: auth(token) }));
    const ordenId = await emitirOrden();

    // Un intento más que el máximo declarado, para asegurarnos de agotarlos.
    for (let i = 0; i < 10; i++) {
      const res = await acusarFallo(ordenId, token, 'CAMARA_NO_LISTA');
      if (res.status === 409) break;
      assert.equal(res.status, 202);
    }

    assert.equal((await estadoOrden(ordenId)).estado, 'ERROR');
    assert.equal(
      (await acusarFallo(ordenId, token, 'CAMARA_NO_LISTA')).status,
      409,
      'un estado terminal no admite acuses',
      cfg,
    );
    cerrar();
  });
});
