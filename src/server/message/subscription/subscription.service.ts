import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { v4 } from 'uuid';
import type { Subscription } from './persistence/subscription.schema';

/**
 * Describes an identifier of a subscription.
 */
export type SubscriptionIdentifier = {
    id: string
};

/**
 * Describes just the relevant information of a subscription without any identifying parts.
 */
export type SubscriptionInfo = {
    analysisId: string
    webhookUrl: URL
};

/**
 * Describes an identified subscription with all relevant information.
 */
export type SubscriptionDto = SubscriptionIdentifier & SubscriptionInfo;

/**
 * A service for managing message subscriptions.
 * Message subscriptions describe simple webhooks that can be used to inform consumers about new
 * messages without the need for them polling an API all the time.
 */
@Injectable()
export class MessageSubscriptionService {
    private readonly Subscription: Model<Subscription>;

    constructor(@InjectModel('subscription') subscription: Model<Subscription>) {
        this.Subscription = subscription;
    }

    /**
     * Adds a new message subscription.
     *
     * @param {SubscriptionInfo} info Relevant information about the subscriptions that shall be added.
     * @returns A promise of the identifier for the newly added subscription.
     */
    async addSubscription(info: SubscriptionInfo): Promise<SubscriptionIdentifier> {
        const subscriptionId = v4();

        await new this.Subscription({
            id: subscriptionId,
            analysisId: info.analysisId,
            webhookUrl: info.webhookUrl,
        }).save();

        return Promise.resolve({
            id: subscriptionId,
        });
    }

    /**
     * Gets information about an existing subscription.
     *
     * @param {SubscriptionIdentifier} subId Identifier of the subscription.
     * @returns A promise of the subscription.
     * @throws {NotFoundException} If no subscription can be found that is identified by the given identifier.
     */
    async getSubscription(subId: SubscriptionIdentifier): Promise<SubscriptionDto> {
        const subscription = await this.Subscription.findOne({ id: subId.id });
        if (subscription == null || subscription === undefined) {
            throw new NotFoundException(`subscription with ID: ${subId.id} does not exist`);
        }

        return Promise.resolve({
            id: subscription.id,
            analysisId: subscription.analysisId,
            webhookUrl: new URL(subscription.webhookUrl),
        });
    }

    /**
     * Deletes an existing subscription.
     *
     * @param {SubscriptionIdentifier} subId Identifier of the subscription.
     * @throws {NotFoundException} If no subscription can be found that is identified by the given identifier.
     */
    async deleteSubscription(subId: SubscriptionIdentifier) {
        const subscription = await this.Subscription.findOneAndDelete({ id: subId.id });
        if (subscription == null || subscription === undefined) {
            throw new NotFoundException(`subscription with ID: ${subId.id} does not exist`);
        }
    }
}
