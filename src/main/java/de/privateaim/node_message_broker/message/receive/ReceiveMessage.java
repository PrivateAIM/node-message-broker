package de.privateaim.node_message_broker.message.receive;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents a message in internal format that has been received.
 *
 * @param sender  sender of the message
 * @param payload the actual message payload
 * @param context meta information regarding the message
 */
public record ReceiveMessage(
        ReceiveMessageSender sender,
        byte[] payload,
        ReceiveMessageContext context
) {

    /**
     * Creates a new builder for a {@link ReceiveMessage} instance.
     *
     * @return the builder.
     */
    public static SentFromBuilder builder() {
        return new MessageBuilder();
    }

    public ReceiveMessage(MessageBuilder messageBuilder) {
        this(messageBuilder.getSender(), messageBuilder.getPayload(), messageBuilder.getContex());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof ReceiveMessage)) {
            return false;
        }
        ReceiveMessage msg = (ReceiveMessage) other;
        return (sender.equals(msg.sender) && Objects.deepEquals(payload, msg.payload) && context.equals(msg.context));
    }

    /**
     * Represents a builder step for creating a {@link ReceiveMessage} instance by adding sender information.
     */
    public interface SentFromBuilder {
        /**
         * Sets the sender of the message.
         *
         * @param sender the sender
         * @return The next builder step.
         */
        WithPayloadBuilder sentFrom(@NotNull ReceiveMessageSender sender);
    }

    /**
     * Represents a builder step for creating a {@link ReceiveMessage} instance by adding the message payload.
     */
    public interface WithPayloadBuilder {
        /**
         * Sets the actual message payload.
         *
         * @param payload the message payload
         * @return The next builder step.
         */
        InContextBuilder withPayload(@NotNull byte[] payload);
    }

    /**
     * Represents a builder step for creating a {@link ReceiveMessage} instance by adding meta information for a message.
     */
    public interface InContextBuilder {
        /**
         * Sets the message's meta information.
         *
         * @param context meta information of the message
         * @return The next builder step.
         */
        Buildable inContext(@NotNull ReceiveMessageContext context);
    }

    /**
     * Represents the final builder step for creating a {@link ReceiveMessage} instance.
     */
    public interface Buildable {
        /**
         * Builds the {@link ReceiveMessage} instance.
         *
         * @return The build instance.
         */
        ReceiveMessage build();
    }

    /**
     * A builder for constructing a {@link ReceiveMessage} instance.
     */
    @Getter
    public static final class MessageBuilder implements SentFromBuilder, WithPayloadBuilder, InContextBuilder, Buildable {
        private ReceiveMessageSender sender;
        private byte[] payload;
        private ReceiveMessageContext contex;

        /**
         * {@inheritDoc}
         */
        @Override
        public WithPayloadBuilder sentFrom(@NotNull ReceiveMessageSender sender) {
            this.sender = requireNonNull(sender, "sender must not be null");
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InContextBuilder withPayload(@NotNull byte[] payload) {
            this.payload = requireNonNull(payload, "payload must not be null");
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Buildable inContext(@NotNull ReceiveMessageContext context) {
            this.contex = requireNonNull(context, "context must not be null");
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ReceiveMessage build() {
            return new ReceiveMessage(this);
        }
    }
}
