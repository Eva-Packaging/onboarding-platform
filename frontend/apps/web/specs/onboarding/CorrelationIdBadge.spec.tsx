import React from 'react';
import { render, screen, act, fireEvent } from '@testing-library/react';
import CorrelationIdBadge from '../../components/onboarding/CorrelationIdBadge';

const CORRELATION_ID = 'cb3f9c3a-9f38-47d4-8fa4-2d9a6a6d8b77';

beforeEach(() => {
  Object.assign(navigator, {
    clipboard: { writeText: jest.fn().mockResolvedValue(undefined) },
  });
});

describe('CorrelationIdBadge', () => {
  it('renders the correlationId', () => {
    render(<CorrelationIdBadge correlationId={CORRELATION_ID} />);
    expect(screen.getByText(CORRELATION_ID)).toBeInTheDocument();
  });

  it('renders a support link pointing to /support with correlationId query param', () => {
    render(<CorrelationIdBadge correlationId={CORRELATION_ID} />);
    const link = screen.getByRole('link', { name: /contact support/i });
    expect(link).toHaveAttribute(
      'href',
      `/support?correlationId=${encodeURIComponent(CORRELATION_ID)}`
    );
  });

  it('copies the correlationId to clipboard when the copy button is clicked', async () => {
    render(<CorrelationIdBadge correlationId={CORRELATION_ID} />);
    const copyButton = screen.getByRole('button', { name: /copy reference id/i });

    await act(async () => {
      fireEvent.click(copyButton);
    });

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(CORRELATION_ID);
  });

  it('shows "Copied" label after clicking copy', async () => {
    render(<CorrelationIdBadge correlationId={CORRELATION_ID} />);
    const copyButton = screen.getByRole('button', { name: /copy reference id/i });

    await act(async () => {
      fireEvent.click(copyButton);
    });

    expect(screen.getByRole('button', { name: /copied/i })).toBeInTheDocument();
  });
});
