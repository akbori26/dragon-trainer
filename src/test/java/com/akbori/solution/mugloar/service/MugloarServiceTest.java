package com.akbori.solution.mugloar.service;

import com.akbori.solution.mugloar.dto.AdItem;
import com.akbori.solution.mugloar.dto.GameStartResponse;
import com.akbori.solution.mugloar.dto.SolutionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(MugloarService.class)
@ExtendWith(OutputCaptureExtension.class)
class MugloarServiceTest {

    @Autowired
    private MugloarService mugloarService;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void startNewGameReturnsParsedGameSession() {
        server.expect(requestTo("https://dragonsofmugloar.com/api/v2/game/start"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"gameId":"abc123","lives":3,"gold":0,"level":0,
                         "score":0,"highScore":0,"turn":0}
                        """, MediaType.APPLICATION_JSON));

        GameStartResponse response = mugloarService.startNewGame();

        assertThat(response.gameId()).isEqualTo("abc123");
        assertThat(response.lives()).isEqualTo(3);
        server.verify();
    }


    @Test
    void getMessageBoardReturnsParsedBoardOfMessages(){
        String gameId = "abc123";

        server.expect(requestTo("https://dragonsofmugloar.com/api/v2/" + gameId + "/messages"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"adId":"XFyAyerI",
                          "message":"Create an advertisement campaign for Florian Milford to promote their house based business",
                          "reward":3,"expiresIn":7,"encrypted":null,"probability":"Sure thing"}]
                        """, MediaType.APPLICATION_JSON));

        List<AdItem> messages = mugloarService.getMessageBoard(gameId);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).adId()).isEqualTo("XFyAyerI");
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "400, Bad request while fetching the message board",
            "404, Game not found or already over",
            "410, Game session has expired",
            "500, Mugloar server error (500)",
            "503, Mugloar server error (503)",
            "418, Unexpected response (418)"
    })
    void getMessageBoardReturnsEmptyListOnErrorStatus(int status, String expectedLog, CapturedOutput output) {
        String gameId = "abc123";

        server.expect(requestTo("https://dragonsofmugloar.com/api/v2/" + gameId + "/messages"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        List<AdItem> messages = mugloarService.getMessageBoard(gameId);

        assertThat(messages).isEmpty();
        assertThat(output).contains(expectedLog);
        server.verify();

    }

    @Test
    void solveAdReturnsParsedSolutionResponse() {
        String gameId = "abc123";
        String adId = "XFyAyerI";

        server.expect(requestTo("https://dragonsofmugloar.com/api/v2/" + gameId + "/solve/" + adId))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success":true,"lives":3,"gold":13,"score":10,
                         "highScore":0,"turn":1,"message":"You successfully solved the mission!"}
                        """, MediaType.APPLICATION_JSON));

        SolutionResponse response = mugloarService.solveAd(gameId, adId);

        assertThat(response.success()).isTrue();
        assertThat(response.gold()).isEqualTo(13);
        assertThat(response.score()).isEqualTo(10);
        assertThat(response.turn()).isEqualTo(1);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "400, Ad XFyAyerI could not be solved (unknown or already expired)",
            "404, Game not found or already over",
            "410, Game session has expired",
            "500, Mugloar server error (500) while solving ad XFyAyerI",
            "503, Mugloar server error (503) while solving ad XFyAyerI",
            "418, Unexpected response (418) while solving ad XFyAyerI"
    })
    void solveAdReturnsNullOnErrorStatus(int status, String expectedLog, CapturedOutput output) {
        String gameId = "abc123";
        String adId = "XFyAyerI";

        server.expect(requestTo("https://dragonsofmugloar.com/api/v2/" + gameId + "/solve/" + adId))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        SolutionResponse response = mugloarService.solveAd(gameId, adId);

        assertThat(response).isNull();
        assertThat(output).contains(expectedLog);
        server.verify();
    }

}