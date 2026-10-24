package com.akbori.solution.mugloar.repository;

import com.akbori.solution.mugloar.model.AppConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

// Runs against a throwaway in-memory SQLite database so the real mugloar.db is never touched
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:appconfigtest?mode=memory&cache=shared",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:app_config_schema.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AppConfigRepositoryTest {

    @Autowired
    private AppConfigRepository appConfigRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savedConfigIsFoundByItsKey() {
        appConfigRepository.save(new AppConfig("LAST_GAME_ID", "game-1"));

        // Clear the persistence context so the next read comes from the database, not the cache
        entityManager.flush();
        entityManager.clear();

        assertThat(appConfigRepository.findById("LAST_GAME_ID"))
                .map(AppConfig::getConfigValue)
                .hasValue("game-1");
    }

    @Test
    void savingTheSameKeyAgainOverwritesTheValue() {
        appConfigRepository.save(new AppConfig("LAST_GAME_ID", "game-1"));
        entityManager.flush();
        entityManager.clear();

        appConfigRepository.save(new AppConfig("LAST_GAME_ID", "game-2"));
        entityManager.flush();
        entityManager.clear();

        assertThat(appConfigRepository.count()).isEqualTo(1);
        assertThat(appConfigRepository.findById("LAST_GAME_ID"))
                .map(AppConfig::getConfigValue)
                .hasValue("game-2");
    }

    @Test
    void findByIdReturnsEmptyForUnknownKey() {
        assertThat(appConfigRepository.findById("LAST_GAME_ID")).isEmpty();
    }
}