import { HttpStatusCode } from 'axios';
import { ProblemDetail } from './types';
import handleApiError from './utils/error';

/**
 * Custom error class for API errors
 */
export class ApiError extends Error {
  constructor(
    public problemDetail: ProblemDetail,
    public originalError?: Error,
  ) {
    super(
      problemDetail.detail ||
        problemDetail.title ||
        originalError?.message ||
        'An API error occurred',
    );
    this.name = 'ApiError';
    Object.setPrototypeOf(this, ApiError.prototype);
  }

  get status(): HttpStatusCode {
    return this.problemDetail.status;
  }

  get body(): ProblemDetail {
    return this.problemDetail;
  }

  get isClientError(): boolean {
    return this.status >= 400 && this.status < 500;
  }

  get isServerError(): boolean {
    return this.status >= 500;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  static builder(): ApiErrorBuilder {
    return new ApiErrorBuilder(null);
  }

  static of(error: Error | unknown): ApiError {
    return handleApiError(error);
  }
}

export class ApiErrorBuilder {
  private _problemDetail: ProblemDetail;

  constructor(private _originalError: Error | null) {
    this._problemDetail = {
      title: _originalError?.name || 'ApiError',
      detail: _originalError?.message || '',
      status: HttpStatusCode.InternalServerError,
      type: 'about:blank',
    };
  }

  /**
   * Set standard ProblemDetail
   */
  problemDetail(problemDetail: ProblemDetail): ApiErrorBuilder {
    if (problemDetail) {
      this._problemDetail = problemDetail;
    }
    return this;
  }

  originalError(error: Error): ApiErrorBuilder {
    this._originalError = error;
    this._problemDetail.title = error.name;
    this._problemDetail.detail = error.message;
    return this;
  }

  message(msg: string): ApiErrorBuilder {
    this._problemDetail.detail = msg;
    return this;
  }

  status(status: HttpStatusCode): ApiErrorBuilder {
    this._problemDetail.status = status;
    return this;
  }

  errors(errors: Record<string, string>): ApiErrorBuilder {
    this._problemDetail.errors = errors || {};
    return this;
  }

  title(title: string): ApiErrorBuilder {
    this._problemDetail.title = title;
    return this;
  }

  instance(instance: string): ApiErrorBuilder {
    this._problemDetail.instance = instance;
    return this;
  }

  build(): ApiError {
    return new ApiError(this._problemDetail, this._originalError);
  }
}
