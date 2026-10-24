package com.akbori.solution.mugloar.repository;

import com.akbori.solution.mugloar.dto.AdItem;
import com.akbori.solution.mugloar.model.CachedAd;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Runs against a throwaway in-memory SQLite database so the real mugloar.db is never touched
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:cachedadtest?mode=memory&cache=shared",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:cached_ad_schema.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CachedAdRepositoryTest {

    @Autowired
    private CachedAdRepository cachedAdRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static CachedAd cachedAd(String gameId, int displayIndex, String adId) {
        AdItem ad = new AdItem(adId, "Help defend the village", 10, 7, null, "Sure thing");
        return new CachedAd(gameId, displayIndex, ad, "Low", 0);
    }

    @Test
    void findByGameIdOrderByDisplayIndexAscReturnsOnlyThatGamesAdsInBoardOrder() {
        cachedAdRepository.saveAll(List.of(
                cachedAd("game-1", 3, "ad-3"),
                cachedAd("game-1", 1, "ad-1"),
                cachedAd("game-2", 1, "other-game-ad"),
                cachedAd("game-1", 2, "ad-2")));

        List<CachedAd> ads = cachedAdRepository.findByGameIdOrderByDisplayIndexAsc("game-1");

        assertThat(ads).extracting(CachedAd::getDisplayIndex).containsExactly(1, 2, 3);
        assertThat(ads).extracting(CachedAd::getAdId).containsExactly("ad-1", "ad-2", "ad-3");
    }

    @Test
    void deleteByGameIdRemovesOnlyThatGamesAds() {
        cachedAdRepository.saveAll(List.of(
                cachedAd("game-1", 1, "ad-1"),
                cachedAd("game-1", 2, "ad-2"),
                cachedAd("game-2", 1, "other-game-ad")));

        cachedAdRepository.deleteByGameId("game-1");

        assertThat(cachedAdRepository.findByGameIdOrderByDisplayIndexAsc("game-1")).isEmpty();
        assertThat(cachedAdRepository.findByGameIdOrderByDisplayIndexAsc("game-2"))
                .extracting(CachedAd::getAdId).containsExactly("other-game-ad");
    }

    @Test
    void savingAnAdMarkedUsedPersistsTheFlagUnderItsCompositeKey() {
        CachedAd ad = cachedAdRepository.save(cachedAd("game-1", 1, "ad-1"));
        ad.setUsed(true);
        cachedAdRepository.save(ad);

        // Clear the persistence context so the next read comes from the database, not the cache
        entityManager.flush();
        entityManager.clear();

        CachedAd reloaded = cachedAdRepository.findById(new CachedAd.Key("game-1", 1)).orElseThrow();

        assertThat(reloaded.isUsed()).isTrue();
        assertThat(reloaded.getAdId()).isEqualTo("ad-1");
        assertThat(reloaded.getProbability()).isEqualTo("Sure thing");
        assertThat(cachedAdRepository.count()).isEqualTo(1);
    }
}