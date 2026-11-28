
-- XXX-CORE-VPN
-- FRANKDEV BUILD
-- Migration 0005 : sessions d'authentification clients

-- 1. Sessions d'authentification distinctes des tunnels VPN
CREATE TABLE IF NOT EXISTS client_auth_sessions (
    id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0
        CHECK (revoked IN (0, 1)),
    FOREIGN KEY (client_id)
        REFERENCES clients(id)
        ON DELETE CASCADE
);

-- 2. Une seule session d'authentification active par client
CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_auth_session_per_client
ON client_auth_sessions(client_id)
WHERE revoked = 0;

-- 3. Recherche des sessions d'authentification
CREATE INDEX IF NOT EXISTS idx_client_auth_sessions_expiry
ON client_auth_sessions(revoked, expires_at);

CREATE INDEX IF NOT EXISTS idx_client_auth_sessions_client
ON client_auth_sessions(client_id);

-- 4. Association entre authentification et tunnel VPN
ALTER TABLE client_sessions
ADD COLUMN auth_session_id TEXT
REFERENCES client_auth_sessions(id);

CREATE INDEX IF NOT EXISTS idx_client_sessions_auth
ON client_sessions(auth_session_id);
