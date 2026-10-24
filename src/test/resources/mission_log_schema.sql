-- Mirrors the mission_log table in mugloar.db. Hibernate's generated DDL for SQLite
-- leaves "id" untyped, so it is not a rowid alias and inserted rows get a NULL id.
DROP TABLE IF EXISTS mission_log;
CREATE TABLE mission_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ad_id TEXT,
    game_id TEXT,
    reward INTEGER NOT NULL,
    risk_category TEXT,
    server_message TEXT,
    success INTEGER NOT NULL,
    timestamp TEXT,
    entry_type TEXT NOT NULL DEFAULT 'MISSION'
);
