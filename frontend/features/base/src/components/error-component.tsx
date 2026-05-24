import { toast } from 'sonner';
import type { ProblemDetail } from '@next-feature/client';

interface Props {
  error: ProblemDetail | undefined;
}

export function ErrorComponent(props: Props) {
  return (
    <div
      style={{ display: 'flex', alignItems: 'start', flexDirection: 'column' }}
    >
      <h1>
        <strong>BACKEND SERVER ERROR</strong>
      </h1>
      <span>
        <strong>statusCode</strong>: {props.error?.status}
      </span>
      <span>
        <strong>path</strong>: {props.error?.instance}
      </span>
      <span>
        <strong>message</strong>: {props.error?.detail}
      </span>
      <span>
        <strong>timestamp</strong>: {Date.now()}
      </span>
    </div>
  );
}

export function withToast(error: ProblemDetail) {
  toast.error(<ErrorComponent error={error} />);
}
