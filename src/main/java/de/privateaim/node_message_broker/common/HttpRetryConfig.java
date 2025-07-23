package de.privateaim.node_message_broker.common;

/**
 * Configuration options for retrying failed HTTP requests.
 *
 * @param maxRetries   number of maximum retries carried out by the client in case of a retryable error
 * @param retryDelayMs time between retries in ms
 */
public record HttpRetryConfig(int maxRetries, int retryDelayMs) {
    public static final class Builder {
        private int maxRetries = 5;
        private int retryDelayMs = 2000;

        public Builder withMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder withRetryDelayMs(int retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
            return this;
        }

        public HttpRetryConfig build() {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be greater than 0");
            }

            if (retryDelayMs < 0) {
                throw new IllegalArgumentException("retryDelayMs must be greater than 0");
            }

            return new HttpRetryConfig(maxRetries, retryDelayMs);
        }
    }
}
