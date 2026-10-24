-- Mirrors the cached_ad table in mugloar.db
DROP TABLE IF EXISTS cached_ad;
CREATE TABLE cached_ad (
    game_id TEXT NOT NULL,
    display_index INTEGER NOT NULL,
    ad_id TEXT, message TEXT,
    reward INTEGER NOT NULL, expires_in INTEGER NOT NULL,
    probability TEXT, risk_category TEXT, used INTEGER NOT NULL DEFAULT 0, fetched_turn INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (game_id, display_index)
);