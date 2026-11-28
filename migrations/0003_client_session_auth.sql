-- XXX-CORE-VPN
-- FRANKDEV BUILD
-- Migration 0003 : authentification des sessions clients

-- Jeton client haché (jamais stocké en clair)
ALTER TABLE client_sessions
ADD COLUMN token_hash TEXT;

-- Expiration du jeton de session
ALTER TABLE client_sessions
ADD COLUMN expires_at INTEGER;

-- Un jeton ne peut appartenir qu'à une seule session
CREATE UNIQUE INDEX IF NOT EXISTS idx_client_sessions_token
ON client_sessions(token_hash)
WHERE token_hash IS NOT NULL;

-- Recherche des sessions actives par expiration
CREATE INDEX IF NOT EXISTS idx_client_sessions_expiry
ON client_sessions(revoked, expires_at);