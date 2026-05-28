import { useState, useEffect } from 'react';
import { cartApi, CartItem, orderApi } from '../api';
import { useAuth } from '../context/AuthContext';

interface CartProps {
  onNavigate: (page: string) => void;
}

export default function Cart({ onNavigate }: CartProps) {
  const { user } = useAuth();
  const [items, setItems] = useState<CartItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadCart = () => {
    if (!user) return;
    setLoading(true);
    cartApi.get()
      .then(r => setItems(r.items || []))
      .catch(() => setError('Failed to load cart'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { loadCart(); }, [user]);

  const handleUpdateQty = async (productId: string, quantity: number) => {
    try {
      const res = await cartApi.updateItem(productId, quantity);
      setItems(res.items || []);
    } catch (e: any) { setError(e.message); }
  };

  const handleRemove = async (productId: string) => {
    try {
      const res = await cartApi.removeItem(productId);
      setItems(res.items || []);
    } catch (e: any) { setError(e.message); }
  };

  const handleClear = async () => {
    try {
      await cartApi.clear();
      setItems([]);
    } catch (e: any) { setError(e.message); }
  };

  const handleCheckout = async () => {
    if (!user) return onNavigate('login');
    setError('');
    try {
      const orderItems = items.map(i => ({ productId: i.productId, quantity: i.quantity }));
      await orderApi.reserve({ items: orderItems });
      onNavigate('orders');
    } catch (e: any) { setError(e.message); }
  };

  const total = items.reduce((sum, i) => sum + i.unitPrice * i.quantity, 0);

  if (loading) return <div className="loading">Loading...</div>;

  return (
    <div className="cart-page">
      <h2>Shopping Cart</h2>
      {error && <div className="error-msg">{error} <button onClick={() => setError('')}>×</button></div>}
      {items.length === 0 ? (
        <div className="empty-state">
          <p>Your cart is empty</p>
          <button className="btn btn-primary" onClick={() => onNavigate('products')}>Browse Products</button>
        </div>
      ) : (
        <>
          <div className="cart-items">
            {items.map(item => (
              <div key={item.productId} className="cart-item">
                <div className="item-image">
                  {item.imageUrl ? <img src={item.imageUrl} alt={item.productName} /> : <div className="placeholder-image sm">🍔</div>}
                </div>
                <div className="item-info">
                  <h4>{item.productName}</h4>
                  <p className="price">${item.unitPrice.toFixed(2)}</p>
                </div>
                <div className="item-qty">
                  <button className="qty-btn" onClick={() => handleUpdateQty(item.productId, item.quantity - 1)}>−</button>
                  <span>{item.quantity}</span>
                  <button className="qty-btn" onClick={() => handleUpdateQty(item.productId, item.quantity + 1)}>+</button>
                </div>
                <div className="item-subtotal">${(item.unitPrice * item.quantity).toFixed(2)}</div>
                <button className="btn-remove" onClick={() => handleRemove(item.productId)}>×</button>
              </div>
            ))}
          </div>
          <div className="cart-summary">
            <div className="summary-row"><span>Subtotal:</span><span>${total.toFixed(2)}</span></div>
            <div className="summary-row total"><span>Total:</span><span>${total.toFixed(2)}</span></div>
            <div className="cart-actions">
              <button className="btn btn-secondary" onClick={handleClear}>Clear Cart</button>
              <button className="btn btn-primary" onClick={handleCheckout}>Checkout</button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}