import usePasswordChecks from '../hooks/usePasswordChecks.js';

const state = (valid, invalid = false) => valid ? 'is-valid' : invalid ? 'is-invalid' : '';
function Rule({ valid, invalid, children }) { return <span className={state(valid, invalid)}><span aria-hidden="true">{valid ? '✓' : '○'}</span><span>{children}</span></span>; }

export default function PasswordRequirements({ password, confirmPassword, username, displayName, serverError }) {
  const checks = usePasswordChecks(password, confirmPassword, username, displayName);
  const uniquenessInvalid = checks.hasInput && !checks.isNotCommonOrSimilar;
  return <div className="auth-password-rules" aria-label="안전한 비밀번호 만들기"><strong>안전한 비밀번호 만들기</strong>
    <Rule valid={checks.hasMinimumLength} invalid={checks.hasInput && !checks.hasMinimumLength}>15자 이상{checks.hasInput && !checks.hasMinimumLength ? ` · ${checks.remainingMinimumCharacters}자 더 필요` : ''}</Rule>
    <Rule valid={checks.isWithinMaximumLength} invalid={checks.hasInput && !checks.isWithinMaximumLength}>최대 64자 이하</Rule>
    <Rule valid={checks.hasInput && !checks.matchesUsername} invalid={checks.matchesUsername}>아이디가 그대로 포함되지 않음</Rule>
    <Rule valid={checks.hasInput && !checks.matchesDisplayName} invalid={checks.matchesDisplayName}>이름 또는 닉네임이 그대로 포함되지 않음</Rule>
    <Rule valid={checks.hasInput && !checks.hasRepeatedPattern} invalid={checks.hasRepeatedPattern}>같은 문자나 짧은 문자열을 반복하지 않음</Rule>
    <Rule valid={checks.hasInput && !checks.isCommonPassword && !checks.hasSequentialPattern && !serverError} invalid={uniquenessInvalid || Boolean(serverError)}>흔히 사용하는 비밀번호나 연속 패턴이 아님</Rule>
    <Rule valid={checks.confirmationMatches} invalid={confirmPassword.length > 0 && !checks.confirmationMatches}>비밀번호 확인과 일치</Rule>
    <p>문장처럼 길고 고유한 비밀번호를 권장합니다. 가입할 때 반복 패턴, 계정 정보 포함 여부와 흔히 사용되는 비밀번호를 확인합니다.</p>
  </div>;
}
