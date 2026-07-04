import React from 'react';
import ReactDOM from 'react-dom/client';
import { SimuladorApp } from './SimuladorApp';
import '@/styles/global.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <SimuladorApp />
  </React.StrictMode>,
);
