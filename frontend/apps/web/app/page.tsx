interface Props {
  params: Promise<{}>;
  searchParams: Promise<{}>;
}

async function OnboardingPage(props: Props) {
  const params = await props.params;

  return <>Onboarding Page</>;
}

export default OnboardingPage;
