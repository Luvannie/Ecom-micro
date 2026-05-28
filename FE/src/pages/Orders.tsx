import { useState, useEffect } from 'react';
import { orderApi, Order } from '../api';
import { useAuth } from '../context/AuthContext';

interface OrdersProps {
  onNavigate: (page: string) => void;
}

export default function Orders({ onNavigate }: OrdersProps) {
  const { user } = useAuth();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!user) { setLoading(false); return; }
    orderApi.getAll()
      .then(r => setOrders(r.content || []))
      .catch(() => setError('Failed to load orders'))
      .finally(() => setLoading(false));
  }, [user]);

  const handleCancel = async (orderId: string) => {
    if (!confirm('Cancel this order?')) return;
    try {
      await orderApi.cancel(orderId);
      setOrders(ords => ords.map(o => o.id === orderId ? { ...o, status: 'CANCELLED' } : o));
    } catch (e: any) { setError(e.message); }
  };

  if (loading) return <div className="loading">Loading...</div>;
  if (!user) return <div className="empty-state"><p>Please login to view orders</p><button className="btn btn-primary" onClick={() => onNavigate('login')}>Login</button></div>;

  return (
    <div className="orders-page">
      <h2>My Orders</h2>
      {error && <div className="error-msg">{error} <button onClick={() => setError('')}>×</button></div>}
      {orders.length === 0 ? (
        <div className="empty-state">
          <p>No orders yet</p>
          <button className="btn btn-primary" onClick={() => onNavigate('products')}>Start Shopping</button>
        </div>
      ) : (
        <div className="orders-list">
          {orders.map(order => (
            <div key={order.id} className="order-card">
              <div className="order-header">
                <span className="order-id">#{order.id.slice(0, 8)}</span>
                <span className={`status-badge ${order.status.toLowerCase()}`}>{order.status}</span>
              </div>
              <div className="order-items">
                {order.items.slice(0, 3).map((item, i) => (
                  <div key={i} className="order-item">{item.productName} × {item.quantity}</div>
                ))}
                {order.items.length > 3 && <div className="order-item">+{order.items.length - 3} more</div>}
              </div>
              <div className="order-footer">
                <span className="order-total">${Number(order.total).toFixed(2)}</span>
                <span className="order-date">{new Date(order.createdAt).toLocaleDateString()}</span>
                {order.status === 'RESERVED' && <button className="btn btn-secondary btn-sm" onClick={() => handleCancel(order.id)}>Cancel</button>}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}