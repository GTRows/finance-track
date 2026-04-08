-- ============================================================
-- V4: Admin settings table for runtime configuration
-- ============================================================

CREATE TABLE admin_settings (
    key         VARCHAR(100) PRIMARY KEY,
    value       TEXT NOT NULL,
    description TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Pre-seed default settings
INSERT INTO admin_settings (key, value, description) VALUES
    ('log.max_total_size_gb',       '10',   'Maximum total size of log files in GB'),
    ('log.max_age_days',            '90',   'Maximum age of log files in days'),
    ('log.level.app',               'INFO', 'Application log level (DEBUG, INFO, WARN, ERROR)'),
    ('price_sync.interval_minutes', '5',    'How often to sync crypto and fund prices'),
    ('price_sync.forex_interval_minutes', '30', 'How often to sync forex rates');
