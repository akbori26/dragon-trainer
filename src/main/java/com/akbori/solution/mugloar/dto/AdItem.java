package com.akbori.solution.mugloar.dto;

public record AdItem(
        String adId,
        String message,
        int reward,
        int expiresIn,
        String encrypted,    // always null
        String probability // e.g., "Sure thing", "Piece of cake", "Walk in the park", "Risky", "Suicide mission"

) {}