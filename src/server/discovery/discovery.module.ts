import { Module } from '@nestjs/common';
import {
    APIClient as HubApiClient,
} from '@privateaim/core';
import { ConfigService } from '@nestjs/config';
import type { ClientResponseErrorTokenHookOptions } from '@authup/core';
import { mountClientResponseErrorTokenHook } from '@authup/core';
import { DiscoveryController } from './discovery.controller';
import { DISCOVERY_SERVICE, HubBackedDiscoveryService } from './discovery.service';

/**
 * A Nest.js module that offers discovery functionality for analyses.
 */
@Module({
    controllers: [DiscoveryController],
    providers: [
        {
            // TODO: This is a duplicate of what has been done in the message module.
            //       Needs to be refactored later!
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
            provide: DISCOVERY_SERVICE,
            useFactory: async (hubApiClient: HubApiClient, configService: ConfigService) => {
                const ownNodeId = configService.getOrThrow<string>('hub.auth.robotId');
                return new HubBackedDiscoveryService(hubApiClient, ownNodeId);
            },
            inject: [HubApiClient, ConfigService],
        },
    ],
})
export class DiscoveryModule { }
