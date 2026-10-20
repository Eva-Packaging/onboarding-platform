import { NextRequest, NextResponse } from 'next/server';
import { logger } from '@next-feature/logging/server';
import { NEXT_PUBLIC_LOGGING_BEACON_PATH } from "@app/web/lib/config/env";

interface ClientLogPayload {
  level: string;
  ts: number;
  messages: unknown[];
  bindings: Record<string, unknown>[];
  correlationId?: string;
}

export async function POST(request: NextRequest) {
  const start = performance.now();
  const correlationId = request.headers.get('x-correlation-id') ?? undefined;

  try {
    const payload: ClientLogPayload = await request.json();
    const msg = payload.messages?.filter(Boolean).join(' ') || 'client log';
    const ctx = {
      ...payload.bindings?.[0],
      correlationId: payload.correlationId ?? correlationId,
      source: 'browser',
    };

    if (payload.level === 'error') {
      logger.error(ctx, msg);
    } else if (payload.level === 'warn') {
      logger.warn(ctx, msg);
    } else {
      logger.info(ctx, msg);
    }
  } catch {
    logger.warn({ correlationId, source: 'browser' }, 'Failed to parse client log payload');
  }

  const responseTime = Math.round(performance.now() - start);
  logger.info(
    { responseTime, correlationId },
    `POST ${NEXT_PUBLIC_LOGGING_BEACON_PATH} 200`,
  );

  return NextResponse.json({ ok: true });
}