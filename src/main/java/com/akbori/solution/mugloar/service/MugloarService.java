package com.akbori.solution.mugloar.service;

/*
    Implement
        startGame() , investigateReputation()
    logic sequence using RestClient.
    The client automatically reads the server's response body directly into your structural Java Record schema object.

*/
import com.akbori.solution.mugloar.dto.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class MugloarService {

    private final RestClient restClient;

    public MugloarService(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://dragonsofmugloar.com/api/v2")
                .build();
    }

    /**
     * Sends a POST request to start a new game loop.
     * Extracts and returns the dynamic gameId string.
     */
    public GameStartResponse startNewGame() {

        GameStartResponse response = restClient.post()
                .uri("/game/start")
                .header(HttpHeaders.CONTENT_TYPE, "application/json") // 2. Headers
                .retrieve()
                .body(GameStartResponse.class); // Automatically maps JSON to the Record

        if (response != null && response.gameId() != null) {
            return response;
        }
        throw new RuntimeException("Failed to retrieve a valid game session ID.");
    }




    /**
     * Executes API call to investigate the dragon's reputation:
     * Sends a POST message to retrieve the reputation with the people, the state and the underworld.
     * If the return message is other than success it just writes the information to the console.
     *
     * @param gameId Active session ID token
     * @return ReputationResponse, or null if the request did not succeed
     */
    public ReputationResponse investigateReputation(String gameId) {
        // exchange() hands us the raw response so the status is checked before any body mapping
        return restClient.post()
                .uri("/{gameId}/investigate/reputation", gameId)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();

                    if (status.value() == 200) {
                        return response.bodyTo(ReputationResponse.class);
                    }

                    switch (status.value()) {
                        case 400 -> System.out.println("❌ Bad request while investigating reputation for game: " + gameId);
                        case 404 -> System.out.println("❌ Game not found or already over: " + gameId);
                        case 410 -> System.out.println("❌ Game session has expired: " + gameId);
                        default -> {
                            if (status.is5xxServerError()) {
                                System.out.println("❌ Mugloar server error (" + status.value() + ") while investigating reputation.");
                            } else {
                                System.out.println("❌ Unexpected response (" + status.value() + ") while investigating reputation.");
                            }
                        }
                    }
                    return null;
                });
    }


    /**
     * Executes API call and download the active ads from the message board:
     * Sends a GET message to retrieve all the task from the message board
     * If the return message is other than success it just writes the information to the console.
     *
     * @param gameId Active session ID token
     * @return list of AdItems
    */
    public List<AdItem> getMessageBoard(String gameId) {
        // exchange() hands us the raw response so the status is checked before any body mapping
        List<AdItem> tasks = restClient.get()
                .uri("/{gameId}/messages", gameId)
                .header(HttpHeaders.CONTENT_TYPE, "application/json") // 2. Headers
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();

                    if (status.value() == 200) {
                        return response.bodyTo(new ParameterizedTypeReference<List<AdItem>>() {});
                    }

                    switch (status.value()) {
                        case 400 -> System.out.println("❌ Bad request while fetching the message board for game: " + gameId);
                        case 404 -> System.out.println("❌ Game not found or already over: " + gameId);
                        case 410 -> System.out.println("❌ Game session has expired: " + gameId);
                        default -> {
                            if (status.is5xxServerError()) {
                                System.out.println("❌ Mugloar server error (" + status.value() + ") while fetching the message board.");
                            } else {
                                System.out.println("❌ Unexpected response (" + status.value() + ") while fetching the message board.");
                            }
                        }
                    }
                    return null;
                });

        if (tasks == null) {
            return List.of();
        }

        BoardOfMessages board = new BoardOfMessages(tasks);
        return board.messages();
    }


    /**
     *
     * Sends a POST request to solve a specific advertisement task.
     * The SolutionResponse is only created when the status is 200,
     * otherwise it just writes the information to the console.
     *
     * @param gameId Active session ID token
     * @param adId   The unique ID of the target advertisement message
     * @return SolutionResponse payload detailing updated state matrices, or null if the request did not succeed
     */
    public SolutionResponse solveAd(String gameId, String adId) {
        // exchange() hands us the raw response so the status is checked before any body mapping
        return restClient.post()
                .uri("/{gameId}/solve/{adId}", gameId, adId) // Safely injects both path variables in order
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();

                    if (status.value() == 200) {
                        return response.bodyTo(SolutionResponse.class);
                    }

                    switch (status.value()) {
                        case 400 -> System.out.println("❌ Ad " + adId + " could not be solved (unknown or already expired) for game: " + gameId);
                        case 404 -> System.out.println("❌ Game not found or already over: " + gameId);
                        case 410 -> System.out.println("❌ Game session has expired: " + gameId);
                        default -> {
                            if (status.is5xxServerError()) {
                                System.out.println("❌ Mugloar server error (" + status.value() + ") while solving ad " + adId + ".");
                            } else {
                                System.out.println("❌ Unexpected response (" + status.value() + ") while solving ad " + adId + ".");
                            }
                        }
                    }
                    return null;
                });
    }




    /**
     * Executes API call and download the shopping Items for the particular game
     * If the return message is other than success it just writes the information to the console.
     *
     * @param gameId Active session ID token
     * @return list of ShopItems
     */
    public List<ShopItem> getMarketItems(String gameId) {
        // exchange() hands us the raw response so the status is checked before any body mapping
        List<ShopItem> items = restClient.get()
                .uri("/{gameId}/shop", gameId)
                .header(HttpHeaders.CONTENT_TYPE, "application/json") // 2. Headers
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();

                    if (status.value() == 200) {
                        return response.bodyTo(new ParameterizedTypeReference<List<ShopItem>>() {});
                    }

                    switch (status.value()) {
                        case 400 -> System.out.println("❌ Bad request while fetching the shop/market items for game: " + gameId);
                        case 404 -> System.out.println("❌ Game not found or already over: " + gameId);
                        case 410 -> System.out.println("❌ Game session has expired: " + gameId);
                        default -> {
                            if (status.is5xxServerError()) {
                                System.out.println("❌ Mugloar server error (" + status.value() + ") while fetching the message board.");
                            } else {
                                System.out.println("❌ Unexpected response (" + status.value() + ") while fetching the message board.");
                            }
                        }
                    }
                    return null;
                });

        if (items == null) {
            return List.of();
        }

        BoardOfMarket boardOfMarket = new BoardOfMarket(items);
        return boardOfMarket.shopItems();
    }






    /**
            *
            * Sends a POST request to buy a specific market item
            * The TradeResponse is only created when the status is 200,
            * otherwise it just writes the information to the console.
     *
             * @param gameId Active session ID token
     * @param itemId   The unique ID of the target market item
     * @return TradeResponse payload detailing updated state matrices, or null if the request did not succeed
     */
    public TradeResponse buyItem(String gameId, String itemId) {
        // exchange() hands us the raw response so the status is checked before any body mapping
        return restClient.post()
                .uri("/{gameId}/shop/buy/{itemId}", gameId, itemId) // Safely injects both path variables in order
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();

                    if (status.value() == 200) {
                        return response.bodyTo(TradeResponse.class);
                    }

                    switch (status.value()) {
                        case 400 -> System.out.println("❌ Item " + itemId + " could not be bought for game: " + gameId);
                        case 404 -> System.out.println("❌ Game not found or already over: " + gameId);
                        case 410 -> System.out.println("❌ Game session has expired: " + gameId);
                        default -> {
                            if (status.is5xxServerError()) {
                                System.out.println("❌ Mugloar server error (" + status.value() + ") while buying item " + itemId + ".");
                            } else {
                                System.out.println("❌ Unexpected response (" + status.value() + ") while buying item " + itemId + ".");
                            }
                        }
                    }
                    return null;
                });
    }

}