-- Mirrors the cached_item table in mugloar.db
DROP TABLE IF EXISTS cached_item;
CREATE TABLE cached_item (
    game_id TEXT NOT NULL,
    display_index INTEGER NOT NULL,
    item_id TEXT, name TEXT,
    cost INTEGER NOT NULL,
    PRIMARY KEY (game_id, display_index)
);
