package com.akbori.solution.mugloar.model;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AppConfig {
    @Id private String configKey;
    private String configValue;

    public AppConfig() {}
    public AppConfig(String configKey, String configValue) {
        this.configKey = configKey;
        this.configValue = configValue;
    }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
}