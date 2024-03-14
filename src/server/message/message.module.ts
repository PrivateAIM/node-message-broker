import { Module } from '@nestjs/common';
import { MessageSubscriptionModule } from './subscription/subscription.module';
import { HubMessageModule } from './hub/hub.message.module';

/**
 * A Nest.js module that takes care of message functionality.
 */
@Module({
    imports: [MessageSubscriptionModule, HubMessageModule],
})
export class MessageModule { }
