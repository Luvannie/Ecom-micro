import { BrowserRouter, Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
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
  const { user, logout } = useAuth();
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
            <button className={location.pathname === '/profile' ? 'active' : ''} onClick={() => navigate('/profile')}>{user.displayName}</button>
            <button onClick={() => { logout(); navigate('/'); }}>Logout</button>
          </>
        ) : (
          <>
            <button className={location.pathname === '/login' ? 'active' : ''} onClick={() => navigate('/login')}>Login</button>
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
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login onNavigate={navigate} />} />
          <Route path="/register" element={<Register onNavigate={navigate} />} />
          <Route path="/products" element={<Products />} />
          <Route path="/cart" element={<Cart onNavigate={navigate} />} />
          <Route path="/checkout" element={<Checkout onNavigate={navigate} onOrderComplete={() => {}} />} />
          <Route path="/orders" element={<Orders onNavigate={navigate} />} />
          <Route path="/profile" element={<Profile onNavigate={navigate} />} />
        </Routes>
      </main>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppContent />
      </AuthProvider>
    </BrowserRouter>
  );
}
