export function marketRunFailureMessage(code) {
  if (code === 'DEADLINE_EXCEEDED' || code === 'TASK_TIMEOUT') {
    return '시장조사 실행 시간이 제한을 초과했습니다';
  }
  if (code === 'DEPENDENCY_UNAVAILABLE' || code === 'AI_SERVICE_UNAVAILABLE') {
    return 'AI 또는 외부 근거 서비스에 연결할 수 없습니다';
  }
  if (code === 'INVALID_REQUEST' || code === 'AI_RESULT_INVALID') {
    return '시장조사 입력이 유효하지 않습니다';
  }
  if (code === 'RESULT_SCHEMA_INVALID') {
    return '시장조사 결과가 서비스 계약을 충족하지 못했습니다';
  }
  return '시장조사 실행을 완료하지 못했습니다';
}
