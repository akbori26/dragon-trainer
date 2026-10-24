package com.akbori.solution.mugloar;

import com.akbori.solution.mugloar.dto.GameStartResponse;
import com.akbori.solution.mugloar.dto.ReputationResponse;
import com.akbori.solution.mugloar.model.AppConfig;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Start Game (menu 1) and Reputation (menu 6) on the success path.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MugloarCLIRunnerStartAndReputationTest {

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
    }

    // --- Start Game ---

    @Test
    void startSavesTheNewGameAndMakesItCurrent(CapturedOutput output) {
        ReflectionTestUtils.setField(runner, "currentGameId", "old-game");
        when(mugloarService.startNewGame()).thenReturn(new GameStartResponse("new-game", 3, 0, 0, 0, 0, 0));

        ReflectionTestUtils.invokeMethod(runner, "handleStart");

        assertThat(ReflectionTestUtils.getField(runner, "currentGameId")).isEqualTo("new-game");

        ArgumentCaptor<PlayerStatus> status = ArgumentCaptor.forClass(PlayerStatus.class);
        verify(playerRepo).save(status.capture());
        assertThat(status.getValue().getGameId()).isEqualTo("new-game");
        assertThat(status.getValue().getLives()).isEqualTo(3);
        assertThat(status.getValue().getTurn()).isZero();

        // LAST_GAME_ID is what the next start-up resumes
        ArgumentCaptor<AppConfig> config = ArgumentCaptor.forClass(AppConfig.class);
        verify(configRepo).save(config.capture());
        assertThat(config.getValue().getConfigValue()).isEqualTo("new-game");

        assertThat(output.getOut()).contains("New run established and recorded!");
    }

    // --- Reputation ---

    @Test
    void reputationPrintsTheReportAndCountsATurn(CapturedOutput output) {
        ReflectionTestUtils.setField(runner, "currentGameId", GAME_ID);
        PlayerStatus status = new PlayerStatus(GAME_ID, 3, 0, 0, 0, 6);
        when(playerRepo.findById(GAME_ID)).thenReturn(Optional.of(status));
        when(mugloarService.investigateReputation(GAME_ID)).thenReturn(new ReputationResponse(2.5, -1.0, 0.0));

        ReflectionTestUtils.invokeMethod(runner, "handleReputation");

        // The response has no turn, so the stored turn is moved on by one locally
        verify(playerRepo).save(status);
        assertThat(status.getTurn()).isEqualTo(7);
        assertThat(output.getOut()).contains("REPUTATION REPORT", "| People            | 2.5", "| State             | -1.0",
                "| Underworld        | 0.0");
    }

    @Test
    void failedReputationCallDoesNotCountATurn() {
        ReflectionTestUtils.setField(runner, "currentGameId", GAME_ID);
        when(playerRepo.findById(GAME_ID)).thenReturn(Optional.of(new PlayerStatus(GAME_ID, 3, 0, 0, 0, 6)));
        // The service has already printed the HTTP error and returns null
        when(mugloarService.investigateReputation(GAME_ID)).thenReturn(null);

        ReflectionTestUtils.invokeMethod(runner, "handleReputation");

        verify(playerRepo, never()).save(any());
    }
}