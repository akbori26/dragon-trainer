-- DDL for mugloar.db, exported from the live database with `sqlite3 mugloar.db .schema`.
-- sqlite_sequence is left out: SQLite creates it itself for the AUTOINCREMENT column in mission_log.
-- To create an empty database, from the project root: sqlite3 data/mugloar.db < src/main/resources/data/mugloar_schema.sql

CREATE TABLE app_config (
    config_key TEXT NOT NULL,
    config_value TEXT,
    PRIMARY KEY (config_key)
);

CREATE TABLE mission_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ad_id TEXT,
    game_id TEXT,
    reward INTEGER NOT NULL,
    risk_category TEXT,
    server_message TEXT,
    success INTEGER NOT NULL, -- SQLite handles boolean flags as 0 or 1 integers
    timestamp TEXT            -- Mapped using ISO-8601 formatting text strings
);

CREATE TABLE player_status (
    game_id TEXT NOT NULL,
    gold INTEGER NOT NULL,
    high_score INTEGER NOT NULL,
    lives INTEGER NOT NULL,
    score INTEGER NOT NULL,
    turn INTEGER NOT NULL,
    level INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (game_id)
);

CREATE TABLE cached_ad (
    game_id TEXT NOT NULL,
    display_index INTEGER NOT NULL,
    ad_id TEXT, message TEXT,
    reward INTEGER NOT NULL, expires_in INTEGER NOT NULL,
    probability TEXT, risk_category TEXT, used INTEGER NOT NULL DEFAULT 0, fetched_turn INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (game_id, display_index)
);

CREATE TABLE cached_item (
    game_id TEXT NOT NULL,
    display_index INTEGER NOT NULL,
    item_id TEXT, name TEXT,
    cost INTEGER NOT NULL,
    PRIMARY KEY (game_id, display_index)
);
