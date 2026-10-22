import React from 'react';
import { StarsBackground } from '@app/web/components/ui/animate-ui/backgrounds/stars';

interface Props {
  children: React.ReactNode;
}

export function Layout(props: Props): React.ReactElement {
  return (
    <>
      {/* Fixed behind all content so it fills the viewport and doesn't scroll or capture clicks.
          Colors come from theme tokens so the background follows light/dark mode. */}
      <StarsBackground
        aria-hidden="true"
        pointerEvents={false}
        starColor="var(--foreground)"
        className="pointer-events-none fixed inset-0 -z-10 bg-[radial-gradient(ellipse_at_bottom,var(--muted)_0%,var(--background)_100%)]"
      />
      <main className="relative min-h-dvh">{props.children}</main>
    </>
  );
}

export default Layout;
