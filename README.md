# Mini Payments Ledger API

[![Ledger API CI](https://github.com/sagar002108/mini-payments-ledger-api/actions/workflows/ci.yml/badge.svg)](https://github.com/sagar002108/mini-payments-ledger-api/actions/workflows/ci.yml)

A secure REST API for managing payment accounts and financial transactions, built with Java 21, Spring Boot, and PostgreSQL.

This project demonstrates backend engineering concepts used in financial systems, including double-entry bookkeeping, transactional consistency, API-key authentication, idempotency, concurrency control, database integrity, and automated integration testing.

## Features

- **Account Management** – Create accounts and retrieve account details.
- **Money Transfers** – Transfer funds between accounts with balance validation.
- **Deposits and Withdrawals** – Record money movements through system accounts.
- **Double-Entry Ledger** – Maintain balanced debit and credit entries for financial transactions.
- **Idempotency** – Prevent duplicate transactions when requests are retried.
- **API-Key Authentication** – Authenticate clients using securely hashed API keys.
- **Client Isolation** – Restrict account access based on the authenticated API client.
- **Transaction History** – Retrieve account transaction records with pagination.
- **Concurrency Control** – Use database row locking and deterministic lock ordering to protect account balances.
- **Database Integrity** – Use PostgreSQL deferred constraint triggers to validate completed transactions.
- **Ledger Immutability** – Prevent ledger entries from being updated, deleted, or truncated.
- **Database Migrations** – Manage schema changes using Flyway.
- **Automated Testing** – Unit and integration tests using JUnit and Testcontainers.
- **Docker Support** – Containerized application and PostgreSQL database.
- **Continuous Integration** – GitHub Actions workflow for automated build and verification.

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Programming language |
| Spring Boot 4 | Backend framework |
| Spring Security | API-key authentication |
| Spring JDBC | Database access |
| PostgreSQL 16 | Relational database |
| Flyway | Database migrations |
| Maven | Build and dependency management |
| JUnit 5 | Automated testing |
| Testcontainers | PostgreSQL integration testing |
| Docker | Containerization |
| GitHub Actions | CI pipeline |
| OpenAPI / Swagger | API documentation |

## Architecture

The application follows a layered architecture:

```text
Client
  |
  | X-API-KEY
  v
API Controllers
  |
  v
Services
  |
  | Business Logic
  | Transaction Management
  v
Repositories
  |
  | SQL / JDBC
  v
PostgreSQL
  |
  +-- Accounts
  +-- Transfers
  +-- Deposits
  +-- Withdrawals
  +-- Ledger Entries
  +-- API Clients
```

### Financial Transaction Design

The ledger uses double-entry bookkeeping. Each completed transaction creates corresponding debit and credit entries.

For example, a transfer of 300 INR:

| Account | Entry | Amount |
|---|---|---:|
| Sender | DEBIT | 300 INR |
| Receiver | CREDIT | 300 INR |

Both entries and the corresponding account balance updates are persisted within the same database transaction.

Account balances are maintained as cached balances, while ledger entries record the underlying financial movements.

### Transaction Safety and Data Integrity

The project includes several mechanisms to protect financial operations:

- **Atomic transactions:** Related database changes are committed or rolled back together.
- **Idempotency:** Requests are identified using client-scoped idempotency keys and request hashes.
- **Concurrency control:** Account rows are locked in deterministic UUID order to reduce race conditions and deadlocks.
- **Balanced ledger validation:** Deferred PostgreSQL constraint triggers validate that completed transactions have exactly one debit and one credit, with matching amounts and currencies.
- **Ledger immutability:** Database triggers prevent ledger entries from being updated, deleted, or truncated.

## API Endpoints

All business endpoints require an API key supplied through the `X-API-KEY` header.

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/accounts` | Create an account |
| GET | `/api/accounts/{id}` | Retrieve account details |
| POST | `/api/deposits` | Deposit funds |
| POST | `/api/withdrawals` | Withdraw funds |
| POST | `/api/transfers` | Transfer funds |
| GET | `/api/accounts/{id}/transactions` | Retrieve transaction history |

### Idempotency

Financial transaction endpoints use the `Idempotency-Key` request header.

Example:

```http
POST /api/transfers
X-API-KEY: your-api-key
Idempotency-Key: transfer-001
Content-Type: application/json
```

Repeated requests with the same key and payload return the original transaction result rather than creating another transaction.

Reusing a key with a different payload returns HTTP `409 Conflict`.

## Running the Project

### Prerequisites

- Java 21
- Maven
- Docker Desktop
- Git

### 1. Clone the repository

```bash
git clone https://github.com/sagar002108/mini-payments-ledger-api.git

cd mini-payments-ledger-api
```

### 2. Start PostgreSQL

```bash
docker compose up -d
```

The local development database uses the following configuration:

```text
Database: ledger
Username: ledger
Password: ledger
Host port: 5434
Container port: 5432
```

Flyway automatically applies database migrations when the application starts.

### 3. Configure API-key authentication

The application authenticates clients using SHA-256 hashes stored in the `api_clients` table.

For local development:

1. Generate a random API key.
2. Calculate its SHA-256 hash.
3. Register the client ID and hash in the `api_clients` table with `ACTIVE` status.
4. Keep the original API key private and use it in API requests.

Never commit actual API keys or production credentials to the repository.

### 4. Run the application

On Windows PowerShell:

```powershell
$env:JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"
mvn spring-boot:run
```

The API starts at:

```text
http://localhost:8080
```

### 5. Open Swagger documentation

```text
http://localhost:8080/swagger-ui/index.html
```

Use the **Authorize** option to provide your API key.

## Running with Docker

Build the application image:

```bash
docker build -t ledger-api:1.0 .
```

When PostgreSQL is running through Docker Compose, the application container can connect to it using the Compose network:

```powershell
docker run -d `
  --name ledger-api-app `
  --network ledger-api_default `
  -p 8081:8080 `
  -e DB_URL=jdbc:postgresql://ledger-postgres:5432/ledger `
  -e DB_USER=ledger `
  -e DB_PASSWORD=ledger `
  ledger-api:1.0
```

Check application health:

```text
http://localhost:8081/actuator/health
```

## Testing

The project uses JUnit 5 and Testcontainers to run integration tests against a real PostgreSQL container.

Run the complete Maven verification lifecycle:

```bash
mvn clean verify
```

The test suite covers:

- Account creation and ownership.
- Successful deposits and withdrawals.
- Successful transfers and balanced ledger entries.
- Idempotency and duplicate request handling.
- Concurrent requests and transfers.
- Insufficient balance validation.
- API-key authentication and client isolation.
- Transaction history and pagination.

## Continuous Integration

GitHub Actions automatically runs the Maven verification workflow on pushes and pull requests targeting `main` or `master`.

The workflow:

1. Checks out the repository.
2. Sets up Java 21.
3. Configures Maven dependency caching.
4. Runs `./mvnw -B clean verify`.

## Future Improvements

Potential areas for further development:

- Transaction history filtering and richer pagination metadata.
- API-key provisioning, rotation, and management endpoints.
- Rate limiting and improved monitoring.
- Additional reconciliation and database integrity tests.
- Production deployment configuration and secrets management.

## Project Status

This is a portfolio project designed to demonstrate backend development, database design, financial transaction processing, security, concurrency control, and automated testing.

It is not intended for handling real customer funds or production financial operations.

## Author

**Sagar R.**

- Email: [sagarr212000@gmail.com](mailto:sagarr212000@gmail.com)
- GitHub: [@sagar002108](https://github.com/sagar002108)
- LinkedIn: [Sagar R.](https://www.linkedin.com/in/sagar-r-692904259/)

**Repository:** [Mini Payments Ledger API](https://github.com/sagar002108/mini-payments-ledger-api)
