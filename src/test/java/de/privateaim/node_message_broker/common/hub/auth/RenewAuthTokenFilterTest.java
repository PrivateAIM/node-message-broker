package de.privateaim.node_message_broker.common.hub.auth;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RenewAuthTokenFilterTest {

    private static final String ROBOT_ID = "test-id";
    private static final String ROBOT_SECRET = "test-secret";

    @Mock
    private HubAuthClient hubAuthClientMock;
    private RenewAuthTokenFilter renewAuthTokenFilter;

    @BeforeEach
    void setUp() {
        renewAuthTokenFilter = new RenewAuthTokenFilter(hubAuthClientMock, ROBOT_ID, ROBOT_SECRET);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(hubAuthClientMock);
    }

    @Test
    void requestDoesNotGetReAuthenticatedOnSuccess() {
        var targetResponse = Mockito.mock(ClientResponse.class);
        doReturn(HttpStatusCode.valueOf(HttpStatus.SC_OK)).when(targetResponse).statusCode();
        var request = ClientRequest.create(HttpMethod.GET, URI.create("/some-resource")).build();

        ExchangeFunction targetExchangeFunction = r -> Mono.just(targetResponse);

        var actualResponse = renewAuthTokenFilter.filter(request, targetExchangeFunction);

        StepVerifier.create(actualResponse)
                .expectNext(targetResponse)
                .expectComplete()
                .verify();
    }

    @Test
    void requestGetsReAuthenticatedOnFailure() {
        doReturn(Mono.just("some-access-token")).when(hubAuthClientMock).requestAccessToken(ROBOT_ID, ROBOT_SECRET);
        var unauthorizedTargetResponse = ClientResponse.create(HttpStatusCode.valueOf(HttpStatus.SC_UNAUTHORIZED))
                .body("unauthorized").build();
        var request = ClientRequest.create(HttpMethod.GET, URI.create("/some-resource")).build();

        var reAuthenticatedTargetResponse = Mockito.mock(ClientResponse.class);
        var targetExchangeFunction = Mockito.mock(ExchangeFunction.class);
        doReturn(Mono.just(unauthorizedTargetResponse), Mono.just(reAuthenticatedTargetResponse))
                .when(targetExchangeFunction)
                .exchange(Mockito.any(ClientRequest.class));

        var actualResponse = renewAuthTokenFilter.filter(request, targetExchangeFunction);

        StepVerifier.create(actualResponse)
                .expectNext(reAuthenticatedTargetResponse)
                .expectComplete()
                .verify();

        verify(hubAuthClientMock, times(1))
                .requestAccessToken(ROBOT_ID, ROBOT_SECRET);
    }

    @Test
    void requestUltimatelyFailsOnReAuthenticatedRequestFailure() {
        doReturn(Mono.error(new Exception("some-error"))).when(hubAuthClientMock)
                .requestAccessToken(ROBOT_ID, ROBOT_SECRET);

        var unauthorizedTargetResponse = ClientResponse.create(HttpStatusCode.valueOf(HttpStatus.SC_UNAUTHORIZED))
                .body("unauthorized").build();
        var request = ClientRequest.create(HttpMethod.GET, URI.create("/some-resource")).build();

        var targetExchangeFunction = Mockito.mock(ExchangeFunction.class);
        doReturn(Mono.just(unauthorizedTargetResponse))
                .when(targetExchangeFunction)
                .exchange(Mockito.any(ClientRequest.class));

        var actualResponse = renewAuthTokenFilter.filter(request, targetExchangeFunction);

        StepVerifier.create(actualResponse)
                .expectError(Exception.class)
                .verify();

        verify(hubAuthClientMock, times(1))
                .requestAccessToken(ROBOT_ID, ROBOT_SECRET);
    }
}
