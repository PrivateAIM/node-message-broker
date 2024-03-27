import {
    BadGatewayException,
    Controller, Get, HttpCode, Inject, NotFoundException, Param, UseGuards,
} from '@nestjs/common';
import type { AnalysisNodeDiscoveryResult, DiscoveryService } from './discovery.service';
import { DISCOVERY_SERVICE, DiscoveryError } from './discovery.service';
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
    async discoverParticipants(@Param('id') analysisId: string): Promise<void | AnalysisNodeDiscoveryResult[]> {
        return this.discoveryService.discoverParticipatingAnalysisNodes(analysisId)
            .catch((err) => {
                if (err instanceof DiscoveryError) {
                    if (err.name === 'FAILED_TO_FETCH_ANALYSIS_NODES') {
                        throw new BadGatewayException('could not fetch analysis nodes', {
                            cause: err,
                            description: err.message,
                        });
                    }
                }
            });
    }

    @Get('participants/self')
    @HttpCode(200)
    async discoverSelf(@Param('id') analysisId: string): Promise<void | AnalysisNodeDiscoveryResult> {
        return this.discoveryService.discoverSelf(analysisId)
            .catch((err) => {
                if (err instanceof DiscoveryError) {
                    if (err.name === 'FAILED_TO_FETCH_ANALYSIS_NODES') {
                        throw new BadGatewayException('could not fetch analysis nodes', {
                            cause: err,
                            description: err.message,
                        });
                    } else if (err.name === 'SELF_NOT_FOUND') {
                        // TODO: this can be discussed
                        throw new NotFoundException('could not find own identity in analysis', {
                            cause: err,
                            description: err.message,
                        });
                    }
                }
            });
    }
}
