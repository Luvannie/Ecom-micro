import { useState, useEffect } from 'react';
import { cartApi, orderApi, paymentApi, CartItem } from '../api';
import { useAuth } from '../context/AuthContext';

interface CheckoutProps {
  onNavigate: (page: string) => void;
  onOrderComplete: () => void;
}

export default function Checkout({ onNavigate, onOrderComplete }: CheckoutProps) {
  const { user } = useAuth();
  const [items, setItems] = useState<CartItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!user) { setLoading(false); return; }
    cartApi.get()
      .then(res => setItems(res.items || []))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [user]);

  const subtotal = items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);
  const shippingFee = items.length > 0 ? 5.99 : 0;
  const total = subtotal + shippingFee;

  const handlePlaceOrder = async () => {
    if (!user || items.length === 0) return;
    setProcessing(true);
    setError('');
    try {
      const reserveResult = await orderApi.reserve({
        items: items.map(i => ({ productId: i.productId, quantity: i.quantity })),
      });
      if (reserveResult.status === 'RESERVED') {
        await paymentApi.create({
          orderId: reserveResult.orderId,
          amount: total,
          currency: 'USD',
        });
        await onOrderComplete();
        onNavigate('orders');
      }
    } catch (err: any) {
      setError(err.message || 'Checkout failed');
    } finally {
      setProcessing(false);
    }
  };

  if (!user) return <div className="checkout-page"><p>Please login to checkout.</p></div>;
  if (loading) return <div className="loading">Loading...</div>;
  if (items.length === 0) {
    return <div className="checkout-page"><p>Your cart is empty. <button onClick={() => onNavigate('products')}>Go shopping</button></p></div>;
  }

  return (
    <div className="checkout-page">
      <h1>Checkout</h1>
      {error && <div className="error-msg">{error}<button onClick={() => setError('')}>×</button></div>}

      <div className="checkout-items">
        <h2>Order Summary</h2>
        {items.map(item => (
          <div key={item.productId} className="checkout-item">
            <span>{item.productName} × {item.quantity}</span>
            <span>${(item.unitPrice * item.quantity).toFixed(2)}</span>
          </div>
        ))}
      </div>

      <div className="checkout-summary">
        <div className="summary-row"><span>Subtotal</span><span>${subtotal.toFixed(2)}</span></div>
        <div className="summary-row"><span>Shipping</span><span>${shippingFee.toFixed(2)}</span></div>
        <div className="summary-row total"><span>Total</span><span>${total.toFixed(2)}</span></div>
      </div>

      <div className="checkout-actions">
        <button className="btn btn-secondary" onClick={() => onNavigate('cart')}>Back to Cart</button>
        <button className="btn btn-primary" onClick={handlePlaceOrder} disabled={processing}>
          {processing ? 'Processing...' : 'Place Order'}
        </button>
      </div>
    </div>
  );
}