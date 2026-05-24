'use client';

import { useState } from 'react';
import { Copy, Check } from 'lucide-react';
import Link from 'next/link';

interface Props {
  correlationId: string;
}

export default function CorrelationIdBadge({ correlationId }: Props) {
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    await navigator.clipboard.writeText(correlationId);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  const CopyIcon = copied ? Check : Copy;

  return (
    <div className="mt-6 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3">
      <p className="text-xs font-medium text-gray-500 uppercase tracking-wide mb-1">
        Reference ID
      </p>
      <div className="flex items-center gap-2">
        <span className="flex-1 font-mono text-sm text-gray-800 break-all">
          {correlationId}
        </span>
        <button
          onClick={handleCopy}
          aria-label={copied ? 'Copied' : 'Copy reference ID'}
          className="shrink-0 rounded p-1 text-gray-500 hover:bg-gray-200 hover:text-gray-700 transition-colors"
        >
          <CopyIcon size={14} />
        </button>
      </div>
      <Link
        href={`/support?correlationId=${encodeURIComponent(correlationId)}`}
        className="mt-2 inline-block text-xs text-blue-600 hover:underline"
      >
        Need help? Contact support →
      </Link>
    </div>
  );
}