import { useEffect, useState } from 'react';

function formatRemaining(seconds) {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`;
}

export default function LoginRateLimitNotice({ remainingSeconds }) {
  const [warning, setWarning] = useState(null);
  useEffect(() => {
    const restoreWarning = () => {
      try {
        const saved = JSON.parse(window.sessionStorage.getItem('authLoginAttemptWarning') ?? 'null');
        if (saved?.expiresAt > Date.now()) setWarning(saved);
        else {
          window.sessionStorage.removeItem('authLoginAttemptWarning');
          setWarning(null);
        }
      } catch {
        window.sessionStorage.removeItem('authLoginAttemptWarning');
        setWarning(null);
      }
    };
    restoreWarning();
    window.addEventListener('auth-login-attempt-warning', restoreWarning);
    return () => window.removeEventListener('auth-login-attempt-warning', restoreWarning);
  }, [remainingSeconds]);
  if (!remainingSeconds && warning) {
    const isFinal = warning.warningLevel === 'FINAL_WARNING';
    return <div className={`auth-attempt-warning${isFinal ? ' auth-attempt-warning--final' : ''}`} role="status">
      <strong>{isFinal ? '마지막 로그인 시도 전 안내' : '반복된 로그인 실패 안내'}</strong>
      <span>{isFinal
        ? '다시 실패하면 로그인이 일시적으로 제한됩니다. 아이디와 비밀번호를 다시 확인해 주세요.'
        : '로그인에 연속으로 실패했습니다. 2회 더 실패하면 로그인이 일시적으로 제한됩니다.'}</span>
    </div>;
  }
  if (!remainingSeconds) return null;
  return <div className="auth-rate-limit" role="alert">
    <strong>로그인 시도가 반복되어 잠시 제한되었습니다.</strong>
    <span>계정이 영구 잠긴 것은 아닙니다. 보안을 위해 {formatRemaining(remainingSeconds)} 후 다시 시도해 주세요.</span>
    <small>아이디와 비밀번호를 확인한 뒤 제한 시간이 끝나면 다시 시도할 수 있습니다.</small>
  </div>;
}
