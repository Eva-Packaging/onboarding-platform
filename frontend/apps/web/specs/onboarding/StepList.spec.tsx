import React from 'react';
import { render, screen } from '@testing-library/react';
import StepList from '../../components/onboarding/StepList';
import type { OnboardingStep } from '@feature/base/server';

const STEPS: OnboardingStep[] = [
  { type: 'IDENTITY_CORRELATION', state: 'SUCCEEDED' },
  { type: 'GITHUB_TEAM_PROVISIONING', state: 'PENDING_EXTERNAL_ACCEPTANCE' },
  { type: 'JIRA_GROUP_PROVISIONING', state: 'PENDING' },
];

describe('StepList', () => {
  it('renders nothing for an empty array', () => {
    const { container } = render(<StepList steps={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders one item per step', () => {
    render(<StepList steps={STEPS} />);
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
  });

  it('renders human-readable labels for all steps', () => {
    render(<StepList steps={STEPS} />);
    expect(screen.getByText('Identity Verification')).toBeInTheDocument();
    expect(screen.getByText('GitHub Team Provisioning')).toBeInTheDocument();
    expect(screen.getByText('Jira Group Provisioning')).toBeInTheDocument();
  });

  it('renders the action-required callout for PENDING_EXTERNAL_ACCEPTANCE steps', () => {
    render(<StepList steps={STEPS} />);
    expect(screen.getByText(/Accept your GitHub team invitation/i)).toBeInTheDocument();
  });

  it('renders a single step correctly', () => {
    const single: OnboardingStep[] = [{ type: 'IDENTITY_CORRELATION', state: 'SUCCEEDED' }];
    render(<StepList steps={single} />);
    expect(screen.getAllByRole('listitem')).toHaveLength(1);
    expect(screen.getByText('Identity Verification')).toBeInTheDocument();
  });

  it('renders all steps as completed when all have SUCCEEDED state', () => {
    const allDone: OnboardingStep[] = STEPS.map((s) => ({ ...s, state: 'SUCCEEDED' }));
    render(<StepList steps={allDone} />);
    expect(screen.queryByText(/invitation/i)).not.toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
  });
});