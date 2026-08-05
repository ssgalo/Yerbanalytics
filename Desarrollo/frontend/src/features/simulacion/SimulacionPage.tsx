/* ============================================================
   Dashboard de simulación. Alterna entre datos estáticos y simulación manual:
   en simulación permite crear sensores simulados (serial/MAC + macro-zona de la
   topología vigente) y enviar sus lecturas por MQTT real —cada métrica por separado
   o todas juntas—, simulando lo que enviaría el hardware. Los sensores simulados son
   emisores en memoria, desacoplados del registro de hardware del sistema.
   ============================================================ */
import { useEffect, useMemo, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { usePageTitle } from '@/hooks/PageMeta';
import { useSimulacion } from '@/hooks/useSimulacion';
import { useTopologia } from '@/hooks/useTopologia';
import type { SensorSimulado } from '@/types/domain';
import { ModoSwitch } from './components/ModoSwitch';
import { NuevoSensorForm } from './components/NuevoSensorForm';
import { RegenerarTopologiaForm } from './components/RegenerarTopologiaForm';
import { SensorSimCard } from './components/SensorSimCard';
import { CamaraPanel } from './components/CamaraPanel';
import { getRepository } from '@/data';
import styles from './Simulacion.module.css';

export function SimulacionPage() {
  const { estado, sensores, loading, error, sending, mutating, setModo, crearSensor, eliminarSensor, enviar } =
    useSimulacion();
  const { data: topologia, loading: topoLoading, generating, generar } = useTopologia();

  // Macro-zonas de la topología vigente (compartidas por regenerar y alta de sensores).
  const zonas = useMemo(
    () => Array.from({ length: Math.max(0, topologia?.macroZonas ?? 0) }, (_, i) => `MZ-${i + 1}`),
    [topologia],
  );

  const esSimulacion = estado?.modo === 'simulacion';

  // Sectores de la topología vigente, para el panel de cámara. El pedido de captura NO
  // depende del modo de operación: el planificador tampoco va a depender de él, y el ensayo
  // tiene que parecerse a la operación real.
  const [sectores, setSectores] = useState<{ id: string; zonaName: string }[]>([]);
  const [sectoresLoading, setSectoresLoading] = useState(true);

  useEffect(() => {
    let activo = true;
    getRepository()
      .getNursery()
      .then((n) => {
        if (!activo) return;
        setSectores(n.sectors.map((sec) => ({ id: sec.id, zonaName: sec.zonaName })));
      })
      .finally(() => activo && setSectoresLoading(false));
    return () => {
      activo = false;
    };
  }, []);

  usePageTitle(
    'Simulación de sensores',
    esSimulacion ? 'Modo simulación · envío manual por MQTT' : 'Modo datos estáticos',
  );

  if (loading) {
    return <div className={styles.state}>Cargando simulación…</div>;
  }
  if (error) {
    return <div className={styles.state}>No se pudo cargar la simulación: {error.message}</div>;
  }

  const onCrear = async (sensor: SensorSimulado) => {
    await crearSensor(sensor);
  };

  const onRegenerar = async (macroZonas: number, sectoresPorMacroZona: number) => {
    await generar({ macroZonas, sectoresPorMacroZona, regenerar: true });
  };

  return (
    <div>
      <p className={styles.intro}>
        Simulá el comportamiento del hardware sin equipos físicos: creá sensores simulados, asignalos
        a una macro-zona y enviá sus lecturas por MQTT, tal como los enviaría un nodo ESP32 real. Para
        que un nodo actualice su estado técnico, registrá el hardware en la sección de Hardware con el{' '}
        <strong>mismo serial/MAC y la misma macro-zona</strong>.
      </p>

      <ModoSwitch modo={estado?.modo ?? 'estatico'} busy={sending || mutating} onChange={setModo} />

      {esSimulacion ? (
        <>
          <RegenerarTopologiaForm
            topologia={topologia}
            generating={generating}
            onRegenerar={onRegenerar}
          />
          <NuevoSensorForm
            busy={mutating}
            zonas={zonas}
            zonasLoading={topoLoading}
            onSubmit={onCrear}
          />
        </>
      ) : (
        <div className={styles.note}>
          El envío de lecturas está deshabilitado en modo estático. Activá la simulación para
          regenerar la topología, crear sensores y enviar telemetría.
        </div>
      )}

      <div className={styles.sectionHead} style={{ marginTop: 8 }}>
        <span className={styles.sectionTitle}>Sensores simulados</span>
        <span className={styles.sectionHint}>
          {sensores.length} sensor{sensores.length === 1 ? '' : 'es'} · cada uno emite la telemetría de
          su macro-zona
        </span>
      </div>

      {sensores.length === 0 ? (
        <Card className={styles.empty}>
          {esSimulacion
            ? 'No hay sensores simulados todavía. Creá uno arriba para empezar a enviar lecturas.'
            : 'No hay sensores simulados.'}
        </Card>
      ) : (
        <div className={styles.sensorGrid}>
          {sensores.map((s) => (
            <SensorSimCard
              key={s.serial}
              sensor={s}
              disabled={!esSimulacion}
              sending={sending}
              onEnviar={enviar}
              onEliminar={eliminarSensor}
            />
          ))}
        </div>
      )}

      {/* ---- Cámara ---- */}
      <div className={styles.sectionHead} style={{ marginTop: 24 }}>
        <span className={styles.sectionTitle}>Cámara del riel</span>
        <span className={styles.sectionHint}>
          Banco de pruebas del dispositivo de captura y del recorrido completo hasta el
          diagnóstico
        </span>
      </div>

      <CamaraPanel sectores={sectores} sectoresLoading={sectoresLoading} />
    </div>
  );
}
