import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { categoryApi, productApi, Product, cartApi } from '../api';
import { useAuth } from '../context/AuthContext';

export default function Home() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [categories, setCategories] = useState<any[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      categoryApi.getAll().catch(() => ({ categories: [] })),
      productApi.getAll({ size: 12 }).catch(() => ({ products: [] })),
    ]).then(([catRes, prodRes]) => {
      setCategories(catRes.categories || []);
      setProducts(prodRes.products || []);
      setLoading(false);
    });
  }, []);

  const handleAddToCart = async (productId: string) => {
    if (!user) { navigate('/login'); return; }
    try {
      await cartApi.addItem({ productId, quantity: 1 });
    } catch (e: any) { alert(e.message); }
  };

  if (loading) return <div className="loading">Loading...</div>;

  return (
    <div className="home-page">
      <section className="hero">
        <h1>Welcome to Ecom Food Delivery</h1>
        <p>Fresh food delivered to your door</p>
        {!user && (
          <div className="hero-actions">
            <button className="btn btn-primary" onClick={() => navigate('/register')}>Get Started</button>
            <button className="btn btn-secondary" onClick={() => navigate('/login')}>Login</button>
          </div>
        )}
      </section>

      {categories.length > 0 && (
        <section className="categories-section">
          <h2>Categories</h2>
          <div className="categories-grid">
            {categories.map(cat => (
              <div key={cat.id} className="category-card" onClick={() => navigate('/products')}>
                <span className="category-icon">🍽️</span>
                <h3>{cat.name}</h3>
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="products-section">
        <div className="section-header">
          <h2>Featured Products</h2>
          <button className="btn-text" onClick={() => navigate('/products')}>View All →</button>
        </div>
        <div className="products-grid">
          {products.map(product => (
            <div key={product.id} className="product-card">
              <div className="product-image">
                {product.imageUrl ? (
                  <img src={product.imageUrl} alt={product.name} />
                ) : (
                  <div className="placeholder-image">🍔</div>
                )}
                {product.promotionTag && <span className="promo-tag">{product.promotionTag}</span>}
              </div>
              <div className="product-info">
                <h3>{product.name}</h3>
                <p className="description">{product.description}</p>
                <div className="product-footer">
                  <span className="price">${product.price.toFixed(2)}</span>
                  <button className="btn btn-primary btn-sm" onClick={() => handleAddToCart(product.id)}>
                    Add to Cart
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}