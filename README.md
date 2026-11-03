# Mini Payments Ledger API

A secure REST API for managing payment accounts and transactions, built with Java 21, Spring Boot, and PostgreSQL.

The project demonstrates backend engineering concepts used in financial systems, including double-entry bookkeeping, transactional consistency, API-key authentication, idempotency, concurrency control, and automated integration testing.

## Features

- **Account Management** – Create accounts and retrieve account details.
- **Money Transfers** – Transfer funds between accounts with balance validation.
- **Deposits and Withdrawals** – Record money movements through system accounts.
- **Double-Entry Ledger** – Maintain balanced debit and credit entries for financial transactions.
- **Idempotency** – Prevent duplicate transactions when requests are retried.
- **API-Key Authentication** – Authenticate clients using securely hashed API keys.
- **Transaction History** – Retrieve account transaction records with pagination.
- **Concurrency Control** – Use database row locking to protect account balances during transfers.
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

The ledger uses double-entry bookkeeping.

Each completed transaction creates corresponding debit and credit entries.

For example, a transfer of 300 INR:

| Account | Entry | Amount |
|---|---|---:|
| Sender | DEBIT | 300 INR |
| Receiver | CREDIT | 300 INR |

The debit and credit entries are persisted in the same database transaction as the corresponding balance updates.

The ledger records financial movements, while account balances are maintained as cached balances.

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

Reusing a key with a different payload returns HTTP 409 Conflict.

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

PostgreSQL runs on port `5434` and uses the following local development configuration:

```text
Database: ledger
Username: ledger
Password: ledger
Port: 5434
```

Flyway automatically applies database migrations when the application starts.

### 3. Run the application

On Windows PowerShell:

```powershell
$env:JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"
mvn spring-boot:run
```

The API starts at:

```text
http://localhost:8080
```

### 4. Open Swagger documentation

```text
http://localhost:8080/swagger-ui/index.html
```

Use the **Authorize** option to provide your API key.

API clients must be provisioned in the `api_clients` table with a SHA-256 API-key hash. Do not commit actual API keys to the repository.

## Running with Docker

Build the application image:

```bash
docker build -t ledger-api:1.0 .
```

The application container can connect to the PostgreSQL container using the Docker network created by Docker Compose:

```bash
docker run -d \
  --name ledger-api-app \
  --network ledger-api_default \
  -p 8081:8080 \
  -e DB_URL=jdbc:postgresql://ledger-postgres:5432/ledger \
  -e DB_USER=ledger \
  -e DB_PASSWORD=ledger \
  ledger-api:1.0
```

Check application health:

```text
http://localhost:8081/actuator/health
```

## Testing

The project uses JUnit 5 and Testcontainers to run integration tests against a real PostgreSQL container.

Run the complete verification lifecycle:

```bash
mvn clean verify
```

The test suite covers:

- Account creation and ownership.
- Successful deposits and withdrawals.
- Successful transfers and ledger integrity.
- Idempotency and duplicate request handling.
- Insufficient balance validation.
- Concurrent transfers.
- API-key authentication and client isolation.
- Transaction history and pagination.

## Continuous Integration

GitHub Actions automatically runs the Maven verification workflow on pushes and pull requests targeting `main` or `master`.

Workflow:

1. Check out the repository.
2. Set up Java 21.
3. Configure Maven dependency caching.
4. Run `./mvnw -B clean verify`.

## Future Improvements

- Improve concurrent idempotency handling across all transaction types.
- Add transaction history metadata and filtering.
- Add stronger reconciliation and ledger integrity checks.
- Introduce rate limiting and monitoring.
- Add API-key provisioning and management endpoints.
- Improve deployment configuration and secrets management.

## Project Status

This is a portfolio project designed to demonstrate backend development, database design, financial transaction processing, security, and testing concepts. It is not intended for handling real customer funds or production financial operations.

## Author

**Sagar R.**

GitHub: [@sagar002108](https://github.com/sagar002108)

Repository: [Mini Payments Ledger API](https://github.com/sagar002108/mini-payments-ledger-api)