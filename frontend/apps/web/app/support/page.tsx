interface Props {
  params: Promise<{}>;
  searchParams: Promise<{}>;
}

async function SupportRequiredPage(props: Props) {
  const params = await props.params;

  return <>SupportRequired Page</>;
}

export default SupportRequiredPage;
