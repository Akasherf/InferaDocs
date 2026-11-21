# InferaDocs

> Enterprise AI Document Intelligence Platform — Stage 2: Enterprise Authentication, JWT Security & Multi-Tenancy

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.3.1-blue.svg)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6.2-blue.svg)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-blue.svg)](https://www.postgresql.org/)
[![pgvector](https://img.shields.io/badge/pgvector-enabled-purple.svg)](https://github.com/pgvector/pgvector)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue.svg)](https://docs.docker.com/compose/)

## Overview

InferaDocs is a secure, multi-tenant enterprise platform for document intelligence. This repository contains **Stage 1** (Project Foundation) and **Stage 2** (Enterprise Authentication, JWT Security & Multi-Tenancy).

### Stage 1 Features (Foundation)

- ✅ Spring Boot 4.x backend with Java 21
- ✅ React 18 + TypeScript + Vite frontend
- ✅ PostgreSQL 17 with pgvector extension
- ✅ Redis 7 for caching (infrastructure only)
- ✅ MinIO for S3-compatible object storage (infrastructure only)
- ✅ Flyway database migrations
- ✅ Health checks (liveness, readiness, health)
- ✅ System information endpoint
- ✅ Centralized exception handling
- ✅ Standardized API responses
- ✅ CORS configuration
- ✅ Testcontainers integration tests
- ✅ Docker Compose for local development
- ✅ Dark-themed responsive frontend

### Stage 2 Features (Authentication & Multi-Tenancy) ✨ NEW

- ✅ **User Registration & Login** — BCrypt (cost 12), email normalization, rate limiting ready
- ✅ **JWT Authentication** — RS256 signed access tokens (15 min), full claim validation (iss, aud, exp, nbf, jti, sub)
- ✅ **Refresh Token Rotation** — 256-bit entropy, SHA-256 hashed storage, 7-day expiry, atomic revoke+create, reuse detection (revokes entire family)
- ✅ **Multi-Tenancy** — Organizations with unique slugs, creator becomes OWNER
- ✅ **RBAC** — Roles: OWNER, ADMIN, MEMBER, VIEWER with permission matrix
- ✅ **Tenant Isolation** — Request-scoped tenant context via JWT org_id claim, repository-level filtering
- ✅ **Organization Management** — CRUD, member invite/update/remove, ownership transfer safeguards
- ✅ **Audit Logging** — All auth/org events with JSONB metadata, source IP, request correlation
- ✅ **Frontend Auth UI** — Login, Register pages with validation, protected routes, organization workspace

### Planned Stages

| Stage | Focus | Status |
|-------|-------|--------|
| **Stage 1** | Project Foundation | ✅ Complete |
| **Stage 2** | Enterprise Auth & Multi-Tenancy | ✅ Complete |
| Stage 3 | Document Management & Hybrid Search | 📋 Planned |
| Stage 4 | AI & RAG Pipeline | 📋 Planned |
| Stage 5 | Enterprise Features & Monitoring | 📋 Planned |

## Quick Start

### Prerequisites

- **JDK 21** (tested with OpenJDK 21.0.9)
- **Maven 3.9+**
- **Node.js 20+** (tested with v24.12.0)
- **npm 10+**
- **Docker & Docker Compose** (tested with Docker 29.9.3, Compose v5.7.0)

### 1. Clone and Configure

```bash
git clone <repository-url>
cd documind-ai

# Copy environment template
cp .env.example .env
# Edit .env if needed (ports, passwords, etc.)
```

### 2. Start Infrastructure

```bash
cd infrastructure
docker compose up -d
```

This starts:
- **PostgreSQL** on port 5433 (with pgvector)
- **Redis** on port 6380
- **MinIO** on ports 9000 (API) / 9001 (Console)

Verify health:
```bash
docker compose ps
```

### 3. Start Backend

```bash
cd ../backend
./mvnw spring-boot:run
```

Backend runs on **http://localhost:8081/api/v1**

Verify:
```bash
curl http://localhost:8081/api/v1/health
curl http://localhost:8081/api/v1/system/info
```

### 4. Start Frontend

```bash
cd ../frontend
npm install
npm run dev
```

Frontend runs on **http://localhost:5173**

## Project Structure

```
documind-ai/
├── backend/                 # Spring Boot application
│   ├── src/main/java/com/documind/
│   │   ├── common/          # Shared: config, exceptions, responses
│   │   ├── infrastructure/  # Health, persistence
│   │   ├── identity/        # (Stage 2+) User authentication
│   │   ├── organization/    # (Stage 2+) Multi-tenancy
│   │   ├── document/        # (Stage 2+) Document lifecycle
│   │   ├── processing/      # (Stage 3+) Async processing
│   │   ├── search/          # (Stage 3+) Hybrid search
│   │   ├── ai/              # (Stage 4+) RAG, embeddings
│   │   └── audit/           # (Stage 5+) Audit logging
│   ├── src/main/resources/
│   │   ├── application.yml           # Main config
│   │   ├── application-local.yml     # Local profile
│   │   ├── application-test.yml      # Test profile
│   │   └── db/migration/             # Flyway migrations
│   └── src/test/            # Unit & integration tests
├── frontend/                # React + TypeScript + Vite
│   ├── src/
│   │   ├── app/             # App entry, routing
│   │   ├── components/      # Reusable UI components
│   │   ├── pages/           # Page components
│   │   ├── services/        # API client, types
│   │   └── styles/          # Tailwind CSS
│   └── public/              # Static assets
├── infrastructure/          # Docker Compose
│   └── docker-compose.yml
├── docs/                    # Documentation
│   ├── architecture.md
│   └── development.md
├── .env.example             # Environment template
├── .gitignore
└── README.md
```

## API Endpoints

### Health & System

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/health` | Full health check (DB connectivity) |
| GET | `/api/v1/health/live` | Liveness probe |
| GET | `/api/v1/health/ready` | Readiness probe |
| GET | `/api/v1/system/info` | Application metadata |

### Actuator (Management)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/actuator/health` | Spring Boot health |
| GET | `/api/v1/actuator/info` | Application info |

### Authentication (Stage 2)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register new user |
| POST | `/api/v1/auth/login` | Login (returns access token + sets HttpOnly refresh cookie) |
| POST | `/api/v1/auth/refresh` | Refresh access token (requires refresh cookie) |
| POST | `/api/v1/auth/logout` | Logout (revokes refresh token family) |
| GET | `/api/v1/auth/me` | Get current user profile |
| GET | `/api/v1/auth/sessions` | List active sessions |
| DELETE | `/api/v1/auth/sessions/{id}` | Revoke specific session |
| DELETE | `/api/v1/auth/sessions` | Revoke all sessions |

### Organizations (Stage 2)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/organizations` | Create organization (creator becomes OWNER) |
| GET | `/api/v1/organizations` | List user's organizations |
| GET | `/api/v1/organizations/{id}` | Get organization details |
| PATCH | `/api/v1/organizations/{id}` | Update organization (OWNER/ADMIN) |
| DELETE | `/api/v1/organizations/{id}` | Delete organization (OWNER only) |
| GET | `/api/v1/organizations/{id}/members` | List members (member access) |
| POST | `/api/v1/organizations/{id}/members` | Add member (OWNER/ADMIN) |
| PATCH | `/api/v1/organizations/{id}/members/{memberId}` | Update member role/status (OWNER/ADMIN) |
| DELETE | `/api/v1/organizations/{id}/members/{memberId}` | Remove member (OWNER/ADMIN) |

### Response Format

All endpoints return standardized responses:

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "meta": {
    "timestamp": "2026-11-15T10:30:00Z",
    "requestId": "uuid"
  }
}
```

Error responses:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": { "field": "error message" },
    "path": "/api/v1/endpoint",
    "timestamp": "2026-11-15T10:30:00Z"
  },
  "meta": { "timestamp": "..." }
}
```

## Environment Variables

### Stage 1 (Infrastructure)

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `local` | Spring profile |
| `SERVER_PORT` | `8081` | Backend port |
| `POSTGRES_HOST_PORT` | `5433` | PostgreSQL host port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/documind` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `documind` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `documind` | DB password |
| `REDIS_HOST_PORT` | `6380` | Redis host port |
| `MINIO_API_PORT` | `9000` | MinIO API port |
| `MINIO_CONSOLE_PORT` | `9001` | MinIO Console port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Allowed CORS origins |
| `VITE_API_BASE_URL` | `http://localhost:8081/api/v1` | Frontend API base URL |

### Stage 2 (Authentication & Security)

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_ISSUER` | `documind-ai` | JWT issuer claim |
| `JWT_AUDIENCE` | `documind-ai-client` | JWT audience claim |
| `JWT_ACCESS_TOKEN_EXPIRY_MINUTES` | `15` | Access token lifetime |
| `JWT_REFRESH_TOKEN_EXPIRY_DAYS` | `7` | Refresh token lifetime |
| `JWT_PRIVATE_KEY_PEM` | *(generated)* | RSA private key (PEM) — **set in production** |
| `JWT_PUBLIC_KEY_PEM` | *(generated)* | RSA public key (PEM) — **set in production** |

See `.env.example` for all variables.

## Testing

### Backend Tests

```bash
cd backend

# Unit tests
./mvnw test

# Integration tests (requires Docker)
./mvnw verify -Ptest
```

### Frontend Tests

```bash
cd frontend
npm run lint
npm run build
```

## Development

### Backend Development

- Hot reload: Spring Boot DevTools (add dependency if needed)
- Database: Flyway migrations in `src/main/resources/db/migration/`
- Profiles: `local` (default), `test`

### Frontend Development

- Dev server: `npm run dev` (Vite with HMR)
- Build: `npm run build`
- Preview: `npm run preview`
- Lint: `npm run lint`

### Code Style

- Java: Google Java Format (via Spotless/Checkstyle)
- TypeScript: ESLint + Prettier (configured)
- Git hooks: Consider adding Husky for pre-commit

## Architecture

See [docs/architecture.md](docs/architecture.md) for detailed architecture documentation.

### Key Principles

1. **Modular Monolith** — Logical separation, single deployment
2. **Clean Architecture** — Controllers → Services → Repositories → Domain
3. **Configuration over Convention** — Explicit profiles, env-based config
4. **Security First** — No hardcoded secrets, validate inputs, CORS restricted
5. **Observability** — Health checks, structured logging, Actuator

## Troubleshooting

### Port Conflicts

Default ports: 8081 (backend), 5173 (frontend), 5433 (PostgreSQL), 6380 (Redis), 9000/9001 (MinIO).

Change in `.env`:
```bash
POSTGRES_HOST_PORT=5434
SERVER_PORT=8082
```

### Database Connection Failed

1. Ensure Docker containers are healthy: `docker compose ps`
2. Check PostgreSQL logs: `docker compose logs postgres`
3. Verify `.env` values match `docker-compose.yml`

### Frontend Can't Connect to Backend

1. Verify backend is running on port 8081
2. Check CORS: `CORS_ALLOWED_ORIGINS` includes `http://localhost:5173`
3. Check browser network tab for proxy errors

### Tests Fail

- Integration tests require Docker daemon
- Run `docker compose up -d` before integration tests
- Check Testcontainers can pull images (network access)

## Security

- No secrets in code or config files
- `.env` files gitignored
- CORS restricted to configured origins
- Input validation on all endpoints
- Error responses don't leak stack traces
- Actuator endpoints not exposed publicly

## License

Proprietary — All rights reserved.

## Contributing

This is Stage 1 foundation. Future stages will follow the same patterns.
Please read [docs/development.md](docs/development.md) for contribution guidelines.