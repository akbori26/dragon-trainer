package com.akbori.solution.mugloar.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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
    private LocalDateTime timestamp = LocalDateTime.now();

    // Constructors, Getters, Setters
    public MissionLog() {}
    public MissionLog(String gameId, String adId, int reward, String riskCategory, boolean success, String serverMessage) {
        this.gameId = gameId; this.adId = adId; this.reward = reward; this.riskCategory = riskCategory; this.success = success; this.serverMessage = serverMessage;
    }
    public Long getId() { return id; }
    public String getGameId() { return gameId; }
    public String getAdId() { return adId; }
    public int getReward() { return reward; }
    public String getRiskCategory() { return riskCategory; }
    public boolean isSuccess() { return success; }
    public String getServerMessage() { return serverMessage; }
    public LocalDateTime getTimestamp() { return timestamp; }
}