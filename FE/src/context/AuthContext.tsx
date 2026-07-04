import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import keycloak from '../keycloak';

export interface User {
  id: string;
  email: string;
  displayName: string;
  roles: string[];
}

interface AuthContextType {
  user: User | null;
  loading: boolean;
  isAuthenticated: boolean;
  login: () => void;
  register: () => void;
  logout: () => void;
  hasRole: (role: string) => boolean;
  getToken: () => string | undefined;
}

const AuthContext = createContext<AuthContextType | null>(null);

function readUser(): User | null {
  if (!keycloak.authenticated || !keycloak.tokenParsed) return null;
  const profile = (keycloak.tokenParsed as Record<string, unknown>);
  const realmAccess = profile['realm_access'] as { roles?: string[] } | undefined;
  const roles = realmAccess?.roles ?? [];
  return {
    id: String(profile['sub'] ?? ''),
    email: String(profile['email'] ?? ''),
    displayName: String(profile['name'] ?? profile['preferred_username'] ?? profile['email'] ?? ''),
    roles,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setUser(readUser());
    keycloak.onAuthSuccess = () => setUser(readUser());
    keycloak.onAuthLogout = () => setUser(null);
    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).then(refreshed => {
        if (refreshed) setUser(readUser());
      }).catch(() => keycloak.logout());
    };
    setLoading(false);
  }, []);

  const login = () => {
    keycloak.login({ redirectUri: window.location.origin });
  };

  const register = () => {
    keycloak.register({ redirectUri: window.location.origin });
  };

  const logout = () => {
    keycloak.logout({ redirectUri: window.location.origin });
  };

  const hasRole = (role: string): boolean => {
    return keycloak.hasRealmRole(role);
  };

  const getToken = (): string | undefined => keycloak.token;

  return (
    <AuthContext.Provider value={{
      user,
      loading,
      isAuthenticated: !!keycloak.authenticated,
      login,
      register,
      logout,
      hasRole,
      getToken,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
