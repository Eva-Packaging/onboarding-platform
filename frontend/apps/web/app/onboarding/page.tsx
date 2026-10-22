import { redirect } from 'next/navigation';
import { auth } from '@feature/auth/server';
import { ProvisioningSteps } from '@app/web/components/onboarding/provisioning-steps';
import { fetchOnboardingStatus } from './actions';

import { logger } from "@next-feature/logging/server";

const log = logger.child({module: "onboarding-page"})

interface Props {
  params: Promise<{}>;
  searchParams: Promise<{}>;
}

async function OnboardingPage(props: Props) {
  const session = await auth();
  if (!session?.user) {
    redirect('/');
  }

  const initial = await fetchOnboardingStatus();
  log.info(initial, "Onboarding status")
  return <ProvisioningSteps initial={initial} />;
}

export default OnboardingPage;
