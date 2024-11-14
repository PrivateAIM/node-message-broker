package de.privateaim.node_message_broker.message;

public class HubMessageForwardUndeliverableException extends RuntimeException {
    public HubMessageForwardUndeliverableException(String message) {
        super(message);
    }
}
