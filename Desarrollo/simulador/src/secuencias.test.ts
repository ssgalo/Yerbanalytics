import assert from 'node:assert/strict';
import { test } from 'node:test';
import { DOSIS, etiquetaPaso, textoPaso } from './secuencias';
import type { PasoSecuencia, Secuencia, TipoPasoSecuencia, TipoSecuencia } from './types';

function secuencia(tipo: TipoSecuencia, parametros: Secuencia['parametros']): Secuencia {
  return {
    id: 's-1',
    tipo,
    estado: 'EN_CURSO',
    zonaId: 'MZ-1',
    sectorId: 'MZ-1-001',
    parametros,
    iniciadaEn: 0,
    finalizadaEn: null,
    cancelacionSolicitada: false,
    error: null,
    lectura: null,
    pasos: [],
  };
}

function paso(tipo: TipoPasoSecuencia, estado: PasoSecuencia['estado'], esperaHasta: number | null = null): PasoSecuencia {
  return { n: 1, tipo, estado, codigoError: null, detalle: null, commandId: null, esperaHasta, iniciadoEn: null, terminadoEn: null };
}

const dosis = secuencia('DOSIS', { ml: 8 });
const riego = secuencia('RIEGO', { duracionSeg: 15 });

test('dosis: los pasos hablan de la bomba de insumo y de los ml', () => {
  assert.equal(etiquetaPaso(paso('ABRIR', 'OK'), dosis), 'Prender la bomba de insumo (MZ-1-001)');
  assert.equal(etiquetaPaso(paso('ESPERAR', 'EN_CURSO'), dosis), 'Dosificar 8 ml');
  assert.equal(etiquetaPaso(paso('CERRAR', 'PENDIENTE'), dosis), 'Apagar la bomba');
});

test('dosis: los estados de cada paso nombran la bomba, no la válvula', () => {
  assert.equal(textoPaso(paso('ABRIR', 'OK'), dosis), 'Bomba encendida');
  assert.equal(textoPaso(paso('CERRAR', 'EN_CURSO'), dosis), 'Apagando la bomba…');
  assert.equal(textoPaso(paso('CERRAR', 'OK'), dosis), 'Bomba apagada');
});

test('riego: sigue igual que antes', () => {
  assert.equal(etiquetaPaso(paso('ABRIR', 'OK'), riego), 'Abrir válvula (MZ-1-001)');
  assert.equal(etiquetaPaso(paso('ESPERAR', 'EN_CURSO'), riego), 'Regar 15 s');
  assert.equal(etiquetaPaso(paso('CERRAR', 'PENDIENTE'), riego), 'Cerrar válvula');
  assert.equal(textoPaso(paso('ABRIR', 'OK'), riego), 'Válvula abierta');
  assert.equal(textoPaso(paso('CERRAR', 'OK'), riego), 'Válvula cerrada');
});

test('la cuenta regresiva de la espera vale igual para riego y dosis', () => {
  assert.equal(textoPaso(paso('ESPERAR', 'EN_CURSO', 9_000), dosis, 1_000), 'Faltan 8 s');
  assert.equal(textoPaso(paso('ESPERAR', 'EN_CURSO', 1_000), dosis, 5_000), 'Esperando confirmación del nodo…');
});

test('los límites de la dosis son los del firmware: de 1 a 50 ml, 5 por defecto', () => {
  assert.deepEqual(DOSIS, { min: 1, max: 50, defecto: 5 });
});
