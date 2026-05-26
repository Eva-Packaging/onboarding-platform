import { pinoOptions } from '@feature/logging/config';

export async function register() {
  if (process.env.NEXT_RUNTIME === 'nodejs') {
    const [{ default: pinoHttp }, { default: pino }] = await Promise.all([
      import('pino-http'),
      import('pino'),
    ]);

    const logger = pino(pinoOptions);

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (globalThis as any).__httpLogger = pinoHttp({
      logger,
      customLogLevel(_req: any, res: any, err: unknown) {
        if (err || res.statusCode >= 500) return 'error';
        if (res.statusCode >= 400) return 'warn';
        return 'info';
      },
      serializers: {
        req(req: any) {
          return {
            method: req.method,
            url: req.url,
            correlationId: req.headers?.['x-correlation-id'],
          };
        },
        res(res: any) {
          return { statusCode: res.statusCode };
        },
      },
    });

    logger.info('Next.js server instrumentation registered');
  }
}