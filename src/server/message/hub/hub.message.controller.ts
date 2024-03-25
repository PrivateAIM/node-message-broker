/* eslint-disable max-classes-per-file */
import {
    BadRequestException,
    Body, Controller, HttpCode, NotFoundException, Param, Post, UseGuards,
} from '@nestjs/common';
import { ArrayNotEmpty, IsObject } from 'class-validator';
import { AuthGuard } from '../../auth/auth.guard';
import { HubMessageProducerService, MessageProducerError } from './hub.message.producer.service';

class SendMessageBody {
    @ArrayNotEmpty()
        recipients: Array<string>;

    @IsObject()
        message: Record<string, any>;
}

/**
 * Bundles API endpoints related to message exchange with the central side (hub).
 */
@Controller('analyses/:id/messages')
@UseGuards(AuthGuard)
export class HubMessageController {
    private readonly hubMessageProducerService: HubMessageProducerService;

    constructor(hubMessageProducerService: HubMessageProducerService) {
        this.hubMessageProducerService = hubMessageProducerService;
    }

    @Post('/broadcasts')
    @HttpCode(201)
    async sendNodeBroadcast(@Param('id') analysisId: string, @Body() messagePayload: Record<string, any>) {
        return this.hubMessageProducerService.produceNodeBroadcastMessage(analysisId, messagePayload);
    }

    @Post('/')
    @HttpCode(201)
    async send(@Param('id') analysisId: string, @Body() data: SendMessageBody) {
        return this.hubMessageProducerService.produceMessage(data.recipients, analysisId, data.message)
            .catch((err) => {
                if (err instanceof MessageProducerError) {
                    if (err.name === 'INVALID_RECIPIENTS') {
                        throw new BadRequestException('one or more of the given recipient node ids are invalid', {
                            cause: err,
                            description: err.message,
                        });
                    } else if (err.name === 'ANALYSIS_NOT_FOUND') {
                        throw new NotFoundException(`analysis '${analysisId}' cannot be found`, {
                            cause: err,
                        });
                    }
                }
            });
    }
}
