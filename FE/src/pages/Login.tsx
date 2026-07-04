import { useState } from 'react';
import { useAuth } from '../context/AuthContext';

interface LoginProps {
  onNavigate: (page: string) => void;
}

export default function Login({ onNavigate }: LoginProps) {
  const { login } = useAuth();
  const [error] = useState('');

  const handleSignIn = () => {
    try {
      login();
    } catch (err) {
      // eslint-disable-next-line no-console
      console.error('Sign-in redirect failed', err);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h2>Sign in</h2>
        <p className="auth-subtitle">Authentication is handled by Keycloak.</p>
        {error && <div className="error-msg">{error}</div>}
        <button onClick={handleSignIn} className="btn btn-primary btn-block">
          Sign in with Keycloak
        </button>
        <p className="auth-footer">
          Don't have an account?{' '}
          <button onClick={() => onNavigate('register')}>Register</button>
        </p>
      </div>
    </div>
  );
}
