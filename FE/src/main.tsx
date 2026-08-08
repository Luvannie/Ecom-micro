import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import keycloak from './keycloak'
import './index.css'

// Boot Keycloak before rendering the React tree so ProtectedRoute and
// other guards can read keycloak.authenticated synchronously.
keycloak
  .init({
    onLoad: 'check-sso',
    silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
    pkceMethod: 'S256',
    checkLoginIframe: false,
  })
  .catch((err) => {
    // eslint-disable-next-line no-console
    console.error('Keycloak init failed', err)
  })
  .finally(() => {
    ReactDOM.createRoot(document.getElementById('root')!).render(
      <React.StrictMode>
        <App />
      </React.StrictMode>,
    )
  })
