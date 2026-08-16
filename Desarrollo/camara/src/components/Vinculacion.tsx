import { useState, type FormEvent } from 'react';

/**
 * Pantalla de vinculación: se ve una sola vez en la vida del dispositivo.
 *
 * El código lo genera la plataforma y lo tipea el operario. Nada de larga duración viene
 * embebido en el bundle — el código de una PWA es público.
 */
export function Vinculacion({
  onVincular,
}: {
  onVincular: (codigo: string, nombre: string) => Promise<void>;
}) {
  const [codigo, setCodigo] = useState('');
  const [nombre, setNombre] = useState('iPhone riel');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function enviar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      await onVincular(codigo.trim().toUpperCase(), nombre.trim());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={enviar} style={{ padding: 24, display: 'grid', gap: 16, maxWidth: 460 }}>
      <div>
        <h1 style={{ margin: '0 0 6px', fontSize: 26 }}>Vincular dispositivo</h1>
        <p style={{ margin: 0, color: 'var(--texto-2)', lineHeight: 1.5 }}>
          Generá un código de vinculación en Yerbanalytics y escribilo acá. Se hace una sola
          vez.
        </p>
      </div>

      <label style={{ display: 'grid', gap: 8 }}>
        <span style={{ color: 'var(--texto-2)', fontSize: 14 }}>Código de vinculación</span>
        <input
          value={codigo}
          onChange={(e) => setCodigo(e.target.value.toUpperCase())}
          placeholder="7F3K-2M9Q"
          autoCapitalize="characters"
          autoCorrect="off"
          spellCheck={false}
          style={{ fontFamily: 'var(--mono)', fontSize: 22, letterSpacing: 2 }}
          required
        />
      </label>

      <label style={{ display: 'grid', gap: 8 }}>
        <span style={{ color: 'var(--texto-2)', fontSize: 14 }}>Nombre del equipo</span>
        <input value={nombre} onChange={(e) => setNombre(e.target.value)} required />
      </label>

      {error && (
        <div
          role="alert"
          // pre-line: los errores de conectividad vienen con saltos de línea porque enumeran
          // qué revisar, y sin esto se leerían como un párrafo corrido.
          style={{ color: 'var(--rojo)', fontSize: 15, lineHeight: 1.5, whiteSpace: 'pre-line' }}
        >
          {error}
        </div>
      )}

      <button type="submit" disabled={enviando || codigo.length < 4}>
        {enviando ? 'Vinculando…' : 'Vincular'}
      </button>
    </form>
  );
}
