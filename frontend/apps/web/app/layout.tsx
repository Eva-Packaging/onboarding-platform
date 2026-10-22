import { PROJECT_DESCRIPTION, PROJECT_NAME } from '@app/web/lib/constants/metadata';
import type { Metadata } from 'next';
import { ReactNode } from 'react';
import './global.css';
import Providers from './providers';
import { Layout } from '../components/layout';
import { Noto_Serif, Public_Sans } from "next/font/google";
import { cn } from "@app/web/lib/ui/utils";

const publicSansHeading = Public_Sans({subsets:['latin'],variable:'--font-heading'});

const notoSerif = Noto_Serif({subsets:['latin'],variable:'--font-serif'});

export const metadata: Metadata = {
  title: {
    default: PROJECT_NAME,
    template: `%s | ${PROJECT_NAME}`,
  },
  description: PROJECT_DESCRIPTION,
  keywords: [],
  authors: [{ name: "" }],
  creator: "",
  publisher: "",
  generator: "next-feature",
  openGraph: {
    type: "website",
    locale: "en_US",
    url: "/",
    title: PROJECT_NAME,
    description: PROJECT_DESCRIPTION,
    siteName: PROJECT_NAME,
    images: [],
  },
  twitter: {},
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      'max-video-preview': -1,
      'max-image-preview': 'large',
      'max-snippet': -1,
    },
  },
  icons: {
    icon: [],
  },
  manifest: "/site.webmanifest",
}

export default function RootLayout({
  children,
}: {
  children: ReactNode
}) {
  return (
    <html lang="en" suppressHydrationWarning className={cn("font-serif", notoSerif.variable, publicSansHeading.variable)}>
      <body>
        <Providers>
          <Layout>
            {children}
          </Layout>
        </Providers>
      </body>
    </html>
  )
}
