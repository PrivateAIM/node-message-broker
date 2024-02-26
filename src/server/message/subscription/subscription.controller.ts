/* eslint-disable max-classes-per-file */
// Disabled to allow keeping validation classes close to where they are used!
import {
    Body, Controller, Get, HttpCode, Param, Post, Req, Res,
} from '@nestjs/common';
import { IsNotEmpty, IsString, IsUrl } from 'class-validator';
import { Request, Response } from 'express';
import type { SubscriptionDto } from './subscription.service';
import { MessageSubscriptionService } from './subscription.service';

/**
 * Request data for adding a message subscription.
 * Makes use of auto-validation using `class-validator`.
 */
class AddSubscriptionRequestBody {
    @IsString()
    @IsNotEmpty()
        analysisId: string;

    @IsUrl({ require_tld: false })
        webhookUrl: URL;
    // TODO: might need some auth information as well (left out for brevity at the moment)
}

/**
 * Bundles API endpoints related to message subscriptions.
 */
@Controller('messages')
export class MessageSubscriptionController {
    private readonly subscriptionService: MessageSubscriptionService;

    constructor(subscriptionService: MessageSubscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Post('subscriptions')
    @HttpCode(201)
    async subscribe(@Body() data: AddSubscriptionRequestBody, @Req() req: Request, @Res() res: Response) {
        const { id } = await this.subscriptionService.addSubscription({
            analysisId: data.analysisId,
            webhookUrl: data.webhookUrl,
        });
        const subscriptionResourceLocation = `${req.protocol}://${req.get('Host')}${req.originalUrl}/${id}`;
        res.header('Location', subscriptionResourceLocation);
        res.json({
            subscriptionId: id,
        });
    }

    @Get('subscriptions/:id')
    async subscriptionInfo(@Param('id') id: string): Promise<SubscriptionDto> {
        return this.subscriptionService.getSubscription({ id });
    }
}
