package com.akbori.solution.mugloar.model;

import com.akbori.solution.mugloar.dto.AdItem;
import com.akbori.solution.mugloar.dto.ShopItem;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;

import java.io.Serializable;

@Entity
@IdClass(CachedItem.Key.class)
public class
CachedItem {
    @Id private String gameId;
    @Id private int displayIndex;
    private String itemId;
    private String name;
    private int cost;

    // Composite primary key: (game_id, display_index)
    public record Key(String gameId, int displayIndex) implements Serializable {
        public Key() { this(null, 0); }
    }

    public CachedItem() {}
    public CachedItem(String gameId, int displayIndex, ShopItem shopItem) {
        this.gameId = gameId; this.displayIndex = displayIndex; this.itemId = shopItem.id(); this.name = shopItem.name();
        this.cost = shopItem.cost();
    }



    public String getGameId() { return gameId; }
    public int getDisplayIndex() { return displayIndex; }
    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public int getCost() { return cost; }

}
