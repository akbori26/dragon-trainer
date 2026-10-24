package com.akbori.solution.mugloar;

import com.akbori.solution.mugloar.dto.ShopItem;
import com.akbori.solution.mugloar.dto.TradeResponse;
import com.akbori.solution.mugloar.model.CachedItem;
import com.akbori.solution.mugloar.model.EntryType;
import com.akbori.solution.mugloar.model.MissionLog;
import com.akbori.solution.mugloar.model.PlayerStatus;
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
import java.util.Optional;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the private handleBuying() menu action, called through ReflectionTestUtils
 * so the runner's code does not need to change for testing. All collaborators are mocks.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MugloarCLIRunnerBuyingTest {

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

    /** Runs handleBuying() as if the user typed the given line at the index prompt. */
    private void buy(String userInput) {
        ReflectionTestUtils.invokeMethod(runner, "handleBuying", new Scanner(userInput + "\n"));
    }

    private void givenMarket(CachedItem... items) {
        when(marketRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of(items));
    }

    private static CachedItem item(int displayIndex, String id, String name, int cost) {
        return new CachedItem(GAME_ID, displayIndex, new ShopItem(id, name, cost));
    }

    @Test
    void successfulPurchaseLogsItUpdatesStatusAndShowsLevel(CapturedOutput output) {
        givenMarket(item(0, "hpot", "Healing potion", 50), item(1, "cs", "Claw Sharpening", 100));
        PlayerStatus status = new PlayerStatus(GAME_ID, 2, 120, 40, 40, 5);
        when(playerRepo.findById(GAME_ID)).thenReturn(Optional.of(status));
        when(mugloarService.buyItem(GAME_ID, "cs")).thenReturn(new TradeResponse(true, 20, 2, 1, 6));

        buy("2");

        ArgumentCaptor<MissionLog> log = ArgumentCaptor.forClass(MissionLog.class);
        verify(logRepo).save(log.capture());
        assertThat(log.getValue().getGameId()).isEqualTo(GAME_ID);
        assertThat(log.getValue().getAdId()).isEqualTo("cs");
        assertThat(log.getValue().getReward()).isEqualTo(100);
        assertThat(log.getValue().isSuccess()).isTrue();
        assertThat(log.getValue().getEntryType()).isEqualTo(EntryType.PURCHASE);
        assertThat(log.getValue().getServerMessage()).isEqualTo("Bought Claw Sharpening for 100 gold");

        verify(playerRepo).save(status);
        assertThat(status.getGold()).isEqualTo(20);
        assertThat(status.getLives()).isEqualTo(2);
        assertThat(status.getLevel()).isEqualTo(1);
        assertThat(status.getTurn()).isEqualTo(6);

        assertThat(output.getOut()).contains("SUCCESSFUL SHOPPING", "Dragon Level: ", "🏅 Level 1");
    }

    @Test
    void failedPurchaseIsStillLoggedAsUnsuccessful(CapturedOutput output) {
        givenMarket(item(0, "hpot", "Healing potion", 50));
        when(playerRepo.findById(GAME_ID)).thenReturn(Optional.of(new PlayerStatus(GAME_ID, 3, 50, 0, 0, 1)));
        when(mugloarService.buyItem(GAME_ID, "hpot")).thenReturn(new TradeResponse(false, 50, 3, 0, 2));

        buy("1");

        ArgumentCaptor<MissionLog> log = ArgumentCaptor.forClass(MissionLog.class);
        verify(logRepo).save(log.capture());
        assertThat(log.getValue().isSuccess()).isFalse();
        assertThat(log.getValue().getEntryType()).isEqualTo(EntryType.PURCHASE);
        assertThat(log.getValue().getServerMessage()).isEqualTo("Could not buy Healing potion for 50 gold");
        assertThat(output.getOut()).contains("FAILURE");
    }

    @Test
    void itemTooExpensiveIsNotBought(CapturedOutput output) {
        givenMarket(item(0, "ch", "Claw Honing", 300));
        when(playerRepo.findById(GAME_ID)).thenReturn(Optional.of(new PlayerStatus(GAME_ID, 3, 299, 0, 0, 4)));

        buy("1");

        verify(mugloarService, never()).buyItem(anyString(), anyString());
        verify(logRepo, never()).save(any());
        verify(playerRepo, never()).save(any());
        assertThat(output.getOut()).contains("so expensive for your budget", "You have only 299 Gold");
    }

    @Test
    void serverErrorLeavesLogAndStatusUntouched() {
        givenMarket(item(0, "hpot", "Healing potion", 50));
        when(playerRepo.findById(GAME_ID)).thenReturn(Optional.of(new PlayerStatus(GAME_ID, 3, 100, 0, 0, 1)));
        // The service returns null after printing the HTTP error itself
        when(mugloarService.buyItem(GAME_ID, "hpot")).thenReturn(null);

        buy("1");

        verify(logRepo, never()).save(any());
        verify(playerRepo, never()).save(any());
    }

    @Test
    void indexOutOfRangeIsRejected(CapturedOutput output) {
        givenMarket(item(0, "hpot", "Healing potion", 50));

        buy("2");

        verify(mugloarService, never()).buyItem(anyString(), anyString());
        assertThat(output.getOut()).contains("Invalid number index entry");
    }

    @Test
    void nonNumericIndexIsRejected(CapturedOutput output) {
        givenMarket(item(0, "hpot", "Healing potion", 50));

        buy("abc");

        verify(mugloarService, never()).buyItem(anyString(), anyString());
        assertThat(output.getOut()).contains("Invalid number index entry");
    }

    @Test
    void emptyMarketPointsUserToMarketOption(CapturedOutput output) {
        givenMarket();

        buy("1");

        verify(mugloarService, never()).buyItem(anyString(), anyString());
        assertThat(output.getOut()).contains("Run action [8] to open the market first");
    }

    @Test
    void missingPlayerStatusStopsThePurchase(CapturedOutput output) {
        givenMarket(item(0, "hpot", "Healing potion", 50));
        when(playerRepo.findById(GAME_ID)).thenReturn(Optional.empty());

        buy("1");

        verify(mugloarService, never()).buyItem(anyString(), anyString());
        assertThat(output.getOut()).contains("there is no status for your dragon");
    }

    @Test
    void noActiveSessionDoesNothing(CapturedOutput output) {
        ReflectionTestUtils.setField(runner, "currentGameId", null);

        buy("1");

        verify(marketRepo, never()).findByGameIdOrderByDisplayIndexAsc(any());
        assertThat(output.getOut()).contains("Access Denied");
    }
}