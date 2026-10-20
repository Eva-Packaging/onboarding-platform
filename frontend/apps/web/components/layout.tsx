import React from 'react';

interface Props {
  children: React.ReactNode;
}

export function Layout(props: Props): React.ReactElement {
  return (
    <>{props.children}</>
  );
}

export default Layout;
