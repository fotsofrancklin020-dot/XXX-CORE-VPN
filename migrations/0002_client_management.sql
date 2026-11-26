-- XXX-CORE-VPN
-- FRANKDEV BUILD
-- Migration 0002 : abonnements, protocoles et consommation cumulée

-- 1. Abonnements des clients
CREATE TABLE IF NOT EXISTS client_subscriptions (
    id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    plan_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'expired', 'suspended', 'cancelled')),
    starts_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    quota_bytes INTEGER NOT NULL DEFAULT 0
        CHECK (quota_bytes >= 0),
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (client_id)
        REFERENCES clients(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_client_subscriptions_client
ON client_subscriptions(client_id);

CREATE INDEX IF NOT EXISTS idx_client_subscriptions_expiry
ON client_subscriptions(status, expires_at);

CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_subscription_per_client
ON client_subscriptions(client_id)
WHERE status = 'active';


-- 2. Configurations individuelles des protocoles
-- Plusieurs configurations peuvent exister pour un même protocole.

CREATE TABLE IF NOT EXISTS client_protocol_configs (
    id TEXT PRIMARY KEY,
    client_id TEXT NOT NULL,
    protocol TEXT NOT NULL
        CHECK (
            protocol IN (
                'VLESS',
                'VMess',
                'Trojan',
                'SSH Tunnel',
                'OpenVPN',
                'WireGuard',
                'SlowDNS',
                'UDP Custom',
                'UDP Request',
                'ZivUDP'
            )
        ),
    config_name TEXT NOT NULL DEFAULT 'Configuration principale',
    config_json TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1
        CHECK (enabled IN (0, 1)),
    expires_at INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (client_id)
        REFERENCES clients(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_protocol_configs_client
ON client_protocol_configs(client_id);

CREATE INDEX IF NOT EXISTS idx_protocol_configs_protocol
ON client_protocol_configs(client_id, protocol, enabled);


-- 3. Consommation cumulée par client
-- Les compteurs ne sont pas réinitialisés lors d'une reconnexion.

CREATE TABLE IF NOT EXISTS client_usage_totals (
    client_id TEXT PRIMARY KEY,
    upload_bytes INTEGER NOT NULL DEFAULT 0
        CHECK (upload_bytes >= 0),
    download_bytes INTEGER NOT NULL DEFAULT 0
        CHECK (download_bytes >= 0),
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (client_id)
        REFERENCES clients(id)
        ON DELETE CASCADE
);


-- 4. Index pour les recherches de consommation
CREATE INDEX IF NOT EXISTS idx_client_usage_updated
ON client_usage_totals(updated_at);


-- 5. Initialisation des compteurs pour les clients existants
INSERT OR IGNORE INTO client_usage_totals (
    client_id,
    upload_bytes,
    download_bytes,
    updated_at
)
SELECT
    id,
    0,
    0,
    CAST(strftime('%s', 'now') AS INTEGER)
FROM clients;
