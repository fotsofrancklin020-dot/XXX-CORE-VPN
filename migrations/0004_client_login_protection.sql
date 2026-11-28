-- XXX-CORE-VPN
-- FRANKDEV BUILD
-- Migration 0004 : protection des connexions clients

CREATE TABLE IF NOT EXISTS client_login_attempts (
    attempt_key TEXT PRIMARY KEY,
    attempts INTEGER NOT NULL DEFAULT 0,
    window_started_at INTEGER NOT NULL,
    blocked_until INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_client_login_attempts_blocked
ON client_login_attempts(blocked_until);