import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource-variable/manrope';
import './styles/index.css';
import './i18n';
import { App } from './app/App';
import { syncUserLanguage } from './lib/syncUserLanguage';

// Idioma salvo na conta vale ao entrar.
syncUserLanguage();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
