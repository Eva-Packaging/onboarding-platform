'use client';

import React, { Component, ErrorInfo, ReactNode } from 'react';
import { ApiError } from '../lib/error';
import { getErrorMessage } from '../lib/utils/error';

interface Props {
  children: ReactNode;
  fallback?: (error: ApiError) => ReactNode;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ApiErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('ApiErrorBoundary caught error:', error, errorInfo);

    if (this.props.onError) {
      this.props.onError(error, errorInfo);
    }
  }

  render() {
    if (this.state.hasError && this.state.error) {
      if (this.state.error instanceof ApiError && this.props.fallback) {
        return this.props.fallback(this.state.error);
      }

      // Default error UI
      return (
        <div className="error-container">
          <h2>Something went wrong</h2>
          <p>{getErrorMessage(this.state.error)}</p>
          <button
            onClick={() => this.setState({ hasError: false, error: null })}
          >
            Try again
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
