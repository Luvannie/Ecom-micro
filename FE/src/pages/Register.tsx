import { useAuth } from '../context/AuthContext';

interface RegisterProps {
  onNavigate: (page: string) => void;
}

export default function Register({ onNavigate }: RegisterProps) {
  const { register } = useAuth();

  const handleRegister = () => {
    try {
      register();
    } catch (err) {
      // eslint-disable-next-line no-console
      console.error('Register redirect failed', err);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h2>Create account</h2>
        <p className="auth-subtitle">Account creation is handled by Keycloak.</p>
        <button onClick={handleRegister} className="btn btn-primary btn-block">
          Register with Keycloak
        </button>
        <p className="auth-footer">
          Already have an account?{' '}
          <button onClick={() => onNavigate('login')}>Sign in</button>
        </p>
      </div>
    </div>
  );
}
