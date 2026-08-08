import { BrowserRouter, Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import ErrorBoundary from './components/ErrorBoundary';
import Home from './pages/Home';
import Login from './pages/Login';
import Register from './pages/Register';
import Products from './pages/Products';
import Cart from './pages/Cart';
import Checkout from './pages/Checkout';
import Orders from './pages/Orders';
import Profile from './pages/Profile';
import { cartApi } from './api';
import { useState, useEffect } from 'react';
import './App.css';

function Navbar() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout, hasRole } = useAuth();
  const [cartCount, setCartCount] = useState(0);

  useEffect(() => {
    if (user) {
      cartApi.get().then(r => setCartCount(r.items?.length || 0)).catch(() => {});
    } else {
      setCartCount(0);
    }
  }, [user, location]);

  return (
    <nav className="navbar">
      <div className="nav-brand" onClick={() => navigate('/')}>Ecom Food</div>
      <div className="nav-links">
        <button className={location.pathname === '/' ? 'active' : ''} onClick={() => navigate('/')}>Home</button>
        <button className={location.pathname === '/products' ? 'active' : ''} onClick={() => navigate('/products')}>Products</button>
        {user ? (
          <>
            <button className={location.pathname === '/cart' ? 'active' : ''} onClick={() => navigate('/cart')}>Cart ({cartCount})</button>
            <button className={location.pathname === '/orders' ? 'active' : ''} onClick={() => navigate('/orders')}>Orders</button>
            <button className={location.pathname === '/profile' ? 'active' : ''} onClick={() => navigate('/profile')}>{user.displayName || user.email}</button>
            {hasRole('admin') && (
              <button onClick={() => navigate('/admin')}>Admin</button>
            )}
            <button onClick={() => { logout(); navigate('/'); }}>Logout</button>
          </>
        ) : (
          <>
            <button className={location.pathname === '/login' ? 'active' : ''} onClick={() => navigate('/login')}>Sign in</button>
            <button className={location.pathname === '/register' ? 'active' : ''} onClick={() => navigate('/register')}>Register</button>
          </>
        )}
      </div>
    </nav>
  );
}

function AppContent() {
  const navigate = useNavigate();

  return (
    <div className="app">
      <Navbar />
      <main className="container">
        <Routes>
          {/* Public routes */}
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login onNavigate={navigate} />} />
          <Route path="/register" element={<Register onNavigate={navigate} />} />
          <Route path="/products" element={<Products />} />

          {/* Protected routes - require authentication */}
          <Route path="/cart" element={
            <ProtectedRoute><Cart onNavigate={navigate} /></ProtectedRoute>
          } />
          <Route path="/checkout" element={
            <ProtectedRoute><Checkout onNavigate={navigate} onOrderComplete={() => {}} /></ProtectedRoute>
          } />
          <Route path="/orders" element={
            <ProtectedRoute><Orders onNavigate={navigate} /></ProtectedRoute>
          } />
          <Route path="/profile" element={
            <ProtectedRoute><Profile onNavigate={navigate} /></ProtectedRoute>
          } />

          {/* Admin-only route example — replace element with real component when ready */}
          <Route path="/admin" element={
            <ProtectedRoute requiredRole="admin"><div style={{padding: '2rem'}}>Admin dashboard (placeholder)</div></ProtectedRoute>
          } />
        </Routes>
      </main>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <ErrorBoundary>
        <AuthProvider>
          <AppContent />
        </AuthProvider>
      </ErrorBoundary>
    </BrowserRouter>
  );
}
