interface Props {
  params: Promise<{}>;
  searchParams: Promise<{}>;
}

async function RegisterPage(props: Props) {
  const params = await props.params;

  return <>Register Page</>;
}

export default RegisterPage;
