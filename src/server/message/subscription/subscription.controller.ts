/* eslint-disable max-classes-per-file */
// Disabled to allow keeping validation classes close to where they are used!
import {
    BadGatewayException,
    Body, Controller, Get, HttpCode, HttpException, InternalServerErrorException, NotFoundException, Param, Post, Req, Res, UseGuards,
} from '@nestjs/common';
import { IsUrl } from 'class-validator';
import { Request, Response } from 'express';
import { APIClient } from '@privateaim/core';
import type { SubscriptionDto } from './subscription.service';
import { MessageSubscriptionService } from './subscription.service';
import { AuthGuard } from '../../auth/auth.guard';

/**
 * Request data for adding a message subscription.
 * Makes use of auto-validation using `class-validator`.
 */
class AddSubscriptionRequestBody {
    @IsUrl({ require_tld: false })
        webhookUrl: URL;
    // TODO: might need some auth information as well (left out for brevity at the moment)
}

// TODO: this somehow needs to be handled by the API Client -> maybe introduce a wrapper later on
function handleHubApiError(err: any, analysisId: string) {
    if (err.statusCode !== undefined) {
        if ((err.statusCode as number) === 404) {
            throw new NotFoundException(`analysis '${analysisId}' does not exist`);
        }

        if ((err.statusCode as number) >= 500) {
            throw new BadGatewayException(`cannot check existence of analysis '${analysisId}'`, {
                cause: err,
                description: 'unrecoverable error when requesting central side (hub)',
            });
        }
    } else {
        throw new InternalServerErrorException(`cannot check existence of analysis '${analysisId}'`, { cause: err });
    }
}

/**
 * Bundles API endpoints related to message subscriptions.
 */
@Controller('analyses/:id/messages')
@UseGuards(AuthGuard)
export class MessageSubscriptionController {
    private readonly subscriptionService: MessageSubscriptionService;

    private readonly hubApiClient: APIClient;

    constructor(subscriptionService: MessageSubscriptionService, hubApiClient: APIClient) {
        this.subscriptionService = subscriptionService;
        this.hubApiClient = hubApiClient;
    }

    @Post('subscriptions')
    @HttpCode(201)
    async subscribe(@Param('id') analysisId: string, @Body() data: AddSubscriptionRequestBody, @Req() req: Request, @Res() res: Response) {
        return this.hubApiClient.analysis.getOne(analysisId)
            .catch((err) => handleHubApiError(err, analysisId))
            .then(() => this.subscriptionService.addSubscription({
                analysisId,
                webhookUrl: data.webhookUrl,
            }))
            .catch((err) => {
                if (err instanceof HttpException) {
                    throw err;
                } else {
                    throw new InternalServerErrorException(`cannot save subscription for analysis '${analysisId}'`, { cause: err });
                }
            })
            .then(({ id }) => {
                const subscriptionResourceLocation = `${req.protocol}://${req.get('Host')}${req.originalUrl}/${id}`;
                res.header('Location', subscriptionResourceLocation);
                res.json({
                    subscriptionId: id,
                });
            });
    }

    @Get('subscriptions/:id')
    async subscriptionInfo(@Param('id') id: string): Promise<SubscriptionDto> {
        return this.subscriptionService.getSubscription({ id });
    }
}
