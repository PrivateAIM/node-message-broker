import {
    Controller, Get, HttpCode, Inject, Param, UseGuards,
} from '@nestjs/common';
import type { AnalysisNodeDiscoveryResult, DiscoveryService } from './discovery.service';
import { DISCOVERY_SERVICE } from './discovery.service';
import { AuthGuard } from '../auth/auth.guard';

/**
 * Bundles API endpoints related to discovery functionality.
 */
@Controller('analyses/:id')
@UseGuards(AuthGuard)
export class DiscoveryController {
    private readonly discoveryService: DiscoveryService;

    constructor(@Inject(DISCOVERY_SERVICE) discoveryService: DiscoveryService) {
        this.discoveryService = discoveryService;
    }

    // TODO: add ETag later on to allow for caching
    @Get('participants')
    @HttpCode(200)
    async discoverParticipants(@Param('id') analysisId: string): Promise<AnalysisNodeDiscoveryResult[]> {
        return this.discoveryService.discoverParticipatingAnalysisNodes(analysisId);
    }
}
