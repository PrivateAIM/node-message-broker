import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { SubscriptionSchema } from './persistence/subscription.schema';
import { MessageSubscriptionService } from './subscription.service';
import { MessageSubscriptionController } from './subscription.controller';

/**
 * A Nest.js module that handles message subscription functionality.
 */
@Module({
    imports: [MongooseModule.forFeature([{ name: 'subscription', schema: SubscriptionSchema }])],
    controllers: [MessageSubscriptionController],
    providers: [MessageSubscriptionService],
    exports: [MessageSubscriptionService],
})
export class MessageSubscriptionModule { }
