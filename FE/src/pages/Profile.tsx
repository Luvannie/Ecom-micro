import { useState, useEffect } from 'react';
import { userApi, UserAddress } from '../api';
import { useAuth } from '../context/AuthContext';

interface ProfileProps {
  onNavigate: (page: string) => void;
}

export default function Profile({ onNavigate }: ProfileProps) {
  const { user, logout } = useAuth();
  const [addresses, setAddresses] = useState<UserAddress[]>([]);
  const [showAddressForm, setShowAddressForm] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (user) {
      userApi.getAddresses()
        .then(r => setAddresses(r.addresses || []))
        .catch(() => {})
        .finally(() => setLoading(false));
    }
  }, [user]);

  const handleAddAddress = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError('');
    const form = e.currentTarget;
    const data = {
      recipientName: (form.elements.namedItem('recipientName') as HTMLInputElement).value,
      phone: (form.elements.namedItem('phone') as HTMLInputElement).value,
      line1: (form.elements.namedItem('line1') as HTMLInputElement).value,
      city: (form.elements.namedItem('city') as HTMLInputElement).value,
      district: (form.elements.namedItem('district') as HTMLInputElement).value,
      postalCode: (form.elements.namedItem('postalCode') as HTMLInputElement).value,
      defaultAddress: (form.elements.namedItem('defaultAddress') as HTMLInputElement).checked,
    };
    try {
      await userApi.addAddress(data);
      const r = await userApi.getAddresses();
      setAddresses(r.addresses || []);
      setShowAddressForm(false);
    } catch (e: any) { setError(e.message); }
  };

  const handleDeleteAddress = async (addressId: string) => {
    if (!confirm('Delete this address?')) return;
    try {
      await userApi.deleteAddress(addressId);
      setAddresses(addrs => addrs.filter(a => a.id !== addressId));
    } catch (e: any) { setError(e.message); }
  };

  const handleLogout = () => { logout(); onNavigate('home'); };

  if (!user) return <div className="empty-state"><p>Please login</p><button className="btn btn-primary" onClick={() => onNavigate('login')}>Login</button></div>;
  if (loading) return <div className="loading">Loading...</div>;

  return (
    <div className="profile-page">
      <h2>My Profile</h2>
      {error && <div className="error-msg">{error} <button onClick={() => setError('')}>×</button></div>}

      <div className="profile-section">
        <h3>Account</h3>
        <div className="profile-field"><label>Name:</label><span>{user.displayName}</span></div>
        <div className="profile-field"><label>Email:</label><span>{user.email}</span></div>
      </div>

      <div className="profile-section">
        <div className="section-header">
          <h3>Addresses</h3>
          <button className="btn btn-primary btn-sm" onClick={() => setShowAddressForm(!showAddressForm)}>
            {showAddressForm ? 'Cancel' : '+ Add Address'}
          </button>
        </div>

        {showAddressForm && (
          <form onSubmit={handleAddAddress} className="address-form">
            <div className="form-row">
              <div className="form-group"><label>Recipient Name</label><input name="recipientName" className="input" required /></div>
              <div className="form-group"><label>Phone</label><input name="phone" className="input" required /></div>
            </div>
            <div className="form-group"><label>Address</label><input name="line1" className="input" required /></div>
            <div className="form-row">
              <div className="form-group"><label>City</label><input name="city" className="input" required /></div>
              <div className="form-group"><label>District</label><input name="district" className="input" required /></div>
            </div>
            <div className="form-row">
              <div className="form-group"><label>Postal Code</label><input name="postalCode" className="input" required /></div>
              <div className="form-group checkbox"><label><input name="defaultAddress" type="checkbox" /> Default address</label></div>
            </div>
            <button type="submit" className="btn btn-primary">Save Address</button>
          </form>
        )}

        {addresses.length === 0 && !showAddressForm ? (
          <p className="empty-text">No addresses saved</p>
        ) : (
          <div className="addresses-list">
            {addresses.map(addr => (
              <div key={addr.id} className={`address-card ${addr.defaultAddress ? 'default' : ''}`}>
                {addr.defaultAddress && <span className="default-badge">Default</span>}
                <p><strong>{addr.recipientName}</strong> {addr.phone}</p>
                <p>{addr.line1}, {addr.district}, {addr.city} {addr.postalCode}</p>
                <button className="btn-text btn-sm" onClick={() => handleDeleteAddress(addr.id)}>Delete</button>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="profile-section">
        <button className="btn btn-secondary" onClick={handleLogout}>Logout</button>
      </div>
    </div>
  );
}