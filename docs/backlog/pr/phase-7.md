## EDP-198 · Phase 7: Frontend Onboarding UX

Implements the complete GitHub-first onboarding user experience in the Next.js frontend. A new user clicks the GitHub sign-in button on `/register`, completes OAuth, is automatically registered against the backend in the JWT callback, and is redirected to `/onboarding` where real-time provisioning progress is displayed via React Query polling. Step states, action-required callouts, error codes, and correlation ID display are all covered. A new `@feature/base` Nx library is introduced to own backend API actions that are not auth-specific.

---

## Commits

| Commit | Story | Summary |
|---|---|---|
| `114ab32` | EDP-199 | GitHub OAuth provider and JWT session enrichment |
| `7eb6f4a` | EDP-200 | Registration API client and first-sign-in backend call |
| `c796760` | EDP-201 | Onboarding status polling page |
| `dac8b82` | EDP-201 | Onboarding status polling page |
| `14bc58e` | — | GitHub sign-in button (LiquidMetalButton) |
| `52ec6fd` | EDP-202 | Step-by-step progress UI components |
| `6ccf6ec` | EDP-203 | Pending, error, and action-required states with correlation ID display |

---

## What's in this PR

### EDP-199 · GitHub OAuth provider and JWT session enrichment

- `jwtCallback` and `sessionCallback` extracted from the NextAuth config into `features/auth/src/lib/auth/callbacks.ts` as standalone exported async functions — enables unit testing without mounting the full NextAuth handler
- `jwtCallback` captures `githubId`, `githubLogin`, and `githubAccessToken` from the GitHub profile on every OAuth sign-in; stores `onboardingRequestId`, `correlationId`, and a `registrationError` flag on first sign-in
- `sessionCallback` propagates all six fields from the JWT to `session.user`
- `features/auth/src/lib/types/next-auth.d.ts` — extended `User` and `JWT` interfaces with `githubId?`, `githubLogin?`, `githubAccessToken?`, `onboardingRequestId?`, `correlationId?`, `registrationError?`
- `features/auth/src/lib/types/schema.ts` (new) — exports `registerUserSchema` (`z.object`) shared between the action and its spec
- `features/auth/src/lib/auth/auth.config.ts` — fixed `pages.signIn`, `pages.error`, and `pages.verifyRequest` from the non-existent `/auth` route to `/register`; the `/auth` target caused NextAuth error redirects to 404

### EDP-200 · Registration API client and first-sign-in backend call

- `features/auth/src/lib/actions/registration.ts` (new) — `'use server'` action wrapping `api.post<RegisterUserResponse>('/api/v1/registrations', ...)` via `withApi`; Zod `safeParse` against `registerUserSchema` before the request is sent
- Exports `RegisterUserRequest`, `RegisterUserResponse`, `StepSummary` types derived from the Zod schema and inline interfaces
- Called inside `jwtCallback` when `!token.onboardingRequestId`; on success, `onboardingRequestId` and `correlationId` are written to the token; on failure, `registrationError = true` is set so the onboarding page can surface it without an unhandled exception
- `features/auth/src/lib/config/client.ts` — `ApiClient` instance configured against `AUTH_API_URL`; `User-Agent` header set from `package.json` name and version

### EDP-201 · Onboarding status polling page

**`@feature/base` library (new Nx project)**

- `features/base/src/lib/actions/onboarding.ts` — `'use server'` action wrapping `api.get<GetOnboardingStatusResponse>('/api/v1/onboarding/${requestId}')` via `withApi`; Zod `safeParse` against `getOnboardingStatusSchema` (UUID) before the request
- Exports `GetOnboardingStatusRequest`, `GetOnboardingStatusResponse`, `OnboardingStep`, `OnboardingStepTarget`
- `features/base/src/lib/types/schema.ts` — `getOnboardingStatusSchema = z.object({ requestId: z.string().uuid() })`
- `features/base/src/lib/config/client.ts` — dedicated `ApiClient` instance against `BASE_API_URL`
- `server.ts` entry point re-exports the action and its types for consumption as `@feature/base/server`

**Pages and components**

- `apps/web/app/onboarding/page.tsx` — server component; calls `auth()`, redirects to `/register` if no session, passes `onboardingRequestId` and `correlationId` props to `OnboardingPoller`
- `apps/web/components/onboarding/OnboardingPoller.tsx` — `'use client'`; uses `useQuery` with `refetchInterval: (query) => shouldStopPolling(query.state.data?.state) ? false : 3000`; exports `TERMINAL_STATES` (Set), `shouldStopPolling`, and `shouldShowCorrelationBadge` as pure functions for unit testing
- `apps/web/components/onboarding/OnboardingStatusBanner.tsx` — renders the top-level request state (`REQUESTED`, `IN_PROGRESS`, `COMPLETED`, `PARTIAL_SUCCESS`, `FAILED`) as a colour-coded `role="status" aria-live="polite"` banner
- `apps/web/app/register/page.tsx` — renders `LiquidMetalButton` centred on a black background
- `apps/web/jest.config.cts` — fixed `dir: './'` → `dir: __dirname`; added `setupFilesAfterEnv: ['<rootDir>/jest.setup.ts']`
- `apps/web/jest.setup.ts` (new) — `import '@testing-library/jest-dom'`

**GitHub sign-in button**

- `apps/web/components/register/liquid-metal-button.tsx` — animated 3D layered button with CSS ripple effect; calls `signIn("github")` on click; supports `viewMode: "text" | "icon"`

### EDP-202 · Step-by-step progress UI components

- `apps/web/components/onboarding/StepItem.tsx` — renders a single step row; state-to-icon mapping:
  - `PENDING` → `Clock` (gray, `opacity-50`)
  - `PROCESSING` / `IN_PROGRESS` → `Loader2` with `animate-spin` (blue)
  - `SUCCEEDED` / `COMPLETED` → `CheckCircle2` (green)
  - `FAILED` → `XCircle` (red)
  - `PENDING_EXTERNAL_ACCEPTANCE` / `ACTION_REQUIRED` → `AlertTriangle` (yellow)
- Human-readable step type labels: `IDENTITY_CORRELATION` → "Identity Verification", `GITHUB_TEAM_PROVISIONING` → "GitHub Team Provisioning", `JIRA_GROUP_PROVISIONING` → "Jira Group Provisioning"; unknown types fall back to the raw enum string
- Action-required callout (yellow bordered block) rendered for `PENDING_EXTERNAL_ACCEPTANCE` and `ACTION_REQUIRED` states, with per-type instruction text
- `lastErrorCode` shown as a monospace error line when `step.state === 'FAILED'` and the field is present
- `apps/web/components/onboarding/StepList.tsx` — `<ul aria-label="Onboarding steps">` rendering a `StepItem` per entry; returns `null` for empty arrays
- `OnboardingPoller` updated to compose `StepList` below `OnboardingStatusBanner`

### EDP-203 · Pending, error, and action-required states with correlation ID display

- `apps/web/components/onboarding/CorrelationIdBadge.tsx` — `'use client'`; renders the correlation ID in a monospace box; copy-to-clipboard button (icon toggles `Copy` → `Check` for 2 s via `useState`); "Need help? Contact support →" `Link` to `/support?correlationId={encodeURIComponent(correlationId)}`
- `shouldShowCorrelationBadge(data)` exported from `OnboardingPoller` — returns `true` when overall state is `FAILED`, `PARTIAL_SUCCESS`, or `CANCELLED`, or when any step has state `PENDING_EXTERNAL_ACCEPTANCE` or `ACTION_REQUIRED`; `CorrelationIdBadge` is composed below `StepList` when this condition is met, using `data.correlationId` with session `correlationId` as fallback
- `apps/web/app/support/page.tsx` — server component; reads `searchParams.correlationId`; pre-populates it in a `select-all` monospace field; support email `href` includes a pre-filled subject line containing the correlation ID

---

## Test coverage

| Library / App | Story | Test type | File | Tests |
|---|---|---|---|---|
| `features/auth` | EDP-199, EDP-200 | Jest unit | `callbacks.spec.ts` | 13 |
| `features/auth` | EDP-200 | Jest unit | `registration.spec.ts` | 11 |
| `features/base` | EDP-201 | Jest unit | `onboarding.spec.ts` | 6 |
| `apps/web` | EDP-201 | React Testing Library | `OnboardingStatusBanner.spec.tsx` | 8 |
| `apps/web` | EDP-201, EDP-203 | React Testing Library | `OnboardingPoller.spec.tsx` | 24 |
| `apps/web` | EDP-202 | React Testing Library | `StepItem.spec.tsx` | 11 |
| `apps/web` | EDP-202 | React Testing Library | `StepList.spec.tsx` | 6 |
| `apps/web` | EDP-203 | React Testing Library | `CorrelationIdBadge.spec.tsx` | 4 |

---

## Key design decisions

**`withApi` wrapper preserved in all server actions.** Both `registerUser` (auth feature) and `getOnboardingStatus` (base feature) keep the `withApi` boundary and Zod `safeParse`. This ensures errors from the backend are always converted to an `ApiResponse<T>` shape rather than thrown as raw fetch errors, which would escape React Query's error boundary.

**`@feature/base` is a separate Nx library, not co-located in `@feature/auth`.** The onboarding polling action does not require auth state; placing it in `features/base` keeps auth concerns isolated and allows any future page (dashboard, admin) to import `getOnboardingStatus` without pulling in the NextAuth dependency chain.

**`jwtCallback` registers the user exactly once.** The guard `if (!token.onboardingRequestId)` prevents `POST /api/v1/registrations` from being called on every token refresh or returning-user sign-in. The backend returns 409 for duplicate registrations, but the guard avoids the round-trip entirely. `registrationError = true` on failure keeps sign-in from throwing so the user lands on the onboarding page and sees an error state rather than a broken OAuth redirect.

**`refetchInterval` as a function, not a value.** `refetchInterval: (query) => shouldStopPolling(query.state.data?.state) ? false : 3000` evaluates on every query result. The moment a terminal state arrives, the next interval is `false` — no extra request is issued after `COMPLETED` or `FAILED`.

**`shouldStopPolling` and `shouldShowCorrelationBadge` exported as pure functions.** Keeping conditional display logic outside the component body allows them to be unit-tested without React Testing Library rendering, keeping those tests fast and deterministic. Both are covered in `OnboardingPoller.spec.tsx`.

**`dir: __dirname` in `jest.config.cts`.** `next/jest`'s `createJestConfig({ dir })` resolves the Next.js project root relative to the config file, not the process working directory. When Nx runs tests from the workspace root (`frontend/`), the original `dir: './'` resolved to the workspace root and Next.js could not find `apps/web/app/` or `apps/web/pages/`. Switching to `dir: __dirname` pins resolution to the config file's own directory regardless of where the test runner is invoked.

---

## Docs & schema references

- REST contract: `docs/rest-api-reference.md` — section 3 (`GET /api/v1/onboarding/{requestId}`)
- Architecture constraints: `docs/design-spec.md` — frontend calls backend REST APIs; backend never handles GitHub OAuth
- Roadmap exit criteria: `docs/roadmap.md` — Phase 7
- Backlog: `docs/backlog/phase-7-frontend-onboarding-ux.json`

---

## How to verify locally

```bash
# 1. Create the frontend env file
cp frontend/apps/web/.env.example frontend/apps/web/.env.local
# Fill in AUTH_SECRET, GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, AUTH_API_URL, BACKEND_API_URL

# 2. Run the full test suite
cd frontend
pnpm nx run-many -t test

# 3. Start the dev server
pnpm nx run web:serve

# 4. Open http://localhost:3000/register
#    Click the GitHub sign-in button
#    Complete OAuth — you are redirected to /onboarding
#    Polling starts automatically every 3 s
#    Navigate to /support?correlationId=<any-uuid> to verify pre-population
```

GitHub OAuth App callback URL must be set to `http://localhost:3000/api/auth/callback/github`.