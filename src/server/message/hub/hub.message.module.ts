import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { EventEmitter2, EventEmitterModule } from '@nestjs/event-emitter';
import { Agent as HttpAgent } from 'node:http';
import { Agent as HttpsAgent } from 'node:https';
import { Socket } from 'socket.io-client';
import type { ClientResponseErrorTokenHookOptions } from '@authup/core';
import {
    APIClient as AuthupAPIClient,
    mountClientResponseErrorTokenHook,
} from '@authup/core';
import type {
    SocketMessagesNamespaceCTSMessagesEvents,
    SocketMessagesNamespaceSTCEvents,
} from '@privateaim/core';
import {
    APIClient as HubApiClient,
} from '@privateaim/core';
import { HubMessageController } from './hub.message.controller';
import { SocketNamespaceName, createHubMessageSocket, registerMessageEventHandlers } from './hub.socket';
import { HubMessageProducerService } from './hub.message.producer.service';
import { HubClient } from './hub.client';
import { HubAuth } from './hub.auth';
import { HubMessageConsumerService } from './hub.message.consumer.service';
import { MessageSubscriptionService } from '../subscription/subscription.service';
import { MessageSubscriptionModule } from '../subscription/subscription.module';

/**
 * A Nest.js module that handles message exchanges with the central side (hub).
 */
@Module({
    imports: [
        EventEmitterModule.forRoot({
            delimiter: '.',
            wildcard: false,
            newListener: false,
            removeListener: false,
            verboseMemoryLeak: false,
            ignoreErrors: false,
        }),
        MessageSubscriptionModule,
    ],
    controllers: [HubMessageController],
    providers: [
        {
            provide: HttpAgent,
            useFactory: async () => new HttpAgent({
                keepAlive: true,
            }),
        },
        {
            provide: HttpsAgent,
            useFactory: async () => new HttpsAgent({
                keepAlive: true,
            }),
        },
        {
            provide: HubAuth,
            useFactory: async (configService: ConfigService) => {
                const authClient = new AuthupAPIClient({
                    baseURL: configService.getOrThrow<string>('hub.auth.baseUrl'),
                });
                mountClientResponseErrorTokenHook(authClient, {
                    tokenCreator: {
                        type: 'robot',
                        id: configService.getOrThrow<string>('hub.auth.robotId'),
                        secret: configService.getOrThrow<string>('hub.auth.robotSecret'),
                    },
                });

                return new HubAuth(authClient);
            },
            inject: [ConfigService],
        },
        {
            provide: HubApiClient,
            useFactory: async (configService: ConfigService) => {
                const hookOptions: ClientResponseErrorTokenHookOptions = {
                    baseURL: configService.getOrThrow<string>('hub.auth.baseUrl'),
                    tokenCreator: {
                        type: 'robot',
                        id: configService.getOrThrow<string>('hub.auth.robotId'),
                        secret: configService.getOrThrow<string>('hub.auth.robotSecret'),
                    },
                };

                const hubClient = new HubApiClient({
                    baseURL: configService.get<string>('hub.baseUrl'),
                });
                mountClientResponseErrorTokenHook(hubClient, hookOptions);

                return hubClient;
            },
            inject: [ConfigService],
        },
        {
            provide: Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>,
            useFactory: async (configService: ConfigService, eventEmitter: EventEmitter2, hubAuth: HubAuth) => {
                const token = await hubAuth.obtainAuthToken(
                    configService.getOrThrow<string>('hub.auth.robotId'),
                    configService.getOrThrow<string>('hub.auth.robotSecret'),
                );

                return createHubMessageSocket(
                    {
                        hubBaseUrl: configService.getOrThrow<string>('hub.baseUrl'),
                        namespaceName: SocketNamespaceName.MESSAGES,
                    },
                    {
                        auth: {
                            token,
                        },
                    },
                )
                    .then((socket) => {
                        registerMessageEventHandlers(socket, eventEmitter);
                        return socket;
                    });
            },
            inject: [ConfigService, EventEmitter2, HubAuth],
        },
        {
            provide: HubClient,
            useFactory: async (
                hubApiClient: HubApiClient,
                hubMessageSocket: Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>,
            ) => new HubClient(hubApiClient, hubMessageSocket),
            inject: [HubApiClient, Socket<SocketMessagesNamespaceSTCEvents, SocketMessagesNamespaceCTSMessagesEvents>],
        },
        {
            provide: HubMessageConsumerService,
            useFactory: async (
                subService: MessageSubscriptionService,
                httpAgent: HttpAgent,
                httpsAgent: HttpsAgent,
            ) => new HubMessageConsumerService(subService, httpAgent, httpsAgent),
            inject: [MessageSubscriptionService, HttpAgent, HttpsAgent],
        },
        HubMessageProducerService,
    ],
})
export class HubMessageModule { }
