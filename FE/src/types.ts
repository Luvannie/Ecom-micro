export interface User {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
}

export interface Address {
  id: string;
  recipientName: string;
  phone: string;
  line1: string;
  city: string;
  district: string;
  postalCode: string;
  defaultAddress: boolean;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
}

export interface Product {
  id: string;
  categoryId: string;
  name: string;
  slug: string;
  description: string;
  price: number;
  imageUrl: string;
  promotionTag?: string;
}

export interface CartItem {
  productId: string;
  quantity: number;
}

export interface Cart {
  userId: string;
  items: CartItem[];
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface Order {
  id: string;
  userId: string;
  status: string;
  totalAmount: number;
  currency: string;
  createdAt: string;
}

export interface Payment {
  id: string;
  orderId: string;
  status: string;
  amount: number;
  currency: string;
}