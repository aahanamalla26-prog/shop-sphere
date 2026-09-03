# ShopSphere — E-Commerce Backend Platform

A distributed, Dockerized e-commerce backend built with **Java, Spring Boot, Spring Security, JWT, and MySQL**.
ShopSphere is split into two independently deployable microservices — an **auth-service** that owns
identity and token issuance, and a **core-service** that owns catalog, cart, orders, and reviews —
so authentication can be scaled, redeployed, and secured independently of the commerce logic.

## Architecture

```
                         ┌─────────────────────┐
                         │   Client (web/app)   │
                         └──────────┬───────────┘
                                    │
                 ┌──────────────────┴──────────────────┐
                 │                                      │
         ┌───────▼────────┐                    ┌────────▼────────┐
         │  auth-service   │                    │   core-service   │
         │   (port 8081)   │                    │   (port 8082)    │
         │                 │   shares JWT        │                  │
         │  • register     │   signing secret    │  • catalog       │
         │  • login        │◄──────────────────► │  • cart          │
         │  • refresh      │   (no runtime        │  • orders        │
         │  • validate     │    coupling)         │  • reviews       │
         └───────┬─────────┘                    └────────┬─────────┘
                 │                                        │
         ┌───────▼─────────┐                    ┌─────────▼────────┐
         │  MySQL (auth db) │                    │ MySQL (core db)   │
         └──────────────────┘                    └───────────────────┘
```

**Why two services?** Extracting auth into its own service means it can be scaled, patched, and
redeployed independently of the commerce backend — a basic distributed-systems pattern (service
boundaries + independent deployability). The two services never call each other synchronously:
`core-service` validates JWTs locally using a signing secret shared with `auth-service` via
environment variables, so there's no runtime coupling or single point of failure between them.

## Tech stack

| Concern              | Choice                                   |
|-----------------------|-------------------------------------------|
| Language / Framework  | Java 21, Spring Boot 3.3                  |
| Security              | Spring Security, JWT (jjwt), BCrypt       |
| Persistence           | Spring Data JPA / Hibernate, MySQL 8      |
| Validation            | Jakarta Bean Validation                   |
| Containerization      | Docker, Docker Compose, multi-stage builds|
| Build                 | Maven                                     |

## Project layout

```
shopsphere/
├── auth-service/            # standalone auth microservice
│   ├── src/main/java/com/shopsphere/auth/
│   │   ├── config/          # Spring Security config
│   │   ├── security/        # JWT issuing, validation, filter
│   │   ├── controller/      # REST endpoints
│   │   ├── service/         # business logic, @Transactional
│   │   ├── dto/             # request/response records
│   │   ├── entity/          # JPA entities
│   │   ├── repository/      # Spring Data repositories
│   │   └── exception/       # custom exceptions + global handler
│   ├── Dockerfile
│   └── pom.xml
├── core-service/             # catalog, cart, orders, reviews
│   ├── src/main/java/com/shopsphere/core/
│   │   ├── config/           # Spring Security config
│   │   ├── security/         # JWT validation (shared secret), CurrentUser helper
│   │   ├── catalog/          # products & categories (entity/dto/repo/service/controller)
│   │   ├── cart/             # per-user cart
│   │   ├── order/            # checkout, order lifecycle
│   │   ├── review/           # product reviews
│   │   └── exception/        # custom exceptions + global handler
│   ├── Dockerfile
│   └── pom.xml
├── docker-compose.yml         # wires both services + their MySQL instances
└── .gitignore
```

Each domain (catalog, cart, order, review) follows the same layered structure —
`controller → service → repository → entity`, with request/response DTOs decoupling the API
contract from persistence — so REST contracts (routes, request/response shapes, status codes)
were defined up front, before implementation, to reduce integration rework.

## Running locally

**Requirements:** Docker & Docker Compose (this spins up MySQL for you — no local MySQL install needed).

```bash
# from the shopsphere/ root
docker compose up --build
```

This starts:
- `mysql-auth` on `localhost:3306`, `mysql-core` on `localhost:3307`
- `auth-service` on `http://localhost:8081`
- `core-service` on `http://localhost:8082`

Both services share a `JWT_SECRET` (see `docker-compose.yml`) — **change this before any real
deployment**, e.g. via a `.env` file or secret manager; never commit real secrets.

### Running a single service without Docker

```bash
cd auth-service
mvn spring-boot:run
```

You'll need a local MySQL instance and to override `DB_HOST`/`DB_PORT`/`DB_USERNAME`/`DB_PASSWORD`
env vars (see `application.yml` for defaults).

## API overview (29 endpoints)

All protected endpoints expect `Authorization: Bearer <accessToken>`, obtained from
`auth-service`'s `/api/auth/login` or `/api/auth/register`.

### auth-service (`:8081`)

| Method | Path                  | Auth      | Description                       |
|--------|------------------------|-----------|------------------------------------|
| POST   | `/api/auth/register`   | Public    | Create an account                  |
| POST   | `/api/auth/login`      | Public    | Exchange credentials for tokens    |
| POST   | `/api/auth/refresh`    | Public    | Exchange a refresh token           |
| GET    | `/api/auth/validate`   | Public    | Validate an access token           |
| GET    | `/api/auth/me`         | User      | Get the current user's profile     |
| POST   | `/api/auth/logout`     | User      | Logout (stateless; client discards)|

### core-service (`:8082`)

**Catalog**

| Method | Path                                  | Auth   | Description             |
|--------|-----------------------------------------|--------|---------------------------|
| GET    | `/api/categories`                       | Public | List categories           |
| GET    | `/api/categories/{id}`                  | Public | Get a category             |
| POST   | `/api/categories`                       | Admin  | Create a category           |
| PUT    | `/api/categories/{id}`                  | Admin  | Update a category           |
| DELETE | `/api/categories/{id}`                  | Admin  | Delete a category (if empty)|
| GET    | `/api/products`                         | Public | List products (paged, `?search=`) |
| GET    | `/api/products/{id}`                    | Public | Get a product               |
| GET    | `/api/products/category/{categoryId}`   | Public | List products in a category |
| POST   | `/api/products`                         | Admin  | Create a product             |
| PUT    | `/api/products/{id}`                    | Admin  | Update a product             |
| DELETE | `/api/products/{id}`                    | Admin  | Soft-delete a product        |

**Cart** (always the caller's own cart)

| Method | Path                        | Auth | Description         |
|--------|------------------------------|------|-----------------------|
| GET    | `/api/cart`                  | User | View cart              |
| POST   | `/api/cart/items`             | User | Add an item             |
| PUT    | `/api/cart/items/{itemId}`    | User | Update item quantity    |
| DELETE | `/api/cart/items/{itemId}`    | User | Remove an item          |
| DELETE | `/api/cart`                   | User | Clear the cart          |

**Orders**

| Method | Path                     | Auth  | Description                              |
|--------|----------------------------|-------|---------------------------------------------|
| POST   | `/api/orders`               | User  | Checkout (creates order from cart, reserves stock)|
| GET    | `/api/orders`               | User  | List the caller's orders                     |
| GET    | `/api/orders/all`           | Admin | List all orders                              |
| GET    | `/api/orders/{id}`          | User  | Get an order (owner or admin)                |
| PUT    | `/api/orders/{id}/status`   | Admin | Update order status                          |
| PUT    | `/api/orders/{id}/cancel`   | User  | Cancel an order (restocks items)             |

**Reviews**

| Method | Path                                | Auth | Description               |
|--------|---------------------------------------|------|------------------------------|
| GET    | `/api/products/{productId}/reviews`   | Public | List reviews for a product |
| POST   | `/api/products/{productId}/reviews`   | User   | Add a review (one per user) |
| PUT    | `/api/reviews/{id}`                   | User   | Edit your review            |
| DELETE | `/api/reviews/{id}`                   | User/Admin | Delete a review          |

## Design notes

- **Transactional integrity**: checkout (`POST /api/orders`) reserves stock for every cart line
  and builds the order in a single `@Transactional` method — if any item is out of stock, the
  entire operation rolls back so the system never produces a partially-placed order or oversells
  inventory.
- **Global exception handling**: both services expose a `@RestControllerAdvice` that maps domain
  exceptions (`ResourceNotFoundException`, `InsufficientStockException`, validation errors, etc.)
  to a single consistent JSON error shape (`timestamp`, `status`, `error`, `message`, optional
  `fieldErrors`), so API consumers never need to special-case error formats per endpoint.
- **Stateless JWT auth**: access tokens (15 min TTL) carry `uid`, `email`, and `role` as claims;
  refresh tokens (7 day TTL) are used solely to mint new access tokens. `core-service` never talks
  to `auth-service` over the network to validate a token — it verifies the HMAC signature locally.
- **Soft deletes for products**, so past orders and reviews referencing a deleted product still
  resolve correctly.

## What's not included (by design, for a portfolio-scoped project)

- No API gateway / service discovery (Eureka, Spring Cloud Gateway) — two services talk directly
  to clients; a gateway would be a natural next step for a larger deployment.
- No token blocklist for logout (documented as a TODO in `AuthController`) — a production system
  would maintain a short-lived Redis-backed blocklist keyed by JWT id.
- No payment integration — orders are created in `PENDING` status; a payments module would sit
  between `POST /api/orders` and `CONFIRMED` status.
