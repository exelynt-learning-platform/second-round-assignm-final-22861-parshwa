# E-Commerce Backend — Spring Boot

A production-ready REST API for an e-commerce platform built with **Spring Boot 3**, **Spring Security + JWT**, **Spring Data JPA**, and **Stripe** for payment processing.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2, Java 17 |
| Security | Spring Security 6, JWT (JJWT 0.11) |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL |
| Payments | Stripe Java SDK 23 |
| Utilities | Lombok, MapStruct |
| Testing | JUnit 5, Mockito, Spring MockMvc |
| Build | Maven 3.9 |

---

## Project Structure

```
src/
├── main/java/com/ecommerce/
│   ├── EcommerceApplication.java
│   ├── config/
│   │   ├── JwtAuthEntryPoint.java      ← 401 JSON responses
│   │   ├── SecurityConfig.java         ← JWT filter chain, CORS, role rules
│   │   └── StripeConfig.java           ← Stripe SDK initialisation
│   ├── controller/
│   │   ├── AuthController.java         ← /api/auth/**
│   │   ├── CartController.java         ← /api/cart/**
│   │   ├── OrderController.java        ← /api/orders/**
│   │   ├── PaymentController.java      ← /api/payments/**
│   │   └── ProductController.java      ← /api/products/**
│   ├── dto/
│   │   ├── request/                    ← validated inbound payloads
│   │   └── response/                   ← outbound shapes
│   ├── entity/
│   │   ├── User.java                   ← ROLE_USER / ROLE_ADMIN
│   │   ├── Product.java                ← soft-delete via active flag
│   │   ├── Cart.java + CartItem.java
│   │   └── Order.java + OrderItem.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java ← maps every exception → HTTP code
│   │   ├── BadRequestException.java    ← 400
│   │   ├── ConflictException.java      ← 409
│   │   ├── InsufficientStockException  ← 409
│   │   ├── PaymentException.java       ← 402
│   │   └── ResourceNotFoundException  ← 404
│   ├── repository/                     ← JPA repositories w/ custom JPQL
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtUtils.java               ← sign / validate / parse tokens
│   │   ├── UserDetailsImpl.java
│   │   └── UserDetailsServiceImpl.java
│   └── service/
│       ├── AuthService.java  (+ impl)
│       ├── CartService.java  (+ impl)
│       ├── OrderService.java (+ impl)
│       ├── PaymentService.java (+ impl)
│       └── ProductService.java (+ impl)
└── test/java/com/ecommerce/
    ├── controller/AuthControllerTest.java
    └── service/
        ├── AuthServiceTest.java
        ├── CartServiceTest.java
        ├── OrderServiceTest.java
        ├── PaymentServiceTest.java
        └── ProductServiceTest.java
```

---

## Environment Variables

Create a `.env` file or set these in your environment before running:

```bash
# Database
DB_USERNAME=postgres
DB_PASSWORD=your_db_password

# JWT — generate a 256-bit hex key, e.g.: openssl rand -hex 32
JWT_SECRET=your_256bit_hex_secret

# Stripe
STRIPE_SECRET_KEY=sk_test_...        # from Stripe Dashboard
STRIPE_WEBHOOK_SECRET=whsec_...      # from Stripe Dashboard → Webhooks

# Frontend (for CORS)
FRONTEND_URL=http://localhost:3000
```

---

## Running Locally

### Prerequisites
- Java 17+
- Maven 3.9+
- PostgreSQL 14+ running locally (or Docker)

### 1. Create the database
```sql
CREATE DATABASE ecommerce_db;
```

### 2. Clone and build
```bash
git clone <repo-url>
cd ecommerce-backend
mvn clean install -DskipTests
```

### 3. Run
```bash
mvn spring-boot:run
```
The API starts on **http://localhost:8080**.

### 4. Run tests
```bash
mvn test
```

---

## Docker (optional)

```yaml
# docker-compose.yml
version: "3.9"
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: ecommerce_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"

  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - postgres
    environment:
      DB_USERNAME: postgres
      DB_PASSWORD: password
      JWT_SECRET: 404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
      STRIPE_SECRET_KEY: sk_test_your_key
      STRIPE_WEBHOOK_SECRET: whsec_your_secret
```

```bash
docker-compose up --build
```

---

## API Reference

### Authentication — `/api/auth`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/register` | Public | Register a new user |
| POST | `/api/auth/login` | Public | Login, receive JWT pair |
| POST | `/api/auth/refresh?refreshToken=` | Public | Refresh access token |

**Register request body:**
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "SecurePass1!",
  "fullName": "John Doe",
  "phone": "+1-555-0100"
}
```

**Login request body:**
```json
{
  "usernameOrEmail": "john_doe",
  "password": "SecurePass1!"
}
```

**Auth response:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "tokenType": "Bearer",
    "userId": 1,
    "username": "john_doe",
    "email": "john@example.com",
    "role": "ROLE_USER"
  }
}
```

> All subsequent requests require the header:
> `Authorization: Bearer <accessToken>`

---

### Products — `/api/products`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/products` | Public | List all active products (paginated) |
| GET | `/api/products/{id}` | Public | Get product by ID |
| GET | `/api/products/search?query=` | Public | Full-text search |
| GET | `/api/products/category/{cat}` | Public | Filter by category |
| GET | `/api/products/price-range?min=&max=` | Public | Filter by price |
| POST | `/api/products` | **ADMIN** | Create product |
| PUT | `/api/products/{id}` | **ADMIN** | Update product |
| DELETE | `/api/products/{id}` | **ADMIN** | Soft-delete product |

**Pagination params:** `?page=0&size=20&sort=price,asc`

**Create/Update body:**
```json
{
  "name": "Mechanical Keyboard",
  "description": "Tactile switches, RGB backlighting",
  "price": 149.99,
  "stockQuantity": 50,
  "imageUrl": "https://cdn.example.com/keyboard.jpg",
  "category": "Electronics"
}
```

---

### Cart — `/api/cart`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/cart` | User | View current cart |
| POST | `/api/cart/items` | User | Add item (increments if exists) |
| PUT | `/api/cart/items/{itemId}` | User | Update item quantity |
| DELETE | `/api/cart/items/{itemId}` | User | Remove single item |
| DELETE | `/api/cart` | User | Clear entire cart |

**Add/Update body:**
```json
{ "productId": 10, "quantity": 2 }
```

**Cart response:**
```json
{
  "id": 1,
  "userId": 1,
  "items": [
    {
      "id": 1,
      "productId": 10,
      "productName": "Mechanical Keyboard",
      "priceAtAddition": 149.99,
      "quantity": 2,
      "subtotal": 299.98
    }
  ],
  "totalItems": 2,
  "totalPrice": 299.98
}
```

---

### Orders — `/api/orders`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/orders` | User | Checkout (cart → order) |
| GET | `/api/orders` | User | List my orders (paginated) |
| GET | `/api/orders/{id}` | User | Get order details |
| GET | `/api/orders/admin/all` | **ADMIN** | All orders |
| PATCH | `/api/orders/{id}/status?status=` | **ADMIN** | Update fulfilment status |

**Checkout body:**
```json
{
  "shippingFullName": "John Doe",
  "shippingAddressLine1": "123 Main St",
  "shippingAddressLine2": "Apt 4B",
  "shippingCity": "New York",
  "shippingState": "NY",
  "shippingPostalCode": "10001",
  "shippingCountry": "US",
  "shippingPhone": "+1-555-0100"
}
```

**Order statuses:** `PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED`
**Payment statuses:** `PENDING → PROCESSING → PAID → FAILED → REFUNDED`

---

### Payments — `/api/payments`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/payments/create-intent` | User | Create Stripe PaymentIntent |
| POST | `/api/payments/webhook` | Public | Stripe webhook receiver |

**Payment flow:**
1. Create order → `POST /api/orders`
2. Create PaymentIntent → `POST /api/payments/create-intent` with `{ "orderId": 1 }`
3. Complete payment on frontend using Stripe.js with the returned `clientSecret`
4. Stripe sends webhook to `/api/payments/webhook`
5. Order is automatically marked `PAID` + `CONFIRMED`

**PaymentIntent response:**
```json
{
  "clientSecret": "pi_xxx_secret_yyy",
  "paymentIntentId": "pi_xxx",
  "amount": 29998,
  "currency": "usd",
  "status": "requires_payment_method",
  "orderId": 1
}
```

**Register the webhook in Stripe Dashboard:**
- URL: `https://your-domain.com/api/payments/webhook`
- Events to listen for: `payment_intent.succeeded`, `payment_intent.payment_failed`, `payment_intent.canceled`

---

## Security Design

```
Request
  │
  ▼
JwtAuthenticationFilter        ← extracts Bearer token, sets SecurityContext
  │
  ▼
SecurityFilterChain
  ├── /api/auth/**              → permitAll
  ├── GET /api/products/**      → permitAll
  ├── /api/payments/webhook     → permitAll
  ├── POST/PUT/DELETE /api/products → hasRole(ADMIN)
  └── everything else           → authenticated
```

- Passwords hashed with **BCrypt** (cost factor 12)
- Access tokens expire in **24 h**, refresh tokens in **7 days**
- Role-based method-level security via `@PreAuthorize`
- Users can only access **their own** cart and orders (enforced in service layer)

---

## Data Model

```
users ──< orders ──< order_items >── products
  │                                     │
  └──< cart ──< cart_items >────────────┘
```

- **One-to-Many:** User → Orders, Order → OrderItems, Cart → CartItems
- **Many-to-One:** OrderItem/CartItem → Product
- **One-to-One:** User → Cart
- Products use a **soft-delete** (`active` flag) so historical order data is preserved

---

## Error Responses

All errors follow the same envelope:

```json
{
  "success": false,
  "message": "Human-readable description",
  "timestamp": "2025-01-01T12:00:00"
}
```

Validation errors include a `data` map of field → message:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "email": "Invalid email format",
    "password": "Password must be at least 8 characters"
  }
}
```

| HTTP Code | Cause |
|-----------|-------|
| 400 | Validation failure, bad request body |
| 401 | Missing or invalid JWT |
| 402 | Payment processing failure |
| 403 | Insufficient role/permissions |
| 404 | Resource not found |
| 409 | Duplicate email/username, insufficient stock |
| 500 | Unhandled server error |
