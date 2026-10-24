package com.akbori.solution.mugloar;

import com.akbori.solution.mugloar.dto.AdItem;
import com.akbori.solution.mugloar.model.CachedAd;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A failing action must end only that action: handleCommand() reports the error
 * and returns, so the menu loop keeps running.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MugloarCLIRunnerErrorHandlingTest {

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

    private void command(String command) {
        ReflectionTestUtils.invokeMethod(runner, "handleCommand", command, new Scanner(""));
    }

    @Test
    void networkOutageIsReportedInsteadOfCrashing(CapturedOutput output) {
        when(mugloarService.getMessageBoard(GAME_ID)).thenThrow(new ResourceAccessException("I/O error: Connection refused"));

        assertThatCode(() -> command("7")).doesNotThrowAnyException();

        assertThat(output.getOut()).contains("Could not reach the Mugloar server");
    }

    @Test
    void unreadableServerResponseIsReportedInsteadOfCrashing(CapturedOutput output) {
        when(mugloarService.investigateReputation(GAME_ID)).thenThrow(new RestClientException("Could not extract response"));

        assertThatCode(() -> command("6")).doesNotThrowAnyException();

        assertThat(output.getOut()).contains("could not be read", "Could not extract response");
    }

    @Test
    void databaseErrorIsReportedInsteadOfCrashing(CapturedOutput output) {
        when(logRepo.findByGameIdOrderByIdDesc(GAME_ID)).thenThrow(new IllegalStateException("database is locked"));

        assertThatCode(() -> command("5")).doesNotThrowAnyException();

        assertThat(output.getOut()).contains("The action failed: database is locked");
    }

    @Test
    void failedGameStartKeepsTheCurrentGame() {
        // The service has already printed the HTTP error and returns null
        when(mugloarService.startNewGame()).thenReturn(null);

        command("1");

        assertThat(ReflectionTestUtils.getField(runner, "currentGameId")).isEqualTo(GAME_ID);
        verify(playerRepo, never()).save(any());
        verify(configRepo, never()).save(any());
    }

    @Test
    void unknownCommandPrintsHelp(CapturedOutput output) {
        command("42");

        assertThat(output.getOut()).contains("Invalid option");
    }

    // --- Input ending (Ctrl+D, end of a piped script) ---

    /** Runs the whole menu loop with the given text as keyboard input. */
    private void runWithInput(String input) throws Exception {
        InputStream original = System.in;
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            runner.run();
        } finally {
            System.setIn(original);
        }
    }

    @Test
    void menuExitsCleanlyWhenInputEnds(CapturedOutput output) {
        assertThatCode(() -> runWithInput("")).doesNotThrowAnyException();

        assertThat(output.getOut()).contains("Input closed. Goodbye!");
    }

    @Test
    void inputEndingAtAnIndexPromptEndsTheMenuCleanly(CapturedOutput output) {
        AdItem ad = new AdItem("ad-1", "Help a farmer", 20, 5, null, "Piece of cake");
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of(new CachedAd(GAME_ID, 0, ad, "🟢 SAFE", 0)));

        // "3" opens Solve Ad, then the input ends before an index is typed
        assertThatCode(() -> runWithInput("3\n")).doesNotThrowAnyException();

        verify(mugloarService, never()).solveAd(any(), any());
        assertThat(output.getOut()).contains("Input closed before an index was entered.", "Input closed. Goodbye!");
    }
}