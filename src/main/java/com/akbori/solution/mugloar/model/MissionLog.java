package com.akbori.solution.mugloar.model;

import jakarta.persistence.*;

/**
 * One entry in a game's history: a mission attempt or a shop purchase (see entryType).
 * For a purchase, adId holds the item ID and reward holds the gold spent.
 */
@Entity
public class MissionLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String gameId;
    private String adId;
    private int reward;
    private String riskCategory;
    private boolean success;
    private String serverMessage;
    private long timestamp = System.currentTimeMillis(); // epoch millis
    @Enumerated(EnumType.STRING)
    private EntryType entryType = EntryType.MISSION;

    // Constructors, Getters, Setters
    public MissionLog() {}
    public MissionLog(String gameId, String adId, int reward, String riskCategory, boolean success, String serverMessage) {
        this.gameId = gameId; this.adId = adId; this.reward = reward; this.riskCategory = riskCategory; this.success = success; this.serverMessage = serverMessage;
    }
    public MissionLog(EntryType entryType, String gameId, String adId, int reward, String riskCategory, boolean success, String serverMessage) {
        this(gameId, adId, reward, riskCategory, success, serverMessage);
        this.entryType = entryType;
    }
    public Long getId() { return id; }
    public String getGameId() { return gameId; }
    public String getAdId() { return adId; }
    public int getReward() { return reward; }
    public String getRiskCategory() { return riskCategory; }
    public boolean isSuccess() { return success; }
    public String getServerMessage() { return serverMessage; }
    public long getTimestamp() { return timestamp; }
    public EntryType getEntryType() { return entryType; }
}



