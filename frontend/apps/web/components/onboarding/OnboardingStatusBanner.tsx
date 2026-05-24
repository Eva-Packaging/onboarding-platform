interface StateConfig {
  label: string;
  className: string;
}

const STATE_CONFIG: Record<string, StateConfig> = {
  REQUESTED: {
    label: 'Setting up your account…',
    className: 'bg-blue-50 text-blue-800 border-blue-200',
  },
  IN_PROGRESS: {
    label: 'Setting up your account…',
    className: 'bg-blue-50 text-blue-800 border-blue-200',
  },
  COMPLETED: {
    label: 'Onboarding complete! Welcome aboard.',
    className: 'bg-green-50 text-green-800 border-green-200',
  },
  PARTIAL_SUCCESS: {
    label: 'Onboarding partially complete. Some steps may need attention.',
    className: 'bg-yellow-50 text-yellow-800 border-yellow-200',
  },
  FAILED: {
    label: 'Onboarding failed. Please contact support.',
    className: 'bg-red-50 text-red-800 border-red-200',
  },
};

const DEFAULT_CONFIG: StateConfig = {
  label: 'Processing…',
  className: 'bg-gray-50 text-gray-800 border-gray-200',
};

interface Props {
  state?: string;
}

export default function OnboardingStatusBanner({ state }: Props) {
  const config = (state && STATE_CONFIG[state]) ?? DEFAULT_CONFIG;

  return (
    <div
      role="status"
      aria-live="polite"
      className={`rounded-lg border px-4 py-3 text-sm font-medium ${config.className}`}
    >
      {config.label}
    </div>
  );
}
