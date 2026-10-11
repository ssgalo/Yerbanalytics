import { RouterProvider } from 'react-router-dom';
import { AuthProvider } from '@/hooks/AuthContext';
import { router } from './router';

/**
 * La sesión envuelve a todo: el vivero (`NurseryProvider`) y la preferencia de Demo Expo se
 * montan dentro del shell, que sólo existe con sesión.
 */
export function App() {
  return (
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  );
}
