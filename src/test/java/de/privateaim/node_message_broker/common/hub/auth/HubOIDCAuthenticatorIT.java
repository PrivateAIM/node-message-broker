package de.privateaim.node_message_broker.common.hub.auth;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import de.privateaim.node_message_broker.common.HttpRetryConfig;
import de.privateaim.node_message_broker.common.OIDCAuthenticator;
import de.privateaim.node_message_broker.common.OIDCTokenPair;
import io.jsonwebtoken.Jwts;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HubOIDCAuthenticatorIT {

    private MockWebServer mockWebServer;
    private OIDCAuthenticator authenticator;

    private static final int MAX_RETRIES = 5;
    private static final int RETRY_DELAY_MILLIS = 10; // keeping it short for testing purposes!

    private static final String CLIENT_ID = "some-client-id";
    private static final String CLIENT_SECRET = "some-client-secret";

    @BeforeEach
    void setUp() {
        mockWebServer = new MockWebServer();
        authenticator = HubOIDCAuthenticator.builder()
                .usingWebClient(WebClient.create(mockWebServer.url("/").toString()))
                .withRetryConfig(new HttpRetryConfig(MAX_RETRIES, RETRY_DELAY_MILLIS))
                .withJsonDecoder(new com.fasterxml.jackson.databind.ObjectMapper())
                .withAuthCredentials(CLIENT_ID, CLIENT_SECRET)
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Nested
    class AuthenticationTests {

        @Test
        void succeedsEvenIfServerDoesNotSendRefreshToken() throws JsonProcessingException {
            var accessToken = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            new ObjectMapper().writeValueAsString(Map.of("access_token", accessToken))
                    ));


            var oidcTokenPair = new ArrayList<OIDCTokenPair>();
            StepVerifier.create(authenticator.authenticate())
                    .recordWith(ArrayList::new)
                    .expectNextCount(1)
                    .consumeRecordedWith(oidcTokenPair::addAll)
                    .expectComplete()
                    .verify();

            assertEquals(1, oidcTokenPair.size());
            assertEquals(accessToken, oidcTokenPair.getFirst().accessToken().getTokenValue());
            assertTrue(oidcTokenPair.getFirst().refreshToken().isEmpty());
        }

        @Test
        void failsIfServerDoesNotSendAccessToken() throws JsonProcessingException {
            var refreshToken = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            new ObjectMapper().writeValueAsString(Map.of("refresh_token", refreshToken))
                    ));

            StepVerifier.create(authenticator.authenticate())
                    .expectError()
                    .verify();
        }

        @Test
        void failsIfAccessTokenIsNotAValidJWT() throws JsonProcessingException {
            var tokenValue = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            new ObjectMapper().writeValueAsString(Map.of(
                                    "access_token", "test",
                                    "refresh_token", tokenValue))
                    ));

            StepVerifier.create(authenticator.authenticate())
                    .expectError(HubAuthMalformedResponseException.class)
                    .verify();
        }

        @Test
        void failsIfRefreshTokenIsNotAValidJWT() throws JsonProcessingException {
            var tokenValue = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            new ObjectMapper().writeValueAsString(Map.of(
                                    "access_token", tokenValue,
                                    "refresh_token", "test_refresh"))
                    ));

            StepVerifier.create(authenticator.authenticate())
                    .expectError(HubAuthMalformedResponseException.class)
                    .verify();
        }

        @Test
        void authenticateSucceedsOnFirstTry() throws JsonProcessingException {
            var accessToken = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            var refreshToken = Jwts.builder()
                    .subject("test_refresh")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            new ObjectMapper().writeValueAsString(Map.of(
                                    "access_token", accessToken,
                                    "refresh_token", refreshToken
                            ))));

            var oidcTokenPair = new ArrayList<OIDCTokenPair>();
            StepVerifier.create(authenticator.authenticate())
                    .recordWith(ArrayList::new)
                    .expectNextCount(1)
                    .consumeRecordedWith(oidcTokenPair::addAll)
                    .expectComplete()
                    .verify();

            assertEquals(1, oidcTokenPair.size());
            assertEquals(accessToken, oidcTokenPair.getFirst().accessToken().getTokenValue());
            assertTrue(oidcTokenPair.getFirst().refreshToken().isPresent());
            assertEquals(refreshToken, oidcTokenPair.getFirst().refreshToken().get().getTokenValue());
        }

        @Test
        void authenticateSucceedsWithinRetryRange() throws JsonProcessingException, InterruptedException {
            if (MAX_RETRIES < 2) {
                fail("misconfigured test environment");
            }

            var accessToken = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            var refreshToken = Jwts.builder()
                    .subject("test_refresh")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            new ObjectMapper().writeValueAsString(Map.of(
                                    "access_token", accessToken,
                                    "refresh_token", refreshToken
                            ))
                    ));

            var oidcTokenPair = new ArrayList<OIDCTokenPair>();
            StepVerifier.create(authenticator.authenticate())
                    .recordWith(ArrayList::new)
                    .expectNextCount(1)
                    .consumeRecordedWith(oidcTokenPair::addAll)
                    .expectComplete()
                    .verify();

            assertEquals(2, mockWebServer.getRequestCount());
            assertEquals(1, oidcTokenPair.size());
            assertEquals(accessToken, oidcTokenPair.getFirst().accessToken().getTokenValue());
            assertTrue(oidcTokenPair.getFirst().refreshToken().isPresent());
            assertEquals(refreshToken, oidcTokenPair.getFirst().refreshToken().get().getTokenValue());

            for (int i = 0; i < 2; i++) {
                var recordedRequest = mockWebServer.takeRequest();
                assertEquals("/token", recordedRequest.getPath());
            }
        }

        @Test
        void authenticateFinallyFailsAfterExhaustedRetryCount() throws InterruptedException {
            for (int i = 0; i < (MAX_RETRIES + 1); i++) {
                mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));
            }

            StepVerifier.create(authenticator.authenticate())
                    .verifyError(HubAccessTokenNotObtainable.class);

            for (int i = 0; i < (MAX_RETRIES + 1); i++) {
                var recordedRequest = mockWebServer.takeRequest();
                assertEquals("/token", recordedRequest.getPath());
            }
        }

        // TODO: add tests for checking what happens if issuedAt and expiresAt are missing...
    }

    @Nested
    class RefreshTests {
        private OAuth2Token getSimpleRefreshToken() {
            var issuedAt = Instant.now();
            var expiresAt = Instant.now().plus(Duration.ofDays(5));

            var refreshTokenValue = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(issuedAt))
                    .expiration(Date.from(expiresAt))
                    .compact();

            return new OAuth2RefreshToken(
                    refreshTokenValue,
                    issuedAt,
                    expiresAt
            );
        }

        @Test
        void succeedsEvenIfServerDoesNotSendRefreshToken() throws JsonProcessingException {
            var accessToken = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            new ObjectMapper().writeValueAsString(Map.of("access_token", accessToken))
                    ));

            var oidcTokenPair = new ArrayList<OIDCTokenPair>();
            StepVerifier.create(authenticator.authenticate())
                    .recordWith(ArrayList::new)
                    .expectNextCount(1)
                    .consumeRecordedWith(oidcTokenPair::addAll)
                    .expectComplete()
                    .verify();

            assertEquals(1, oidcTokenPair.size());
            assertEquals(accessToken, oidcTokenPair.getFirst().accessToken().getTokenValue());
            assertTrue(oidcTokenPair.getFirst().refreshToken().isEmpty());
        }

        @Test
        void failsIfServerDoesNotSendAccessToken() throws JsonProcessingException {
            var refreshToken = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            new ObjectMapper().writeValueAsString(Map.of("refresh_token", refreshToken))
                    ));

            StepVerifier.create(authenticator.refresh(getSimpleRefreshToken()))
                    .expectError()
                    .verify();
        }

        @Test
        void failsIfAccessTokenIsNotAValidJWT() throws JsonProcessingException {
            var tokenValue = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            new ObjectMapper().writeValueAsString(Map.of(
                                    "access_token", "test",
                                    "refresh_token", tokenValue))
                    ));

            StepVerifier.create(authenticator.refresh(getSimpleRefreshToken()))
                    .expectError(HubAuthMalformedResponseException.class)
                    .verify();
        }

        @Test
        void failsIfRefreshTokenIsNotAValidJWT() throws JsonProcessingException {
            var tokenValue = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            new ObjectMapper().writeValueAsString(Map.of(
                                    "access_token", tokenValue,
                                    "refresh_token", "test_refresh"))
                    ));

            StepVerifier.create(authenticator.refresh(getSimpleRefreshToken()))
                    .expectError(HubAuthMalformedResponseException.class)
                    .verify();
        }

        @Test
        void refreshSucceedsOnFirstTry() throws JsonProcessingException {
            var accessToken = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            var refreshToken = Jwts.builder()
                    .subject("test_refresh")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            new ObjectMapper().writeValueAsString(Map.of(
                                    "access_token", accessToken,
                                    "refresh_token", refreshToken
                            ))));

            var oidcTokenPair = new ArrayList<OIDCTokenPair>();
            StepVerifier.create(authenticator.refresh(getSimpleRefreshToken()))
                    .recordWith(ArrayList::new)
                    .expectNextCount(1)
                    .consumeRecordedWith(oidcTokenPair::addAll)
                    .expectComplete()
                    .verify();

            assertEquals(1, oidcTokenPair.size());
            assertEquals(accessToken, oidcTokenPair.getFirst().accessToken().getTokenValue());
            assertTrue(oidcTokenPair.getFirst().refreshToken().isPresent());
            assertEquals(refreshToken, oidcTokenPair.getFirst().refreshToken().get().getTokenValue());
        }

        @Test
        void refreshSucceedsWithinRetryRange() throws JsonProcessingException, InterruptedException {
            if (MAX_RETRIES < 2) {
                fail("misconfigured test environment");
            }

            var accessToken = Jwts.builder()
                    .subject("test")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            var refreshToken = Jwts.builder()
                    .subject("test_refresh")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plus(Duration.ofDays(5))))
                    .compact();

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            new ObjectMapper().writeValueAsString(Map.of(
                                    "access_token", accessToken,
                                    "refresh_token", refreshToken
                            ))
                    ));

            var oidcTokenPair = new ArrayList<OIDCTokenPair>();
            StepVerifier.create(authenticator.refresh(getSimpleRefreshToken()))
                    .recordWith(ArrayList::new)
                    .expectNextCount(1)
                    .consumeRecordedWith(oidcTokenPair::addAll)
                    .expectComplete()
                    .verify();

            assertEquals(2, mockWebServer.getRequestCount());
            assertEquals(1, oidcTokenPair.size());
            assertEquals(accessToken, oidcTokenPair.getFirst().accessToken().getTokenValue());
            assertTrue(oidcTokenPair.getFirst().refreshToken().isPresent());
            assertEquals(refreshToken, oidcTokenPair.getFirst().refreshToken().get().getTokenValue());

            for (int i = 0; i < 2; i++) {
                var recordedRequest = mockWebServer.takeRequest();
                assertEquals("/token", recordedRequest.getPath());
            }
        }

        @Test
        void refreshFinallyFailsAfterExhaustedRetryCount() throws InterruptedException {
            for (int i = 0; i < (MAX_RETRIES + 1); i++) {
                mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));
            }

            StepVerifier.create(authenticator.refresh(getSimpleRefreshToken()))
                    .verifyError(HubAccessTokenNotObtainable.class);

            for (int i = 0; i < (MAX_RETRIES + 1); i++) {
                var recordedRequest = mockWebServer.takeRequest();
                assertEquals("/token", recordedRequest.getPath());
            }
        }
    }
}
