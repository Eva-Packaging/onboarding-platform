'use client';

import pino from 'pino';
import { getCorrelationId } from './correlation';

const browserLogger = pino({
  browser: {
    asObject: true,
    transmit: {
      level: 'info',
      send(level, logEvent) {
        const body = JSON.stringify({
          level,
          ts: logEvent.ts,
          messages: logEvent.messages,
          bindings: logEvent.bindings,
          correlationId: getCorrelationId(),
        });

        if (typeof navigator !== 'undefined' && 'sendBeacon' in navigator) {
          navigator.sendBeacon('/api/log', body);
        } else {
          fetch('/api/log', {
            method: 'POST',
            body,
            headers: { 'Content-Type': 'application/json' },
            keepalive: true,
          }).catch(() => {});
        }
      },
    },
  },
});

export default browserLogger;