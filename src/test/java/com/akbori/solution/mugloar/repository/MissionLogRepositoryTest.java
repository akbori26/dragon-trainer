package com.akbori.solution.mugloar.repository;

import com.akbori.solution.mugloar.model.MissionLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Runs against a throwaway in-memory SQLite database so the real mugloar.db is never touched
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:missionlogtest?mode=memory&cache=shared",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:mission_log_schema.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MissionLogRepositoryTest {

    @Autowired
    private MissionLogRepository missionLogRepository;

    @Test
    void findByGameIdOrderByIdDescReturnsOnlyThatGamesLogsNewestFirst() {
        MissionLog first = missionLogRepository.save(
                new MissionLog("game-1", "ad-1", 10, "Low", true, "You successfully solved the mission!"));
        MissionLog second = missionLogRepository.save(
                new MissionLog("game-1", "ad-2", 25, "High", false, "You were defeated"));
        missionLogRepository.save(
                new MissionLog("game-2", "ad-3", 5, "Low", true, "Another game's mission"));

        List<MissionLog> logs = missionLogRepository.findByGameIdOrderByIdDesc("game-1");

        assertThat(logs).extracting(MissionLog::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(logs).extracting(MissionLog::getGameId).containsOnly("game-1");
        assertThat(logs.get(0).getAdId()).isEqualTo("ad-2");
        assertThat(logs.get(0).isSuccess()).isFalse();
    }
}