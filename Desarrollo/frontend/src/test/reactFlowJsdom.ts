/* ============================================================
   Stubs mínimos para montar React Flow en jsdom (que no tiene layout): ResizeObserver,
   DOMMatrixReadOnly y dimensiones de elementos. Se importa al principio de los tests que
   renderizan un <ReactFlow>. Es la receta de la documentación de React Flow para testing.
   ============================================================ */

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

class DOMMatrixReadOnlyStub {
  m22: number;
  constructor(transform?: string) {
    const scale = transform?.match(/scale\(([1-9.]+)\)/)?.[1];
    this.m22 = scale !== undefined ? +scale : 1;
  }
}

globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;
globalThis.DOMMatrixReadOnly = DOMMatrixReadOnlyStub as unknown as typeof DOMMatrixReadOnly;

Object.defineProperties(globalThis.HTMLElement.prototype, {
  offsetHeight: { get: () => 120 },
  offsetWidth: { get: () => 270 },
  clientWidth: { get: () => 1200 },
});

(globalThis.SVGElement.prototype as unknown as { getBBox: () => unknown }).getBBox = () => ({
  x: 0,
  y: 0,
  width: 0,
  height: 0,
});
