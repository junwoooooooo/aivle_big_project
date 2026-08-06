import { Button } from '../../../shared/ui/index.js';
import { getAdminErrorMessage } from '../api/adminErrorResolver.js';

export default function AdminErrorState({ error, onRetry }) {
  return (
    <section className="admin-error-state" role="alert">
      <p>{getAdminErrorMessage(error)}</p>
      {onRetry && <Button size="small" variant="outline" onClick={onRetry}>다시 시도</Button>}
    </section>
  );
}
