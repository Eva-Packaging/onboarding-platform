import { toast } from 'sonner';
import type { ErrorMessage } from '../lib/axios/types';

interface Props {
  error: ErrorMessage | undefined;
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
        <strong>statusCode</strong>: {props.error?.statusCode}
      </span>
      <span>
        <strong>path</strong>: {props.error?.path}
      </span>
      <span>
        <strong>message</strong>: {props.error?.message}
      </span>
      <span>
        <strong>timestamp</strong>: {props.error?.timestamp.toString()}
      </span>
    </div>
  );
}

export function withToast(error: ErrorMessage) {
  toast.error(<ErrorComponent error={error} />);
}
