import { Outlet } from 'react-router-dom';
import { PageMetaProvider } from '@/hooks/PageMeta';
import { DemoExpoProvider } from '@/hooks/DemoExpoContext';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import styles from './AppLayout.module.css';

/** Shell de la aplicación: sidebar + topbar + área scrolleable con la vista. */
export function AppLayout() {
  return (
    <PageMetaProvider>
      <DemoExpoProvider>
        <Sidebar />
        <div className={styles.main}>
          <Topbar />
          <main className={styles.scroll}>
            <Outlet />
          </main>
        </div>
      </DemoExpoProvider>
    </PageMetaProvider>
  );
}
