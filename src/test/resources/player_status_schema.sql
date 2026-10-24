-- Mirrors the player_status table in mugloar.db
DROP TABLE IF EXISTS player_status;
CREATE TABLE player_status (
    game_id TEXT NOT NULL,
    gold INTEGER NOT NULL,
    high_score INTEGER NOT NULL,
    lives INTEGER NOT NULL,
    score INTEGER NOT NULL,
    turn INTEGER NOT NULL,
    PRIMARY KEY (game_id)
);