import { describe, expect, it } from 'vitest';
import {
  cuentaRegresivaSeg,
  duracionPasoSecuenciaSeg,
  filasLectura,
  textoPasoSecuencia,
  tituloPasoSecuencia,
} from './secuenciaPresentacion';
import type {
  EstadoPaso,
  PasoSecuencia,
  Secuencia,
  TipoPasoSecuencia,
  TipoSecuencia,
} from '@/types/domain';

const paso = (
  tipo: TipoPasoSecuencia,
  estado: EstadoPaso,
  over: Partial<PasoSecuencia> = {},
): PasoSecuencia => ({
  n: 1,
  tipo,
  estado,
  codigoError: null,
  detalle: null,
  commandId: null,
  esperaHasta: null,
  iniciadoEn: null,
  terminadoEn: null,
  ...over,
});

const secuencia = (tipo: TipoSecuencia, over: Partial<Secuencia> = {}): Secuencia => ({
  id: 's',
  tipo,
  estado: 'EN_CURSO',
  zonaId: 'MZ-1',
  sectorId: tipo === 'LECTURA' ? null : 'MZ-1-001',
  parametros: { duracionSeg: 15, esperaSeg: 8 },
  iniciadaEn: 1,
  finalizadaEn: null,
  cancelacionSolicitada: false,
  error: null,
  lectura: null,
  pasos: [],
  ...over,
});

describe('tituloPasoSecuencia', () => {
  it('nombra cada tipo de paso con su sector o zona', () => {
    const riego = secuencia('RIEGO');
    expect(tituloPasoSecuencia(paso('ABRIR', 'OK'), riego)).toBe('Abrir la válvula de MZ-1-001');
    expect(tituloPasoSecuencia(paso('CERRAR', 'OK'), riego)).toBe('Cerrar la válvula');
    const sombra = secuencia('MEDIASOMBRA');
    expect(tituloPasoSecuencia(paso('DESPLEGAR', 'OK'), sombra)).toBe('Desplegar la mediasombra');
    expect(tituloPasoSecuencia(paso('ENROLLAR', 'OK'), sombra)).toBe('Enrollarla');
    const lectura = secuencia('LECTURA');
    expect(tituloPasoSecuencia(paso('PEDIR', 'OK'), lectura)).toBe('Pedir lectura a MZ-1');
    expect(tituloPasoSecuencia(paso('ESPERAR_TELEMETRIA', 'OK'), lectura)).toBe(
      'Esperando la telemetría…',
    );
    expect(tituloPasoSecuencia(paso('MOSTRAR', 'OK'), lectura)).toBe('Lectura de la zona');
  });

  it('la espera dice cuántos segundos según el tipo de secuencia', () => {
    expect(tituloPasoSecuencia(paso('ESPERAR', 'OK'), secuencia('RIEGO'))).toBe(
      'Regar durante 15 s',
    );
    expect(tituloPasoSecuencia(paso('ESPERAR', 'OK'), secuencia('MEDIASOMBRA'))).toBe(
      'Mantenerla desplegada 8 s',
    );
  });
});

describe('textoPasoSecuencia', () => {
  it('pendiente, omitido y error muestran el estado o el detalle del backend', () => {
    expect(textoPasoSecuencia(paso('ABRIR', 'PENDIENTE'), 0)).toBe('Pendiente');
    expect(textoPasoSecuencia(paso('ABRIR', 'OMITIDO'), 0)).toBe('Omitido');
    expect(
      textoPasoSecuencia(paso('ABRIR', 'OMITIDO', { detalle: 'Cancelada por el operador' }), 0),
    ).toBe('Cancelada por el operador');
    expect(
      textoPasoSecuencia(
        paso('ABRIR', 'ERROR', { detalle: 'La válvula no respondió en 10 s.' }),
        0,
      ),
    ).toBe('La válvula no respondió en 10 s.');
    expect(textoPasoSecuencia(paso('ABRIR', 'ERROR'), 0)).toBe('Falló');
  });

  it('en curso y ok, según el tipo', () => {
    expect(textoPasoSecuencia(paso('ABRIR', 'EN_CURSO'), 0)).toBe('Abriendo…');
    expect(textoPasoSecuencia(paso('ABRIR', 'OK'), 0)).toBe('Válvula abierta');
    expect(textoPasoSecuencia(paso('CERRAR', 'EN_CURSO'), 0)).toBe('Cerrando…');
    expect(textoPasoSecuencia(paso('CERRAR', 'OK'), 0)).toBe('Válvula cerrada');
    expect(textoPasoSecuencia(paso('DESPLEGAR', 'EN_CURSO'), 0)).toBe('Desplegando…');
    expect(textoPasoSecuencia(paso('DESPLEGAR', 'OK'), 0)).toBe('Desplegada');
    expect(textoPasoSecuencia(paso('ENROLLAR', 'EN_CURSO'), 0)).toBe('Enrollando…');
    expect(textoPasoSecuencia(paso('ENROLLAR', 'OK'), 0)).toBe('Enrollada');
    expect(textoPasoSecuencia(paso('PEDIR', 'EN_CURSO'), 0)).toBe('Pidiendo la lectura…');
    expect(textoPasoSecuencia(paso('PEDIR', 'OK'), 0)).toBe('Pedido enviado');
    expect(textoPasoSecuencia(paso('ESPERAR_TELEMETRIA', 'EN_CURSO'), 0)).toBe(
      'Esperando al nodo…',
    );
    expect(textoPasoSecuencia(paso('ESPERAR_TELEMETRIA', 'OK'), 0)).toBe('Lectura recibida');
    expect(textoPasoSecuencia(paso('MOSTRAR', 'OK'), 0)).toBe('Lista');
  });

  it('la espera en curso muestra la cuenta regresiva', () => {
    const espera = paso('ESPERAR', 'EN_CURSO', { esperaHasta: 20_000 });
    expect(textoPasoSecuencia(espera, 12_500)).toBe('Faltan 8 s');
    expect(textoPasoSecuencia(paso('ESPERAR', 'OK'), 0)).toBe('Listo');
  });
});

describe('cuentaRegresivaSeg', () => {
  it('redondea para arriba y no baja de cero', () => {
    expect(cuentaRegresivaSeg(20_000, 12_500)).toBe(8);
    expect(cuentaRegresivaSeg(20_000, 19_999)).toBe(1);
    expect(cuentaRegresivaSeg(20_000, 25_000)).toBe(0);
    expect(cuentaRegresivaSeg(null, 0)).toBeNull();
  });
});

describe('duracionPasoSecuenciaSeg', () => {
  it('cuenta hasta ahora si está en curso y es null si no arrancó', () => {
    expect(duracionPasoSecuenciaSeg(paso('ABRIR', 'PENDIENTE'), 9_000)).toBeNull();
    expect(duracionPasoSecuenciaSeg(paso('ABRIR', 'EN_CURSO', { iniciadoEn: 1_000 }), 4_000)).toBe(
      3,
    );
    expect(
      duracionPasoSecuenciaSeg(
        paso('ABRIR', 'OK', { iniciadoEn: 1_000, terminadoEn: 2_000 }),
        9_000,
      ),
    ).toBe(1);
  });
});

describe('filasLectura', () => {
  it('descarta las nulas y etiqueta con unidad: uv es Luz (%), ce en dS/m', () => {
    const filas = filasLectura({
      recibidaEn: 1,
      metricas: {
        humSus: 41,
        humAmb: 63,
        temp: 22.5,
        tempSuelo: null,
        uv: 78,
        ce: 1.2,
        phSuelo: null,
        n: null,
        p: null,
        k: null,
      },
    });

    expect(filas.map((f) => f.key)).toEqual(['humSus', 'humAmb', 'temp', 'uv', 'ce']);
    const porClave = Object.fromEntries(filas.map((f) => [f.key, f]));
    expect(porClave.uv).toEqual({ key: 'uv', etiqueta: 'Luz (%)', valor: '78', unidad: '' });
    expect(porClave.ce).toEqual({
      key: 'ce',
      etiqueta: 'Conductividad (CE)',
      valor: '1,2',
      unidad: 'dS/m',
    });
    expect(porClave.temp.valor).toBe('22,5');
    expect(porClave.temp.unidad).toBe('°C');
  });

  it('sin lectura no hay filas', () => {
    expect(filasLectura(null)).toEqual([]);
  });

  it('una clave desconocida se muestra con su clave', () => {
    expect(filasLectura({ recibidaEn: 1, metricas: { raro: 3 } })).toEqual([
      { key: 'raro', etiqueta: 'raro', valor: '3', unidad: '' },
    ]);
  });
});
