import React from 'react';
import { render, screen } from '@testing-library/react';
import OnboardingStatusBanner from '../../components/onboarding/OnboardingStatusBanner';

describe('OnboardingStatusBanner', () => {
  it('shows the in-progress label for IN_PROGRESS state', () => {
    render(<OnboardingStatusBanner state="IN_PROGRESS" />);
    expect(screen.getByRole('status')).toHaveTextContent('Setting up your account');
  });

  it('shows the in-progress label for REQUESTED state', () => {
    render(<OnboardingStatusBanner state="REQUESTED" />);
    expect(screen.getByRole('status')).toHaveTextContent('Setting up your account');
  });

  it('shows the completed label for COMPLETED state', () => {
    render(<OnboardingStatusBanner state="COMPLETED" />);
    expect(screen.getByRole('status')).toHaveTextContent('Onboarding complete');
  });

  it('shows the partial success label for PARTIAL_SUCCESS state', () => {
    render(<OnboardingStatusBanner state="PARTIAL_SUCCESS" />);
    expect(screen.getByRole('status')).toHaveTextContent('partially complete');
  });

  it('shows the failed label for FAILED state', () => {
    render(<OnboardingStatusBanner state="FAILED" />);
    expect(screen.getByRole('status')).toHaveTextContent('failed');
  });

  it('shows the default label for an unknown state', () => {
    render(<OnboardingStatusBanner state="UNKNOWN_STATE" />);
    expect(screen.getByRole('status')).toHaveTextContent('Processing');
  });

  it('shows the default label when state is undefined', () => {
    render(<OnboardingStatusBanner />);
    expect(screen.getByRole('status')).toHaveTextContent('Processing');
  });

  it('has aria-live attribute for accessibility', () => {
    render(<OnboardingStatusBanner state="IN_PROGRESS" />);
    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite');
  });
});