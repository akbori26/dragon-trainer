package com.akbori.solution.mugloar.dto;


/**
 * Maps the properties returned from the /game/start endpoint.
 *
 */
public record GameStartResponse(
        String gameId,
        int lives,
        int gold,
        int level,
        int score,
        int highScore,
        int turn
) {}