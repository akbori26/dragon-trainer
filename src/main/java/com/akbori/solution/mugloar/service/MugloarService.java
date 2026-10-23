package com.akbori.solution.mugloar.service;

/*
    Implement
        startGame() , investigateReputation()
    logic sequence using RestClient.
    The client automatically reads the server's response body directly into your structural Java Record schema object.

*/
import com.akbori.solution.mugloar.dto.AdItem;
import com.akbori.solution.mugloar.dto.BoardOfMessages;
import com.akbori.solution.mugloar.dto.GameStartResponse;
import com.akbori.solution.mugloar.dto.SolutionResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class MugloarService {

    private final RestClient restClient;

    public MugloarService() {
        this.restClient = RestClient.builder()
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
     * Executes the reputation post-check using our dynamically captured variable.
     */
    public String investigateReputation(String gameId) {
        return restClient.post()
                .uri("/{gameId}/investigate/reputation", gameId)
                .retrieve()
                .body(String.class);
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
     *
     * @param gameId Active session ID token
     * @param adId   The unique ID of the target advertisement message
     * @return SolutionResponse payload detailing updated state matrices
     */
    public SolutionResponse solveAd(String gameId, String adId) {
        return restClient.post()
                .uri("/{gameId}/solve/{adId}", gameId, adId) // Safely injects both path variables in order
                .retrieve()
                .body(SolutionResponse.class);
    }






}