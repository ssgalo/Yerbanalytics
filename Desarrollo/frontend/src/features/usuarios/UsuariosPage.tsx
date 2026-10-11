/* ============================================================
   Vista Usuarios (HU-20): gestión de usuarios, matriz de permisos, auditoría y política de
   sesión. La ruta exige `usuarios.gestionar`; la pestaña Auditoría además `auditoria.ver`, que
   es un permiso aparte (por defecto, sólo del Administrador).

   La pestaña vive en la URL (`?tab=`), como en el Motor de reglas.
   ============================================================ */
import { useSearchParams } from 'react-router-dom';
import { usePageTitle } from '@/hooks/PageMeta';
import { useAuth } from '@/hooks/AuthContext';
import { UsuariosTab } from './UsuariosTab';
import { RolesTab } from './RolesTab';
import { AuditoriaTab } from './AuditoriaTab';
import { SeguridadTab } from './SeguridadTab';
import styles from './Usuarios.module.css';

type Pestania = 'usuarios' | 'roles' | 'auditoria' | 'seguridad';

const ETIQUETAS: Record<Pestania, string> = {
  usuarios: 'Usuarios',
  roles: 'Roles y permisos',
  auditoria: 'Auditoría',
  seguridad: 'Seguridad',
};

export function UsuariosPage() {
  const [params, setParams] = useSearchParams();
  const { puede } = useAuth();
  const pestanias: Pestania[] = puede('auditoria.ver')
    ? ['usuarios', 'roles', 'auditoria', 'seguridad']
    : ['usuarios', 'roles', 'seguridad'];
  const pedida = params.get('tab') as Pestania | null;
  const tab: Pestania = pedida && pestanias.includes(pedida) ? pedida : 'usuarios';

  usePageTitle('Usuarios', 'Cuentas, permisos por rol, auditoría y política de sesión');

  const elegir = (t: Pestania) => {
    const next = new URLSearchParams(params);
    if (t === 'usuarios') next.delete('tab');
    else next.set('tab', t);
    setParams(next, { replace: true });
  };

  return (
    <div className={styles.page}>
      <div className={styles.tabs} role="tablist" aria-label="Secciones de usuarios">
        {pestanias.map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={tab === t}
            className={tab === t ? `${styles.tab} ${styles.tabActive}` : styles.tab}
            onClick={() => elegir(t)}
          >
            {ETIQUETAS[t]}
          </button>
        ))}
      </div>

      {tab === 'usuarios' && <UsuariosTab />}
      {tab === 'roles' && <RolesTab />}
      {tab === 'auditoria' && <AuditoriaTab />}
      {tab === 'seguridad' && <SeguridadTab />}
    </div>
  );
}
