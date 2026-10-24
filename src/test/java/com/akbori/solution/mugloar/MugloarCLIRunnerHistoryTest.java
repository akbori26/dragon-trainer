package com.akbori.solution.mugloar;

import com.akbori.solution.mugloar.model.EntryType;
import com.akbori.solution.mugloar.model.MissionLog;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * View Logs shows missions and purchases apart: a TYPE column, and gold earned (+) or spent (-).
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MugloarCLIRunnerHistoryTest {

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

    private void showHistory() {
        ReflectionTestUtils.invokeMethod(runner, "handleHistory");
    }

    /** The printed table row that contains the given ad or item ID. */
    private static String rowFor(CapturedOutput output, String id) {
        return output.getOut().lines().filter(line -> line.contains(" " + id + " ")).findFirst().orElseThrow();
    }

    @Test
    void missionsAndPurchasesAreShownApart(CapturedOutput output) {
        when(logRepo.findByGameIdOrderByIdDesc(GAME_ID)).thenReturn(List.of(
                new MissionLog(EntryType.PURCHASE, GAME_ID, "hpot", 50, null, true, "Bought Healing potion for 50 gold"),
                new MissionLog(GAME_ID, "ad-1", 20, "🟢 SAFE", true, "You successfully solved the mission")));

        showHistory();

        assertThat(output.getOut()).contains("TYPE", "AD / ITEM").doesNotContain("MISSION AD ID");
        assertThat(rowFor(output, "hpot")).contains("PURCHASE", "-50", "SUCCESS", "Bought Healing potion");
        assertThat(rowFor(output, "ad-1")).contains("MISSION", "+20", "SUCCESS");
    }

    @Test
    void entryWithoutMessageIsShownWithoutFailing(CapturedOutput output) {
        when(logRepo.findByGameIdOrderByIdDesc(GAME_ID)).thenReturn(List.of(
                new MissionLog(GAME_ID, "ad-2", 15, "🟡 LIKELY", false, null)));

        showHistory();

        assertThat(rowFor(output, "ad-2")).contains("MISSION", "+15", "FAIL");
    }

    @Test
    void emptyHistoryMentionsMissionsAndPurchases(CapturedOutput output) {
        when(logRepo.findByGameIdOrderByIdDesc(GAME_ID)).thenReturn(List.of());

        showHistory();

        assertThat(output.getOut()).contains("No missions or purchases recorded yet in this game.");
    }
}
