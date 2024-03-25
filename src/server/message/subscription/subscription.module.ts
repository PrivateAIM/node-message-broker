import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { APIClient } from '@privateaim/core';
import { ConfigService } from '@nestjs/config';
import type { ClientResponseErrorTokenHookOptions } from '@authup/core';
import { mountClientResponseErrorTokenHook } from '@authup/core';
import { SubscriptionSchema } from './persistence/subscription.schema';
import { MessageSubscriptionService } from './subscription.service';
import { MessageSubscriptionController } from './subscription.controller';

/**
 * A Nest.js module that handles message subscription functionality.
 */
@Module({
    imports: [MongooseModule.forFeature([{ name: 'subscription', schema: SubscriptionSchema }])],
    controllers: [MessageSubscriptionController],
    providers: [
        {
            provide: APIClient,
            useFactory: async (configService: ConfigService) => {
                const hookOptions: ClientResponseErrorTokenHookOptions = {
                    baseURL: configService.getOrThrow<string>('hub.auth.baseUrl'),
                    tokenCreator: {
                        type: 'robot',
                        id: configService.getOrThrow<string>('hub.auth.robotId'),
                        secret: configService.getOrThrow<string>('hub.auth.robotSecret'),
                    },
                };

                const hubClient = new APIClient({
                    baseURL: configService.get<string>('hub.baseUrl'),
                });
                mountClientResponseErrorTokenHook(hubClient, hookOptions);

                return hubClient;
            },
            inject: [ConfigService],
        },
        MessageSubscriptionService,
    ],
    exports: [MessageSubscriptionService],
})
export class MessageSubscriptionModule { }
