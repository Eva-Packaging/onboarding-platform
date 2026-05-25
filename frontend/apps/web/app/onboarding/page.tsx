import { auth } from '@feature/auth';
import { redirect } from 'next/navigation';
import OnboardingPoller from '../../components/onboarding/OnboardingPoller';

export default async function OnboardingPage() {
  const session = await auth();

  if (!session?.user) {
    redirect('/register');
  }

  if (session.user.registrationError) {
    redirect('/register?error=registration_failed');
  }

  if (!session.user.onboardingRequestId) {
    redirect('/dashboard');
  }

  return (
    <OnboardingPoller
      requestId={session.user.onboardingRequestId}
      correlationId={session.user.correlationId}
    />
  );
}
