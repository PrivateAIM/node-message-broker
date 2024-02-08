import { Controller, Get } from '@nestjs/common';
import { HealthCheck, HealthCheckService } from '@nestjs/terminus';

@Controller('health')
export class HealthController {
    private readonly healthService: HealthCheckService;

    constructor(healthService: HealthCheckService) {
        this.healthService = healthService;
    }

    @Get()
    @HealthCheck()
    check() {
        return this.healthService.check([]);
    }
}
