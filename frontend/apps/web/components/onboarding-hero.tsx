import React from 'react';
import { signIn } from '@feature/auth/server';
import { PROJECT_DESCRIPTION, PROJECT_NAME } from '@app/web/lib/constants/metadata';
import { CosmicButton } from '@app/web/components/ui/common/cosmic-button';
import {
  HeroHeatmapActions,
  HeroHeatmapContainer,
  HeroHeatmapContent,
  HeroHeatmapDescription,
  HeroHeatmapHeading,
  HeroHeatmapMobileVisual,
  HeroHeatmapRoot,
  HeroHeatmapVisual,
} from '@app/web/components/ui/common/hero-heatmap';

async function connectToGithub() {
  'use server';
  await signIn('github', { redirectTo: '/onboarding' });
}

export function OnboardingHero(): React.ReactElement {
  return (
    <HeroHeatmapRoot
      srTitle={PROJECT_NAME}
      title={PROJECT_NAME}
      subtitle={<span className="text-foreground/60">GitHub to Jira, automated.</span>}
      description={PROJECT_DESCRIPTION}
      image="/onboarding-mark.svg"
      showBadges={false}
    >
      <HeroHeatmapContainer className="min-h-dvh pt-16 sm:pt-20 lg:pt-0">
        <HeroHeatmapContent>
          <HeroHeatmapHeading headingClassName="font-heading" />
          <HeroHeatmapDescription />
          <HeroHeatmapActions>
            <form action={connectToGithub}>
              <CosmicButton as="button" type="submit" className="cursor-pointer">
                Connect to GitHub
              </CosmicButton>
            </form>
          </HeroHeatmapActions>
        </HeroHeatmapContent>
        <HeroHeatmapVisual />
      </HeroHeatmapContainer>
      <HeroHeatmapMobileVisual />
    </HeroHeatmapRoot>
  );
}

export default OnboardingHero;
