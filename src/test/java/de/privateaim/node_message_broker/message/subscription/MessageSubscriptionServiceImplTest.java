package de.privateaim.node_message_broker.message.subscription;

import de.privateaim.node_message_broker.message.subscription.persistence.MessageSubscriptionRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

@ExtendWith(MockitoExtension.class)
public class MessageSubscriptionServiceImplTest {

    private static String TEST_ANALYSIS_ID;
    private static URL TEST_WEBHOOK_URL;

    @Mock
    private MessageSubscriptionRepository subscriptionRepository;

    @InjectMocks
    private MessageSubscriptionServiceImpl service;

    @BeforeAll
    public static void setUp() throws URISyntaxException, MalformedURLException {
        TEST_ANALYSIS_ID = "7495ed61-5cda-4290-aed9-dd49de1f31ee";
        TEST_WEBHOOK_URL = new URI("https://my-target.org/my-target-path").toURL();
    }

    @Test
    public void addNewSubscription() {
        service.addSubscription(TEST_ANALYSIS_ID, TEST_WEBHOOK_URL);
    }

    @Test
    public void getSubscription() {
    }

    @Test
    public void getNonExistingSubscription() {
    }

    @Test
    public void deleteSubscription() {
    }

    @Test
    public void deleteNonExistingSubscription() {
    }

    @Test
    public void listingSubscriptionsWhenThereAreNone() {
    }

    @Test
    public void listingSubscriptions() {
    }
}
