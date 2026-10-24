package com.akbori.solution.mugloar.dto;

public record AdItem(
        String adId,
        String message,
        int reward,
        int expiresIn,
        String encrypted,    // null for plain ads; "1" = Base64, "2" = ROT13 on adId, message and probability
        String probability // e.g., "Sure thing", "Piece of cake", "Walk in the park", "Risky", "Suicide mission"

) {}