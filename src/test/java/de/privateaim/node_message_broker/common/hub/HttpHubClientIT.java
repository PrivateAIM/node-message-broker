package de.privateaim.node_message_broker.common.hub;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import de.privateaim.node_message_broker.common.HttpRetryConfig;
import de.privateaim.node_message_broker.common.hub.api.AnalysisNode;
import de.privateaim.node_message_broker.common.hub.api.HubResponseContainer;
import de.privateaim.node_message_broker.common.hub.api.Node;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HttpHubClientIT {

    private static final int MAX_RETRIES = 5;
    private static final int RETRY_DELAY_MILLIS = 10; // keeping it short for testing purposes!

    private static final String ANALYSIS_ID = "test-analysis-id";
    private static final String ROBOT_ID = "robot-123";

    private static final ObjectMapper JSON = new ObjectMapper();

    private MockWebServer mockWebServer;
    private HubClient httpHubClient;


    @BeforeEach
    void setUp() {
        mockWebServer = new MockWebServer();
        var webClient = WebClient.create(mockWebServer.url("/").toString());
        httpHubClient = new HttpHubClient(webClient, new HttpRetryConfig(MAX_RETRIES, RETRY_DELAY_MILLIS));
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Nested
    public class AnalysisNodesTests {
        @Test
        void obtainingAnalysisNodesSucceedsOnFirstTry() throws JsonProcessingException {
            var node = new Node("test-node-id", "default", "not-relevant-here", ROBOT_ID);
            var analysisNodes = List.of(new AnalysisNode("id-1", "test-node-id", node));
            var mockedHubResponse = new HubResponseContainer<>(analysisNodes);

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            StepVerifier.create(httpHubClient.fetchAnalysisNodes(ANALYSIS_ID))
                    .expectNext(analysisNodes)
                    .verifyComplete();
        }

        @Test
        void obtainingAnalysisNodesSucceedsWithinRetryRange() throws JsonProcessingException, InterruptedException {
            var node = new Node("test-node-id", "default", "not-relevant-here", ROBOT_ID);
            var analysisNodes = List.of(new AnalysisNode("id-1", "test-node-id", node));
            var mockedHubResponse = new HubResponseContainer<>(analysisNodes);

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            StepVerifier.create(httpHubClient.fetchAnalysisNodes(ANALYSIS_ID))
                    .expectNext(analysisNodes)
                    .verifyComplete();

            assertEquals(2, mockWebServer.getRequestCount());

            for (int i = 0; i < 2; i++) {
                var recordedRequest = mockWebServer.takeRequest();
                var requestUrl = recordedRequest.getRequestUrl();

                assertNotNull(requestUrl);
                assertNotNull(recordedRequest.getPath());
                assertEquals(ANALYSIS_ID, requestUrl.queryParameter("filter[analysis_id]"));
                assertEquals("node", requestUrl.queryParameter("include"));
                assertEquals("/analysis-nodes", URI.create(recordedRequest.getPath()).getPath());
            }
        }

        @Test
        void obtainingAnalysisNodesFailsAfterMaximumNumberOfRetriesOnServerError() throws InterruptedException {
            for (int i = 0; i < (MAX_RETRIES + 1); i++) {
                mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));
            }

            StepVerifier.create(httpHubClient.fetchAnalysisNodes(ANALYSIS_ID))
                    .verifyError(HubAnalysisNodesNotObtainable.class);

            for (int i = 0; i < (MAX_RETRIES + 1); i++) {
                var recordedRequest = mockWebServer.takeRequest();
                var requestUrl = recordedRequest.getRequestUrl();

                assertNotNull(requestUrl);
                assertNotNull(recordedRequest.getPath());
                assertEquals(ANALYSIS_ID, requestUrl.queryParameter("filter[analysis_id]"));
                assertEquals("node", requestUrl.queryParameter("include"));
                assertEquals("/analysis-nodes", URI.create(recordedRequest.getPath()).getPath());
            }
        }
    }

    @Nested
    public class PublicKeyTests {

        private RSAPublicKey generateRSAPublicKey() throws NoSuchAlgorithmException, NoSuchProviderException,
                InvalidAlgorithmParameterException, IOException {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA", "BC");
            gen.initialize(new RSAKeyGenParameterSpec(3072, RSAKeyGenParameterSpec.F4));
            return (RSAPublicKey) gen.generateKeyPair().getPublic();

//            var bos = new ByteArrayOutputStream();
//            var writer = new OutputStreamWriter(bos);
//
//            var pemWriter = new PemWriter(writer);
//            pemWriter.writeObject(new PemObject("RSA PUBLIC KEY", publicKey.getEncoded()));
//            pemWriter.close();
//
//            return new String(Hex.encode(bos.toByteArray()));
        }

        private ECPublicKey generateECDHPublicKey() throws NoSuchAlgorithmException, NoSuchProviderException {
            var gen = KeyPairGenerator.getInstance("EC", "BC");
            gen.initialize(384);

            return (ECPublicKey) gen.generateKeyPair().getPublic();

//            var bos = new ByteArrayOutputStream();
//            var writer = new OutputStreamWriter(bos);
//
//            var pemWriter = new PemWriter(writer);
//            pemWriter.writeObject(new PemObject("PUBLIC KEY", publicKey.getEncoded()));
//            pemWriter.close();
//
//            return new String(Hex.encode(bos.toByteArray()));
        }

        private String convertToPem(String description, Key key) throws IOException {
            var bos = new ByteArrayOutputStream();
            var writer = new OutputStreamWriter(bos);

            var pemWriter = new PemWriter(writer);
            pemWriter.writeObject(new PemObject(description, key.getEncoded()));
            pemWriter.close();

            return bos.toString();
        }

        private String hexEncoded(String subject) {
            return new String(Hex.encode(subject.getBytes()));
        }

        @Test
        void nodeAssociatedWithRobotIdIsNotFound() throws JsonProcessingException {
            var mockedHubResponse = new HubResponseContainer<>(List.of());
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            StepVerifier.create(httpHubClient.fetchPublicKey(ROBOT_ID))
                    .expectError(NoMatchingNodeFoundException.class)
                    .verify();
        }

        @Test
        void publicKeyDoesNotExistOnNodeWithRobotId() throws JsonProcessingException {
            var node = new Node("test-node-id", "default", null, ROBOT_ID);
            var mockedHubResponse = new HubResponseContainer<>(List.of(node));
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            StepVerifier.create(httpHubClient.fetchPublicKey(ROBOT_ID))
                    .expectError(NoPublicKeyException.class)
                    .verify();
        }

        @Test
        void publicKeyIsNotAnECDHKey() throws IOException, NoSuchAlgorithmException, NoSuchProviderException,
                InvalidAlgorithmParameterException {
            var securityProvider = new BouncyCastleProvider();
            Security.addProvider(securityProvider);
            var rsaPublicKey = hexEncoded(convertToPem("RSA PUBLIC KEY", generateRSAPublicKey()));

            var node = new Node("test-node-id", "default", rsaPublicKey, ROBOT_ID);
            var mockedHubResponse = new HubResponseContainer<>(List.of(node));
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            StepVerifier.create(httpHubClient.fetchPublicKey(ROBOT_ID))
                    .expectError(MalformedPublicKeyException.class)
                    .verify();
        }

        @Test
        void succeedsIfPublicKeyIsAnECDHKey() throws NoSuchAlgorithmException, IOException, NoSuchProviderException {
            var securityProvider = new BouncyCastleProvider();
            Security.addProvider(securityProvider);
            var ecdhPublicKey = generateECDHPublicKey();
            var encodedEcdhPublicKey = hexEncoded(convertToPem("PUBLIC KEY", ecdhPublicKey));

            var node = new Node("test-node-id", "default", encodedEcdhPublicKey, ROBOT_ID);
            var mockedHubResponse = new HubResponseContainer<>(List.of(node));
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            StepVerifier.create(httpHubClient.fetchPublicKey(ROBOT_ID))
                    .expectNext(ecdhPublicKey)
                    .verifyComplete();
        }

        @Test
        void succeedsWithinRetryRange() throws NoSuchAlgorithmException, IOException, NoSuchProviderException,
                InterruptedException {
            var securityProvider = new BouncyCastleProvider();
            Security.addProvider(securityProvider);
            var ecdhPublicKey = generateECDHPublicKey();
            var encodedEcdhPublicKey = hexEncoded(convertToPem("PUBLIC KEY", ecdhPublicKey));

            var node = new Node("test-node-id", "default", encodedEcdhPublicKey, ROBOT_ID);
            var mockedHubResponse = new HubResponseContainer<>(List.of(node));

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));
            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(JSON.writeValueAsString(mockedHubResponse)));

            StepVerifier.create(httpHubClient.fetchPublicKey(ROBOT_ID))
                    .expectNext(ecdhPublicKey)
                    .verifyComplete();

            assertEquals(2, mockWebServer.getRequestCount());

            for (int i = 0; i < 2; i++) {
                var recordedRequest = mockWebServer.takeRequest();
                var requestUrl = recordedRequest.getRequestUrl();

                assertNotNull(requestUrl);
                assertNotNull(recordedRequest.getPath());
                assertEquals(ROBOT_ID, requestUrl.queryParameter("filter[robot_id]"));
                assertEquals("/nodes", URI.create(recordedRequest.getPath()).getPath());
            }
        }

        @Test
        void failsIfMaximumNumberOfRetriesHasBeenExhausted() throws InterruptedException {
            for (int i = 0; i < (MAX_RETRIES + 1); i++) {
                mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_SERVICE_UNAVAILABLE));
            }

            StepVerifier.create(httpHubClient.fetchPublicKey(ROBOT_ID))
                    .verifyError(HubNodePublicKeyNotObtainable.class);

            for (int i = 0; i < (MAX_RETRIES + 1); i++) {
                var recordedRequest = mockWebServer.takeRequest();
                var requestUrl = recordedRequest.getRequestUrl();

                assertNotNull(requestUrl);
                assertNotNull(recordedRequest.getPath());
                assertEquals(ROBOT_ID, requestUrl.queryParameter("filter[robot_id]"));
                assertEquals("/nodes", URI.create(recordedRequest.getPath()).getPath());
            }
        }
    }
}
