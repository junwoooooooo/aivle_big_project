import { useId, useRef, useState } from 'react';

import { Button, Dialog, PasswordInput, TextInput } from '../../../shared/ui/index.js';
import { getAdminErrorMessage, isAdminReauthenticationError } from '../api/adminErrorResolver.js';

export default function AdminActionConfirmDialog({
  open,
  title,
  description,
  targetLabel,
  currentState,
  nextState,
  purpose,
  requiresReauthentication,
  onCancel,
  onConfirm,
  busy = false,
  confirmLabel = '확인',
}) {
  const [reason, setReason] = useState('');
  const [password, setPassword] = useState('');
  const [reasonError, setReasonError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [submitError, setSubmitError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const reasonRef = useRef(null);
  const passwordRef = useRef(null);
  const descriptionId = useId();

  function close() {
    if (busy || isSubmitting) return;
    setPassword('');
    onCancel();
  }

  async function submit() {
    if (busy || isSubmitting) return;
    const nextReasonError = reason.trim() ? '' : '변경 사유를 입력해 주세요.';
    const nextPasswordError = requiresReauthentication && !password
      ? '현재 관리자 비밀번호를 입력해 주세요.'
      : '';
    setReasonError(nextReasonError);
    setPasswordError(nextPasswordError);
    setSubmitError('');
    if (nextReasonError) {
      reasonRef.current?.focus();
      return;
    }
    if (nextPasswordError) {
      passwordRef.current?.focus();
      return;
    }
    setIsSubmitting(true);
    try {
      await onConfirm({ reason: reason.trim(), password, purpose });
    } catch (error) {
      const message = getAdminErrorMessage(error);
      setPassword('');
      if (isAdminReauthenticationError(error)) {
        setPasswordError(message);
        setSubmitError('');
        window.requestAnimationFrame(() => passwordRef.current?.focus());
      } else {
        setPasswordError('');
        setSubmitError(message);
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  const submitting = busy || isSubmitting;
  return (
    <Dialog
      open={open}
      onClose={close}
      title={title}
      initialFocusRef={reasonRef}
      describedBy={descriptionId}
    >
      <p id={descriptionId}>{description}</p>
      <dl className="admin-confirm-summary">
        <dt>대상</dt><dd>{targetLabel}</dd>
        <dt>현재 상태</dt><dd>{currentState}</dd>
        <dt>변경 후 상태</dt><dd>{nextState}</dd>
      </dl>
      <TextInput
        ref={reasonRef}
        label="변경 사유"
        value={reason}
        error={reasonError}
        onChange={(event) => {
          setReason(event.target.value);
          setReasonError('');
        }}
        required
      />
      {requiresReauthentication && (
        <PasswordInput
          ref={passwordRef}
          label="현재 관리자 비밀번호"
          value={password}
          error={passwordError}
          onChange={(event) => {
            setPassword(event.target.value);
            setPasswordError('');
          }}
          required
        />
      )}
      {submitError && <p className="admin-error" role="alert" tabIndex="-1">{submitError}</p>}
      <div className="admin-actions admin-confirm-actions">
        <Button variant="outline" disabled={submitting} onClick={close}>취소</Button>
        <Button loading={submitting} disabled={submitting} onClick={submit}>{confirmLabel}</Button>
      </div>
    </Dialog>
  );
}
