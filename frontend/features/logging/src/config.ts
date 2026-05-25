const isDev = process.env.NODE_ENV !== 'production';

export const pinoOptions = {
  base: { service: 'web' },
  level: 'info' as const,
  ...(isDev && {
    transport: {
      target: 'pino-pretty',
      options: { colorize: true, translateTime: 'SYS:standard', ignore: 'pid,hostname' },
    },
  }),
};