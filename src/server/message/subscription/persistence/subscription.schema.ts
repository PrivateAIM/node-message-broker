import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import type { HydratedDocument } from 'mongoose';

@Schema()
export class Subscription {
    @Prop({ type: String, required: true, immutable: true })
        id: string;

    @Prop({ type: String, required: true, immutable: true })
        analysisId: string;

    @Prop({ type: String, required: true, immutable: true })
        webhookUrl: string;
}

export type SubscriptionDocument = HydratedDocument<Subscription>;

export const SubscriptionSchema = SchemaFactory.createForClass(Subscription);

SubscriptionSchema.index({ id: 1 }, { name: 'unique_id', unique: true });
SubscriptionSchema.index({ analysisId: 1 });
