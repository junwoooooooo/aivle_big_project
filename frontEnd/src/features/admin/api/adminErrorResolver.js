const MESSAGES = {
  ADMIN_REAUTHENTICATION_FAILED: '관리자 비밀번호가 올바르지 않습니다.',
  REAUTHENTICATION_FAILED: '관리자 비밀번호가 올바르지 않습니다.',
  ADMIN_REAUTHENTICATION_EXPIRED: '재인증 시간이 만료되었습니다. 다시 인증해 주세요.',
  REAUTHENTICATION_EXPIRED: '재인증 시간이 만료되었습니다. 다시 인증해 주세요.',
  ADMIN_REAUTHENTICATION_PURPOSE_MISMATCH: '현재 인증 정보는 이 작업에 사용할 수 없습니다.',
  ADMIN_ACTION_TOKEN_ALREADY_USED: '이미 사용된 인증 정보입니다. 다시 인증해 주세요.',
  LAST_ACTIVE_ADMIN_REQUIRED: '마지막 활성 관리자는 변경할 수 없습니다.',
  SELF_ADMIN_ROLE_CHANGE_NOT_ALLOWED: '자기 자신의 관리자 권한은 변경할 수 없습니다.',
  SELF_ADMIN_ACCOUNT_CHANGE_NOT_ALLOWED: '현재 로그인한 관리자 계정은 잠금 또는 비활성화할 수 없습니다.',
  SELF_SESSION_REVOKE_NOT_ALLOWED: '현재 로그인한 관리자 계정의 세션은 이 화면에서 종료할 수 없습니다.',
  ACCOUNT_ALREADY_DELETED: '이미 탈퇴 처리된 사용자입니다.',
  ADMIN_SELF_DELETE_NOT_ALLOWED: '현재 로그인한 관리자 계정은 삭제할 수 없습니다.',
  LAST_ACTIVE_ADMIN_DELETE_NOT_ALLOWED: '마지막 활성 관리자 계정은 삭제할 수 없습니다.',
  USER_DELETE_REAUTHENTICATION_REQUIRED: '사용자 삭제를 수행하려면 관리자 재인증이 필요합니다.',
  CLUSTER_PERSONA_DISABLED: '군집 페르소나 기능이 비활성화되어 있습니다.',
  CLUSTER_PERSONA_NOT_ALLOWED: '현재 사용자에게 제공되지 않는 페르소나입니다.',
  CLUSTER_PERSONA_NOT_FOUND: '군집 페르소나를 찾을 수 없습니다.',
  CLUSTER_PERSONA_SELECTION_REQUIRED: '기능을 사용하려면 페르소나를 하나 이상 노출해야 합니다.',
  CLUSTER_PERSONA_LIMIT_EXCEEDED: '군집 페르소나는 최대 6개까지 노출할 수 있습니다.',
  PROJECT_PERSONA_SELECTION_NOT_ALLOWED: '현재 프로젝트에서는 이 페르소나를 선택할 수 없습니다.',
  ADMIN_ACCESS_REQUIRED: '관리자 권한이 필요합니다.',
  ACCESS_DENIED: '관리자 권한이 필요합니다.',
  AUTHENTICATION_REQUIRED: '로그인이 필요합니다.',
  USER_NOT_FOUND: '사용자를 찾을 수 없습니다.',
  PROJECT_NOT_FOUND: '프로젝트를 찾을 수 없습니다.',
  AUDIT_EVENT_NOT_FOUND: '감사 기록을 찾을 수 없습니다.',
  INVALID_REQUEST: '검색 조건을 확인해 주세요.',
  USER_ALREADY_LOCKED: '이미 잠긴 사용자입니다.',
  SERVICE_SETTING_INVALID: '서비스 설정 값이 올바르지 않습니다.',
  SERVICE_SETTING_ALREADY_APPLIED: '이미 적용된 설정입니다. 최신 상태를 다시 확인해 주세요.',
  SERVICE_SETTING_NOT_FOUND: '서비스 설정을 찾을 수 없습니다.',
};

export function getAdminErrorMessage(error) {
  const message = MESSAGES[error?.code] ?? '작업을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.';
  return error?.status >= 500 && error?.requestId ? `${message} 요청 ID: ${error.requestId}` : message;
}

export function isAdminRequestAborted(error) {
  return error?.name === 'AbortError' || error?.code === 'REQUEST_ABORTED';
}

export function isAdminReauthenticationError(error) {
  return ['ADMIN_REAUTHENTICATION_FAILED', 'REAUTHENTICATION_FAILED', 'ADMIN_REAUTHENTICATION_EXPIRED', 'REAUTHENTICATION_EXPIRED', 'ADMIN_REAUTHENTICATION_PURPOSE_MISMATCH', 'ADMIN_ACTION_TOKEN_ALREADY_USED', 'USER_DELETE_REAUTHENTICATION_REQUIRED'].includes(error?.code);
}
