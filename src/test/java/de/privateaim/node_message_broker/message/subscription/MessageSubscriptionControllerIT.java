package de.privateaim.node_message_broker.message.subscription;


import de.privateaim.node_message_broker.AbstractBaseDatabaseIT;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@WebAppConfiguration
@AutoConfigureMockMvc
public class MessageSubscriptionControllerIT extends AbstractBaseDatabaseIT {

    @Autowired
    private MockMvc mockMvc;


}
