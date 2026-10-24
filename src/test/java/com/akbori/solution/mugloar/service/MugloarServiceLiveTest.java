package com.akbori.solution.mugloar.service;

import com.akbori.solution.mugloar.dto.AdItem;
import com.akbori.solution.mugloar.dto.GameStartResponse;
import com.akbori.solution.mugloar.dto.ReputationResponse;
import com.akbori.solution.mugloar.dto.ShopItem;
import com.akbori.solution.mugloar.dto.SolutionResponse;
import com.akbori.solution.mugloar.dto.TradeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the real Dragons of Mugloar server, so it needs network access.
 * Each test starts a fresh game, which has 0 gold, so no purchase can go through
 * and the tests never depend on a stored game ID.
 */
@Tag("live")
@ExtendWith(OutputCaptureExtension.class)
class MugloarServiceLiveTest {

    private static final String BASE_URL = "https://dragonsofmugloar.com/api/v2";
    // The server has been seen answering an unknown game ID with either 400 or 404
    private static final String UNKNOWN_GAME_ID = "noSuchGame";
    private static final String GAME_NOT_FOUND = "Game not found or already over: " + UNKNOWN_GAME_ID;

    private MugloarService mugloarService;
    private RestClient rawClient;
    private String gameId;

    @BeforeEach
    void startGame() {
        mugloarService = new MugloarService(RestClient.builder());
        rawClient = RestClient.create(BASE_URL);
        gameId = mugloarService.startNewGame().gameId();
    }

    /**
     * Returns the ID of the first ad on the board that is not encrypted,
     * since encrypted ads need their ID decoded before they can be solved.
     */
    private String firstPlainAdId() {
        return mugloarService.getMessageBoard(gameId).stream()
                .filter(ad -> ad.encrypted() == null)
                .map(AdItem::adId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No unencrypted ad on the board for game " + gameId));
    }

    // --- Game start ---

    @Test
    void startEndpointRespondsWithOkAndGameJson() {
        ResponseEntity<String> response = rawClient.post()
                .uri("/game/start")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"gameId\"", "\"lives\"", "\"gold\"", "\"level\"", "\"score\"", "\"highScore\"", "\"turn\"");
    }

    @Test
    void startNewGameReturnsFreshGameState() {
        GameStartResponse res = mugloarService.startNewGame();

        assertThat(res.gameId()).isNotBlank().isNotEqualTo(gameId);
        assertThat(res.lives()).isEqualTo(3);
        assertThat(res.gold()).isZero();
        assertThat(res.level()).isZero();
        assertThat(res.score()).isZero();
        assertThat(res.turn()).isZero();
    }

    // --- Message board ---

    @Test
    void messagesEndpointRespondsWithOkAndAdJson() {
        ResponseEntity<String> response = rawClient.get()
                .uri("/{gameId}/messages", gameId)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"adId\"", "\"message\"", "\"reward\"", "\"expiresIn\"", "\"probability\"");
    }

    @Test
    void getMessageBoardReturnsAdsWithIds() {
        List<AdItem> ads = mugloarService.getMessageBoard(gameId);

        assertThat(ads).isNotEmpty();
        assertThat(ads).allSatisfy(ad -> assertThat(ad.adId()).isNotBlank());
    }

    // --- Solve ---

    @Test
    void solveEndpointRespondsWithOkAndSolutionJson() {
        String adId = firstPlainAdId();

        ResponseEntity<String> response = rawClient.post()
                .uri("/{gameId}/solve/{adId}", gameId, adId)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"success\"", "\"lives\"", "\"gold\"", "\"score\"", "\"turn\"", "\"message\"");
    }

    @Test
    void solveAdMapsServerResponseToSolutionResponse() {
        SolutionResponse res = mugloarService.solveAd(gameId, firstPlainAdId());

        assertThat(res).isNotNull();
        // Solving always uses up a turn, whether it succeeds or not
        assertThat(res.turn()).isGreaterThan(0);
        assertThat(res.message()).isNotBlank();
    }

    // --- Reputation ---

    @Test
    void reputationEndpointRespondsWithOkAndReputationJson() {
        ResponseEntity<String> response = rawClient.post()
                .uri("/{gameId}/investigate/reputation", gameId)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"people\"", "\"state\"", "\"underworld\"");
    }

    @Test
    void investigateReputationMapsServerResponseToReputationResponse() {
        ReputationResponse res = mugloarService.investigateReputation(gameId);

        assertThat(res).isNotNull();
        // A new game starts with neutral standing
        assertThat(res.people()).isZero();
        assertThat(res.state()).isZero();
        assertThat(res.underworld()).isZero();
    }

    // --- Shop ---

    @Test
    void shopEndpointRespondsWithOkAndItemJson() {
        ResponseEntity<String> response = rawClient.get()
                .uri("/{gameId}/shop", gameId)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"id\"", "\"name\"", "\"cost\"");
    }

    @Test
    void buyEndpointRespondsWithOkAndTradeJson() {
        ResponseEntity<String> response = rawClient.post()
                .uri("/{gameId}/shop/buy/{itemId}", gameId, "hpot")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"shoppingSuccess\"", "\"gold\"", "\"lives\"", "\"level\"", "\"turn\"");
    }

    @Test
    void buyItemMapsServerResponseToTradeResponse() {
        TradeResponse res = mugloarService.buyItem(gameId, "hpot");

        assertThat(res).isNotNull();
        // A new game cannot afford anything
        assertThat(res.shoppingSuccess()).isFalse();
        assertThat(res.lives()).isEqualTo(3);
    }

    @Test
    void getMarketItemsReturnsItemsWithIds() {
        List<ShopItem> items = mugloarService.getMarketItems(gameId);

        assertThat(items).isNotEmpty();
        assertThat(items).allSatisfy(item -> assertThat(item.id()).isNotBlank());
    }

    // --- Unknown game ---

    @ParameterizedTest
    @CsvSource({
            "GET,  /{gameId}/messages",
            "POST, /{gameId}/solve/abc",
            "POST, /{gameId}/investigate/reputation",
            "GET,  /{gameId}/shop",
            "POST, /{gameId}/shop/buy/hpot"
    })
    void endpointsRejectUnknownGame(String method, String path) {
        // exchange() returns the status without throwing on 4xx
        int status = rawClient.method(HttpMethod.valueOf(method))
                .uri(path, UNKNOWN_GAME_ID)
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isIn(400, 404);
    }

    @Test
    void getMessageBoardReturnsEmptyListForUnknownGame(CapturedOutput output) {
        assertThat(mugloarService.getMessageBoard(UNKNOWN_GAME_ID)).isEmpty();
        assertThat(output.getOut()).containsAnyOf(
                "Bad request while fetching the message board for game: " + UNKNOWN_GAME_ID, GAME_NOT_FOUND);
    }

    @Test
    void solveAdReturnsNullForUnknownGame(CapturedOutput output) {
        assertThat(mugloarService.solveAd(UNKNOWN_GAME_ID, "abc")).isNull();
        assertThat(output.getOut()).containsAnyOf(
                "Ad abc could not be solved (unknown or already expired) for game: " + UNKNOWN_GAME_ID, GAME_NOT_FOUND);
    }

    @Test
    void investigateReputationReturnsNullForUnknownGame(CapturedOutput output) {
        assertThat(mugloarService.investigateReputation(UNKNOWN_GAME_ID)).isNull();
        assertThat(output.getOut()).containsAnyOf(
                "Bad request while investigating reputation for game: " + UNKNOWN_GAME_ID, GAME_NOT_FOUND);
    }

    @Test
    void getMarketItemsReturnsEmptyListForUnknownGame(CapturedOutput output) {
        assertThat(mugloarService.getMarketItems(UNKNOWN_GAME_ID)).isEmpty();
        assertThat(output.getOut()).containsAnyOf(
                "Bad request while fetching the shop/market items for game: " + UNKNOWN_GAME_ID, GAME_NOT_FOUND);
    }

    @Test
    void buyItemReturnsNullForUnknownGame(CapturedOutput output) {
        assertThat(mugloarService.buyItem(UNKNOWN_GAME_ID, "hpot")).isNull();
        assertThat(output.getOut()).containsAnyOf(
                "Item hpot could not be bought for game: " + UNKNOWN_GAME_ID, GAME_NOT_FOUND);
    }
}