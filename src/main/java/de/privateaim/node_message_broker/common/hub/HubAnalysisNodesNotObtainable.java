package de.privateaim.node_message_broker.common.hub;

/**
 * Signals that analysis nodes could not be obtained from the hub core services.
 */
public final class HubAnalysisNodesNotObtainable extends RuntimeException {
    public HubAnalysisNodesNotObtainable(String message) {
        super(message);
    }
}
