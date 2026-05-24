import { CheckCircle2, XCircle, Clock, Loader2, AlertTriangle } from 'lucide-react';
import type { OnboardingStep } from '@feature/base/server';

const STEP_LABELS: Record<string, string> = {
  IDENTITY_CORRELATION: 'Identity Verification',
  GITHUB_TEAM_PROVISIONING: 'GitHub Team Provisioning',
  JIRA_GROUP_PROVISIONING: 'Jira Group Provisioning',
};

const ACTION_REQUIRED_INSTRUCTIONS: Record<string, string> = {
  GITHUB_TEAM_PROVISIONING: 'Accept your GitHub team invitation to continue.',
  JIRA_GROUP_PROVISIONING: 'Accept your Atlassian account invitation to continue.',
  IDENTITY_CORRELATION: 'Additional identity verification is required. Please contact support.',
};

interface StateVisual {
  icon: React.ElementType;
  iconClass: string;
  rowClass: string;
}

const STATE_VISUAL: Record<string, StateVisual> = {
  PENDING: {
    icon: Clock,
    iconClass: 'text-gray-400',
    rowClass: 'opacity-50',
  },
  PROCESSING: {
    icon: Loader2,
    iconClass: 'text-blue-500 animate-spin',
    rowClass: '',
  },
  IN_PROGRESS: {
    icon: Loader2,
    iconClass: 'text-blue-500 animate-spin',
    rowClass: '',
  },
  SUCCEEDED: {
    icon: CheckCircle2,
    iconClass: 'text-green-500',
    rowClass: '',
  },
  COMPLETED: {
    icon: CheckCircle2,
    iconClass: 'text-green-500',
    rowClass: '',
  },
  FAILED: {
    icon: XCircle,
    iconClass: 'text-red-500',
    rowClass: '',
  },
  PENDING_EXTERNAL_ACCEPTANCE: {
    icon: AlertTriangle,
    iconClass: 'text-yellow-500',
    rowClass: '',
  },
  ACTION_REQUIRED: {
    icon: AlertTriangle,
    iconClass: 'text-yellow-500',
    rowClass: '',
  },
};

const DEFAULT_VISUAL: StateVisual = {
  icon: Clock,
  iconClass: 'text-gray-400',
  rowClass: 'opacity-50',
};

interface Props {
  step: OnboardingStep;
}

export default function StepItem({ step }: Props) {
  const label = STEP_LABELS[step.type] ?? step.type;
  const visual = STATE_VISUAL[step.state] ?? DEFAULT_VISUAL;
  const Icon = visual.icon;

  const isActionRequired =
    step.state === 'PENDING_EXTERNAL_ACCEPTANCE' || step.state === 'ACTION_REQUIRED';
  const isFailed = step.state === 'FAILED';

  return (
    <li className={`flex flex-col gap-1 ${visual.rowClass}`}>
      <div className="flex items-center gap-3">
        <Icon size={18} className={`shrink-0 ${visual.iconClass}`} />
        <span className="text-sm font-medium text-gray-800">{label}</span>
      </div>

      {isActionRequired && (
        <p className="ml-[30px] text-sm text-yellow-700 bg-yellow-50 border border-yellow-200 rounded px-3 py-2">
          {ACTION_REQUIRED_INSTRUCTIONS[step.type] ?? 'Action required. Please contact support.'}
        </p>
      )}

      {isFailed && step.lastErrorCode && (
        <p className="ml-[30px] text-xs text-red-600 font-mono">
          Error: {step.lastErrorCode}
        </p>
      )}
    </li>
  );
}