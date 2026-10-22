import React from 'react';
import { OnboardingHero } from '@app/web/components/onboarding-hero';

interface Props {
  params: Promise<{ [key: string]: string | string[] | undefined }>
}

export default async function Index(props: Props) {
  return <OnboardingHero />;
}
