-- Mirrors the app_config table in mugloar.db
DROP TABLE IF EXISTS app_config;
CREATE TABLE app_config (
    config_key TEXT NOT NULL,
    config_value TEXT,
    PRIMARY KEY (config_key)
);