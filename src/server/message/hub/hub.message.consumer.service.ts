import { Injectable, Logger } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import type { AxiosError, AxiosRequestConfig } from 'axios';
import axios from 'axios';
import { Agent as HttpAgent } from 'node:http';
import { Agent as HttpsAgent } from 'node:https';
import { HubMessageBroadcastEvent } from './events';
import { MessageSubscriptionService } from '../subscription/subscription.service';

/**
 * A service for consuming messages received from the central side (hub).
 */
@Injectable()
export class HubMessageConsumerService {
    private readonly logger: Logger = new Logger(HubMessageConsumerService.name);

    private readonly subscriptionService: MessageSubscriptionService;

    private readonly httpAgent: HttpAgent;

    private readonly httpsAgent: HttpsAgent;

    constructor(subscriptionService: MessageSubscriptionService, httpAgent: HttpAgent, httpsAgent: HttpsAgent) {
        this.subscriptionService = subscriptionService;
        this.httpAgent = httpAgent;
        this.httpsAgent = httpsAgent;
    }

    /**
     * Handles an incoming message broadcast event.
     * Forwards the wrapped message to all clients that are subscribed to the analysis that this broadcast
     * message is associated with.
     *
     * @param { HubMessageBroadcastEvent } event the message event
     */
    @OnEvent('hub.message.broadcast.received')
    handleHubMessageBroadcastEvent(event: HubMessageBroadcastEvent) {
        this.subscriptionService.findSubscriptionsForAnalysis(event.analysisId)
            .catch((err) => {
                this.logger.error(err);
                throw err;
            })
            .then((subs) => subs.map((sub) => {
                const requestOpts: AxiosRequestConfig<any> = {
                    method: 'POST',
                    url: sub.webhookUrl.toString(),
                    headers: {
                        'Content-Type': 'appliction/json',
                    },
                    data: event.messagePayload,
                    httpAgent: this.httpAgent,
                    httpsAgent: this.httpsAgent,
                };

                return axios.request(requestOpts)
                    .then(() => {
                        this.logger.log(`sent message to client at ${sub.webhookUrl}`);
                    })
                    .catch((error: AxiosError) => {
                        if (error.response) {
                            this.logger.error(`[${error.response.status}] could not send message to client at ${sub.webhookUrl}: ${error.response.data}`);
                        } else if (error.request) {
                            this.logger.error(`could not get a response when sending message to client at ${sub.webhookUrl}`);
                        } else {
                            this.logger.error('could not set up request');
                        }
                    });
            }))
            .then((inFlightMessageForwardRequests) => {
                Promise.all(inFlightMessageForwardRequests);
            });
    }
}
