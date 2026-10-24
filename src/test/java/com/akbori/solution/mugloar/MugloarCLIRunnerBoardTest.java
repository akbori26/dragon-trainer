package com.akbori.solution.mugloar;

import com.akbori.solution.mugloar.dto.AdItem;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * View Board (menu 2) prints the stored board: USED and EXPIRED markers, risk labels,
 * shortened messages and a summary of the used ads. It never calls the server.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MugloarCLIRunnerBoardTest {

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

    private void givenTurn(int turn) {
        when(playerRepo.findById(GAME_ID)).thenReturn(Optional.of(new PlayerStatus(GAME_ID, 3, 0, 0, 0, turn)));
    }

    /** An ad downloaded at turn 0. */
    private static CachedAd ad(int index, String adId, String message, int expiresIn, String probability, boolean used) {
        CachedAd ad = new CachedAd(GAME_ID, index, new AdItem(adId, message, 25, expiresIn, null, probability), null, 0);
        ad.setUsed(used);
        return ad;
    }

    private void showBoard() {
        ReflectionTestUtils.invokeMethod(runner, "handleBoard");
    }

    /** The printed board row for the given ad ID. */
    private static String rowFor(CapturedOutput output, String adId) {
        return output.getOut().lines().filter(line -> line.contains(" " + adId + " ")).findFirst().orElseThrow();
    }

    @Test
    void usedAdsAreMarkedAndListedInTheSummary(CapturedOutput output) {
        givenTurn(0);
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of(
                ad(0, "fresh", "Help a farmer", 7, "Piece of cake", false),
                ad(1, "done", "Guard a caravan", 7, "Gamble", true)));

        showBoard();

        assertThat(rowFor(output, "fresh")).contains("----");
        assertThat(rowFor(output, "done")).contains("USED");
        assertThat(output.getOut()).contains("Used ads in this game: 1 -> 2 (done)");
        verifyNoInteractions(mugloarService);
    }

    @Test
    void expiresColumnCountsDownAndShowsExpired(CapturedOutput output) {
        // Turn 3: an ad with expiresIn 5 has 2 turns left, one with expiresIn 3 has none
        givenTurn(3);
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of(
                ad(0, "later", "Help a farmer", 5, "Piece of cake", false),
                ad(1, "gone", "Guard a caravan", 3, "Piece of cake", false)));

        showBoard();

        assertThat(rowFor(output, "later")).contains("| 2       |");
        assertThat(rowFor(output, "gone")).contains("EXPIRED");
    }

    @Test
    void riskLabelAndLongMessageAreShownShortened(CapturedOutput output) {
        givenTurn(0);
        String longMessage = "Escort the royal convoy through the haunted marshes before nightfall";
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of(
                ad(0, "long", longMessage, 7, "Rather detrimental", false)));

        showBoard();

        assertThat(rowFor(output, "long"))
                .contains("🔴 DANGEROUS")
                .contains(longMessage.substring(0, 37) + "...")
                .doesNotContain(longMessage);
    }

    @Test
    void boardTableRowsAllHaveTheSameWidth(CapturedOutput output) {
        givenTurn(2);
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of(
                ad(0, "a1", "Short", 7, "Piece of cake", false),
                ad(1, "a2", "A message that is far too long to fit in the column", 1, "Suicide mission", true),
                ad(2, "a3", "Mid-length message here", 4, "Playing with fire", false)));

        showBoard();

        List<String> table = output.getOut().lines()
                .filter(line -> line.startsWith("|") || line.startsWith("+"))
                .toList();
        assertThat(table).hasSize(7);
        assertThat(table).extracting(String::length).containsOnly(table.get(0).length());
    }

    @Test
    void emptyBoardPointsUserToDownloadAds(CapturedOutput output) {
        givenTurn(0);
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of());

        showBoard();

        assertThat(output.getOut()).contains("No contracts stored for this game. Run action [7] to download them.");
    }
}