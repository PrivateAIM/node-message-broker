import { Body, Controller, HttpCode, Param, Post, UseGuards } from "@nestjs/common";
import { AuthGuard } from "../../auth/auth.guard";
import { HubMessageProducerService } from "./hub.message.producer.service";

@Controller('messages')
@UseGuards(AuthGuard)
export class HubMessageController {
    private readonly hubMessageProducerService: HubMessageProducerService;

    constructor(hubMessageProducerService: HubMessageProducerService) {
        this.hubMessageProducerService = hubMessageProducerService;
    }

    @Post('/broadcasts/:analysisId')
    @HttpCode(201)
    async sendNodeBroadcast(@Param('analysisId') analysisId: string, @Body() messagePayload: Record<string, any>) {
        return this.hubMessageProducerService.produceNodeBroadcastMessage(analysisId, messagePayload);
    }
}
