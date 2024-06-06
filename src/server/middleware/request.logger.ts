import { Injectable, Logger, NestMiddleware } from "@nestjs/common";
import { Request, Response } from 'express';

/**
 * A middleware for logging requests to this server.
 */
@Injectable()
export class RequestLoggerMiddleware implements NestMiddleware {

    private readonly logger: Logger = new Logger('HTTP_REQUEST');

    use(request: Request, response: Response, next: (error?: any) => void) {
        const { ip, method, path: url } = request;

        response.on('finish', () => {
            const { statusCode } = response;
            const contentLength = response.get('content-length');

            this.logger.log(
                `${method} ${url} ${statusCode} ${contentLength} - ${ip}`
            );
        });
        next();
    }
}
