import { redirect } from 'next/navigation';

interface Props {
  params: Promise<{}>;
  searchParams: Promise<{}>;
}

async function RootPage(props: Props) {
  redirect("/onboarding")
}

export default RootPage;
