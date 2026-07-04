import Keycloak, { KeycloakInstance } from 'keycloak-js';

const keycloakConfig = {
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'ecom',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'ecom-frontend',
};

const keycloak: KeycloakInstance = new Keycloak(keycloakConfig);

export default keycloak;
