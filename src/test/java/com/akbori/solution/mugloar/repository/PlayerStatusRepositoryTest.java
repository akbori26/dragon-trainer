package com.akbori.solution.mugloar.repository;

import com.akbori.solution.mugloar.model.PlayerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

// Runs against a throwaway in-memory SQLite database so the real mugloar.db is never touched
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:playerstatustest?mode=memory&cache=shared",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:player_status_schema.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlayerStatusRepositoryTest {

    @Autowired
    private PlayerStatusRepository playerStatusRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savedStatusIsFoundByGameIdWithAllFields() {
        playerStatusRepository.save(new PlayerStatus("game-1", 3, 50, 120, 300, 7));

        // Clear the persistence context so the next read comes from the database, not the cache
        entityManager.flush();
        entityManager.clear();

        PlayerStatus status = playerStatusRepository.findById("game-1").orElseThrow();

        assertThat(status.getGameId()).isEqualTo("game-1");
        assertThat(status.getLives()).isEqualTo(3);
        assertThat(status.getGold()).isEqualTo(50);
        assertThat(status.getScore()).isEqualTo(120);
        assertThat(status.getHighScore()).isEqualTo(300);
        assertThat(status.getTurn()).isEqualTo(7);
    }

    @Test
    void updatingAnExistingStatusKeepsOneRowPerGame() {
        playerStatusRepository.save(new PlayerStatus("game-1", 3, 0, 0, 0, 0));
        entityManager.flush();
        entityManager.clear();

        // Same read-modify-save sequence the CLI runner uses after solving an ad
        PlayerStatus status = playerStatusRepository.findById("game-1").orElseThrow();
        status.setLives(2);
        status.setGold(25);
        status.setScore(40);
        status.setTurn(1);
        playerStatusRepository.save(status);
        entityManager.flush();
        entityManager.clear();

        PlayerStatus reloaded = playerStatusRepository.findById("game-1").orElseThrow();

        assertThat(playerStatusRepository.count()).isEqualTo(1);
        assertThat(reloaded.getLives()).isEqualTo(2);
        assertThat(reloaded.getGold()).isEqualTo(25);
        assertThat(reloaded.getScore()).isEqualTo(40);
        assertThat(reloaded.getTurn()).isEqualTo(1);
    }

    @Test
    void findByIdReturnsEmptyForUnknownGame() {
        assertThat(playerStatusRepository.findById("missing-game")).isEmpty();
    }

    @Test
    void findTopByOrderByScoreDescReturnsTheBestGame() {
        playerStatusRepository.save(new PlayerStatus("low", 3, 0, 120, 0, 5));
        playerStatusRepository.save(new PlayerStatus("best", 1, 0, 1378, 0, 30));
        playerStatusRepository.save(new PlayerStatus("mid", 2, 0, 640, 0, 12));

        assertThat(playerStatusRepository.findTopByOrderByScoreDesc())
                .get().extracting(PlayerStatus::getGameId).isEqualTo("best");
    }
}