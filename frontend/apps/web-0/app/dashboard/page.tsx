import { auth } from '@onboarding-frontend/auth-0';
import { redirect } from 'next/navigation';

interface Props {
  params: Promise<{}>;
  searchParams: Promise<{}>;
}

async function DashboardPage(props: Props) {
  const session = await auth();

  if (!session?.user) {
    redirect('/register');
  }

  return <>Dashboard Page</>;
}

export default DashboardPage;
