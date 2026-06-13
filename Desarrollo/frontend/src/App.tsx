import { RouterProvider } from 'react-router-dom';
import { NurseryProvider } from '@/hooks/NurseryContext';
import { router } from './router';

export function App() {
  return (
    <NurseryProvider>
      <RouterProvider router={router} />
    </NurseryProvider>
  );
}
