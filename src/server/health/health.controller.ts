import { Controller, Get } from '@nestjs/common';
import { HealthCheck, HealthCheckService, MongooseHealthIndicator } from '@nestjs/terminus';

@Controller('health')
export class HealthController {
    private readonly healthService: HealthCheckService;

    private readonly db: MongooseHealthIndicator;

    constructor(healthService: HealthCheckService, db: MongooseHealthIndicator) {
        this.healthService = healthService;
        this.db = db;
    }

    @Get()
    @HealthCheck()
    check() {
        return this.healthService.check([
            async () => this.db.pingCheck('db'),
        ]);
    }
}
