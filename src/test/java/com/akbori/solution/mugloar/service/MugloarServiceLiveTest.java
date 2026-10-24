package com.akbori.solution.mugloar.service;

import com.akbori.solution.mugloar.dto.ShopItem;
import com.akbori.solution.mugloar.dto.TradeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
class MugloarServiceLiveTest {

    private static final String BASE_URL = "https://dragonsofmugloar.com/api/v2";

    private MugloarService mugloarService;
    private String gameId;

    @BeforeEach
    void startGame() {
        mugloarService = new MugloarService(RestClient.builder());
        gameId = mugloarService.startNewGame().gameId();
    }

    @Test
    void buyEndpointRespondsWithOkAndTradeJson() {
        ResponseEntity<String> response = RestClient.create(BASE_URL).post()
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
}