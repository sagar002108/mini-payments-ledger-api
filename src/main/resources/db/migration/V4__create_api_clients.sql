CREATE TABLE api_clients (
    id UUID PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL UNIQUE,
    api_key_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_api_client_status
        CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_api_clients_client_id
ON api_clients(client_id);