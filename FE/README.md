# Ecom Micro Services - Frontend

Ứng dụng React SPA cho hệ thống Ecom Micro Services.

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| **React** | 18.3.1 | UI framework |
| **Vite** | 5.4.11 | Build tool & dev server |
| **TypeScript** | 5.6.3 | Type safety |
| **React Router** | 6.30.3 | Client-side routing |

## Prerequisites

- Node.js 18+
- Backend services running (xem [BE README](../BE/README.md))

## Installation

```bash
cd FE
npm install
```

## Development

```bash
npm run dev
```

Ứng dụng sẽ chạy tại **http://localhost:5173**

API requests được proxy qua Vite config:
- `/api` → `http://localhost:8080` (API Gateway)

## Build

```bash
npm run build    # Build for production
npm run preview  # Preview production build
```

## Project Structure

```
FE/
├── index.html              # HTML entry point
├── package.json            # Dependencies & scripts
├── vite.config.ts          # Vite configuration
├── tsconfig.json           # TypeScript config
└── src/
    ├── main.tsx            # React entry point
    ├── App.tsx             # Root component + routing
    ├── App.css             # Global styles
    ├── index.css           # CSS reset
    ├── api.ts              # API client (Axios-like)
    ├── types.ts            # TypeScript interfaces
    ├── context/
    │   └── AuthContext.tsx # Authentication state
    └── pages/
        ├── Home.tsx        # Landing page
        ├── Login.tsx       # User login
        ├── Register.tsx    # User registration
        ├── Products.tsx    # Product listing
        ├── Cart.tsx        # Shopping cart
        ├── Checkout.tsx    # Checkout flow
        ├── Orders.tsx      # Order history
        └── Profile.tsx     # User profile
```

## Pages & Features

### Authentication
- **Login** (`/login`) - Email/password login
- **Register** (`/register`) - New user registration

### Shopping
- **Home** (`/`) - Hero banner, categories, featured products
- **Products** (`/products`) - Browse products with search & filter
- **Cart** (`/cart`) - View/edit cart items, quantities

### Checkout & Orders
- **Checkout** (`/checkout`) - Reserve inventory, process payment
- **Orders** (`/orders`) - View order history & status

### User
- **Profile** (`/profile`) - View/edit profile, manage addresses

## API Integration

API client được cấu hình trong `src/api.ts` với các endpoints:

| API | Endpoints |
|-----|-----------|
| `authApi` | register, login, refresh |
| `userApi` | getMe, updateMe, addresses |
| `categoryApi` | getAll, getBySlug |
| `productApi` | getAll, getById, getByCategory |
| `cartApi` | get, addItem, updateItem, removeItem, clear |
| `orderApi` | getAll, getById, reserve, cancel |
| `paymentApi` | create, getStatus |

## Authentication Flow

1. User đăng nhập → API trả về JWT token
2. Token được lưu trong `localStorage`
3. `AuthContext` cung cấp `user`, `login()`, `logout()` toàn app
4. Protected routes kiểm tra auth state trước render

```typescript
// AuthContext usage
const { user, login, logout } = useAuth();

// Protected route example
<Route path="/checkout" element={
  user ? <Checkout /> : <Navigate to="/login" />
} />
```

## State Management

- **Auth State**: React Context (`AuthContext`)
- **Server State**: Direct API calls (không có React Query/Zustand)
- **Form State**: Local component state

## Styling

- Custom CSS trong `App.css` (887 lines)
- CSS reset trong `index.css`
- Responsive design với media queries
- CSS Variables cho colors & spacing

## Environment Variables

Tạo `.env` file nếu cần override:

```env
VITE_API_URL=http://localhost:8080
```

## Troubleshooting

### API requests fail
- Đảm bảo backend đang chạy tại `http://localhost:8080`
- Kiểm tra CORS configuration trong API Gateway

### Auth issues
- Clear localStorage (`localStorage.clear()`)
- Đăng nhập lại

### Build errors
```bash
rm -rf node_modules package-lock.json
npm install
```

## Related Documentation

- [Backend Documentation](../BE/README.md)
- [Kafka Setup](../docs/kafka-setup-documentation.html)
- [Redis Setup](../docs/redis-setup-documentation.html)