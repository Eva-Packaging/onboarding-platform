interface Props {
  params: Promise<Record<string, never>>;
  searchParams: Promise<{ correlationId?: string }>;
}

export default async function SupportPage(props: Props) {
  const { correlationId } = await props.searchParams;

  return (
    <div className="min-h-screen bg-gray-50 flex items-start justify-center pt-24 px-4">
      <div className="w-full max-w-lg">
        <h1 className="text-2xl font-semibold text-gray-900 mb-2">Contact Support</h1>
        <p className="text-gray-600 mb-8">
          Our team will investigate your onboarding issue. Please include your reference ID
          in all correspondence.
        </p>

        {correlationId && (
          <div className="mb-6 rounded-lg border border-gray-200 bg-white px-4 py-3">
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1">
              Reference ID
            </label>
            <p className="font-mono text-sm text-gray-800 break-all select-all">
              {correlationId}
            </p>
          </div>
        )}

        <div className="rounded-lg border border-gray-200 bg-white px-4 py-4 space-y-3 text-sm text-gray-700">
          <p>
            <span className="font-medium">Email: </span>
            <a
              href={`mailto:support@eva.com${correlationId ? `?subject=Onboarding%20issue%20%5B${encodeURIComponent(correlationId)}%5D` : ''}`}
              className="text-blue-600 hover:underline"
            >
              support@eva.com
            </a>
          </p>
          <p className="text-gray-500 text-xs">
            Response times are typically within one business day.
          </p>
        </div>
      </div>
    </div>
  );
}

