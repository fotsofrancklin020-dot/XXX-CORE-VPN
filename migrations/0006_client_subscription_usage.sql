
-- XXX-CORE-VPN
-- FRANKDEV BUILD
-- Migration 0006 : consommation par abonnement

CREATE TABLE IF NOT EXISTS client_subscription_usage (
    subscription_id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    upload_bytes INTEGER NOT NULL DEFAULT 0
        CHECK (upload_bytes >= 0),
    download_bytes INTEGER NOT NULL DEFAULT 0
        CHECK (download_bytes >= 0),
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (subscription_id)
        REFERENCES client_subscriptions(id)
        ON DELETE CASCADE,
    FOREIGN KEY (client_id)
        REFERENCES clients(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_subscription_usage_client
ON client_subscription_usage(client_id);

-- Initialiser le suivi pour les abonnements existants.
INSERT OR IGNORE INTO client_subscription_usage (
    subscription_id,
    client_id,
    upload_bytes,
    download_bytes,
    updated_at
)
SELECT
    id,
    client_id,
    0,
    0,
    CAST(strftime('%s', 'now') AS INTEGER)
FROM client_subscriptions;
