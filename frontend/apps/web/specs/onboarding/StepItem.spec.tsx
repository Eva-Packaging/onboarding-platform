import React from 'react';
import { render, screen } from '@testing-library/react';
import StepItem from '../../components/onboarding/StepItem';
import type { OnboardingStep } from '@feature/base/server';

function makeStep(overrides: Partial<OnboardingStep> = {}): OnboardingStep {
  return {
    type: 'GITHUB_TEAM_PROVISIONING',
    state: 'PENDING',
    ...overrides,
  };
}

describe('StepItem — step type labels', () => {
  it.each([
    ['IDENTITY_CORRELATION', 'Identity Verification'],
    ['GITHUB_TEAM_PROVISIONING', 'GitHub Team Provisioning'],
    ['JIRA_GROUP_PROVISIONING', 'Jira Group Provisioning'],
  ])('maps %s to human-readable label', (type, label) => {
    render(<StepItem step={makeStep({ type, state: 'SUCCEEDED' })} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it('falls back to the raw type string for unknown types', () => {
    render(<StepItem step={makeStep({ type: 'UNKNOWN_STEP_TYPE', state: 'PENDING' })} />);
    expect(screen.getByText('UNKNOWN_STEP_TYPE')).toBeInTheDocument();
  });
});

describe('StepItem — action-required callout', () => {
  it('shows the GitHub invitation callout for PENDING_EXTERNAL_ACCEPTANCE', () => {
    render(
      <StepItem
        step={makeStep({ type: 'GITHUB_TEAM_PROVISIONING', state: 'PENDING_EXTERNAL_ACCEPTANCE' })}
      />
    );
    expect(screen.getByText(/Accept your GitHub team invitation/i)).toBeInTheDocument();
  });

  it('shows the Atlassian callout for JIRA_GROUP_PROVISIONING when action required', () => {
    render(
      <StepItem
        step={makeStep({ type: 'JIRA_GROUP_PROVISIONING', state: 'ACTION_REQUIRED' })}
      />
    );
    expect(screen.getByText(/Accept your Atlassian account invitation/i)).toBeInTheDocument();
  });

  it('does not show a callout for PENDING state', () => {
    render(<StepItem step={makeStep({ state: 'PENDING' })} />);
    expect(screen.queryByText(/invitation/i)).not.toBeInTheDocument();
  });

  it('does not show a callout for SUCCEEDED state', () => {
    render(<StepItem step={makeStep({ state: 'SUCCEEDED' })} />);
    expect(screen.queryByText(/invitation/i)).not.toBeInTheDocument();
  });
});

describe('StepItem — failed step error display', () => {
  it('shows the lastErrorCode when state is FAILED', () => {
    render(
      <StepItem
        step={makeStep({ state: 'FAILED', lastErrorCode: 'PROVIDER_TIMEOUT' })}
      />
    );
    expect(screen.getByText(/PROVIDER_TIMEOUT/)).toBeInTheDocument();
  });

  it('does not show an error line when state is FAILED but lastErrorCode is absent', () => {
    render(<StepItem step={makeStep({ state: 'FAILED' })} />);
    expect(screen.queryByText(/Error:/)).not.toBeInTheDocument();
  });

  it('does not show an error line when state is SUCCEEDED even with lastErrorCode', () => {
    render(
      <StepItem step={makeStep({ state: 'SUCCEEDED', lastErrorCode: 'OLD_ERROR' })} />
    );
    expect(screen.queryByText(/OLD_ERROR/)).not.toBeInTheDocument();
  });
});