import { Injectable } from '@nestjs/common';
import type { AxiosError } from 'axios';
import axios, { HttpStatusCode } from 'axios';
import { Agent as HttpAgent } from 'http';
import { Agent as HttpsAgent } from 'https';

/**
 * Error cases for emitting messages to a node side client.
 */
export enum MessageNodeEmitterError {
    RECEIVER_UNAVAILABLE = 'ReceiverUnavailable',
    // AuthenticationFailed = "AuthenticationFailed", // TODO: might be required in the future
    RECEIVER_REJECTED_REQUEST = 'ReceiverRejectedRequest',
    RECEIVER_WITH_INTERNAL_ERROR = 'ReceiverWithInternalError',
    NO_RESPONSE_RECEIVED = 'NoResponseReceived',
    UNKNOWN = 'Unknown',
}

function convertStatusCodeToError(status: number): MessageNodeEmitterError {
    switch (status) {
        case HttpStatusCode.ServiceUnavailable:
            return MessageNodeEmitterError.RECEIVER_UNAVAILABLE;
        case HttpStatusCode.BadRequest:
            return MessageNodeEmitterError.RECEIVER_REJECTED_REQUEST;
        case HttpStatusCode.InternalServerError:
            return MessageNodeEmitterError.RECEIVER_WITH_INTERNAL_ERROR;
        default: return MessageNodeEmitterError.UNKNOWN;
    }
}

/**
 * Metadata for emitting a message to a node side client.
 */
export type MessageReceiverMetadata = {
    webhookUrl: URL
    // TODO: add potentially necessary auth information here
};

/**
 * A service for emitting messages to a node side client using a webhook URL.
 */
@Injectable()
export class MessageNodeEmitterService {
    private readonly httpAgent;

    private readonly httpsAgent;

    private constructor(httpAgent: HttpAgent, httpsAgent: HttpsAgent) {
        this.httpAgent = httpAgent;
        this.httpsAgent = httpsAgent;
    }

    static create(): MessageNodeEmitterService {
        return new MessageNodeEmitterService(
            new HttpAgent({ keepAlive: true }),
            new HttpsAgent({ keepAlive: true }),
        );
    }

    async emitMessage(receiver: MessageReceiverMetadata, message: any): Promise<void | MessageNodeEmitterError> {
        return axios.post(
            receiver.webhookUrl.toString(),
            message,
            {
                headers: {
                    'Content-Type': 'application/json',
                },
                validateStatus: (status) => status < 400,
                httpAgent: this.httpAgent,
                httpsAgent: this.httpsAgent,
            },
        )
            .then(() => {

            })
            .catch((error: AxiosError) => {
                if (error.response) {
                    // TODO: log
                    return convertStatusCodeToError(error.response.status);
                } if (error.request) {
                    return MessageNodeEmitterError.NO_RESPONSE_RECEIVED;
                }
                return MessageNodeEmitterError.UNKNOWN;
            });
    }
}
