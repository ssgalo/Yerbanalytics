/* ============================================================
   Sección "Secuencias" de Demo Expo: tres tarjetas (riego, mediasombra, lectura de sensores)
   que comandan el actuador directo, y el avance de la última. Los botones se deshabilitan si
   hay una secuencia o una pasada en curso; es una ayuda visual: el que decide es el 409 del
   backend (un solo ESP32 para las dos cosas).
   ============================================================ */
import { useState, type ReactNode } from 'react';
import { Card } from '@/components/ui/Card';
import { useSecuencia } from '@/hooks/useSecuencia';
import { SecuenciaProgreso } from './SecuenciaProgreso';
import styles from './SecuenciasPanel.module.css';

const RIEGO = { min: 1, max: 120, defecto: 10 };
const ESPERA = { min: 0, max: 600, defecto: 10 };

/** El valor tipeado como entero dentro del rango, o null si no sirve. */
function enRango(texto: string, { min, max }: { min: number; max: number }): number | null {
  if (texto.trim() === '') return null;
  const n = Number(texto);
  return Number.isInteger(n) && n >= min && n <= max ? n : null;
}

function Tarjeta({
  titulo,
  descripcion,
  children,
}: {
  titulo: string;
  descripcion: string;
  children: ReactNode;
}) {
  return (
    <Card className={styles.tarjeta}>
      <h3 className={styles.tarjetaTitulo}>{titulo}</h3>
      <p className={styles.tarjetaTexto}>{descripcion}</p>
      <div className={styles.controles}>{children}</div>
    </Card>
  );
}

export function SecuenciasPanel({ pasadaEnCurso }: { pasadaEnCurso: boolean }) {
  const { secuencia, error, iniciando, iniciar, cancelar } = useSecuencia();
  const [riegoTxt, setRiegoTxt] = useState(String(RIEGO.defecto));
  const [esperaTxt, setEsperaTxt] = useState(String(ESPERA.defecto));

  const ocupado = secuencia?.estado === 'EN_CURSO' || pasadaEnCurso || iniciando;
  const duracion = enRango(riegoTxt, RIEGO);
  const espera = enRango(esperaTxt, ESPERA);

  return (
    <section className={styles.seccion} aria-labelledby="secuencias-titulo">
      <h2 id="secuencias-titulo" className={styles.titulo}>
        Secuencias
      </h2>
      <p className={styles.subtitulo}>
        Comandos directos a los actuadores del stand. Una a la vez, y nunca durante una pasada del
        riel.
      </p>

      <div className={styles.tarjetas}>
        <Tarjeta titulo="Riego" descripcion="Abre la válvula, espera y la cierra.">
          <label className={styles.campo}>
            <span>Segundos</span>
            <input
              type="number"
              className={styles.input}
              min={RIEGO.min}
              max={RIEGO.max}
              value={riegoTxt}
              onChange={(e) => setRiegoTxt(e.target.value)}
            />
          </label>
          <button
            type="button"
            className={styles.btn}
            disabled={ocupado || duracion === null}
            onClick={() => duracion !== null && void iniciar('RIEGO', { duracionSeg: duracion })}
          >
            Regar
          </button>
        </Tarjeta>

        <Tarjeta titulo="Mediasombra" descripcion="La despliega, espera y la enrolla.">
          <label className={styles.campo}>
            <span>Espera desplegada (s)</span>
            <input
              type="number"
              className={styles.input}
              min={ESPERA.min}
              max={ESPERA.max}
              value={esperaTxt}
              onChange={(e) => setEsperaTxt(e.target.value)}
            />
          </label>
          <button
            type="button"
            className={styles.btn}
            disabled={ocupado || espera === null}
            onClick={() => espera !== null && void iniciar('MEDIASOMBRA', { esperaSeg: espera })}
          >
            Desplegar y enrollar
          </button>
        </Tarjeta>

        <Tarjeta
          titulo="Lectura"
          descripcion="Le pide una lectura ahora al nodo testigo de la zona."
        >
          <button
            type="button"
            className={styles.btn}
            disabled={ocupado}
            onClick={() => void iniciar('LECTURA')}
          >
            Leer sensores ahora
          </button>
        </Tarjeta>
      </div>

      {error && (
        <div role="alert" className={styles.error}>
          {error}
        </div>
      )}

      {secuencia && <SecuenciaProgreso secuencia={secuencia} onCancelar={() => void cancelar()} />}
    </section>
  );
}
