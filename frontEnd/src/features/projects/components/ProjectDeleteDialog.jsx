import { useRef, useState } from 'react';

import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { Alert, Button, Dialog, TextInput } from '../../../shared/ui/index.js';
import { createProjectApi } from '../api/projectApi.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import { getWriteRestriction, isServicePolicyError } from '../../service-policy/servicePolicyRestrictions.js';

export default function ProjectDeleteDialog({ project, open, onClose, onDeleted }) {
  const client = useApiClient();
  const servicePolicy = useServicePolicy();
  const restriction = getWriteRestriction(servicePolicy);
  const inputRef = useRef(null);
  const [confirmation, setConfirmation] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');
  const close = () => { if (!deleting) { setConfirmation(''); setError(''); onClose(); } };
  const remove = async () => {
    if (!project || confirmation !== project.name || deleting || restriction.blocked) return;
    setDeleting(true); setError('');
    try {
      await createProjectApi(client).remove(project.projectId);
      setConfirmation('');
      await onDeleted?.(project);
    } catch (nextError) {
      if (isServicePolicyError(nextError)) {
        void servicePolicy.refresh().catch(() => undefined);
      }
      setError(getUserErrorMessage(nextError));
    } finally {
      setDeleting(false);
    }
  };
  return <Dialog open={open} onClose={close} title="프로젝트를 삭제할까요?" initialFocusRef={inputRef}><div className="project-delete-dialog"><p>이 작업은 되돌릴 수 없습니다. 연결된 문서와 분석 결과도 더 이상 사용할 수 없습니다.</p>{restriction.blocked && <Alert tone={restriction.code === 'POLICY_UNAVAILABLE' ? 'danger' : 'warning'} title="삭제할 수 없습니다"><p>{restriction.message}</p>{restriction.code === 'POLICY_UNAVAILABLE' && <Button type="button" variant="outline" size="small" onClick={() => void servicePolicy.refresh().catch(() => undefined)}>다시 시도</Button>}</Alert>}<div className="project-delete-dialog__target"><span>삭제할 프로젝트</span><strong>{project?.name}</strong></div><div className="project-delete-dialog__field"><TextInput ref={inputRef} id="delete-project-confirmation" label="확인을 위해 프로젝트 이름을 입력하세요." value={confirmation} onChange={(event) => setConfirmation(event.target.value)} disabled={restriction.blocked} />{confirmation && confirmation !== project?.name && <p className="project-delete-dialog__mismatch" role="status">프로젝트 이름이 일치하지 않습니다.</p>}</div>{error && <Alert tone="danger" title="삭제하지 못했습니다.">{error}</Alert>}<div className="project-delete-dialog__footer"><Button variant="outline" disabled={deleting} onClick={close}>취소</Button><Button variant="danger" loading={deleting} disabled={confirmation !== project?.name || deleting || restriction.blocked} onClick={remove}>영구 삭제</Button></div></div></Dialog>;
}
