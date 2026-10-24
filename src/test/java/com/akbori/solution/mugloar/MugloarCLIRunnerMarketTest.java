package com.akbori.solution.mugloar;

import com.akbori.solution.mugloar.dto.ShopItem;
import com.akbori.solution.mugloar.model.CachedItem;
import com.akbori.solution.mugloar.repository.AppConfigRepository;
import com.akbori.solution.mugloar.repository.CachedAdRepository;
import com.akbori.solution.mugloar.repository.CachedItemRepository;
import com.akbori.solution.mugloar.repository.MissionLogRepository;
import com.akbori.solution.mugloar.repository.PlayerStatusRepository;
import com.akbori.solution.mugloar.service.MugloarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Market (menu 8) downloads the shop once per game and reads it from the database after that.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MugloarCLIRunnerMarketTest {

    private static final String GAME_ID = "game-1";

    @Mock private MugloarService mugloarService;
    @Mock private PlayerStatusRepository playerRepo;
    @Mock private MissionLogRepository logRepo;
    @Mock private AppConfigRepository configRepo;
    @Mock private CachedAdRepository adRepo;
    @Mock private CachedItemRepository marketRepo;

    private MugloarCLIRunner runner;

    @BeforeEach
    void setUp() {
        runner = new MugloarCLIRunner(mugloarService, playerRepo, logRepo, configRepo, adRepo, marketRepo);
        ReflectionTestUtils.setField(runner, "currentGameId", GAME_ID);
    }

    private void openMarket() {
        ReflectionTestUtils.invokeMethod(runner, "handleMarketBoard");
    }

    @Test
    @SuppressWarnings("unchecked")
    void firstVisitDownloadsAndStoresTheShop(CapturedOutput output) {
        when(marketRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of());
        when(mugloarService.getMarketItems(GAME_ID)).thenReturn(List.of(
                new ShopItem("hpot", "Healing potion", 50), new ShopItem("cs", "Claw Sharpening", 100)));

        openMarket();

        ArgumentCaptor<List<CachedItem>> saved = ArgumentCaptor.forClass(List.class);
        verify(marketRepo).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(CachedItem::getGameId).containsOnly(GAME_ID);
        assertThat(saved.getValue()).extracting(CachedItem::getDisplayIndex).containsExactly(0, 1);
        assertThat(saved.getValue()).extracting(CachedItem::getItemId).containsExactly("hpot", "cs");
        assertThat(saved.getValue()).extracting(CachedItem::getCost).containsExactly(50, 100);
        assertThat(output.getOut()).contains("Stored 2 market items.", "Healing potion", "Claw Sharpening");
    }

    @Test
    void laterVisitsReadTheStoredShopWithoutCallingTheServer(CapturedOutput output) {
        when(marketRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of(
                new CachedItem(GAME_ID, 0, new ShopItem("hpot", "Healing potion", 50))));

        openMarket();

        verify(mugloarService, never()).getMarketItems(anyString());
        verify(marketRepo, never()).saveAll(anyList());
        assertThat(output.getOut()).contains("hpot", "Healing potion", "50").doesNotContain("Downloading market items");
    }

    @Test
    void emptyShopFromTheServerStoresNothing(CapturedOutput output) {
        when(marketRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of());
        when(mugloarService.getMarketItems(GAME_ID)).thenReturn(List.of());

        openMarket();

        verify(marketRepo, never()).saveAll(anyList());
        assertThat(output.getOut()).contains("No market items available for this game.");
    }

    @Test
    void marketTableRowsAllHaveTheSameWidth(CapturedOutput output) {
        when(marketRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of(
                new CachedItem(GAME_ID, 0, new ShopItem("hpot", "Healing potion", 50)),
                new CachedItem(GAME_ID, 1, new ShopItem("wingpot", "Potion of Stronger Wings", 100))));

        openMarket();

        List<String> table = output.getOut().lines()
                .filter(line -> line.startsWith("|") || line.startsWith("+"))
                .toList();
        assertThat(table).hasSize(6);
        assertThat(table).extracting(String::length).containsOnly(table.get(0).length());
    }
}