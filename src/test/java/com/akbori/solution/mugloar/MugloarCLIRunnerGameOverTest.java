package com.akbori.solution.mugloar;

import com.akbori.solution.mugloar.dto.AdItem;
import com.akbori.solution.mugloar.dto.SolutionResponse;
import com.akbori.solution.mugloar.model.CachedAd;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Game-over handling: actions that call the server are blocked at 0 lives,
 * and a warning is printed when the dragon is down to 1 life.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MugloarCLIRunnerGameOverTest {

    private static final String GAME_ID = "game-1";
    private static final String GAME_OVER = "GAME OVER";
    private static final String ONE_LIFE = "Only 1 life left!";

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

    private void givenLives(int lives) {
        when(playerRepo.findById(GAME_ID)).thenReturn(Optional.of(new PlayerStatus(GAME_ID, lives, 100, 0, 0, 5)));
    }

    private void givenBoardWithOneAd() {
        AdItem ad = new AdItem("ad-1", "Help a farmer", 20, 5, null, "Piece of cake");
        // lenient: the game-over tests stop before the board is read
        lenient().when(adRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID))
                .thenReturn(List.of(new CachedAd(GAME_ID, 0, ad, "🟢 SAFE", 5)));
    }

    // --- Blocked at 0 lives ---

    @Test
    void solveIsBlockedWhenDragonIsDead(CapturedOutput output) {
        givenLives(0);
        givenBoardWithOneAd();

        ReflectionTestUtils.invokeMethod(runner, "handleSolve", new Scanner("1\n"));

        verify(mugloarService, never()).solveAd(anyString(), anyString());
        assertThat(output.getOut()).contains(GAME_OVER);
    }

    @Test
    void buyIsBlockedWhenDragonIsDead(CapturedOutput output) {
        givenLives(0);

        ReflectionTestUtils.invokeMethod(runner, "handleBuying", new Scanner("1\n"));

        verifyNoInteractions(mugloarService, marketRepo);
        assertThat(output.getOut()).contains(GAME_OVER);
    }

    @Test
    void downloadAdsIsBlockedWhenDragonIsDead(CapturedOutput output) {
        givenLives(0);

        ReflectionTestUtils.invokeMethod(runner, "handleDownloadAds");

        verifyNoInteractions(mugloarService, adRepo);
        assertThat(output.getOut()).contains(GAME_OVER);
    }

    @Test
    void reputationIsBlockedWhenDragonIsDead(CapturedOutput output) {
        givenLives(0);

        ReflectionTestUtils.invokeMethod(runner, "handleReputation");

        verifyNoInteractions(mugloarService);
        assertThat(output.getOut()).contains(GAME_OVER);
    }

    @Test
    void statusIsStillShownWhenDragonIsDead(CapturedOutput output) {
        givenLives(0);

        ReflectionTestUtils.invokeMethod(runner, "handleStatus");

        assertThat(output.getOut()).contains("(DEAD)", GAME_OVER);
    }

    // --- Warnings after an action ---

    @Test
    void solveThatLeavesOneLifePrintsLowHealthWarning(CapturedOutput output) {
        givenLives(2);
        givenBoardWithOneAd();
        when(mugloarService.solveAd(GAME_ID, "ad-1"))
                .thenReturn(new SolutionResponse(false, 1, 100, 0, 0, 6, "You were ambushed"));

        ReflectionTestUtils.invokeMethod(runner, "handleSolve", new Scanner("1\n"));

        assertThat(output.getOut()).contains(ONE_LIFE).doesNotContain(GAME_OVER);
    }

    @Test
    void solveThatLosesLastLifePrintsGameOver(CapturedOutput output) {
        givenLives(1);
        givenBoardWithOneAd();
        when(mugloarService.solveAd(GAME_ID, "ad-1"))
                .thenReturn(new SolutionResponse(false, 0, 100, 0, 0, 6, "Your dragon fell"));

        ReflectionTestUtils.invokeMethod(runner, "handleSolve", new Scanner("1\n"));

        verify(playerRepo).save(any());
        assertThat(output.getOut()).contains(GAME_OVER);
    }

    @Test
    void statusShowsTheBestScoreAcrossAllStoredGamesAsHighScore(CapturedOutput output) {
        // The current game scored 40, while an older game reached 1378; the server's highScore is always 0
        when(playerRepo.findById(GAME_ID)).thenReturn(Optional.of(new PlayerStatus(GAME_ID, 3, 100, 40, 0, 5)));
        when(playerRepo.findTopByOrderByScoreDesc()).thenReturn(Optional.of(new PlayerStatus("old-game", 0, 0, 1378, 0, 30)));

        ReflectionTestUtils.invokeMethod(runner, "handleStatus");

        assertThat(output.getOut()).contains("| Dynamic Score     | 40 ", "| Top Record Tier   | 1378 ");
    }

    @Test
    void statusWithOneLifePrintsLowHealthWarning(CapturedOutput output) {
        givenLives(1);

        ReflectionTestUtils.invokeMethod(runner, "handleStatus");

        assertThat(output.getOut()).contains(ONE_LIFE);
    }

    @Test
    void healthyDragonGetsNoWarning(CapturedOutput output) {
        givenLives(3);

        ReflectionTestUtils.invokeMethod(runner, "handleStatus");

        assertThat(output.getOut()).doesNotContain(ONE_LIFE, GAME_OVER);
    }

    @Test
    void statusTableRowsAllHaveTheSameWidth(CapturedOutput output) {
        givenLives(3);

        ReflectionTestUtils.invokeMethod(runner, "handleStatus");

        // Every border and row of the table is padded to the border's width; printf pads by
        // String.length(), so comparing lengths catches a row whose format string is off.
        // ANSI color codes take no space on screen, so they are removed before measuring.
        List<String> table = output.getOut().lines()
                .map(line -> line.replaceAll("\u001B\\[[;\\d]*m", ""))
                .filter(line -> line.startsWith("|") || line.startsWith("+"))
                .toList();
        assertThat(table).hasSizeGreaterThan(5);
        assertThat(table).extracting(String::length).containsOnly(table.get(0).length());
        assertThat(table).anyMatch(line -> line.startsWith("| Turn in the game  | 5 "));
    }
}