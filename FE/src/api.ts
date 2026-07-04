import keycloak from './keycloak'

const API_BASE = '/api'

function getHeaders(): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (keycloak.authenticated && keycloak.token) {
    headers['Authorization'] = `Bearer ${keycloak.token}`
  }
  return headers
}

async function tryRefreshToken(): Promise<boolean> {
  try {
    const refreshed = await keycloak.updateToken(30)
    return refreshed
  } catch {
    return false
  }
}

async function fetchApi<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const doFetch = () => fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers: {
      ...getHeaders(),
      ...options?.headers,
    },
  })

  let response = await doFetch()

  // 401: try refreshing the Keycloak access token, then retry once.
  if (response.status === 401 && keycloak.authenticated) {
    const refreshed = await tryRefreshToken()
    if (refreshed) {
      response = await doFetch()
    } else {
      keycloak.login()
      throw new Error('Session expired')
    }
  }

  if (!response.ok) {
    const error = await response.text()
    throw new Error(error || `HTTP ${response.status}`)
  }

  if (response.status === 204) return {} as T
  return response.json()
}

// Auth — Keycloak handles login/register/refresh/logout via OIDC.
// We only expose the /api/auth/me proxy that decodes the current JWT.
export const authApi = {
  me: () => fetchApi<{ userId: string; email: string; roles: string[] }>('/auth/me'),
}

// User
export interface UserProfile {
  id: string
  email: string
  displayName: string
  createdAt: string
}

export interface UserAddress {
  id: string
  recipientName: string
  phone: string
  line1: string
  city: string
  district: string
  postalCode: string
  defaultAddress: boolean
}

export const userApi = {
  getMe: () => fetchApi<UserProfile>('/users/me'),

  updateMe: (data: { displayName?: string; phone?: string; preferences?: Record<string, any> }) =>
    fetchApi<UserProfile>('/users/me', { method: 'PUT', body: JSON.stringify(data) }),

  getAddresses: () => fetchApi<{ addresses: UserAddress[] }>('/users/me/addresses'),

  addAddress: (data: {
    recipientName: string
    phone: string
    line1: string
    city: string
    district: string
    postalCode: string
    defaultAddress?: boolean
  }) => fetchApi<{ id: string }>('/users/me/addresses', { method: 'POST', body: JSON.stringify(data) }),

  deleteAddress: (addressId: string) =>
    fetchApi(`/users/me/addresses/${addressId}`, { method: 'DELETE' }),
}

// Categories
export interface Category {
  id: string
  name: string
  slug: string
}

export const categoryApi = {
  getAll: () => fetchApi<{ categories: Category[] }>('/categories'),
  getBySlug: (slug: string) => fetchApi<Category>(`/categories/${slug}`),
}

// Products
export interface Product {
  id: string
  categoryId: string
  name: string
  slug: string
  description: string
  price: number
  imageUrl: string
  promotionTag?: string
  active: boolean
}

export const productApi = {
  getAll: (params?: { keyword?: string; categoryId?: string; page?: number; size?: number }) => {
    const searchParams = new URLSearchParams()
    if (params?.keyword) searchParams.set('keyword', params.keyword)
    if (params?.categoryId) searchParams.set('categoryId', params.categoryId)
    if (params?.page !== undefined) searchParams.set('page', String(params.page))
    if (params?.size !== undefined) searchParams.set('size', String(params.size))
    const query = searchParams.toString() ? `?${searchParams.toString()}` : ''
    return fetchApi<{ products: Product[]; totalElements: number; totalPages: number }>(`/products${query}`)
  },
  getById: (id: string) => fetchApi<Product>(`/products/${id}`),
  getByCategory: (categoryId: string) =>
    fetchApi<{ products: Product[] }>(`/products?categoryId=${categoryId}&size=50`),
}

// Cart
export interface CartItem {
  productId: string
  productName: string
  unitPrice: number
  quantity: number
  imageUrl?: string
}

export const cartApi = {
  get: () => fetchApi<{ userId: string; items: CartItem[] }>('/cart'),
  addItem: (data: { productId: string; quantity: number }) =>
    fetchApi<{ items: CartItem[] }>('/cart/items', { method: 'POST', body: JSON.stringify(data) }),
  updateItem: (productId: string, quantity: number) =>
    fetchApi<{ items: CartItem[] }>(`/cart/items/${productId}`, { method: 'PUT', body: JSON.stringify({ quantity }) }),
  removeItem: (productId: string) =>
    fetchApi<{ items: CartItem[] }>(`/cart/items/${productId}`, { method: 'DELETE' }),
  clear: () => fetchApi('/cart', { method: 'DELETE' }),
}

// Orders
export interface OrderItem {
  productId: string
  productName: string
  unitPrice: number
  quantity: number
}

export interface Order {
  id: string
  userId: string
  status: string
  subtotal: number
  shippingFee: number
  total: number
  items: OrderItem[]
  createdAt: string
}

export const orderApi = {
  getAll: () => fetchApi<{ content: Order[]; totalElements: number }>('/orders'),
  getById: (id: string) => fetchApi<Order>(`/orders/${id}`),
  reserve: (data: { items: { productId: string; quantity: number }[] }) =>
    fetchApi<{ orderId: string; status: string }>('/orders/reserve', { method: 'POST', body: JSON.stringify(data) }),
  cancel: (id: string) =>
    fetchApi<Order>(`/orders/${id}/cancel`, { method: 'POST' }),
}

// Payments
export const paymentApi = {
  create: (data: { orderId: string; amount: number; currency: string }) => {
    const idempotencyKey = `pay-${Date.now()}-${Math.random().toString(36).slice(2)}`
    return fetchApi<{ id: string; status: string; redirectUrl?: string }>('/payments', {
      method: 'POST',
      body: JSON.stringify(data),
      headers: { 'Idempotency-Key': idempotencyKey },
    })
  },
  getStatus: (paymentId: string) => fetchApi<{ id: string; status: string }>(`/payments/${paymentId}`),
}
