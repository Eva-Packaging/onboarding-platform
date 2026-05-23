import React, { ReactNode } from "react";
import { SessionProvider } from "next-auth/react";
import { Toaster } from "sonner";
import ReactQueryProvider from "../lib/providers/react-query-provider";


interface Props {
  children: ReactNode;
}

const Providers = ({ children }: Props) => {
  return (
    <SessionProvider>
      <ReactQueryProvider>
        <Toaster position="bottom-right" />
        {children}
      </ReactQueryProvider>
    </SessionProvider>
  );
};

export default Providers;
