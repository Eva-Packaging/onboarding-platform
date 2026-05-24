import { LiquidMetalButton } from '../../components/register/liquid-metal-button';

interface Props {
  params: Promise<{}>;
  searchParams: Promise<{}>;
}

async function RegistrationPage(props: Props) {
  return (
    <div
      className="h-screen w-full flex flex-col items-center justify-center gap-12"
      style={{
        background: '#000000',
      }}
    >
      <LiquidMetalButton label="Connect Github" />
    </div>
  );
}

export default RegistrationPage;
