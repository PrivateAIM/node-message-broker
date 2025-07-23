package de.privateaim.node_message_broker.common;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OIDCAuthenticatorMiddlewareTest {

    @Mock
    private OIDCAuthenticator authenticator;

    @InjectMocks
    private OIDCAuthenticatorMiddleware authenticatorMiddleware;

    private OAuth2RefreshToken getSimpleRefreshToken(Instant issuedAt, Instant expiresAt) {
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

    private OAuth2AccessToken getSimpleAccessToken(Instant issuedAt, Instant expiresAt) {
        var accessTokenValue = Jwts.builder()
                .subject("test")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .compact();

        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                accessTokenValue,
                issuedAt,
                expiresAt
        );
    }

    private String getBearerToken(ClientRequest request) {
        var authorizationHeaderValue = request.headers().getFirst("Authorization");
        if (authorizationHeaderValue.startsWith("Bearer ")) {
            return authorizationHeaderValue.substring("Bearer ".length());
        }

        return null;
    }

    @Test
    void requestFailsIfNoHostInformationIsPresent() {
        var targetResponse = Mockito.mock(ClientResponse.class);
        var request = ClientRequest.create(HttpMethod.GET, URI.create("/no-host/just-a-path")).build();
        ExchangeFunction targetExchangeFunction = r -> Mono.just(targetResponse);

        var responses = new ArrayList<ClientResponse>();
        StepVerifier.create(authenticatorMiddleware.filter(request, targetExchangeFunction))
                .recordWith(ArrayList::new)
                .expectNextCount(1)
                .consumeRecordedWith(responses::addAll)
                .verifyComplete();

        assertEquals(1, responses.size());
        assertEquals(HttpStatus.SC_BAD_REQUEST, responses.getFirst().statusCode().value());
    }

    @Test
    void firstRequestForAHostRequiresAuthentication() {
        var issuedAt = Instant.now();
        var expiresAt = issuedAt.plus(Duration.ofHours(1));

        var accessToken = getSimpleAccessToken(issuedAt, expiresAt);
        var refreshToken = getSimpleRefreshToken(issuedAt, expiresAt);

        var targetResponse = Mockito.mock(ClientResponse.class);
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://test.host/some-resource")).build();
        ExchangeFunction targetExchangeFunction = r -> Mono.just(targetResponse);

        doReturn(Mono.just(new OIDCTokenPair(accessToken, Optional.of(refreshToken))))
                .when(authenticator).authenticate();
        doReturn(HttpStatusCode.valueOf(HttpStatus.SC_OK)).when(targetResponse).statusCode();


        StepVerifier.create(authenticatorMiddleware.filter(request, targetExchangeFunction))
                .expectNext(targetResponse)
                .expectComplete()
                .verify();
        verify(authenticator, Mockito.times(1)).authenticate();
    }

    @Test
    void existingAccessTokenGetsReusedIfNotExpired() {
        var issuedAt = Instant.now();
        var expiresAt = issuedAt.plus(Duration.ofHours(1));

        var accessToken = getSimpleAccessToken(issuedAt, expiresAt);
        var refreshToken = getSimpleRefreshToken(issuedAt, expiresAt);

        var targetResponse = Mockito.mock(ClientResponse.class);
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://test.host/some-resource")).build();
        ExchangeFunction targetExchangeFunction = r -> Mono.just(targetResponse);

        doReturn(Mono.just(new OIDCTokenPair(accessToken, Optional.of(refreshToken))))
                .when(authenticator).authenticate();
        doReturn(HttpStatusCode.valueOf(HttpStatus.SC_OK)).when(targetResponse).statusCode();

        StepVerifier.create(authenticatorMiddleware.filter(request, targetExchangeFunction))
                .expectNext(targetResponse)
                .expectComplete()
                .verify();
        StepVerifier.create(authenticatorMiddleware.filter(request, targetExchangeFunction))
                .expectNext(targetResponse)
                .expectComplete()
                .verify();
        verify(authenticator, Mockito.times(1)).authenticate();
    }

    @Test
    void newAccessTokenIsAcquiredIfExpiredUsingRefreshTokenIfPresent() {
        var expiredAccessToken = getSimpleAccessToken(Instant.now().minus(Duration.ofHours(2)),
                Instant.now().minus(Duration.ofHours(1)));
        var refreshToken = getSimpleRefreshToken(Instant.now(), Instant.now().plus(Duration.ofHours(1)));
        var newAccessToken = getSimpleAccessToken(Instant.now(), Instant.now().plus(Duration.ofHours(1)));
        var newRefreshToken = getSimpleRefreshToken(Instant.now(), Instant.now().plus(Duration.ofHours(2)));


        var targetResponse = Mockito.mock(ClientResponse.class);
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://test.host/some-resource")).build();
        ExchangeFunction targetExchangeFunction = r -> Mono.just(targetResponse);

        doReturn(Mono.just(new OIDCTokenPair(expiredAccessToken, Optional.of(refreshToken))))
                .when(authenticator).authenticate();
        doReturn(Mono.just(new OIDCTokenPair(newAccessToken, Optional.of(newRefreshToken))))
                .when(authenticator).refresh(refreshToken);
        doReturn(HttpStatusCode.valueOf(HttpStatus.SC_OK)).when(targetResponse).statusCode();

        // This in fact runs the first request with an expired token. However, we are not interested in that fact
        // here. It's more about filling the cache for the next request to the same host.
        StepVerifier.create(authenticatorMiddleware.filter(request, targetExchangeFunction))
                .expectNext(targetResponse)
                .expectComplete()
                .verify();
        StepVerifier.create(authenticatorMiddleware.filter(request, targetExchangeFunction))
                .expectNext(targetResponse)
                .expectComplete()
                .verify();
        verify(authenticator, Mockito.times(1)).authenticate();
        verify(authenticator, Mockito.times(1)).refresh(refreshToken);
    }

    @Test
    void newAccessTokenIsAcquiredEvenIfRefreshTokenIsAbsent() {
        var expiredAccessToken = getSimpleAccessToken(Instant.now().minus(Duration.ofHours(2)),
                Instant.now().minus(Duration.ofHours(1)));
        var newAccessToken = getSimpleAccessToken(Instant.now(), Instant.now().plus(Duration.ofHours(1)));


        var targetResponse = Mockito.mock(ClientResponse.class);
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://test.host/some-resource")).build();
        ExchangeFunction targetExchangeFunction = r -> Mono.just(targetResponse);

        doReturn(Mono.just(new OIDCTokenPair(expiredAccessToken, Optional.empty())),
                Mono.just(new OIDCTokenPair(newAccessToken, Optional.empty())))
                .when(authenticator).authenticate();
        doReturn(HttpStatusCode.valueOf(HttpStatus.SC_OK)).when(targetResponse).statusCode();

        // This in fact runs the first request with an expired token. However, we are not interested in that fact
        // here. It's more about filling the cache for the next request to the same host.
        StepVerifier.create(authenticatorMiddleware.filter(request, targetExchangeFunction))
                .expectNext(targetResponse)
                .expectComplete()
                .verify();
        StepVerifier.create(authenticatorMiddleware.filter(request, targetExchangeFunction))
                .expectNext(targetResponse)
                .expectComplete()
                .verify();
        verify(authenticator, Mockito.times(2)).authenticate();
    }

    @Test
    void newAccessAndRefreshTokenPairAcquiredIfBothHaveExpired() {
        var expiredAccessToken = getSimpleAccessToken(Instant.now().minus(Duration.ofHours(3)),
                Instant.now().minus(Duration.ofHours(1)));
        var expiredRefreshToken = getSimpleRefreshToken(Instant.now().minus(Duration.ofHours(3)),
                Instant.now().minus(Duration.ofHours(2)));
        var newAccessToken = getSimpleAccessToken(Instant.now(), Instant.now().plus(Duration.ofHours(1)));
        var newRefreshToken = getSimpleRefreshToken(Instant.now(), Instant.now().plus(Duration.ofHours(2)));


        var targetResponse = Mockito.mock(ClientResponse.class);
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://test.host/some-resource")).build();
        ExchangeFunction targetExchangeFunction = r -> Mono.just(targetResponse);

        when(authenticator.authenticate())
                .thenReturn(Mono.just(new OIDCTokenPair(expiredAccessToken, Optional.of(expiredRefreshToken))))
                .thenReturn(Mono.just(new OIDCTokenPair(newAccessToken, Optional.of(newRefreshToken))));
        doReturn(HttpStatusCode.valueOf(HttpStatus.SC_OK)).when(targetResponse).statusCode();

        // This in fact runs the first request with an expired token. However, we are not interested in that fact
        // here. It's more about filling the cache for the next request to the same host.
        StepVerifier.create(authenticatorMiddleware.filter(request, targetExchangeFunction))
                .expectNext(targetResponse)
                .expectComplete()
                .verify();
        StepVerifier.create(authenticatorMiddleware.filter(request, targetExchangeFunction))
                .expectNext(targetResponse)
                .expectComplete()
                .verify();

        verify(authenticator, Mockito.times(2)).authenticate();
        verify(authenticator, Mockito.never()).refresh(Mockito.any(OAuth2RefreshToken.class));
    }

    @Test
    void attemptsNewRequestWithNewAccessTokenIfServerRespondsWithUnauthorized() {
        var expiredAccessToken = getSimpleAccessToken(Instant.now().minus(Duration.ofHours(2)),
                Instant.now().minus(Duration.ofHours(1)));
        var refreshToken = getSimpleRefreshToken(Instant.now(), Instant.now().plus(Duration.ofHours(1)));
        var newAccessToken = getSimpleAccessToken(Instant.now(), Instant.now().plus(Duration.ofHours(1)));
        var newRefreshToken = getSimpleRefreshToken(Instant.now(), Instant.now().plus(Duration.ofHours(2)));

        var targetResponse = Mockito.mock(ClientResponse.class);
        var unauthorizedTargetResponse = ClientResponse.create(HttpStatusCode.valueOf(HttpStatus.SC_UNAUTHORIZED))
                .body("unauthorized").build();
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://test.host/some-resource")).build();
        ExchangeFunction targetExchangeFunction = Mockito.mock(ExchangeFunction.class);

        doReturn(Mono.just(new OIDCTokenPair(expiredAccessToken, Optional.of(refreshToken))))
                .when(authenticator).authenticate();
        doReturn(Mono.just(new OIDCTokenPair(newAccessToken, Optional.of(newRefreshToken))))
                .when(authenticator).refresh(refreshToken);

        var requestCaptor = ArgumentCaptor.forClass(ClientRequest.class);
        doReturn(Mono.just(unauthorizedTargetResponse), Mono.just(targetResponse))
                .when(targetExchangeFunction)
                .exchange(requestCaptor.capture());

        StepVerifier.create(authenticatorMiddleware.filter(request, targetExchangeFunction))
                .expectNext(targetResponse)
                .expectComplete()
                .verify();
        verify(authenticator, Mockito.times(1)).authenticate();
        verify(authenticator, Mockito.times(1)).refresh(refreshToken);

        var capturedRequests = requestCaptor.getAllValues();

        assertEquals(2, capturedRequests.size());
        assertEquals(expiredAccessToken.getTokenValue(), getBearerToken(capturedRequests.getFirst()));
        assertEquals(newAccessToken.getTokenValue(), getBearerToken(capturedRequests.getLast()));
    }

    @Test
    void requestEventuallyFailsIfRetryIsStillUnauthorized() {
        var expiredAccessToken = getSimpleAccessToken(Instant.now().minus(Duration.ofHours(2)),
                Instant.now().minus(Duration.ofHours(1)));
        var refreshToken = getSimpleRefreshToken(Instant.now(), Instant.now().plus(Duration.ofHours(1)));
        var newAccessToken = getSimpleAccessToken(Instant.now(), Instant.now().plus(Duration.ofHours(1)));
        var newRefreshToken = getSimpleRefreshToken(Instant.now(), Instant.now().plus(Duration.ofHours(2)));

        var unauthorizedTargetResponse = ClientResponse.create(HttpStatusCode.valueOf(HttpStatus.SC_UNAUTHORIZED))
                .body("unauthorized").build();
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://test.host/some-resource")).build();
        ExchangeFunction targetExchangeFunction = Mockito.mock(ExchangeFunction.class);

        doReturn(Mono.just(new OIDCTokenPair(expiredAccessToken, Optional.of(refreshToken))))
                .when(authenticator).authenticate();
        doReturn(Mono.just(new OIDCTokenPair(newAccessToken, Optional.of(newRefreshToken))))
                .when(authenticator).refresh(refreshToken);

        var requestCaptor = ArgumentCaptor.forClass(ClientRequest.class);
        doReturn(Mono.just(unauthorizedTargetResponse))
                .when(targetExchangeFunction)
                .exchange(requestCaptor.capture());

        StepVerifier.create(authenticatorMiddleware.filter(request, targetExchangeFunction))
                .expectNext(unauthorizedTargetResponse)
                .expectComplete()
                .verify();
        verify(authenticator, Mockito.times(1)).authenticate();
        verify(authenticator, Mockito.times(1)).refresh(refreshToken);

        var capturedRequests = requestCaptor.getAllValues();

        assertEquals(2, capturedRequests.size());
        assertEquals(expiredAccessToken.getTokenValue(), getBearerToken(capturedRequests.getFirst()));
        assertEquals(newAccessToken.getTokenValue(), getBearerToken(capturedRequests.getLast()));
    }
}
