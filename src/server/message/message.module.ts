import { Module } from '@nestjs/common';
import { MessageSubscriptionModule } from './subscription/subscription.module';

/**
 * A Nest.js module that takes care of message functionality.
 */
@Module({
    imports: [MessageSubscriptionModule],
})
export class MessageModule { }
