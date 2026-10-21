package com.akbori.solution.mugloar.service;

/*
    Implement
        startGame() , investigateReputation()
    logic sequence using RestClient.
    The client automatically reads the server's response body directly into your structural Java Record schema object.

*/
import com.akbori.solution.mugloar.dto.TaskResponse;
import com.akbori.solution.mugloar.dto.BoardResponse;
import com.akbori.solution.mugloar.dto.GameStartResponse;
import com.akbori.solution.mugloar.dto.SolutionResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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
     Executes API call and download the active tasks from the board:
    */
    public List<TaskResponse> getMessageBoard(String gameId) {
        List<TaskResponse>tasks = restClient.get()
                .uri("/{gameId}/messages", gameId)
                .header(HttpHeaders.CONTENT_TYPE, "application/json") // 2. Headers
                .retrieve()
                .body(new ParameterizedTypeReference<List<TaskResponse>>() {});

        //List<TaskResponse> taskList

        BoardResponse response = new BoardResponse(tasks);
        return response != null ? response.messages() : List.of();
    }


    /**
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