import { auth } from '@feature/auth';
import { redirect } from 'next/navigation';
import OnboardingPoller from '../../components/onboarding/OnboardingPoller';

export default async function OnboardingPage() {
  const session = await auth();

  if (!session?.user) {
    redirect('/register');
  }

  return (
    <OnboardingPoller
      requestId={session.user.onboardingRequestId ?? null}
      correlationId={session.user.correlationId}
    />
  );
}