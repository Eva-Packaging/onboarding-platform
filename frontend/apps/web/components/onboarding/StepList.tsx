import type { OnboardingStep } from '@feature/base/server';
import StepItem from './StepItem';

interface Props {
  steps: OnboardingStep[];
}

export default function StepList({ steps }: Props) {
  if (steps.length === 0) return null;

  return (
    <ul className="mt-6 flex flex-col gap-4" aria-label="Onboarding steps">
      {steps.map((step, index) => (
        <StepItem key={`${step.type}-${index}`} step={step} />
      ))}
    </ul>
  );
}