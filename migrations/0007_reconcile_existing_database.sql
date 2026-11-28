-- XXX-CORE-VPN
-- FRANKDEV BUILD
-- Migration 0007 : mise en conformité de la base existante
-- Préserve les tables et les données déjà présentes.

-- 1. Abonnements des clients
CREATE TABLE IF NOT EXISTS client_subscriptions (
    id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    plan_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'expired', 'suspended', 'cancelled')),
    starts_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    quota_bytes INTEGER NOT NULL DEFAULT 0 CHECK (quota_bytes >= 0),
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_client_subscriptions_client
ON client_subscriptions(client_id);

CREATE INDEX IF NOT EXISTS idx_client_subscriptions_expiry
ON client_subscriptions(status, expires_at);

CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_subscription_per_client
ON client_subscriptions(client_id)
WHERE status = 'active';


-- 2. Configurations des protocoles
CREATE TABLE IF NOT EXISTS client_protocol_configs (
    id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    protocol TEXT NOT NULL
        CHECK (protocol IN (
            'VLESS', 'VMess', 'Trojan', 'SSH Tunnel',
            'OpenVPN', 'WireGuard', 'SlowDNS',
            'UDP Custom', 'UDP Request', 'ZivUDP'
        )),
    config_name TEXT NOT NULL DEFAULT 'Configuration principale',
    config_json TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    expires_at INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_protocol_configs_client
ON client_protocol_configs(client_id);

CREATE INDEX IF NOT EXISTS idx_protocol_configs_protocol
ON client_protocol_configs(client_id, protocol, enabled);


-- 3. Compteurs cumulés des clients
CREATE TABLE IF NOT EXISTS client_usage_totals (
    client_id TEXT PRIMARY KEY,
    upload_bytes INTEGER NOT NULL DEFAULT 0 CHECK (upload_bytes >= 0),
    download_bytes INTEGER NOT NULL DEFAULT 0 CHECK (download_bytes >= 0),
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_client_usage_updated
ON client_usage_totals(updated_at);

INSERT OR IGNORE INTO client_usage_totals (
    client_id, upload_bytes, download_bytes, updated_at
)
SELECT id, 0, 0, CAST(strftime('%s', 'now') AS INTEGER)
FROM clients;


-- 4. Compléter les sessions VPN existantes
-- token_hash existe déjà dans la base : ne pas le recréer.

ALTER TABLE client_sessions
ADD COLUMN expires_at INTEGER;

CREATE UNIQUE INDEX IF NOT EXISTS idx_client_sessions_token
ON client_sessions(token_hash)
WHERE token_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_client_sessions_expiry
ON client_sessions(revoked, expires_at);


-- 5. Protection contre les tentatives de connexion
CREATE TABLE IF NOT EXISTS client_login_attempts (
    attempt_key TEXT PRIMARY KEY,
    attempts INTEGER NOT NULL DEFAULT 0,
    window_started_at INTEGER NOT NULL,
    blocked_until INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_client_login_attempts_blocked
ON client_login_attempts(blocked_until);


-- 6. Sessions d'authentification des clients
CREATE TABLE IF NOT EXISTS client_auth_sessions (
    id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0 CHECK (revoked IN (0, 1)),
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_auth_session_per_client
ON client_auth_sessions(client_id)
WHERE revoked = 0;

CREATE INDEX IF NOT EXISTS idx_client_auth_sessions_expiry
ON client_auth_sessions(revoked, expires_at);

CREATE INDEX IF NOT EXISTS idx_client_auth_sessions_client
ON client_auth_sessions(client_id);

ALTER TABLE client_sessions
ADD COLUMN auth_session_id TEXT
REFERENCES client_auth_sessions(id);

CREATE INDEX IF NOT EXISTS idx_client_sessions_auth
ON client_sessions(auth_session_id);


-- 7. Compteurs par abonnement
CREATE TABLE IF NOT EXISTS client_subscription_usage (
    subscription_id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    upload_bytes INTEGER NOT NULL DEFAULT 0 CHECK (upload_bytes >= 0),
    download_bytes INTEGER NOT NULL DEFAULT 0 CHECK (download_bytes >= 0),
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (subscription_id)
        REFERENCES client_subscriptions(id) ON DELETE CASCADE,
    FOREIGN KEY (client_id)
        REFERENCES clients(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_subscription_usage_client
ON client_subscription_usage(client_id);

INSERT OR IGNORE INTO client_subscription_usage (
    subscription_id, client_id, upload_bytes, download_bytes, updated_at
)
SELECT
    id, client_id, 0, 0, CAST(strftime('%s', 'now') AS INTEGER)
FROM client_subscriptions;