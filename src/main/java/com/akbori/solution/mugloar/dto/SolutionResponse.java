package com.akbori.solution.mugloar.dto;


/**
 * Maps the execution results after submitting a mission solution.
 */
public record SolutionResponse(
        boolean success,
        int lives,
        int gold,
        int score,
        int highScore,
        int turn,
        String message
) {}
