package com.akbori.solution.mugloar.repository;

import com.akbori.solution.mugloar.model.PlayerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerStatusRepository extends JpaRepository<PlayerStatus, String> {

    // The server always sends highScore 0, so the high score is the best score across all stored games
    Optional<PlayerStatus> findTopByOrderByScoreDesc();
}