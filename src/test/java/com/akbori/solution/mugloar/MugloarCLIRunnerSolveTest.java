package com.akbori.solution.mugloar;

import com.akbori.solution.mugloar.dto.AdItem;
import com.akbori.solution.mugloar.dto.GameStartResponse;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * handleSolve() must always pick from the current game's board in the database,
 * never from a board left in memory by an earlier game.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MugloarCLIRunnerSolveTest {

    private static final String OLD_GAME = "game-A";
    private static final String NEW_GAME = "game-B";

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
    }

    private static CachedAd ad(String gameId, String adId) {
        return new CachedAd(gameId, 0, new AdItem(adId, "Help a farmer", 20, 5, null, "Piece of cake"), "🟢 SAFE", 0);
    }

    private void solve(String userInput) {
        ReflectionTestUtils.invokeMethod(runner, "handleSolve", new Scanner(userInput + "\n"));
    }

    @Test
    void newGameDoesNotReuseThePreviousGamesBoard() {
        // Game A: the board is viewed, as a user would before starting over
        ReflectionTestUtils.setField(runner, "currentGameId", OLD_GAME);
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(OLD_GAME)).thenReturn(List.of(ad(OLD_GAME, "old-ad")));
        ReflectionTestUtils.invokeMethod(runner, "handleBoard");

        // Start game B, which has its own board stored
        when(mugloarService.startNewGame()).thenReturn(new GameStartResponse(NEW_GAME, 3, 0, 0, 0, 0, 0));
        ReflectionTestUtils.invokeMethod(runner, "handleStart");
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(NEW_GAME)).thenReturn(List.of(ad(NEW_GAME, "new-ad")));
        when(playerRepo.findById(NEW_GAME)).thenReturn(Optional.of(new PlayerStatus(NEW_GAME, 3, 0, 0, 0, 0)));
        when(mugloarService.solveAd(NEW_GAME, "new-ad")).thenReturn(new SolutionResponse(true, 3, 20, 20, 20, 1, "Done"));

        solve("1");

        verify(mugloarService).solveAd(NEW_GAME, "new-ad");
        verify(mugloarService, never()).solveAd(anyString(), eq("old-ad"));
    }

    @Test
    void solveWorksRightAfterResumeWithoutViewingTheBoard() {
        // Resume only sets the game ID; View Board has not been opened
        ReflectionTestUtils.setField(runner, "currentGameId", NEW_GAME);
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(NEW_GAME)).thenReturn(List.of(ad(NEW_GAME, "new-ad")));
        when(playerRepo.findById(NEW_GAME)).thenReturn(Optional.of(new PlayerStatus(NEW_GAME, 3, 0, 0, 0, 0)));
        when(mugloarService.solveAd(NEW_GAME, "new-ad")).thenReturn(new SolutionResponse(true, 3, 20, 20, 20, 1, "Done"));

        solve("1");

        verify(mugloarService).solveAd(NEW_GAME, "new-ad");
    }

    // ad() is downloaded at turn 0 with expiresIn 5, so it has 0 turns left at turn 5.
    // Checked against the live server: an ad is still listed with 1 turn left and rejected (HTTP 400) at 0.

    @Test
    void expiredAdIsNotSentToTheServer(CapturedOutput output) {
        ReflectionTestUtils.setField(runner, "currentGameId", NEW_GAME);
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(NEW_GAME)).thenReturn(List.of(ad(NEW_GAME, "new-ad")));
        when(playerRepo.findById(NEW_GAME)).thenReturn(Optional.of(new PlayerStatus(NEW_GAME, 3, 0, 0, 0, 5)));

        solve("1");

        verify(mugloarService, never()).solveAd(anyString(), anyString());
        assertThat(output.getOut()).contains("Ad ID new-ad has expired", "run action [7] to download a new board");
    }

    @Test
    void adWithOneTurnLeftCanStillBeSolved() {
        ReflectionTestUtils.setField(runner, "currentGameId", NEW_GAME);
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(NEW_GAME)).thenReturn(List.of(ad(NEW_GAME, "new-ad")));
        when(playerRepo.findById(NEW_GAME)).thenReturn(Optional.of(new PlayerStatus(NEW_GAME, 3, 0, 0, 0, 4)));
        when(mugloarService.solveAd(NEW_GAME, "new-ad")).thenReturn(new SolutionResponse(true, 3, 20, 20, 20, 5, "Done"));

        solve("1");

        verify(mugloarService).solveAd(NEW_GAME, "new-ad");
    }

    @Test
    void emptyStoredBoardPointsUserToDownloadAds(CapturedOutput output) {
        ReflectionTestUtils.setField(runner, "currentGameId", NEW_GAME);
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(NEW_GAME)).thenReturn(List.of());

        solve("1");

        verify(mugloarService, never()).solveAd(anyString(), anyString());
        assertThat(output.getOut()).contains("Run action [7] to download them");
    }
}