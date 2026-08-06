const FAILURE_MESSAGES = {
  AI_SERVICE_UNAVAILABLE: 'AI Provider 또는 내부 AI 서비스에 연결하지 못했습니다. 서비스와 인증 설정을 확인한 뒤 재시도할 수 있습니다.',
  TASK_TIMEOUT: 'AI 작업이 제한 시간을 초과했습니다. Provider 상태를 확인한 뒤 재시도할 수 있습니다.',
  AI_RESULT_INVALID: 'AI 응답이 현재 Contract를 충족하지 못했습니다. 자동 재시도하지 않고 입력과 Contract 로그를 확인해야 합니다.',
  PAYLOAD_TOO_LARGE: '입력 크기가 허용 범위를 초과했습니다. Idea Origin의 입력 범위를 줄여야 합니다.',
  AI_CONFIGURATION_INVALID: 'AI Provider, 모델 또는 API Key 설정이 유효하지 않습니다. 설정을 수정하기 전에는 재시도하지 않습니다.',
  MODEL_DEPENDENCY_UNAVAILABLE: 'AI 모델 또는 Provider 의존성을 사용할 수 없습니다. 인증과 모델 설정을 확인해 주세요.',
  REQUEST_DEADLINE_EXCEEDED: 'AI 작업이 제한 시간을 초과했습니다. Provider 상태를 확인한 뒤 재시도할 수 있습니다.',
};

const SOURCE_MESSAGES = {
  SOURCE_PARTIAL: '공식 Source 일부만 확인되었습니다. 확인된 근거를 Guardrail로 적용해 조건부 진행할 수 있으며, 누락 범위는 후속 검토가 필요합니다.',
  REGISTRY_GAP: '현재 Registry에 없는 규제 경로가 감지되었습니다. 성공으로 간주하지 않으며 Registry 보완 또는 전문가 확인이 필요합니다.',
};

export function journeyFailureMessage(code) {
  return FAILURE_MESSAGES[code] || '작업 실행에 실패했습니다. 요청 ID와 실행 로그에서 실패 원인을 확인해 주세요.';
}

export function legalSourceMessage(status) {
  return SOURCE_MESSAGES[status] || '';
}
