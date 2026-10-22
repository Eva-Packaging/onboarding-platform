"use client"

import * as React from "react"
import Link from "next/link"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import type {
  GetOnboardingStatusResponse,
  OnboardingStep,
} from "@feature/onboarding"

import { cn } from "@app/web/lib/ui/utils"
import {
  fetchOnboardingStatus,
  retryFailedSteps,
  type OnboardingStatusResult,
} from "@app/web/app/onboarding/actions"
import { Button } from "@app/web/components/ui/common/button"
import { CosmicButton } from "@app/web/components/ui/common/cosmic-button"
import { HaloProgress } from "@app/web/components/ui/common/halo-progress"
import IntroDisclosure, {
  type IntroDisclosureStep,
} from "@app/web/components/ui/common/intro-disclosure"

const POLL_INTERVAL_MS = 3000
const STATUS_QUERY_KEY = ["onboarding-status"]

/** Request states after which the backend will not change anything on its own. */
const TERMINAL_REQUEST_STATES = ["COMPLETED", "PARTIAL_SUCCESS", "FAILED"]

/** Step states the user can requeue themselves; MANUAL_REVIEW is left to admins. */
const RETRYABLE_STEP_STATES = ["FAILED"]

/** Step states where the user or support should see the correlation ID. */
const SUPPORT_STEP_STATES = ["FAILED", "MANUAL_REVIEW", "PENDING_EXTERNAL_ACCEPTANCE"]

/** Preview images are the providers' own link-preview (og:image) artwork. */
const STEP_COPY: Record<
  string,
  { title: string; description: string; image: { src: string; alt: string } }
> = {
  IDENTITY_CORRELATION: {
    title: "Match your Atlassian account",
    description:
      "We look up the Atlassian account that belongs to your GitHub identity so access can be granted in both places.",
    image: {
      src: "https://dam-cdn.atl.orangelogic.com/AssetLink/5140xb7orvf7timubceisv3470t0305a.webp",
      alt: "Atlassian",
    },
  },
  GITHUB_TEAM_PROVISIONING: {
    title: "Join your GitHub team",
    description:
      "We invite you to the GitHub organization and add you to your team.",
    image: {
      src: "https://images.ctfassets.net/8aevphvgewt8/4pe4eOtUJ0ARpZRE4fNekf/f52b1f9c52f059a33170229883731ed0/GH-Homepage-Universe-img.png",
      alt: "GitHub",
    },
  },
  JIRA_GROUP_PROVISIONING: {
    title: "Join your Jira group",
    description:
      "We add your Atlassian account to the Jira group for your role.",
    image: {
      src: "https://dam-cdn.atl.orangelogic.com/AssetLink/0qw2hr437x574ovc7721dy64614yooac.png",
      alt: "Jira",
    },
  },
}

const STATE_LABELS: Record<
  string,
  { label: string; detail: string; tone: "idle" | "busy" | "done" | "warn" | "error" }
> = {
  PENDING: { label: "Queued", detail: "Waiting to start.", tone: "idle" },
  PROCESSING: { label: "In progress", detail: "Working on it now.", tone: "busy" },
  SUCCEEDED: { label: "Done", detail: "This step is complete.", tone: "done" },
  PENDING_EXTERNAL_ACCEPTANCE: {
    label: "Action needed",
    detail: "Accept the invitation to finish this step.",
    tone: "warn",
  },
  FAILED: {
    label: "Failed",
    detail: "Something went wrong. Contact support with the correlation ID below.",
    tone: "error",
  },
  MANUAL_REVIEW: {
    label: "Under review",
    detail: "An admin is reviewing this step. No action is needed from you.",
    tone: "warn",
  },
}

const REQUEST_LABELS: Record<string, string> = {
  REQUESTED: "Getting started",
  IN_PROGRESS: "Setting up your access",
  ACTION_REQUIRED: "Your action is needed",
  COMPLETED: "You're all set",
  PARTIAL_SUCCESS: "Partially complete",
  FAILED: "Onboarding failed",
}

const toneClassName = {
  idle: "bg-muted-foreground/50",
  busy: "bg-sky-500 animate-pulse",
  done: "bg-emerald-500",
  warn: "bg-amber-500",
  error: "bg-destructive",
} as const

function stateInfo(state: string) {
  return (
    STATE_LABELS[state] ?? { label: state, detail: "", tone: "idle" as const }
  )
}

function StepStatusBadge({ state }: { state: string }) {
  const { label, tone } = stateInfo(state)
  return (
    <span className="text-muted-foreground inline-flex items-center gap-1.5 text-xs">
      <span className={cn("size-1.5 rounded-full", toneClassName[tone])} />
      {label}
    </span>
  )
}

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString() : undefined
}

function StepStatusPreview({
  step,
  correlationId,
}: {
  step: OnboardingStep
  correlationId: string
}) {
  const { label, detail, tone } = stateInfo(step.state)
  const progressValue =
    tone === "done" ? 100 : tone === "busy" ? null : tone === "idle" ? 0 : 50

  const details = [
    step.target && { term: "Target", value: step.target.externalKey },
    step.attemptCount && { term: "Attempts", value: String(step.attemptCount) },
    step.lastErrorCode && { term: "Error code", value: step.lastErrorCode },
    step.completedAt && { term: "Completed", value: formatTime(step.completedAt) },
    SUPPORT_STEP_STATES.includes(step.state) && {
      term: "Correlation ID",
      value: correlationId,
    },
  ].filter(Boolean) as { term: string; value: string }[]

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <span className="inline-flex items-center gap-1.5 text-sm font-medium">
          <span className={cn("size-2 rounded-full", toneClassName[tone])} />
          {label}
        </span>
        <p className="text-white/80">{detail}</p>
      </div>
      <HaloProgress aria-label={label} value={progressValue} />
      {details.length > 0 && (
        <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
          {details.map(({ term, value }) => (
            <React.Fragment key={term}>
              <dt className="text-white/60">{term}</dt>
              <dd className="font-mono break-all">{value}</dd>
            </React.Fragment>
          ))}
        </dl>
      )}
    </div>
  )
}

function stepAction(
  step: OnboardingStep,
  onRetry: (steps: string[]) => void
): IntroDisclosureStep["action"] {
  if (RETRYABLE_STEP_STATES.includes(step.state)) {
    return { label: "Retry this step", onClick: () => onRetry([step.type]) }
  }
  if (
    step.type === "GITHUB_TEAM_PROVISIONING" &&
    step.state === "PENDING_EXTERNAL_ACCEPTANCE"
  ) {
    return {
      label: "Accept your GitHub invitation",
      href: "https://github.com/settings/organizations",
    }
  }
  return undefined
}

function toDisclosureStep(
  step: OnboardingStep,
  correlationId: string,
  onRetry: (steps: string[]) => void
): IntroDisclosureStep {
  const copy = STEP_COPY[step.type]
  const { detail } = stateInfo(step.state)
  const target = step.target?.externalKey
  // The image overlay (and its correlation ID) is desktop-only, so mobile gets it here.
  const supportNote = SUPPORT_STEP_STATES.includes(step.state)
    ? ` Correlation ID: ${correlationId}`
    : ""

  return {
    title: copy?.title ?? step.type,
    short_description: `${target ? `${detail} (${target})` : detail}${supportNote}`,
    full_description: copy?.description ?? "",
    badge: <StepStatusBadge state={step.state} />,
    media: copy && { type: "image", ...copy.image },
    preview: <StepStatusPreview step={step} correlationId={correlationId} />,
    action: stepAction(step, onRetry),
  }
}

interface Props {
  initial: OnboardingStatusResult
}

export function ProvisioningSteps({ initial }: Props) {
  const [open, setOpen] = React.useState(true)
  const queryClient = useQueryClient()

  const { data: result, refetch, isFetching } = useQuery({
    queryKey: STATUS_QUERY_KEY,
    queryFn: () => fetchOnboardingStatus(),
    initialData: initial,
    throwOnError: false,
    refetchInterval: (query) => {
      const current = query.state.data
      if (!current?.ok) return false
      return TERMINAL_REQUEST_STATES.includes(current.status.state)
        ? false
        : POLL_INTERVAL_MS
    },
  })

  const retry = useMutation({
    mutationFn: (steps: string[]) => retryFailedSteps(steps),
    throwOnError: false,
    onSuccess: (outcome) => {
      if (!outcome.ok) {
        toast.error(outcome.message)
        return
      }
      toast.success("Retrying. We'll update the steps as they progress.")
      // Refetching also restarts polling once the request leaves its terminal state.
      return queryClient.invalidateQueries({ queryKey: STATUS_QUERY_KEY })
    },
    onError: () => toast.error("Unable to retry these steps."),
  })

  if (!result.ok) {
    return (
      <OnboardingMessage title="We couldn't load your onboarding" body={result.message}>
        <OnboardingActions
          retryLabel="Try again"
          onRetry={() => refetch()}
          isRetrying={isFetching}
        />
      </OnboardingMessage>
    )
  }

  return (
    <ProvisioningDisclosure
      status={result.status}
      open={open}
      setOpen={setOpen}
      onRetry={(steps) => retry.mutate(steps)}
      isRetrying={retry.isPending}
    />
  )
}

function ProvisioningDisclosure({
  status,
  open,
  setOpen,
  onRetry,
  isRetrying,
}: {
  status: GetOnboardingStatusResponse
  open: boolean
  setOpen: (open: boolean) => void
  onRetry: (steps: string[]) => void
  isRetrying: boolean
}) {
  const steps = status.steps.map((step) =>
    toDisclosureStep(step, status.correlationId, onRetry)
  )
  const retryableSteps = status.steps
    .filter((step) => RETRYABLE_STEP_STATES.includes(step.state))
    .map((step) => step.type)
  const completedSteps = status.steps.flatMap((step, index) =>
    step.state === "SUCCEEDED" ? [index] : []
  )
  const firstOpenStep = status.steps.findIndex(
    (step) => step.state !== "SUCCEEDED"
  )
  const heading = REQUEST_LABELS[status.state] ?? status.state

  return (
    <>
      <OnboardingMessage
        title={heading}
        body={`${completedSteps.length} of ${steps.length} provisioning steps complete.`}
      >
        {!open && steps.length > 0 && (
          <CosmicButton as="button" type="button" onClick={() => setOpen(true)}>
            View provisioning steps
          </CosmicButton>
        )}
        <OnboardingActions
          retryLabel="Retry failed steps"
          onRetry={
            retryableSteps.length > 0 ? () => onRetry(retryableSteps) : undefined
          }
          isRetrying={isRetrying}
        />
      </OnboardingMessage>
      {steps.length > 0 && (
        <IntroDisclosure
          featureId={`onboarding-${status.requestId}`}
          title={heading}
          steps={steps}
          open={open}
          setOpen={setOpen}
          initialStep={firstOpenStep === -1 ? 0 : firstOpenStep}
          completedSteps={completedSteps}
          progress={(completedSteps.length / steps.length) * 100}
          showDontShowAgain={false}
        />
      )}
    </>
  )
}

function OnboardingActions({
  retryLabel,
  onRetry,
  isRetrying,
}: {
  retryLabel: string
  /** Omit to hide the retry button. */
  onRetry?: () => void
  isRetrying: boolean
}) {
  return (
    <div className="flex flex-wrap items-center justify-center gap-3">
      {onRetry && (
        <Button className="rounded-full" onClick={onRetry} disabled={isRetrying}>
          {isRetrying ? "Retrying…" : retryLabel}
        </Button>
      )}
      <Button asChild className="rounded-full" variant="ghost">
        <Link href="/">Return home</Link>
      </Button>
    </div>
  )
}

function OnboardingMessage({
  title,
  body,
  children,
}: {
  title: string
  body: string
  children?: React.ReactNode
}) {
  return (
    <div className="container flex min-h-dvh flex-col items-center justify-center gap-6 px-4 text-center">
      <h1 className="font-heading text-3xl font-medium tracking-[-0.04em] sm:text-5xl">
        {title}
      </h1>
      <p className="text-muted-foreground max-w-xl">{body}</p>
      {children}
    </div>
  )
}

export default ProvisioningSteps
