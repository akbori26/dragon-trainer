package com.akbori.solution.mugloar.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PlayerStatus {
    @Id private String gameId;
    private int lives;
    private int gold;
    private int score;
    private int highscore;

    // Constructors, Getters, Setters
    public PlayerStatus() {}
    public PlayerStatus(String gameId, int lives, int gold, int score, int highscore) {
        this.gameId = gameId; this.lives = lives; this.gold = gold; this.score = score; this.highscore = highscore;
    }
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public int getLives() { return lives; }
    public void setLives(int lives) { this.lives = lives; }
    public int getGold() { return gold; }
    public void setGold(int gold) { this.gold = gold; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getHighscore() { return highscore; }
    public void setHighscore(int highscore) { this.highscore = highscore; }
}