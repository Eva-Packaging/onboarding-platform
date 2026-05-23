interface Props {
  params: Promise<{}>;
  searchParams: Promise<{}>;
}

async function DashnoardPage(props: Props) {
  const params = await props.params;

  return <>Dashnoard Page</>;
}

export default DashnoardPage;
