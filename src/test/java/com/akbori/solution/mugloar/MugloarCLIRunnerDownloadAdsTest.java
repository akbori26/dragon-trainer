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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Download Ads (menu 7) replaces the stored board for the current game.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MugloarCLIRunnerDownloadAdsTest {

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
        when(playerRepo.findById(GAME_ID)).thenReturn(Optional.of(new PlayerStatus(GAME_ID, 3, 0, 0, 0, 4)));
    }

    private void downloadAds() {
        ReflectionTestUtils.invokeMethod(runner, "handleDownloadAds");
    }

    private static AdItem adItem(String adId, String probability) {
        return new AdItem(adId, "Message for " + adId, 10, 7, null, probability);
    }

    private static CachedAd stored(String adId, int displayIndex, boolean used) {
        CachedAd ad = new CachedAd(GAME_ID, displayIndex, adItem(adId, "Gamble"), "🎲 GAMBLE", 0);
        ad.setUsed(used);
        return ad;
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadReplacesTheStoredBoardAndStampsTheCurrentTurn() {
        when(mugloarService.getMessageBoard(GAME_ID)).thenReturn(List.of(
                adItem("keep-used", "Piece of cake"), adItem("brand-new", "Suicide mission")));
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of(
                stored("keep-used", 0, true), stored("dropped", 1, false)));

        downloadAds();

        // Old rows go first, then the new board is saved
        ArgumentCaptor<List<CachedAd>> saved = ArgumentCaptor.forClass(List.class);
        InOrder order = inOrder(adRepo);
        order.verify(adRepo).deleteByGameId(GAME_ID);
        order.verify(adRepo).saveAll(saved.capture());

        List<CachedAd> rows = saved.getValue();
        assertThat(rows).extracting(CachedAd::getAdId).containsExactly("keep-used", "brand-new");
        assertThat(rows).extracting(CachedAd::getDisplayIndex).containsExactly(0, 1);
        // The download turn is what the EXPIRES countdown starts from
        assertThat(rows).extracting(CachedAd::getFetchedTurn).containsOnly(4);
        assertThat(rows).extracting(CachedAd::getRiskCategory).containsExactly("🟢 SAFE", "☠️ FATAL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void adsAlreadyUsedStayUsedWhenTheServerListsThemAgain() {
        when(mugloarService.getMessageBoard(GAME_ID)).thenReturn(List.of(
                adItem("keep-used", "Piece of cake"), adItem("brand-new", "Gamble")));
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID)).thenReturn(List.of(stored("keep-used", 0, true)));

        downloadAds();

        ArgumentCaptor<List<CachedAd>> saved = ArgumentCaptor.forClass(List.class);
        verify(adRepo).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(CachedAd::isUsed).containsExactly(true, false);
    }

    @Test
    void emptyDownloadKeepsTheStoredBoard(CapturedOutput output) {
        when(mugloarService.getMessageBoard(GAME_ID)).thenReturn(List.of());

        downloadAds();

        verify(adRepo, never()).deleteByGameId(any());
        verify(adRepo, never()).saveAll(anyList());
        assertThat(output.getOut()).contains("No contracts downloaded; the stored board was kept.");
    }

    @Test
    void successfulDownloadReportsTheCountAndShowsTheBoard(CapturedOutput output) {
        when(mugloarService.getMessageBoard(GAME_ID)).thenReturn(List.of(adItem("brand-new", "Gamble")));
        // First read: used flags before the download; second read: the board shown afterwards
        when(adRepo.findByGameIdOrderByDisplayIndexAsc(GAME_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(stored("brand-new", 0, false)));

        downloadAds();

        assertThat(output.getOut()).contains("Stored 1 contracts.", "brand-new", "ADMISSION SPECIFICATION MESSAGE");
    }
}
