package com.akbori.solution.mugloar.dto;


/**
 * Maps the execution results after submitting a mission solution.
 */
public record TradeResponse(
        boolean shoppingSuccess,
        int gold,
        int lives,
        int level,
        int turn
) {}


