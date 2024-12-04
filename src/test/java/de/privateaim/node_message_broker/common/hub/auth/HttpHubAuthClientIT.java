package de.privateaim.node_message_broker.common.hub.auth;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.message.BasicNameValuePair;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.net.URLEncodedUtils;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.mongodb.assertions.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpHubAuthClientIT {

    private MockWebServer mockWebServer;

    private HubAuthClient httpHubAuthClient;

    private static final int MAX_RETRIES = 5;
    private static final int RETRY_DELAY_MILLIS = 10; // keeping it short for testing purposes!

    @BeforeEach
    void setUp() {
        mockWebServer = new MockWebServer();
        var webClient = WebClient.create(mockWebServer.url("/").toString());
        httpHubAuthClient = new HttpHubAuthClient(webClient, new HttpHubAuthClientConfig.Builder()
                .withMaxRetries(MAX_RETRIES)
                .withRetryDelayMs(RETRY_DELAY_MILLIS)
                .build());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void obtainingAccessTokenSucceedsOnFirstTry() throws JsonProcessingException, InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                .setHeader("Content-Type", "application/json")
                .setBody(
                        new ObjectMapper().writeValueAsString(Map.of("access_token", "test"))
                ));

        StepVerifier.create(httpHubAuthClient.requestAccessToken("some-client-id", "some-client-secret"))
                .expectNext("test")
                .verifyComplete();

        assertEquals(1, mockWebServer.getRequestCount());
        var recordedRequest = mockWebServer.takeRequest();
        assertEquals("/token", recordedRequest.getPath());

        var urlEncodedParams = URLEncodedUtils.parse(recordedRequest.getBody().readUtf8(), StandardCharsets.UTF_8);

        assertEquals(3, urlEncodedParams.size());
        assertEquals("robot_credentials", urlEncodedParams.getFirst().getValue());

        assertTrue(urlEncodedParams.containsAll(List.of(
                new BasicNameValuePair("grant_type", "robot_credentials"),
                new BasicNameValuePair("id", "some-client-id"),
                new BasicNameValuePair("secret", "some-client-secret")
        )));
    }

    @Test
    void obtainingAccessTokenSucceedsWithinRetryRange() throws JsonProcessingException, InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                .setHeader("Content-Type", "application/json")
                .setBody(
                        new ObjectMapper().writeValueAsString(Map.of("access_token", "test"))
                ));

        StepVerifier.create(httpHubAuthClient.requestAccessToken("some-client-id", "some-client-secret"))
                .expectNext("test")
                .verifyComplete();

        assertEquals(2, mockWebServer.getRequestCount());

        for (int i = 0; i < 2; i++) {
            var recordedRequest = mockWebServer.takeRequest();
            assertEquals("/token", recordedRequest.getPath());
        }
    }

    @Test
    void obtainingAccessTokenFailsOfMaximumNumberOfRetriesOnServerError() throws InterruptedException {
        for (int i = 0; i < (MAX_RETRIES + 1); i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));
        }

        StepVerifier.create(httpHubAuthClient.requestAccessToken("some-client-id", "some-client-secret"))
                .verifyError(HubAccessTokenNotObtainable.class);

        for (int i = 0; i < (MAX_RETRIES + 1); i++) {
            var recordedRequest = mockWebServer.takeRequest();
            assertEquals("/token", recordedRequest.getPath());
        }
    }
}
