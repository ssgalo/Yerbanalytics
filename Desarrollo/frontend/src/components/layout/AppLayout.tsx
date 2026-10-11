import { Outlet } from 'react-router-dom';
import { PageMetaProvider } from '@/hooks/PageMeta';
import { DemoExpoProvider } from '@/hooks/DemoExpoContext';
import { NurseryProvider } from '@/hooks/NurseryContext';
import { useAuth } from '@/hooks/AuthContext';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { VigilanteInactividad } from './VigilanteInactividad';
import styles from './AppLayout.module.css';

/**
 * Shell de la aplicación: sidebar + topbar + área scrolleable con la vista.
 *
 * Se monta sólo con sesión (lo envuelve `RequireAuth`), y recién acá nacen el sondeo del vivero
 * y la preferencia de Demo Expo: sin sesión no se pide nada. Al cerrarse la sesión el shell se
 * desmonta y con él los datos en pantalla.
 */
export function AppLayout() {
  const { puede } = useAuth();
  const vivero = puede('vivero.ver');

  return (
    <NurseryProvider habilitado={vivero}>
      <PageMetaProvider>
        <DemoExpoProvider habilitado={vivero}>
          <Sidebar />
          <div className={styles.main}>
            <Topbar />
            <main className={styles.scroll}>
              <Outlet />
            </main>
          </div>
          <VigilanteInactividad />
        </DemoExpoProvider>
      </PageMetaProvider>
    </NurseryProvider>
  );
}
