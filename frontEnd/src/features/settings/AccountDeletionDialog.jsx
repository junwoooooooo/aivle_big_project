import { useId, useRef, useState } from 'react';

import { Button, Dialog, PasswordInput, Textarea, TextInput } from '../../shared/ui/index.js';
import { getUserErrorMessage } from '../../shared/api/apiError.js';

export default function AccountDeletionDialog({ open, busy, onCancel, onConfirm }) {
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [reason, setReason] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [confirmationError, setConfirmationError] = useState('');
  const [submitError, setSubmitError] = useState('');
  const passwordRef = useRef(null);
  const confirmationRef = useRef(null);
  const descriptionId = useId();

  function close() {
    if (busy) return;
    setPassword('');
    setConfirmation('');
    setReason('');
    setPasswordError('');
    setConfirmationError('');
    setSubmitError('');
    onCancel();
  }

  async function submit() {
    if (busy) return;
    const nextPasswordError = password ? '' : '현재 비밀번호를 입력해 주세요.';
    const nextConfirmationError = confirmation === '회원탈퇴'
      ? ''
      : '확인 문구에 “회원탈퇴”를 정확히 입력해 주세요.';
    setPasswordError(nextPasswordError);
    setConfirmationError(nextConfirmationError);
    setSubmitError('');
    if (nextPasswordError) {
      passwordRef.current?.focus();
      return;
    }
    if (nextConfirmationError) {
      confirmationRef.current?.focus();
      return;
    }
    try {
      await onConfirm({ password, confirmation, reason: reason.trim() || null });
    } catch (error) {
      setPassword('');
      const message = getUserErrorMessage(error);
      if (error?.code === 'ACCOUNT_DELETION_PASSWORD_INVALID') {
        setPasswordError(message);
        window.requestAnimationFrame(() => passwordRef.current?.focus());
      } else if (error?.code === 'ACCOUNT_DELETION_CONFIRMATION_INVALID') {
        setConfirmationError(message);
        window.requestAnimationFrame(() => confirmationRef.current?.focus());
      } else {
        setSubmitError(message);
      }
    }
  }

  return (
    <Dialog
      open={open}
      onClose={close}
      title="회원 탈퇴"
      initialFocusRef={passwordRef}
      describedBy={descriptionId}
    >
      <p id={descriptionId}>
        탈퇴 즉시 계정 접근과 기존 세션이 종료됩니다. 프로젝트와 기존 결과는 운영·감사·보존
        정책에 따라 유지되며, 계정의 직접 식별정보는 비식별화됩니다.
      </p>
      <PasswordInput
        ref={passwordRef}
        label="현재 비밀번호"
        value={password}
        error={passwordError}
        autoComplete="current-password"
        onChange={(event) => {
          setPassword(event.target.value);
          setPasswordError('');
        }}
        required
      />
      <TextInput
        ref={confirmationRef}
        label="확인 문구"
        description="회원탈퇴를 정확히 입력해 주세요."
        value={confirmation}
        error={confirmationError}
        autoComplete="off"
        onChange={(event) => {
          setConfirmation(event.target.value);
          setConfirmationError('');
        }}
        required
      />
      <Textarea
        label="탈퇴 사유 (선택)"
        value={reason}
        maxLength={500}
        onChange={(event) => setReason(event.target.value)}
      />
      {submitError && <p className="settings-danger-zone__error" role="alert">{submitError}</p>}
      <div className="settings-danger-zone__actions">
        <Button variant="outline" disabled={busy} onClick={close}>취소</Button>
        <Button variant="danger" loading={busy} disabled={busy} onClick={submit}>회원 탈퇴</Button>
      </div>
    </Dialog>
  );
}
