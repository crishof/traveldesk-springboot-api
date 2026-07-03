# TravelDesk API

[![Java](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/Database-PostgreSQL-336791)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 📋 Description

Backend REST API for **TravelDesk**, a travel agency management platform. Handles bookings, customer management, and transactional email notifications. Designed for multi-environment deployment with `dev`/`test`/`prod` Spring profiles and Brevo email API integration.

**Frontend:** [traveldesk-angular-web-client](https://github.com/crishof/traveldesk-angular-web-client) · **Live demo:** [traveldesk-pi.vercel.app](https://traveldesk-pi.vercel.app)

---

## 🎯 Key Features

- ✅ Full REST API for travel bookings and customer management
- ✅ JWT authentication with role-based access control
- ✅ Transactional email via **Brevo API** (registration, password reset, invitations)
- ✅ Multi-profile Spring Boot setup: `dev` (H2), `test`, `prod` (PostgreSQL + Brevo)
- ✅ Password reset and user invitation flows
- ✅ CORS configurable via environment variables
- ✅ Global exception handling with structured error responses

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 21, Spring Boot 3.x |
| **Security** | Spring Security + JWT |
| **Database** | PostgreSQL (prod) · H2 in-memory (dev/test) |
| **ORM** | Spring Data JPA / Hibernate |
| **Email** | Brevo API (HTTPS) with SMTP fallback |
| **Build** | Maven |
| **DevOps** | Docker, Spring Profiles |

---

## 🚀 Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker (optional, for PostgreSQL)
- Brevo account (for email in prod)

### Run in Development (H2, no email needed)

```bash
git clone https://github.com/crishof/traveldesk-springboot-api.git
cd traveldesk-springboot-api
./mvnw spring-boot:run
# API available at http://localhost:8080 (dev profile by default)
```

### Run in Production Profile

Create a `.env` file in the project root:

```env
# Email — Brevo API (recommended)
MAIL_PROVIDER=brevo-api
BREVO_API_KEY=<your-brevo-api-key>
BREVO_API_URL=https://api.brevo.com/v3
MAIL_FROM=noreply@yourdomain.com

# Frontend URLs for email links
RESET_PASSWORD_BASE_URL=https://traveldesk-pi.vercel.app/reset-password
ACCEPT_INVITE_BASE_URL=https://traveldesk-pi.vercel.app/accept-invite
```

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

### Run Tests

```bash
./mvnw -q test
```

---

## 📡 API Endpoints

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/api/v1/auth/signup` | Register new user | Public |
| `POST` | `/api/v1/auth/login` | Login, returns JWT | Public |
| `POST` | `/api/v1/auth/logout` | Logout (revoke token) | Required |
| `POST` | `/api/v1/auth/password/forgot` | Send password reset link | Public |
| `POST` | `/api/v1/auth/password/reset` | Reset password with token | Public |
| `GET` | `/api/v1/bookings` | List all bookings | Required |
| `POST` | `/api/v1/bookings` | Create a booking | Required |
| `GET` | `/api/v1/bookings/{id}` | Get booking by ID | Required |
| `PUT` | `/api/v1/bookings/{id}` | Update booking | Required |
| `DELETE` | `/api/v1/bookings/{id}` | Delete booking | Required |
| `GET` | `/api/v1/customers` | List customers | Required |

---

## 💡 Key Decisions

- **Brevo over plain SMTP:** Cloud hosting providers often block outbound SMTP ports. Brevo's HTTP API (port 443) is always reachable — SMTP is kept as optional fallback.
- **Spring Profiles for environment separation:** No if/else environment checks in code. Each profile (`dev`, `test`, `prod`) has its own config file, keeping secrets out of source control.
- **H2 for dev/test:** Zero-dependency local development — the full test suite runs without any external database or email server.

---

## 📊 Project Status

**Active** · Last update: July 2026

## 📝 Future Improvements

- [ ] Docker Compose for full local stack (API + PostgreSQL)
- [ ] Swagger / OpenAPI documentation
- [ ] Integration test suite
- [ ] CI/CD pipeline (GitHub Actions)
