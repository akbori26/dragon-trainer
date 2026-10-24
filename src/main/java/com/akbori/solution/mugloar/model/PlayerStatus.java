package com.akbori.solution.mugloar.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PlayerStatus {
    @Id private String gameId;
    private int lives;
    private int gold;
    private int score;
    private int highScore;
    private int turn;
    private int level;

    // Constructors, Getters, Setters
    public PlayerStatus() {}
    public PlayerStatus(String gameId, int lives, int gold, int score, int highScore, int turn) {
        this.gameId = gameId; this.lives = lives; this.gold = gold; this.score = score; this.highScore = highScore;
        this.turn = turn;
    }
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public int getLives() { return lives; }
    public void setLives(int lives) { this.lives = lives; }
    public int getGold() { return gold; }
    public void setGold(int gold) { this.gold = gold; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getHighScore() { return highScore; }
    public void setHighScore(int highScore) { this.highScore = highScore; }
    public int getTurn() {
        return turn;
    }
    public void setTurn(int turn) {
        this.turn = turn;
    }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

}