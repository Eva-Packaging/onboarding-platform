const STORAGE_KEY = 'correlation_id';

let _correlationId = '';

export function getCorrelationId(): string {
  // Prefer sessionStorage so per-request updates from the API interceptor
  // (features/base) are reflected without requiring a React re-render.
  if (typeof sessionStorage !== 'undefined') {
    return sessionStorage.getItem(STORAGE_KEY) ?? _correlationId;
  }
  return _correlationId;
}

export function setCorrelationId(id: string): void {
  _correlationId = id;
  if (typeof sessionStorage !== 'undefined') {
    try {
      sessionStorage.setItem(STORAGE_KEY, id);
    } catch {
      // ignore quota / private-browsing errors
    }
  }
}