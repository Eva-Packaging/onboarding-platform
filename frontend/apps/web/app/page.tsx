import React from 'react';

interface Props {
  params: Promise<{ [key: string]: string | string[] | undefined }>
}

export default async function Index(props: Props) {
  return (
    <div>
      Home
    </div>
  );
}
