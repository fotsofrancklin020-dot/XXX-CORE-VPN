-- XXX-CORE-VPN
-- FRANKDEV BUILD
-- Migration 0001 : tables initiales

-- 1. Clients
CREATE TABLE IF NOT EXISTS clients (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1
        CHECK (active IN (0, 1)),
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_clients_active
ON clients(active);


-- 2. Sessions administrateur
CREATE TABLE IF NOT EXISTS admin_sessions (
    id TEXT PRIMARY KEY,
    token_hash TEXT NOT NULL UNIQUE,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0
        CHECK (revoked IN (0, 1))
);

CREATE INDEX IF NOT EXISTS idx_admin_sessions_token
ON admin_sessions(token_hash);


-- 3. Sessions clients
CREATE TABLE IF NOT EXISTS client_sessions (
    id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    connected_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0
        CHECK (revoked IN (0, 1)),
    disconnected_at INTEGER,
    FOREIGN KEY (client_id)
        REFERENCES clients(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_client_sessions_client
ON client_sessions(client_id);

CREATE INDEX IF NOT EXISTS idx_client_sessions_online
ON client_sessions(revoked, last_seen_at);


-- 4. Journal des actions administrateur
CREATE TABLE IF NOT EXISTS admin_logs (
    id TEXT PRIMARY KEY,
    action TEXT NOT NULL,
    details TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_logs_created
ON admin_logs(created_at);


-- 5. Configurations des clients
CREATE TABLE IF NOT EXISTS client_configs (
    id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    config_name TEXT NOT NULL DEFAULT 'Configuration principale',
    config_json TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (client_id)
        REFERENCES clients(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_client_configs_client
ON client_configs(client_id);
