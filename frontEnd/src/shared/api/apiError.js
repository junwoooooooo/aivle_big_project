export class ApiError extends Error {
  constructor({
    status = 0,
    code = 'NETWORK_ERROR',
    message = '요청을 처리하지 못했습니다.',
    fieldErrors = [],
    retryable = false,
    requestId = null,
    retryAfterSeconds = null,
    loginAttempt = null,
    cause,
  } = {}) {
    super(message, cause ? { cause } : undefined);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.retryable = retryable;
    this.requestId = requestId;
    this.retryAfterSeconds = retryAfterSeconds;
    this.loginAttempt = loginAttempt;
  }
}

export function normalizeApiError(error, fallback = {}) {
  if (error instanceof ApiError) return error;
  if (error?.name === 'AbortError') {
    return new ApiError({
      status: 0,
      code: 'REQUEST_ABORTED',
      message: '요청이 취소되었거나 제한 시간을 초과했습니다.',
      retryable: true,
      cause: error,
      ...fallback,
    });
  }
  return new ApiError({
    message: '네트워크 연결을 확인한 뒤 다시 시도해 주세요.',
    retryable: true,
    cause: error,
    ...fallback,
  });
}

const USER_MESSAGE_BY_CODE = {
  VALIDATION_FAILED: '입력한 내용을 다시 확인해 주세요.',
  INVALID_CREDENTIALS: '아이디 또는 비밀번호를 확인해 주세요.',
  USERNAME_ALREADY_EXISTS: '이미 사용 중인 아이디입니다.',
  USERNAME_NOT_ALLOWED: '사용할 수 없는 아이디입니다. 다른 아이디를 입력해 주세요.',
  LOGIN_RATE_LIMITED: '로그인 시도가 여러 번 실패했습니다. 잠시 후 다시 시도해 주세요.',
  EMAIL_ALREADY_EXISTS: '이미 사용 중인 이메일입니다.',
  USER_EMAIL_DUPLICATED: '이미 사용 중인 이메일입니다.',
  PASSWORD_POLICY_VIOLATION: '비밀번호 정책을 확인해 주세요.',
  REGISTRATION_DISABLED: '현재 신규 회원가입이 일시 중지되었습니다. 기존 계정으로 로그인해 주세요.',
  DOCUMENT_PROCESSING_DISABLED: '현재 문서 처리 기능이 일시 중지되었습니다.',
  MAINTENANCE_MODE_ENABLED: '현재 서비스 점검 중입니다. 변경 작업은 잠시 사용할 수 없습니다.',
  ACCESS_TOKEN_EXPIRED: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
  ACCESS_TOKEN_INVALID: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
  REFRESH_TOKEN_INVALID: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
  USER_INACTIVE: '현재 로그인할 수 없는 계정입니다.',
  ACCOUNT_DELETION_PASSWORD_INVALID: '현재 비밀번호가 올바르지 않습니다.',
  ACCOUNT_DELETION_CONFIRMATION_INVALID: '확인 문구에 “회원탈퇴”를 정확히 입력해 주세요.',
  ACCOUNT_ALREADY_DELETED: '이미 탈퇴 처리된 계정입니다.',
  ADMIN_SELF_DELETE_NOT_ALLOWED: '관리자 계정은 일반 계정 설정에서 탈퇴할 수 없습니다.',
  LAST_ACTIVE_ADMIN_DELETE_NOT_ALLOWED: '마지막 활성 관리자 계정은 삭제할 수 없습니다.',
  USER_DELETE_REAUTHENTICATION_REQUIRED: '사용자 삭제를 수행하려면 관리자 재인증이 필요합니다.',
  CLUSTER_PERSONA_DISABLED: '추가 페르소나 기능이 현재 비활성화되어 있습니다.',
  CLUSTER_PERSONA_NOT_ALLOWED: '현재 선택할 수 없는 페르소나입니다.',
  CLUSTER_PERSONA_NOT_FOUND: '페르소나를 찾을 수 없습니다.',
  CLUSTER_PERSONA_SELECTION_REQUIRED: '사용 가능한 페르소나가 아직 설정되지 않았습니다.',
  CLUSTER_PERSONA_LIMIT_EXCEEDED: '사용 가능한 페르소나 수 제한을 초과했습니다.',
  PROJECT_PERSONA_SELECTION_NOT_ALLOWED: '현재 프로젝트에서는 이 페르소나를 선택할 수 없습니다.',
  MARKETING_CONTENT_NOT_FOUND: '마케팅 콘텐츠를 찾을 수 없습니다.',
  MARKETING_CONTENT_ACCESS_DENIED: '이 프로젝트의 마케팅 콘텐츠에 접근할 수 없습니다.',
  MARKETING_CONTENT_INVALID_SIZE: '콘텐츠 크기는 가로·세로 각각 320px 이상 4096px 이하여야 합니다.',
  MARKETING_CONTENT_INVALID_FORMAT: '콘텐츠 규격 또는 디자인 설정을 확인해 주세요.',
  MARKETING_CONTENT_SOURCE_UNAVAILABLE: 'Marketing Source를 확정할 수 없습니다. Concept 선택과 가설 결정을 확인해 주세요.',
  TECH_OPS_PREPARATION_REQUIRED: '기술·운영 입력 준비를 먼저 시작해 주세요.',
  TECH_OPS_INPUT_INVALID: '기술·운영 입력값을 확인해 주세요.',
  TECH_OPS_PROPOSAL_INVALID: '확정할 기술·운영 제안 값을 확인해 주세요.',
  TECH_OPS_EVIDENCE_INVALID: '등록할 실제 근거 자료를 확인해 주세요.',
  TECH_OPS_SNAPSHOT_NOT_READY: '기술·운영 분석에 필요한 입력과 결정을 완료해 주세요.',
  TECH_OPS_SNAPSHOT_IMMUTABLE: '이미 확정한 기술·운영 입력 Snapshot은 수정할 수 없습니다.',
  MARKETING_CONTENT_COPY_GENERATION_UNAVAILABLE: '새 카피 초안을 만들지 못했습니다. 기존 카피는 유지됩니다.',
  MARKETING_CONTENT_VERSION_CONFLICT: '다른 변경사항이 먼저 저장되었습니다. 최신 시안을 다시 불러와 주세요.',
  MARKETING_PANEL_INTERVIEW_INVALID: '이 프로젝트에서 사용할 수 없는 패널 인터뷰입니다.',
  MARKETING_MARKET_RESPONSE_INVALID: '이 프로젝트에서 사용할 수 없는 시장 반응 결과입니다.',
  MARKETING_VALIDATION_RESULT_NOT_COMPLETED: '완료된 검증 결과만 마케팅 콘텐츠에 반영할 수 있습니다.',
  MARKETING_SOURCE_REFRESH_FAILED: '검증 결과를 다시 불러오지 못했습니다. 기존 카피와 근거는 유지됩니다.',
  MARKETING_EXPORT_OVERFLOW: '긴 문구가 출력 영역을 벗어납니다. 문구를 줄인 뒤 다시 시도해 주세요.',
  MARKETING_ASSET_INVALID: '업로드한 이미지 파일을 사용할 수 없습니다.',
  PANEL_INTERVIEW_NOT_FOUND: '패널 인터뷰를 찾을 수 없습니다.',
  PANEL_INTERVIEW_INVALID_PERSONA: '현재 인터뷰에 사용할 수 없는 Persona입니다.',
  PANEL_INTERVIEW_PERSONA_LIMIT_EXCEEDED: '인터뷰 Persona는 최대 3개까지 선택할 수 있습니다.',
  PANEL_INTERVIEW_QUESTION_REQUIRED: '인터뷰 질문을 최소 3개 입력해 주세요.',
  PANEL_INTERVIEW_QUESTION_LIMIT_EXCEEDED: '인터뷰 질문은 최대 10개이며 질문별 300자 이하여야 합니다.',
  PANEL_INTERVIEW_RUN_FAILED: '예상 인터뷰를 만들지 못했습니다. 입력 내용을 확인한 뒤 다시 시도해 주세요.',
  MARKET_RESPONSE_NOT_FOUND: '시장 반응 예측을 찾을 수 없습니다.',
  MARKET_RESPONSE_INVALID_PERSONA: '현재 시장 반응 예측에 사용할 수 없는 Persona입니다.',
  MARKET_RESPONSE_MESSAGE_REQUIRED: '비교할 메시지를 최소 1개 입력해 주세요.',
  MARKET_RESPONSE_MESSAGE_LIMIT_EXCEEDED: '비교 메시지는 최대 3개까지 입력할 수 있습니다.',
  MARKET_RESPONSE_RUN_FAILED: '예상 시장 반응을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.',
  FINANCIAL_ANALYSIS_NOT_FOUND: '재무 분석을 찾을 수 없습니다.',
  FINANCIAL_INPUT_INVALID: '재무 입력 문서를 확인해 주세요.',
  FINANCIAL_PREPARATION_REQUIRED: '재무 입력 문서를 먼저 준비해 주세요.',
  FINANCIAL_SNAPSHOT_NOT_READY: '재무 분석에 필요한 입력을 완료해 주세요.',
  FINANCIAL_SNAPSHOT_IMMUTABLE: '확정된 재무 입력은 직접 변경할 수 없습니다.',
  FINANCIAL_FEASIBILITY_REQUIRED: '완료된 사업 타당성 분석이 필요합니다.',
  FINANCIAL_SOURCE_INVALID: '재무 분석 근거를 확인할 수 없습니다.',
  FINANCIAL_ASSUMPTION_INVALID: '재무 가정을 확인해 주세요.',
  FINANCIAL_SCENARIO_REQUIRED: '보수·기준·낙관 시나리오가 필요합니다.',
  FINANCIAL_SCENARIO_LIMIT_EXCEEDED: '시나리오는 최대 3개까지 설정할 수 있습니다.',
  FINANCIAL_CALCULATION_UNAVAILABLE: '현재 가정으로는 재무 계산을 완료할 수 없습니다.',
  FINANCIAL_ALREADY_COMPLETED: '완료된 분석은 복제해 새로 수정할 수 있습니다.',
  UNAUTHORIZED: '로그인이 필요합니다.',
  FORBIDDEN: '이 작업을 수행할 권한이 없습니다.',
  NOT_FOUND: '요청한 정보를 찾을 수 없습니다.',
  CONFLICT: '다른 변경사항이 반영되었습니다. 최신 내용을 확인해 주세요.',
  IDEMPOTENCY_CONFLICT: '같은 요청 식별자로 다른 내용을 처리할 수 없습니다. 다시 시도해 주세요.',
  RESOURCE_VERSION_CONFLICT: '다른 변경사항이 먼저 저장되었습니다. 최신 내용을 확인해 주세요.',
  PLAN_NOT_EDITABLE: '확정된 사업계획은 더 이상 수정할 수 없습니다.',
  PLAN_ALREADY_CONFIRMED: '이미 확정된 사업계획입니다.',
  PLAN_INCOMPLETE: '필수 보완 항목을 모두 해결한 뒤 확정해 주세요.',
  MISSING_FIELD_NOT_FOUND: '보완 항목을 찾을 수 없습니다. 최신 내용을 다시 불러와 주세요.',
  STRUCTURED_PLAN_NOT_FOUND: '최신 구조화 결과를 찾을 수 없습니다.',
  PROJECT_STAGE_INVALID: '현재 프로젝트 단계에서는 이 작업을 진행할 수 없습니다.',
  ANALYSIS_ALREADY_RUNNING: '동일한 AI 작업이 이미 진행 중입니다. 새 요청을 보내지 말고 현재 상태를 확인해 주세요.',
  AI_CONFIGURATION_INVALID: 'AI Provider, 모델 또는 API Key 설정을 확인해 주세요.',
  AI_RESULT_INVALID: 'AI 응답이 현재 Contract를 충족하지 못했습니다. 입력과 서버 로그를 확인해 주세요.',
  EXTERNAL_AI_SERVICE_UNAVAILABLE: 'AI 서비스에 일시적으로 연결할 수 없습니다. 재시도 가능 여부를 확인해 주세요.',
  AI_SERVICE_UNAVAILABLE: 'AI 서비스에 일시적으로 연결할 수 없습니다. 재시도 가능 여부를 확인해 주세요.',
  TASK_TIMEOUT: 'AI 작업이 제한 시간을 초과했습니다. 현재 실행 상태를 확인해 주세요.',
  NETWORK_ERROR: '네트워크 연결을 확인한 뒤 다시 시도해 주세요.',
  REQUEST_ABORTED: '요청이 취소되었습니다. 다시 시도해 주세요.',
};

export function getUserErrorMessage(error) {
  return USER_MESSAGE_BY_CODE[error?.code] ?? '요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.';
}
