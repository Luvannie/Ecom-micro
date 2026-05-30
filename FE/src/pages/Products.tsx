import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { categoryApi, productApi, Product, cartApi } from '../api';
import { useAuth } from '../context/AuthContext';

export default function Products() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<any[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [addingToCart, setAddingToCart] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    categoryApi.getAll().then(r => setCategories(r.categories || [])).catch(() => {});
  }, []);

  useEffect(() => {
    setLoading(true);
    const params: { page: number; size: number; categoryId?: string; keyword?: string } = {
      page,
      size: 12
    };
    if (selectedCategory) params.categoryId = selectedCategory;
    if (keyword) params.keyword = keyword;
    productApi.getAll(params)
      .then(r => {
        setProducts(r.products || []);
        setTotalPages(r.totalPages || 0);
      })
      .catch(() => setProducts([]))
      .finally(() => setLoading(false));
  }, [page, selectedCategory, keyword]);

  const handleAddToCart = async (productId: string) => {
    if (!user) { navigate('/login'); return; }
    setError(null);
    setAddingToCart(productId);
    try {
      await cartApi.addItem({ productId, quantity: 1 });
    } catch (e: any) {
      setError(e.message || 'Failed to add item to cart');
    }
    finally { setAddingToCart(null); }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    const form = e.target as HTMLFormElement;
    setKeyword((form.elements.namedItem('keyword') as HTMLInputElement).value);
  };

  return (
    <div className="products-page">
      <h2>Products</h2>
      {error && <div className="error-msg" onClick={() => setError(null)}>{error}</div>}

      <div className="filters">
        <form onSubmit={handleSearch} className="search-form">
          <input name="keyword" type="text" className="input" placeholder="Search products..." />
          <button type="submit" className="btn btn-primary">Search</button>
        </form>
        <div className="category-filter">
          <button className={`filter-btn ${!selectedCategory ? 'active' : ''}`} onClick={() => { setSelectedCategory(''); setPage(0); }}>All</button>
          {categories.map(cat => (
            <button key={cat.id} className={`filter-btn ${selectedCategory === cat.id ? 'active' : ''}`} onClick={() => { setSelectedCategory(cat.id); setPage(0); }}>{cat.name}</button>
          ))}
        </div>
      </div>

      {loading ? <div className="loading">Loading...</div> : products.length === 0 ? (
        <div className="empty-state"><p>No products found</p></div>
      ) : (
        <>
          <div className="products-grid">
            {products.map(product => (
              <div key={product.id} className="product-card">
                <div className="product-image">
                  {product.imageUrl ? <img src={product.imageUrl} alt={product.name} /> : <div className="placeholder-image">🍔</div>}
                  {product.promotionTag && <span className="promo-tag">{product.promotionTag}</span>}
                </div>
                <div className="product-info">
                  <h3>{product.name}</h3>
                  <p className="description">{product.description}</p>
                  <div className="product-footer">
                    <span className="price">${product.price.toFixed(2)}</span>
                    <button className="btn btn-primary btn-sm" onClick={() => handleAddToCart(product.id)} disabled={addingToCart === product.id}>
                      {addingToCart === product.id ? 'Adding...' : 'Add to Cart'}
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
          {totalPages > 1 && (
            <div className="pagination">
              <button className="btn btn-secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</button>
              <span>Page {page + 1} of {totalPages}</span>
              <button className="btn btn-secondary" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Next</button>
            </div>
          )}
        </>
      )}
    </div>
  );
}