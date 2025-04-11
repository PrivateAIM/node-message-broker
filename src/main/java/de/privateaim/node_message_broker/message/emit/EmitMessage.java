package de.privateaim.node_message_broker.message.emit;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents a message in internal format that is supposed to get emitted and sent to a recipient.
 *
 * @param recipient recipient of the message
 * @param payload   the actual message payload
 * @param context   meta information regarding the message
 */
public record EmitMessage(
        EmitMessageRecipient recipient,
        byte[] payload,
        EmitMessageContext context
) {
    /**
     * Creates a new builder for an {@link EmitMessage} instance.
     *
     * @return the builder.
     */
    public static SendToBuilder builder() {
        return new MessageBuilder();
    }

    public EmitMessage(@NotNull MessageBuilder messageBuilder) {
        this(messageBuilder.getRecipient(), messageBuilder.getPayload(), messageBuilder.getContext());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof EmitMessage)) {
            return false;
        }
        EmitMessage msg = (EmitMessage) other;
        return (recipient.equals(msg.recipient) && Objects.deepEquals(payload, msg.payload) && context.equals(msg.context));
    }

    /**
     * Represents a builder step for creating an {@link EmitMessage} instance by adding recipient information.
     */
    public interface SendToBuilder {
        /**
         * Sets the recipient of the message.
         *
         * @param recipient the recipient
         * @return The next builder step.
         */
        WithPayloadBuilder sendTo(@NotNull EmitMessageRecipient recipient);
    }

    /**
     * Represents a builder step for creating an {@link EmitMessage} instance by adding the message payload.
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
     * Represents a builder step for creating an {@link EmitMessage} instance by adding meta information for a message.
     */
    public interface InContextBuilder {
        /**
         * Sets the message's meta information.
         *
         * @param context meta information of the message
         * @return The next builder step.
         */
        Buildable inContext(@NotNull EmitMessageContext context);
    }

    /**
     * Represents the final builder step for creating an {@link EmitMessage} instance.
     */
    public interface Buildable {
        /**
         * Builds the {@link EmitMessage} instance.
         *
         * @return The built instance.
         */
        EmitMessage build();
    }

    /**
     * A builder for constructing an {@link EmitMessage} instance.
     */
    @Getter
    public static final class MessageBuilder implements SendToBuilder, WithPayloadBuilder, InContextBuilder, Buildable {
        private EmitMessageRecipient recipient;
        private byte[] payload;
        private EmitMessageContext context;

        /**
         * {@inheritDoc}
         */
        @Override
        public WithPayloadBuilder sendTo(@NotNull EmitMessageRecipient recipient) {
            this.recipient = requireNonNull(recipient, "message recipient must not be null");
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
        public Buildable inContext(@NotNull EmitMessageContext context) {
            this.context = requireNonNull(context, "message context must not be null");
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public EmitMessage build() {
            return new EmitMessage(this);
        }
    }
}
