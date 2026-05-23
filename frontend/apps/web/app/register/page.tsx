interface Props {
  params: Promise<{}>;
  searchParams: Promise<{}>;
}

async function RegistrationPage(props: Props) {
  const params = await props.params;

  return <>Registration Page</>;
}

export default RegistrationPage;
