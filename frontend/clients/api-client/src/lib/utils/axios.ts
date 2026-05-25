import { AxiosError, AxiosResponse, HttpStatusCode } from 'axios';
import { ApiErrorBuilder } from '../error';
import type { ProblemDetail } from '../types';

/**
 * Extract ProblemDetail from error response
 *
 * [extract-problem-detail]
 * next-feature@0.1.1-beta.5
 * January 11th 2026, 9:11:27 pm
 */
function extractProblemDetail(
  response: AxiosResponse<unknown, any>,
): ProblemDetail | null {
  if (!response.data) {
    return null;
  }

  const data = response.data;

  // Check if response matches ProblemDetail structure
  if (
    typeof data === 'object' &&
    'type' in data &&
    'title' in data &&
    'status' in data
  ) {
    return data as ProblemDetail;
  }

  return null;
}

/**
 * [handle-axios-error]
 * next-feature@0.1.1-beta.5
 * January 11th 2026, 9:37:55 pm
 */
export function handleAxiosError(e: AxiosError) {
  const builder = new ApiErrorBuilder(e);
  builder.instance(e.config.url);

  if (e.status) {
    builder.status(e.status);
  }

  if (e.response && e.response.data) {
    const pd = extractProblemDetail(e.response);
    if (pd !== null) {
      return builder.problemDetail(pd).build();
    }
  }

  if (e.code === 'ECONNREFUSED') {
    builder
      .status(HttpStatusCode.ServiceUnavailable)
      .message('Failed to connect to service')
      .title('Service Unavailable');
  }

  return builder.build();
}
